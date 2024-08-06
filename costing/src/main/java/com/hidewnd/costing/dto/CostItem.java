package com.hidewnd.costing.dto;

import com.hidewnd.costing.costant.FormulasEnum;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class CostItem implements Serializable {

    private String server;
    /**
     * 配方名称
     */
    private String formulaName;

    private FormulasEnum type;

    private String materialId;

    /**
     * 数量
     */
    private Integer number;

    /**
     * 合计成本
     */
    private int cost;
    private String costString;

    /**
     * 合计市场价格
     */
    private int value;
    private String valueString;

    private Map<String, Material> requiredMap;

}
