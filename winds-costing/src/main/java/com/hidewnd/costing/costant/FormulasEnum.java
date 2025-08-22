package com.hidewnd.costing.costant;

import lombok.Getter;

/**
 * FormulasEnum 枚举类
 * 用于表示不同的配方类型，包含食物、缝纫、制药、锻造和梓匠等类型
 * 使用@Getter注解自动为每个枚举值生成getter方法
 */
@Getter
public enum FormulasEnum {
    // 食物配方类型
    COOKING("cooking"),
    // 缝纫配方类型
    TAILORING("tailoring"),
    // 制药配方类型
    MEDICINE("medicine"),
    // 锻造配方类型
    FOUNDING("founding"),
    // 梓匠配方类型
    FURNITURE("furniture"),

    ; // 枚举常量结束标记


    // 配方类型的字符串表示
    private final String type;

    /**
     * 构造方法
     * @param type 配方类型的字符串表示
     */
    FormulasEnum(String type) {
        this.type = type;
    }

    /**
     * 根据字符串获取对应的枚举值
     * @param cacheType 输入的字符串类型
     * @return 对应的枚举值，如果找不到则返回null
     */
    public static FormulasEnum getFormulasEnum(String cacheType) {
        return switch (cacheType.trim().toLowerCase()) {
            case "cooking" -> FormulasEnum.COOKING;
            case "tailoring" -> FormulasEnum.TAILORING;
            case "medicine" -> FormulasEnum.MEDICINE;
            case "founding" -> FormulasEnum.FOUNDING;
            case "furniture" -> FormulasEnum.FURNITURE;
            default -> null;
        };
    }
}
