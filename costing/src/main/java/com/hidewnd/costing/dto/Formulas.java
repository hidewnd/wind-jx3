package com.hidewnd.costing.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class Formulas implements Serializable {

    private String id;

    private String materialId;

    /**
     * 配方名称
     */
    private String formulaName;

    /**
     * 消耗精力
     */
    private Integer energies;

    private String type;

    private Integer createMin;
    private Integer createMax;



    /**
     * 配方材料
     */
    private List<Material> items;

    private int times;
    /**
     * 成本价格
     */
    private int price;

    /**
     * 交易价格
     */
    private int tradingPrice;

}
