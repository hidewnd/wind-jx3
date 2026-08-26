package com.hidewnd.winds.costing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hidewnd.winds.common.base.response.R;
import com.hidewnd.winds.costing.dto.*;
import com.hidewnd.winds.costing.dto.request.CostItem;
import com.hidewnd.winds.costing.dto.request.CostItemRequest;
import com.hidewnd.winds.costing.dto.request.CostListRequest;
import com.hidewnd.winds.costing.handler.FormulaBuilder;
import com.hidewnd.winds.costing.handler.FormulaParseAdapter;
import com.hidewnd.winds.costing.service.CacheService;
import com.hidewnd.winds.costing.service.CostingService;
import com.hidewnd.winds.costing.service.Jx3BoxRemote;
import com.hidewnd.winds.costing.utils.BoxUtils;
import com.hidewnd.winds.costing.utils.CostCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
        FormulaBuilder formulaBuilder = FormulaBuilder.create(rangeCreate)
                .addFormula(request.getFormulaName(), request.getNumber())
                .parseFormulas(formulaParseAdapter)
                .analysisMaterial();
        CostItemResult result = new CostItemResult();
        result.setNumber(request.getNumber());
        result.setServer(server);
        Formulas formulas = formulaBuilder.getByName(request.getFormulaName());
        if (formulas != null) {
            result.setFormulaName(formulas.getFormulaName());
            result.setMaterialId(formulas.getMaterialId());
            result.setType(formulas.getType());
            int actualNumber = formulaBuilder.getActualNumber(formulas.getFormulaName());
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
        formulaBuilder.analysisMaterial();
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
                int actualNumber = formulaBuilder.getActualNumber(formulas.getFormulaName());
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
        long tradingPrice = queryPriceWithRetry(server, result.getMaterialId(), result.getActualNumber());
        result.setValue(tradingPrice);
        result.setValueString(BoxUtils.computePrice(tradingPrice));
        // 使用CostCalculator计算实际利润
        BigDecimal fee = new BigDecimal(feeRate);
        long custodyFee = CostCalculator.DEFAULT_CUSTODY_FEE;
        long actualProfit = CostCalculator.calculateProfit(tradingPrice, totalCost, fee, custodyFee);
        result.setActualProfit(actualProfit);
        result.setActualProfitString(BoxUtils.computePrice(result.getActualProfit()));
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
            totalTradingPrice += queryPriceWithRetry(server, resultItem.getMaterialId(), resultItem.getActualNumber());
        }
        result.setValue(totalTradingPrice);
        result.setValueString(BoxUtils.computePrice(totalTradingPrice));
        // 使用CostCalculator计算实际利润
        BigDecimal fee = new BigDecimal(feeRate);
        long custodyFee = CostCalculator.DEFAULT_CUSTODY_FEE;
        long actualProfit = CostCalculator.calculateProfit(totalTradingPrice, totalCost, fee, custodyFee);
        result.setActualProfit(actualProfit);
        result.setActualProfitString(BoxUtils.computePrice(result.getActualProfit()));
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
            // 设置30秒超时，防止永久等待
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("材料价格计算超时，已完成 {} / {} 个材料",
                    materials.size() - latch.getCount(), materials.size());
            }
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
        // 带重试的价格查询
        return queryPriceWithRetry(server, material.getId(), material.getNumber());
    }

    /**
     * 带重试的价格查询方法
     *
     * @param server 服务器
     * @param itemId 物品ID
     * @param number 数量
     * @return 价格
     */
    private long queryPriceWithRetry(String server, String itemId, int number) {
        int maxRetries = 3;
        long lastError = 0;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return jx3BoxRemote.queryPrice(server, itemId, number);
            } catch (Exception e) {
                lastError = 0; // 查询失败返回0
                log.warn("价格查询失败 (尝试 {}/{}): {} - {}, 错误: {}",
                    attempt, maxRetries, itemId, number, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        // 指数退避: 1s, 2s, 4s
                        Thread.sleep(1000L * (1 << (attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("价格查询重试{}次后失败: {} - {}", maxRetries, itemId, number);
        return lastError;
    }

}