package com.hidewnd.costing.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hidewnd.common.base.CommonException;
import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.*;
import com.hidewnd.costing.service.CacheService;
import com.hidewnd.costing.service.CostingService;
import com.hidewnd.costing.service.Jx3BoxRemote;
import com.hidewnd.costing.utils.BoxUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service("costingService")
public class CostingServiceImpl implements CostingService {

    public static final String CACHE_FORMULAS_REQUIRED = "costing:formulas:required:";
    public static final String CACHE_COST_ITEM = "costing:cost:item:";
    public static final String CACHE_COST_LIST = "costing:cost:list:";

    @Value("${box.cache.time:60}")
    private Integer cacheTime;

    @Value("${box.cache.result.time:120}")
    private Integer resultCacheTime;

    @Value("${box.default.server:剑胆琴心}")
    private String defaultServer;

    @Value("${box.default.fee-rate:0.05}")
    private String feeRate;

    private Jx3BoxRemote jx3BoxRemote;

    private CacheService cacheService;

    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;


    @Autowired
    @Qualifier("redisCacheService")
    public void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Autowired
    public void setJx3BoxRemote(Jx3BoxRemote jx3BoxRemote) {
        this.jx3BoxRemote = jx3BoxRemote;
    }

    @Override
    public R<CostItemResult> queryCosting(CostItemRequest request) {
        CostItemResult result = new CostItemResult();
        result.setServer(StrUtil.emptyToDefault(request.getServer(), defaultServer));
        if (StrUtil.isEmpty(request.getFormulaName())) {
            throw new CommonException("未找到配方名称");
        }
        request.setFormulaName(request.getFormulaName().replaceFirst("\\[", "")
                .replaceFirst("]", ""));
        Map<String, Object> map = new HashMap<>();
        map.put("formulaName", request.getFormulaName());
        map.put("number", request.getNumber() == null ? 1 : request.getNumber());
        // 缓存结果直接返回
        String resultKey = StrUtil.format("{}{}_{}", CACHE_COST_ITEM,
                request.getServer(), DigestUtil.sha1Hex(JSONArray.toJSONString(map)));
        result = cacheService.getObject(resultKey, CostItemResult.class);
        if (result != null) {
            return R.successByObj(result);
        }
        Map<String, Material> required = new HashMap<>();
        result = parseFormula(request.getFormulaName(), request.getNumber(), request.getRangeCreate(), required);
        computerCostValue(request, result, required);
        cacheService.set(resultKey, JSONObject.toJSONString(result), resultCacheTime, TimeUnit.SECONDS);
        return R.successByObj(result);
    }

    private CostItemResult parseFormula(String formulaName, Integer number, Boolean rangeCreate,
                                        Map<String, Material> required) {
        CostItemResult result = new CostItemResult();
        result.setFormulaName(formulaName);
        result.setNumber(number);
        Map<String, Material> map = new HashMap<>();
        // 查询配方及所需材料
        Formulas formulas = jx3BoxRemote.queryFormulasAndNumber(null, formulaName);
        List<CostDetailDto> makeList = new ArrayList<>();
        //总计制作次数
        result.setMaterialId(formulas.getMaterialId());
        parseFormula(formulas, number, rangeCreate, makeList, required);
        result.setMakeDetail(makeList);
        return result;
    }

    private void parseFormula(Formulas formulas, Integer number, Boolean rangeCreate,
                              List<CostDetailDto> makeList, Map<String, Material> required) {
        //总计制作次数
        int totalTimes = randomNumber(formulas, number, rangeCreate, makeList);
        formulas.setTimes(totalTimes);
        int energies = formulas.getEnergies() * totalTimes;
        for (Material item : formulas.getItems()) {
            if (item.getFormulas() != null) {
                parseFormula(item.getFormulas(), item.getNumber() * totalTimes, rangeCreate, makeList, required);
                energies += item.getFormulas().getEnergies() * totalTimes;
                continue;
            }
            setMaterialNumber(required, item, item.getNumber() * totalTimes);
        }
        formulas.setEnergies(energies);
        formulas.setMakeList(makeList);
    }


    private int randomNumber(Formulas formulas, int totalNum, Boolean rangeCreate, List<CostDetailDto> makeList) {
        rangeCreate = rangeCreate == null || rangeCreate;
        Integer createMin = formulas.getCreateMin();
        Integer createMax = formulas.getCreateMax();
        // 一次制作成本totalPrice 获取min-max个 概率计算次数
        int num = 0;
        while (totalNum > 0) {
            int makeNum = rangeCreate ? RandomUtil.randomInt(createMin, createMax, true, true) : createMin;
            makeList.add(new CostDetailDto(num, formulas.getFormulaName(), makeNum));
            totalNum -= makeNum;
            num++;
        }
        return num;
    }

    private static void setMaterialNumber(Map<String, Material> required, Material material, int number) {
        Material mt1 = required.getOrDefault(material.getName(), null);
        if (mt1 == null) {
            mt1 = new Material();
            mt1.setName(material.getName());
            mt1.setId(material.getId());
            mt1.setSourceId(material.getSourceId());
            mt1.setNumber(number);
        } else {
            mt1.setNumber(mt1.getNumber() + number);
        }
        required.put(material.getName(), mt1);
    }

    private void addRequireMaterial(Map<String, Material> map, Map<String, Material> required) {
        if (CollectionUtil.isEmpty(map)) {
            return;
        }
        map.forEach((k, v) -> {
            Material material = required.get(k);
            if (material == null) {
                required.put(k, v);
                return;
            }
            material.setNumber(material.getNumber() + v.getNumber());
        });
    }

    private void computerCostValue(CostItemRequest request, CostItemResult result, Map<String, Material> required) {
        // 成本价格计算
        result.setRequiredMap(required);
        long totalCostValue = computeCostValue(request.getServer(), required);
        result.setCost(totalCostValue);
        result.setCostString(BoxUtils.computePrice(totalCostValue));
        // 交易行价格
        long tradingPrice = jx3BoxRemote.queryPrice(request.getServer(), result.getMaterialId(), request.getNumber());
        result.setValue(tradingPrice);
        result.setValueString(BoxUtils.computePrice(tradingPrice));
        // 计算实际收益
        long fees = new BigDecimal(result.getValue() / request.getNumber()).multiply(new BigDecimal(feeRate)).longValue();
        long actualProfit = result.getValue() - result.getCost() - fees * request.getNumber();
        result.setActualProfit(actualProfit);
        result.setActualProfitString(BoxUtils.computePrice(actualProfit));
    }

    private long computeCostValue(String server, Map<String, Material> required) {
        AtomicLong totalCostValue = new AtomicLong(0);
        CountDownLatch countDownLatch = new CountDownLatch(required.size());
        if (!required.isEmpty()) {
            // 查询材料价格
            for (Map.Entry<String, Material> entry : required.entrySet()) {
                asyncTaskExecutor.submitCompletable(() -> {
                    Material material = entry.getValue();
                    String value = cacheService.getString(Jx3BoxRemoteImpl.CACHE_NAME_SPACE + material.getId());
                    if (StrUtil.isNotEmpty(value)) {
                        totalCostValue.addAndGet(Long.parseLong(value));
                        countDownLatch.countDown();
                        return;
                    }
                    totalCostValue.addAndGet(jx3BoxRemote.queryPrice(server, material.getId(), material.getNumber()));
                    countDownLatch.countDown();
                });
            }
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return totalCostValue.get();
    }


    @Override
    public R<CostList> queryCostingList(CostList costList) {
        costList.setServer(StrUtil.emptyToDefault(costList.getServer(), defaultServer));
        if (CollectionUtil.isEmpty(costList.getItems())) {
            throw new CommonException("清单明细列表不能为空");
        }
        if (costList.getItems().stream().map(CostItem::getFormulaName).anyMatch(StrUtil::isEmpty)) {
            throw new CommonException("配方名称不能为空");
        }
        List<Map<String, Object>> info = costList.getItems().stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("formulaName", item.getFormulaName());
            map.put("number", item.getNumber() == null ? 1 : item.getNumber());
            return map;
        }).toList();
        String resultKey = StrUtil.format("{}{}_{}", CACHE_COST_LIST,
                costList.getServer(), DigestUtil.sha1Hex(JSONArray.toJSONString(info)));
        String resultJson = cacheService.getString(resultKey);
        if (StrUtil.isNotEmpty(resultJson)) {
            costList = JSONObject.parseObject(resultJson, CostList.class);
            return R.successByObj(costList);
        }
        // 解析配方
        Map<String, Material> required = new ConcurrentHashMap<>();
        // TODO
        return R.successByObj(costList);
    }


}
