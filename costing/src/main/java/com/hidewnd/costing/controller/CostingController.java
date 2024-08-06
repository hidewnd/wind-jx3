package com.hidewnd.costing.controller;

import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.CostItem;
import com.hidewnd.costing.service.CostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "技艺成本计算接口")
@RequestMapping("/costing")
public class CostingController {

    private CostingService costingService;

    @Autowired
    public void setCostingService(CostingService costingService) {
        this.costingService = costingService;
    }

    @PostMapping("/one")
    @Operation(summary = "查询单个技艺制品成本")
    public R<CostItem> costValue(@RequestBody CostItem costItem) {
        return costingService.queryCosting(costItem);
    }

}
