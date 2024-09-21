package com.hidewnd.costing.dto;

import com.hidewnd.costing.costant.FormulasEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class CostItemResult implements Serializable {

    @Schema(description = "服务器")
    private String server;

    @Schema(description = "技艺类型")
    private FormulasEnum type;

    @Schema(description = "技艺制品名称")
    private String formulaName;

    @Schema(description = "材料ID")
    private String materialId;

    @Schema(description = "需求数量")
    private Integer number;

    @Schema(description = "合计成本价")
    private Long cost;

    @Schema(description = "合计成本价格式化")
    private String costString;

    @Schema(description = "合计交易行价")
    private Long value;

    @Schema(description = "合计交易行价格式化")
    private String valueString;

    @Schema(description = "实际利润")
    private Long actualProfit;

    @Schema(description = "实际利润格式化")
    private String actualProfitString;

    @Schema(description = "合计所需材料数量")
    private Map<String, Material> requiredMap;

    @Schema(description = "制作明细")
    private List<CostDetailDto> makeDetail;

}
