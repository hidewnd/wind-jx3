package com.hidewnd.costing.dto;

import com.hidewnd.costing.costant.FormulasEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
@Schema(description = "技艺成本查询实体")
public class CostItem implements Serializable {

    @Schema(description = "服务器", requiredMode = Schema.RequiredMode.REQUIRED)
    private String server;

    @Schema(description = "技艺制品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String formulaName;

    @Schema(description = "技艺类型", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private FormulasEnum type;

    private String materialId;

    @Schema(description = "需求数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer number;

    /**
     * 合计成本
     */
    @Schema(description = "合计成本")
    private int cost;

    @Schema(description = "合计成本格式化")
    private String costString;

    /**
     * 合计市场价格
     */
    @Schema(description = "合计交易行价格")
    private int value;

    @Schema(description = "合计交易行价格格式化")
    private String valueString;

    @Schema(description = "合计所需材料数量")
    private Map<String, Material> requiredMap;

}
