package com.hidewnd.costing.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class Formulas implements Serializable {

    /**
     * 配方ID
     */
    private String id;

    /**
     * 制品ID
     */
    private String materialId;

    /**
     * 配方名称
     */
    private String formulaName;

    /**
     * 消耗精力
     */
    private Integer energies;

    /**
     * 出品数量最小值
     */
    private Integer createMin;

    /**
     * 出品数量最大值
     */
    private Integer createMax;

    /**
     * 配方材料
     */
    private List<Material> items;

    /**
     * 总计制作次数
     */
    private int times;


}
