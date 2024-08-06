package com.hidewnd.costing.controller;

import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.CostItem;
import com.hidewnd.costing.dto.CostList;
import com.hidewnd.costing.service.CostingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/costing")
public class CostingController {

    @Autowired
    private CostingService costingService;

    @GetMapping("/info")
    public R<String> getCostInfo() {
        return R.success("127.0.0.1");
    }


    @PostMapping("/list")
    public R<CostItem> costValue(@RequestBody CostItem costItem){
        return costingService.queryCosting(costItem);
    }

}
