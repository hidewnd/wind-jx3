package com.hidewnd.costing.dto;

import com.hidewnd.costing.dto.validate.RequestModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class CostList implements Serializable {

    @Schema(description = "服务器")
    private String server;

    @Schema(description = "清单明细列表")
    @NotNull(message = "清单明细列表不能为空", groups = RequestModel.class)
    private List<CostItem> items;

    @Schema(description = "合计成本")
    private Long cost;

    @Schema(description = "合计成本格式化")
    private String costString;

    @Schema(description = "合计交易行价格")
    private Long value;

    @Schema(description = "合计交易行价格格式化")
    private String valueString;

    @Schema(description = "实际利润")
    private Long actualProfit;

    @Schema(description = "实际利润格式化")
    private String actualProfitString;

    @Schema(description = "合计所需材料数量")
    private Map<String, Material> requiredMap;

    @Schema(description = "是否随机产出数量")
    private Boolean rangeCreate;
}
