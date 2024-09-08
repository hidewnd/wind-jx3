package com.hidewnd.costing;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.net.URLEncodeUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hidewnd.costing.dto.CostItem;
import com.hidewnd.costing.dto.Material;
import com.hidewnd.costing.utils.BoxUtils;
import com.hidewnd.costing.utils.RequestUtil;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
    void testPriceV2(){
        long price = queryPrice("剑胆琴心", "5_47642", 20);
        System.out.println( "V1: " + BoxUtils.computePrice(price));
        price = queryPriceV2("剑胆琴心", "5_47642", 20);
        System.out.println( "V2: " + BoxUtils.computePrice(price));
    }

    public long queryPriceV2(String server, String itemId, int number) {
        JSONObject params = new JSONObject();
        params.put("server", server);
        params.put("aggregate_type", "hourly");
        params.put("item_id", itemId);
        String body = RequestUtil.postRequest( "https://next2.jx3box.com/api/auction/", JSONObject.toJSONString(params));
        long remaining = number;
        long price = 0;
        if (StrUtil.isNotEmpty(body)) {
            List<JSONObject> array = JSONArray.parseArray(body, JSONObject.class);
            // 从小大排序
            array.sort(Comparator.comparingLong(o -> o.getLongValue("price", 0)));
            for (JSONObject object : array) {
                if (remaining <= 0) break;
                int count = object.getIntValue("sample", 0);
                long sub = count - remaining > 0 ? 0 : remaining - count;
                price += (remaining - sub) * object.getLongValue("price", 0);
                remaining -= sub == 0 ? remaining : sub;
            }
        }
        return price;
    }

    public long queryPrice(String server, String itemId, int number) {
        String body = RequestUtil.getRequest("https://next2.jx3box.com/api/item-price/{}/detail?server={}", itemId, URLEncodeUtil.encode(server));
        long remaining = number;
        long price = 0;
        if (StrUtil.isNotEmpty(body)) {
            JSONObject jsonObject = JSONObject.parseObject(body);
            if (jsonObject.getInteger("code") == 0 && jsonObject.get("data") != null) {
                JSONObject data = jsonObject.getObject("data", JSONObject.class);
                if (data.get("prices") == null) {
                    return price;
                }
                List<JSONObject> array = data.getObject("prices", JSONArray.class).toJavaList(JSONObject.class);
                // 从小大排序
                array.sort(Comparator.comparingLong(o -> o.getLongValue("unit_price", 0)));
                for (JSONObject object : array) {
                    if (remaining <= 0) break;
                    int count = object.getIntValue("n_count", 0);
                    long sub = count - remaining > 0 ? 0 : remaining - count;
                    price += (remaining - sub) * object.getLongValue("unit_price", 0);
                    remaining -= sub == 0 ? remaining : sub;
                }
            }
        }
        return price;
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
