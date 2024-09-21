package com.hidewnd.costing.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class Material implements Serializable {

    /**
     * 材料ID
     */
    private String id;
    @JsonIgnore
    private String uiId;
    @JsonIgnore
    private String sourceId;
    @JsonIgnore
    private String iconId;

    @Schema(description = "材料名称")
    private String name;

    @Schema(description = "所需材料数量")
    private Integer number;

    /**
     * 中间产物 配方信息
     */
    private Formulas formulas;

    @JsonIgnore
    private String desc;

    @JsonIgnore
    private String link;

}
