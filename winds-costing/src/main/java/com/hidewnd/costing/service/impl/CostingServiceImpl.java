package com.hidewnd.costing.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hidewnd.common.base.CommonException;
import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.*;
import com.hidewnd.costing.service.CacheService;
import com.hidewnd.costing.service.CostingService;
import com.hidewnd.costing.service.Jx3BoxRemote;
import com.hidewnd.costing.utils.BoxUtils;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service("costingService")
public class CostingServiceImpl implements CostingService {

    @Value("${box.default.server:剑胆琴心}")
    private String defaultServer;

    @Value("${box.default.fee-rate:0.05}")
    private String feeRate;

    private Jx3BoxRemote jx3BoxRemote;

    private CacheService cacheService;

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


    @Autowired
    public void setAsyncTaskExecutor(AsyncTaskExecutor asyncTaskExecutor) {
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    @Override
    public R<CostItemResult> queryCosting(CostItemRequest request) {
        CostItemResult result = new CostItemResult();
        result.setServer(StrUtil.emptyToDefault(request.getServer(), defaultServer));
        request.setFormulaName(request.getFormulaName()
                .replaceFirst("\\[", "")
                .replaceFirst("]", ""));
        boolean rangeCreate = request.getRangeCreate() == null ? Boolean.TRUE : request.getRangeCreate();
        Map<String, Material> required = new HashMap<>();
        // 解析配方 计算所需材料及次数
        result = parseFormula(request.getFormulaName(), request.getNumber(), rangeCreate, required);
        if (request.getNumber() == null && result.getActualNumber() != null) {
            request.setNumber(result.getNumber());
        }
        // 成本价格计算
        computerCostValue(request, result, required);
        return R.successByObj(result);
    }

    private CostItemResult parseFormula(String formulaName, Integer number, Boolean rangeCreate,
                                        Map<String, Material> required) {
        CostItemResult result = new CostItemResult();
        result.setFormulaName(formulaName);
        // 查询配方及所需材料
        Formulas formulas = jx3BoxRemote.queryFormulasAndNumber(null, formulaName);
        if (formulas == null) {
            throw new CommonException(R.CODE_PARAM_ERROR, "该配方不存在！");
        }
        if (number == null) {
            number = formulas.getCreateMin();
            rangeCreate = false;
        }
        result.setNumber(number);
        List<CostDetailDto> makeList = new ArrayList<>();
        //总计制作次数
        result.setMaterialId(formulas.getMaterialId());
        parseFormula(formulas, number, rangeCreate, makeList, required);
        result.setEnergies(formulas.getEnergies());
        result.setMakeDetail(makeList);
        result.setActualNumber(makeList.stream().map(CostDetailDto::getMakeNumber).reduce(0, Integer::sum));
        return result;
    }

    private void parseFormula(Formulas formulas, Integer number, Boolean rangeCreate,
                              List<CostDetailDto> makeList, Map<String, Material> required) {
        //总计制作次数
        int totalTimes = randomNumber(formulas, number, rangeCreate, makeList);
        log.info("制作对象{}, 总计制作次数:{}", StrUtil.emptyIfNull(formulas.getFormulaName()), totalTimes);
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
                    long price = -1;
                    if (StrUtil.isNotEmpty(value)) {
                        price = Long.parseLong(value) * material.getNumber();
                        log.info("computeCostValue 材料：{}({}) 数量：{} 价格：{}", material.getName(), material.getId(), material.getNumber(), price);
                    }
                    if (price == -1) {
                        price = jx3BoxRemote.queryPrice(server, material.getId(), material.getNumber());
                        log.info("computeCostValue 材料：{}({}) 数量：{} 价格：{}", material.getName(), material.getId(), material.getNumber(), price);
                    }
                    material.setValue(price);
                    material.setValueString(BoxUtils.computePrice(price));
                    totalCostValue.addAndGet(price);
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
    public R<CostList> queryCostingList(CostListRequest costList) {
        costList.setServer(StrUtil.emptyToDefault(costList.getServer(), defaultServer));
        // 解析配方
        return R.successByObj(null);
    }


}
