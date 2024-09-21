package com.hidewnd.costing.dto;

import com.hidewnd.costing.dto.validate.RequestModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "技艺成本查询体")
public class CostItemRequest implements Serializable {

    @Schema(description = "服务器", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String server;

    @NotBlank(message = "配方名称不能为空", groups = RequestModel.class)
    @Schema(description = "技艺制品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String formulaName;

    @NotBlank(message = "需求数量不能为空", groups = RequestModel.class)
    @Schema(description = "需求数量", requiredMode = Schema.RequiredMode.REQUIRED, minProperties=1)
    private Integer number;

    @Schema(description = "是否随机产出数量")
    private Boolean rangeCreate;

}
