package com.hidewnd.costing.costant;

import lombok.Getter;

@Getter
public enum FormulasEnum {
    // 食物
    COOKING("cooking"),
    // 缝纫
    TAILORING("tailoring"),
    // 制药
    MEDICINE("medicine"),
    // 锻造
    FOUNDING("founding"),
    // 梓匠
    FURNITURE("furniture"),

    ;


    private final String type;

    FormulasEnum(String type) {
        this.type = type;
    }
}
