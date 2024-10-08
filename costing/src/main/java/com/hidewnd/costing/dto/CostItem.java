package com.hidewnd.costing.dto;

import com.hidewnd.costing.costant.FormulasEnum;
import com.hidewnd.costing.dto.validate.RequestModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
@Schema(description = "技艺成本查询实体")
public class CostItem implements Serializable {

    @Schema(description = "材料ID")
    private String materialId;

    @Schema(description = "服务器", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String server;

    @Schema(description = "技艺类型", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private FormulasEnum type;

    @NotBlank(message = "配方名称不能为空", groups = RequestModel.class)
    @Schema(description = "技艺制品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String formulaName;

    @NotBlank(message = "需求数量不能为空", groups = RequestModel.class)
    @Schema(description = "需求数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer number;

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
