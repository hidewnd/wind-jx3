package com.hidewnd.costing.dto;

import com.hidewnd.costing.costant.FormulasEnum;
import lombok.Data;

import java.io.Serializable;

@Data
public class CostList implements Serializable {

    /**
     * 服务名
     */
    private String server;

    private FormulasEnum type;

    private String name;

    private Integer number;

    private Double price;

}
