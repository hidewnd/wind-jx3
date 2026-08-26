package com.hidewnd.winds.costing.service;

import com.hidewnd.winds.common.base.response.R;
import com.hidewnd.winds.costing.dto.request.CostItemRequest;
import com.hidewnd.winds.costing.dto.CostItemResult;
import com.hidewnd.winds.costing.dto.CostListResult;
import com.hidewnd.winds.costing.dto.request.CostListRequest;

public interface CostingService {
    /**
     * 查询单个技艺制品的成本价格及所需物品数量
     *
     * @param request 描述物品名称的CostItem对象
     * @return 包含物品名称、价格、所需物品名称及数量的CostItem对象
     */
    R<CostItemResult> queryCosting(CostItemRequest request);

    R<CostListResult> queryCostingList(CostListRequest request);
}
