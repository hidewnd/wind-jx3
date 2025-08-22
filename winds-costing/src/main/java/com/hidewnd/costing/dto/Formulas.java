package com.hidewnd.costing.dto;

import com.hidewnd.costing.costant.FormulasEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Schema(name = "配方信息", description = "配方详情信息")
@Data
@NoArgsConstructor
public class Formulas implements Serializable {

    /**
     *
     */
    @Schema(description = "配方ID")
    private String id;

    /**
     * 配方名称
     */
    @Schema(description = "配方名称")
    private String formulaName;

    /**
     * 配方分类
     */
    @Schema(description = "配方分类")
    private FormulasEnum type;

    /**
     * 制品ID
     */
     @Schema(description = "制品ID")
    private String materialId;


    /**
     * 消耗精力
     */
     @Schema(description = "消耗精力", example = "10")
    private Integer energies;

    /**
     * 出品数量最小值
     */
     @Schema(description = "出品数量最小值", example = "1")
    private Integer createMin;

    /**
     * 出品数量最大值
     */
     @Schema(description = "出品数量最大值", example = "1")
    private Integer createMax;

    /**
     * 配方材料
     */
     @Schema(description = "配方材料")
    private List<Material> items;

    /**
     * 总计制作次数
     */
     @Schema(description = "总计制作次数")
    private int times;


    private List<CostDetailDto> makeList;


}
