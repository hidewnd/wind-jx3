package com.hidewnd.costing.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class Material implements Serializable {

    /**
     * 材料ID
     */
    private String id;
    private String uiId;
    private String sourceId;
    private String iconId;

    /**
     * 名称
     */
    private String name;

    /**
     * 需求材料数量
     */
    private Integer number;

    /**
     * 原料价格
     */
    private int price;

    private Date searchDate;

    /**
     * 中间产物 配方信息
     */
    private Formulas formulas;

    private String desc;

    private String link;

}
