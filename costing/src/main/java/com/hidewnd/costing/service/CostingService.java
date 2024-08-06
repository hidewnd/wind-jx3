package com.hidewnd.costing.service;

import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.CostItem;
import com.hidewnd.costing.dto.CostList;

public interface CostingService {
    /**
     * 查询单个技艺制品的成本价格及所需物品数量
     * @param costItem 描述物品名称的CostItem对象
     * @return 包含物品名称、价格、所需物品名称及数量的CostItem对象
     */
    R<CostItem> queryCosting(CostItem costItem);
}