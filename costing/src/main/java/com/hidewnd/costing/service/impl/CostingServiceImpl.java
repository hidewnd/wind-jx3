package com.hidewnd.costing.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.hidewnd.common.base.CommonException;
import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.CostItem;
import com.hidewnd.costing.dto.CostList;
import com.hidewnd.costing.dto.Formulas;
import com.hidewnd.costing.dto.Material;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    public R<CostItem> queryCosting(CostItem costItem) {
        costItem.setServer(StrUtil.emptyToDefault(costItem.getServer(), defaultServer));
        if (StrUtil.isEmpty(costItem.getFormulaName())) {
            throw new CommonException("未找到配方名称");
        }
        costItem.setFormulaName(costItem.getFormulaName().replaceFirst("\\[", "").replaceFirst("]", ""));
        Map<String, Object> map = new HashMap<>();
        map.put("formulaName", costItem.getFormulaName());
        map.put("number", costItem.getNumber() == null ? 1 : costItem.getNumber());
        // 缓存结果直接返回
        String resultKey = StrUtil.format("{}{}_{}", CACHE_COST_ITEM, costItem.getServer(), DigestUtil.sha1Hex(JSONArray.toJSONString(map)));
        String costItemJson = cacheService.getString(resultKey);
        if (StrUtil.isNotEmpty(costItemJson)) {
            costItem = JSONObject.parseObject(costItemJson, CostItem.class);
            return R.successByObj(costItem);
        }
        Map<String, Material> required = new HashMap<>();
        parseFormula(costItem, required);
        // 成本价格计算
        costItem.setRequiredMap(required);
        long totalCostValue = computeCostValue(costItem.getServer(), required);
        costItem.setCost(totalCostValue);
        costItem.setCostString(BoxUtils.computePrice(totalCostValue));
        // 交易行价格
        long tradingPrice = jx3BoxRemote.queryPrice(costItem.getServer(), costItem.getMaterialId(), costItem.getNumber());
        costItem.setValue(tradingPrice);
        costItem.setValueString(BoxUtils.computePrice(tradingPrice));
        // 计算实际收益
        long fees = new BigDecimal(costItem.getValue() / costItem.getNumber()).multiply(new BigDecimal(feeRate)).longValue();
        long actualProfit = costItem.getValue() - costItem.getCost() - fees * costItem.getNumber();
        costItem.setActualProfit(actualProfit);
        costItem.setActualProfitString(BoxUtils.computePrice(actualProfit));
        cacheService.set(resultKey, JSONObject.toJSONString(costItem), resultCacheTime, TimeUnit.SECONDS);
        return R.successByObj(costItem);
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
        String resultKey = StrUtil.format("{}{}_{}", CACHE_COST_LIST, costList.getServer(), DigestUtil.sha1Hex(JSONArray.toJSONString(info)));
        String resultJson = cacheService.getString(resultKey);
        if (StrUtil.isNotEmpty(resultJson)) {
            costList = JSONObject.parseObject(resultJson, CostList.class);
            return R.successByObj(costList);
        }
        // 解析配方
        Map<String, Material> required = new ConcurrentHashMap<>();
        if (CollectionUtil.isNotEmpty(costList.getItems())) {
            List<CostItem> list = new CopyOnWriteArrayList<>(costList.getItems());
            CountDownLatch countDownLatch = new CountDownLatch(list.size());
            try {
                for (CostItem costItem : list) {
                    costItem.setServer(costList.getServer());
                    asyncTaskExecutor.submitCompletable(() -> {
                        CostItem item = parseFormula(costItem, required);
                        countDownLatch.countDown();
                        return item;
                    });
                }
                countDownLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            costList.setItems(list);
        }
        costList.setRequiredMap(required);
        // 成本价格计算
        long totalCostValue = computeCostValue(costList.getServer(), required);
        costList.setCost(totalCostValue);
        costList.setCostString(BoxUtils.computePrice(totalCostValue));
        // 查询交易行价格
        long totalValue = 0;
        long totalFees = 0;
        for (CostItem item : costList.getItems()) {
            long value = jx3BoxRemote.queryPrice(item.getServer(), item.getMaterialId(), item.getNumber());
            totalFees += new BigDecimal(value / item.getNumber()).multiply(new BigDecimal(feeRate)).longValue();
            totalValue += value;
        }
        costList.setValue(totalValue);
        costList.setValueString(BoxUtils.computePrice(totalValue));
        long actualProfit = totalValue - costList.getCost() - totalFees;
        costList.setActualProfit(actualProfit);
        costList.setActualProfitString(BoxUtils.computePrice(actualProfit));
        cacheService.set(resultKey, JSONObject.toJSONString(costList), resultCacheTime, TimeUnit.SECONDS);
        return R.successByObj(costList);
    }

    private long computeCostValue(String server, Map<String, Material> required) {
//        long totalCostValue = 0;
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

    private CostItem parseFormula(CostItem costItem, Map<String, Material> required) {
        costItem.setFormulaName(costItem.getFormulaName().replaceFirst("\\[", "").replaceFirst("]", ""));
        // 配方明细KEY
        String costingRequiredKey = StrUtil.format("{}{}_{}_{}", CACHE_FORMULAS_REQUIRED, costItem.getServer(), costItem.getFormulaName(), costItem.getNumber());
        // 查询配方分析记录
        String cacheJson = cacheService.getString(costingRequiredKey);
        Map<String, Material> map;
        if (StrUtil.isNotEmpty(cacheJson)) {
            map = JSONObject.parseObject(cacheJson, new TypeReference<>() {
            });
            addRequireMaterial(map, required);
            Formulas formulas = jx3BoxRemote.queryFormulasAndNumber(costItem.getType(), costItem.getFormulaName(), costItem.getNumber(), map);
            if (formulas != null && StrUtil.isNotEmpty(formulas.getMaterialId())) {
                costItem.setMaterialId(formulas.getMaterialId());
            } else {
                Material material = jx3BoxRemote.queryMaterialByName(costItem.getFormulaName());
                costItem.setMaterialId(material.getId());
            }
            return costItem;
        }
        // 查询配方及所需材料
        map = new HashMap<>();
        Formulas formulas = jx3BoxRemote.queryFormulasAndNumber(costItem.getType(), costItem.getFormulaName(), costItem.getNumber(), map);
        cacheService.set(costingRequiredKey, JSONObject.toJSONString(map), cacheTime, TimeUnit.SECONDS);
        addRequireMaterial(map, required);
        costItem.setMaterialId(formulas.getMaterialId());
        // 计算结果缓存 1min
        return costItem;
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


}
