package com.hidewnd.costing.dto.request;

import com.hidewnd.costing.dto.validate.RequestModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class CostItem implements Serializable {

    @NotBlank(message = "配方名称不能为空", groups = RequestModel.class)
    @Schema(description = "技艺制品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String formulaName;

    @Min(value = 1, message = "需求数量不能小于1", groups = RequestModel.class)
    @NotNull(message = "需求数量不能为空", groups = RequestModel.class)
    @Schema(description = "需求数量", requiredMode = Schema.RequiredMode.REQUIRED, minProperties = 1)
    private Integer number;
}
