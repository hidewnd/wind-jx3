package com.hidewnd.costing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostDetailDto implements Serializable {

    @Schema(description = "批次")
    private Integer no;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "产出数量")
    private Integer makeNumber;

}
