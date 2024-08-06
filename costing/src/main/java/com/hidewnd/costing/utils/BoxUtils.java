package com.hidewnd.costing.utils;

import cn.hutool.core.util.StrUtil;
import com.hidewnd.costing.costant.FormulasEnum;

import java.util.ArrayList;
import java.util.List;

public class BoxUtils {

    public static String computePrice(String price) {
        if (StrUtil.isEmpty(price)) {
            return "";
        }
        return computePrice(Integer.parseInt(price));
    }

    public static String computePrice(Integer value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        List<Integer> params = new ArrayList<>();
        value = computePrice(value, 100000000, builder, "{}砖", params);
        value = computePrice(value, 10000, builder, "{}金", params);
        value = computePrice(value, 100, builder, "{}银", params);
        if (value > 0) {
            builder.append("{}铜");
            params.add(value);
        }
        return StrUtil.format(builder.toString(), params.toArray());
    }

    public static int computePrice(int value, int threshold, StringBuilder builder, String format, List<Integer> params) {
        if (value > threshold) {
            int result = value / threshold;
            if (value % threshold >= 0) {
                value -= result * threshold;
            }
            builder.append(format);
            params.add(result);
        }
        return value;
    }


    public static FormulasEnum getFormulasEnum(String cacheType) {
        if (cacheType == null) {
            return null;
        }
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
