package com.hidewnd.costing.dto.request;

import com.hidewnd.costing.dto.validate.RequestModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "技艺成本清单查询体")
public class CostListRequest implements Serializable {

    @Schema(description = "服务器", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String server;

    @Schema(description = "是否随机产出数量, 默认为True")
    private Boolean rangeCreate;

    @Schema(description = "清单明细列表")
    @NotNull(message = "清单明细列表不能为空", groups = RequestModel.class)
    private List<CostItem> items;

}
