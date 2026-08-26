package com.hidewnd.winds.costing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Map;

/**
 * CostList类，用于存储成本相关的数据信息
 * 实现了Serializable接口，支持序列化操作
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CostListResult extends CostResult implements Serializable {

    @Schema(description = "清单")
    private Map<String, CostResultItem> formulas;

}
