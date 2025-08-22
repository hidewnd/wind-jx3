package com.hidewnd.costing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.*;
import com.hidewnd.costing.dto.request.CostItem;
import com.hidewnd.costing.dto.request.CostItemRequest;
import com.hidewnd.costing.dto.request.CostListRequest;
import com.hidewnd.costing.handler.FormulaBuilder;
import com.hidewnd.costing.handler.FormulaParseAdapter;
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
import java.math.RoundingMode;
import java.util.HashMap;
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
    private FormulaParseAdapter formulaParseAdapter;

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

    @Autowired
    public void setFormulaParseAdapter(FormulaParseAdapter formulaParseAdapter) {
        this.formulaParseAdapter = formulaParseAdapter;
    }

    @Override
    public R<CostItemResult> queryCosting(CostItemRequest request) {
        String server = StrUtil.emptyToDefault(request.getServer(), defaultServer);
        boolean rangeCreate = request.getRangeCreate() != null ? request.getRangeCreate() : true;
        request.setServer(server);
        FormulaBuilder formulaBuilder = FormulaBuilder.create(rangeCreate);
        formulaBuilder.addFormula(request.getFormulaName(), request.getNumber());
        formulaBuilder.parseFormulas(formulaParseAdapter);
        formulaBuilder.parseMaterial();

        CostItemResult result = new CostItemResult();
        result.setNumber(request.getNumber());
        result.setServer(server);
        Formulas formulas = formulaBuilder.getByName(request.getFormulaName());
        if (formulas != null) {
            result.setFormulaName(formulas.getFormulaName());
            result.setMaterialId(formulas.getMaterialId());
            result.setType(formulas.getType());
            int actualNumber = formulaBuilder.getMakeList().stream()
                    .filter(dto -> StrUtil.equals(dto.getName(), formulas.getFormulaName()))
                    .mapToInt(CostDetailDto::getMakeNumber).sum();
            result.setActualNumber(actualNumber);
        }
        result.setRequiredMap(formulaBuilder.getMaterialMap());
        result.setEnergies(formulaBuilder.getTotalEnergies());
        computeItemCost(result);
        return R.successByObj(result);
    }

    @Override
    public R<CostListResult> queryCostingList(CostListRequest request) {
        String server = StrUtil.emptyToDefault(request.getServer(), defaultServer);
        boolean rangeCreate = request.getRangeCreate() != null ? request.getRangeCreate() : true;
        request.setServer(server);
        FormulaBuilder formulaBuilder = FormulaBuilder.create(rangeCreate);
        for (CostItem item : request.getItems()) {
            formulaBuilder.addFormula(item.getFormulaName(), item.getNumber());
        }
        formulaBuilder.parseFormulas(formulaParseAdapter);
        formulaBuilder.parseMaterial();
        CostListResult result = new CostListResult();
        result.setServer(server);
        Map<String, CostResultItem> formulasMap = new HashMap<>();
        for (CostItem item : request.getItems()) {
            CostResultItem resultItem = new CostResultItem();
            resultItem.setNumber(item.getNumber());
            Formulas formulas = formulaBuilder.getByName(item.getFormulaName());
            if (formulas != null) {
                resultItem.setFormulaName(formulas.getFormulaName());
                resultItem.setMaterialId(formulas.getMaterialId());
                resultItem.setType(formulas.getType());
                int actualNumber = formulaBuilder.getMakeList().stream()
                        .filter(dto -> StrUtil.equals(dto.getName(), formulas.getFormulaName()))
                        .mapToInt(CostDetailDto::getMakeNumber).sum();
                resultItem.setActualNumber(actualNumber);
            }
            formulasMap.put(item.getFormulaName(), resultItem);
        }
        result.setFormulas(formulasMap);
        result.setRequiredMap(formulaBuilder.getMaterialMap());
        result.setEnergies(formulaBuilder.getTotalEnergies());
        computeListCost(result);
        return R.successByObj(result);
    }


    private void computeItemCost(CostItemResult result) {
        String server = result.getServer();
        Map<String, Material> requiredMap = result.getRequiredMap();
        // 成本价格
        long totalCost = computeMaterialCost(server, requiredMap);
        result.setCost(totalCost);
        result.setCostString(BoxUtils.computePrice(totalCost));
        // 实际产出所得在交易行的总价
        long tradingPrice = jx3BoxRemote.queryPrice(server, result.getMaterialId(), result.getActualNumber());
        result.setValue(tradingPrice);
        result.setValueString(BoxUtils.computePrice(tradingPrice));
        // 交易行手续费
        BigDecimal fee = new BigDecimal(feeRate);
        long totalFees = new BigDecimal(tradingPrice).multiply(fee)
                .setScale(0, RoundingMode.HALF_UP).longValue();
        // 实际利润：实际产出所得在交易行的总价 - 成本价格 - 交易行手续费
        result.setActualProfit(tradingPrice - totalCost - totalFees);
        result.setActualProfitString(BoxUtils.computePrice(tradingPrice - totalCost - totalFees));
    }

    private void computeListCost(CostListResult result) {
        String server = result.getServer();
        Map<String, Material> requiredMap = result.getRequiredMap();
        // 成本价格
        long totalCost = computeMaterialCost(server, requiredMap);
        result.setCost(totalCost);
        result.setCostString(BoxUtils.computePrice(totalCost));
        // 实际产出所得在交易行的总价
        long totalTradingPrice = 0;
        for (CostResultItem resultItem : result.getFormulas().values()) {
            totalTradingPrice += jx3BoxRemote.queryPrice(server, resultItem.getMaterialId(), resultItem.getActualNumber());
        }
        result.setValue(totalTradingPrice);
        result.setValueString(BoxUtils.computePrice(totalTradingPrice));
        // 交易行手续费
        BigDecimal fee = new BigDecimal(feeRate);
        long totalFees = new BigDecimal(totalTradingPrice).multiply(fee)
                .setScale(0, RoundingMode.HALF_UP).longValue();
        // 实际利润：实际产出所得在交易行的总价 - 成本价格 - 交易行手续费
        result.setActualProfit(totalTradingPrice - totalCost - totalFees);
        result.setActualProfitString(BoxUtils.computePrice(totalTradingPrice - totalCost - totalFees));
    }

    private long computeMaterialCost(String server, Map<String, Material> materials) {
        if (materials.isEmpty()) return 0;

        AtomicLong totalCost = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(materials.size());

        materials.values().forEach(material ->
                asyncTaskExecutor.submit(() -> {
                    try {
                        long price = getMaterialPrice(server, material);
                        material.setValue(price);
                        material.setValueString(BoxUtils.computePrice(price));
                        totalCost.addAndGet(price);
                    } catch (Exception e) {
                        log.error("材料价格计算异常: {}", material.getName(), e);
                    } finally {
                        latch.countDown();
                    }
                })
        );
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("成本计算被中断", e);
        }
        return totalCost.get();
    }

    private long getMaterialPrice(String server, Material material) {
        String cacheKey = Jx3BoxRemoteImpl.CACHE_NAME_SPACE + material.getId();
        String cached = cacheService.getString(cacheKey);

        if (StrUtil.isNotEmpty(cached)) {
            return (long) (Double.parseDouble(cached) * material.getNumber());
        }
        return jx3BoxRemote.queryPrice(server, material.getId(), material.getNumber());
    }

}