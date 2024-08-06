package com.hidewnd.costing.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class ItemPriceDto implements Serializable {
    private Date created;
    private String server;
    private Integer count;
    private Integer unitPrice;
}
