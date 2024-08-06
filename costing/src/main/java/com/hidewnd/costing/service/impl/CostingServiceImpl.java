package com.hidewnd.costing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.hidewnd.common.base.CommonException;
import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.costant.FormulasEnum;
import com.hidewnd.costing.dto.CostItem;
import com.hidewnd.costing.dto.Formulas;
import com.hidewnd.costing.dto.Material;
import com.hidewnd.costing.service.CacheService;
import com.hidewnd.costing.service.CostingService;
import com.hidewnd.costing.service.Jx3BoxRemote;
import com.hidewnd.costing.utils.BoxUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service("costingService")
public class CostingServiceImpl implements CostingService {

    public static final String ITEM_SEARCH = "https://helper.jx3box.com/api/item/search?keyword={}&page=1&limit=20";

    public static final String CACHE_FORMULAS_REQUIRED = "costing:formulas:required:";
    public static final String CACHE_COST_ITEM = "costing:cost:item:";

    @Value("${box.cache.time:60}")
    private Integer cacheTime;

    @Value("${box.cache.result.time:120}")
    private Integer resultCacheTime;

    @Autowired
    private Jx3BoxRemote jx3BoxRemote;

    @Autowired
    @Qualifier("redisCacheService")
    private CacheService cacheService;

    @Override
    public R<CostItem> queryCosting(CostItem costItem) {
        if (StrUtil.isEmpty(costItem.getFormulaName())) {
            throw new CommonException("未找到配方名称");
        }
        costItem.setFormulaName(costItem.getFormulaName().replaceFirst("\\[", "").replaceFirst("]",""));
        if (costItem.getNumber() == null) {
            costItem.setNumber(1);
        }
        // 缓存结果直接返回
        String costItemKey = CACHE_COST_ITEM + costItem.getFormulaName() + "_" + costItem.getNumber();
        String costItemJson = cacheService.getString(costItemKey);
        if (StrUtil.isNotEmpty(costItemJson)) {
            costItem = JSONObject.parseObject(costItemJson, CostItem.class);
            return R.successByObj(costItem);
        }
        // 查询是否存在分析记录缓存
        String costingRequiredKey = CACHE_FORMULAS_REQUIRED + costItem.getFormulaName() + "_" + costItem.getNumber();
        String cacheJson = cacheService.getString(costingRequiredKey);
        Map<String, Material> required = null;
        if (StrUtil.isNotEmpty(cacheJson)) {
            TypeReference<Map<String, Material>> typeRef = new TypeReference<>() {
            };
            required = JSONObject.parseObject(cacheJson, typeRef);
        }
        if (required == null) {
            required = new HashMap<>();
            if (costItem.getType() == null) {
                String cacheType = cacheService.getString(Jx3BoxRemoteImpl.MANUFACTURES_TYPE + costItem.getFormulaName());
                FormulasEnum formulasEnum = BoxUtils.getFormulasEnum(cacheType);
                costItem.setType(formulasEnum);
            }
            // 商人材料价格
            Formulas formulas = jx3BoxRemote.queryFormulasAndNumber(costItem.getType(), costItem.getFormulaName(), costItem.getNumber(), required);
            // 交易行价格
            int tradingPrice = jx3BoxRemote.queryPrice(costItem.getServer(), formulas.getMaterialId(), costItem.getNumber());
            costItem.setValue(tradingPrice);
            costItem.setValueString(BoxUtils.computePrice(tradingPrice));
            costItem.setMaterialId(formulas.getMaterialId());
            // 计算结果缓存 1min
            cacheService.set(costingRequiredKey, JSONObject.toJSONString(required), cacheTime, TimeUnit.SECONDS);
        }
        // 成本价格计算
        int totalCostValue = 0;
        for (Map.Entry<String, Material> entry : required.entrySet()) {
            Material entryValue = entry.getValue();
            String value = cacheService.getString(Jx3BoxRemoteImpl.CACHE_NAME_SPACE + entryValue.getId());
            if (StrUtil.isNotEmpty(value)) {
                totalCostValue += Integer.parseInt(value);
                continue;
            }
            totalCostValue += jx3BoxRemote.queryPrice(costItem.getServer(), entryValue.getId(), entryValue.getNumber());
        }
        costItem.setRequiredMap(required);
        costItem.setCost(totalCostValue);
        costItem.setCostString(BoxUtils.computePrice(totalCostValue));
        cacheService.set(costItemKey, JSONObject.toJSONString(costItem), resultCacheTime, TimeUnit.SECONDS);
        return R.successByObj(costItem);
    }


}
