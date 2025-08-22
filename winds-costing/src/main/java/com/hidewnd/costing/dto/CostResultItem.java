package com.hidewnd.costing.dto;

import com.hidewnd.costing.costant.FormulasEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class CostResultItem implements Serializable {

    @Schema(description = "技艺类型")
    private FormulasEnum type;

    @Schema(description = "技艺制品名称")
    private String formulaName;

    @Schema(description = "材料ID")
    private String materialId;

    @Schema(description = "所需数量")
    private Integer number;

    @Schema(description = "实际产出数量")
    private Integer actualNumber;
}
