package com.hidewnd.costing.controller;

import cn.hutool.core.util.StrUtil;
import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.CostItemRequest;
import com.hidewnd.costing.dto.CostItemResult;
import com.hidewnd.costing.dto.validate.RequestModel;
import com.hidewnd.costing.service.CostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Tag(name = "技艺成本计算接口")
@RequestMapping("/costing")
@CacheConfig(cacheNames = "cost")
public class CostingController {

    @Value("${server.port}")
    private String port;

    private CostingService costingService;

    @Autowired
    public void setCostingService(CostingService costingService) {
        this.costingService = costingService;
    }

    @PostMapping("/one")
    @Operation(summary = "查询单个技艺制品成本")
    @Cacheable(cacheNames = {"item"}, keyGenerator = "costingKeyGenerator")
    public R<CostItemResult> costValue(@Validated(RequestModel.class) @RequestBody CostItemRequest request) {
        return costingService.queryCosting(request);
    }


    @PostConstruct
    public void afterServerStart() {
        log.info(StrUtil.format("接口文档地址：http://localhost:{}/doc.html", port));
    }
}
