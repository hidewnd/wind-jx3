package com.hidewnd.costing;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson2.JSON;
import com.hidewnd.costing.dto.CostItem;
import com.hidewnd.costing.dto.Material;
import com.hidewnd.costing.utils.BoxUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class UtilTest {



    @Test
    void testCompute() {
        String priceString = BoxUtils.computePrice("47009100");
        assert "4700金91银".equals(priceString);
        long price = -47009100;
        String negativePriceString = BoxUtils.computePrice(price);
        assert "负4700金91银".equals(negativePriceString);

    }


    @Test
    void  test1(){
        Map<String, Material> required = new HashMap<>();
        Map<String, Material> map = new HashMap<>();
        Material material = new Material();
        material.setNumber(2);
        map.put("1", material);
        Material material2 = new Material();
        material2.setNumber(3);
        map.put("2", material2);
        CostItem costItem = new CostItem();
        costItem.setRequiredMap(map);
        addRequireMaterial(costItem, required);
        addRequireMaterial(costItem, required);
        System.out.println(JSON.toJSONString(required));
    }

    private void addRequireMaterial(CostItem costItem, Map<String, Material> required) {
        if (CollectionUtil.isEmpty(costItem.getRequiredMap())) {
            return;
        }
        costItem.getRequiredMap().forEach((k, v) -> {
            Material material = required.get(k);
            if (material == null) {
                required.put(k, v);
                return;
            }
            material.setNumber(material.getNumber() + v.getNumber());
        });
    }


}
