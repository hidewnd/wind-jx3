package com.hidewnd.winds.costing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@Schema(description = "技艺成本查询体")
@EqualsAndHashCode(callSuper = true)
public class CostItemRequest extends CostItem implements Serializable {

    @Schema(description = "服务器", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String server;


    @Schema(description = "是否随机产出数量, 默认为True")
    private Boolean rangeCreate;

}
