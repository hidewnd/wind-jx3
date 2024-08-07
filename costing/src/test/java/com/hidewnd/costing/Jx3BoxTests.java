package com.hidewnd.costing;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.net.URLEncodeUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hidewnd.costing.costant.FormulasEnum;
import com.hidewnd.costing.dto.Formulas;
import com.hidewnd.costing.dto.ItemPriceDto;
import com.hidewnd.costing.dto.Material;
import com.hidewnd.costing.utils.BoxUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

class Jx3BoxTests {
    public static final String ITEM_SEARCH = "https://helper.jx3box.com/api/item/search?keyword={}&page=1&limit=20";
    public static final String ITEM_PRICE = "https://next2.jx3box.com/api/item-price/{}/detail?server={}";
    public static final String ITEM_FORMULAS = "https://node.jx3box.com/manufactures?client=std&type={}&name={}";
    public static final String ITEM_MERGED = "https://node.jx3box.com/resource/std/item_merged.{}";
    public static final String CRAFT_PRICE = "https://node.jx3box.com/craft/price?client=std";

    public static final String CACHE_FORMULAS_REQUIRED = "costing:formulas:required:";


    @Test
    void testCompute() {
        String price = "47009100";
        System.out.println(BoxUtils.computePrice(price));
    }

    @Test
    void textStringTemplate() {
        String cache = "aaaa";
        String cache2 = null;
        String cache3 = "cccc";
        long start = System.currentTimeMillis();
        String template = STR."CACHE_FORMULAS_REQUIRED\{cache}_\{cache2}_\{cache3}";
        System.out.println(System.currentTimeMillis() - start);
        System.out.println(template);
    }

    @Test
    void textStringTemplate2() {
        String cache = "aaaa";
        String cache2 = null;
        String cache3 = "cccc";
        long start = System.currentTimeMillis();
        String template = StrUtil.format("{}_{}_{}", CACHE_FORMULAS_REQUIRED, cache, cache2, cache3);
        System.out.println(System.currentTimeMillis() - start);
        System.out.println(template);
    }

    @Test
    void contextLoads() {
        JSONObject jsonObject = queryItem("精肉");
        if (jsonObject != null) {
            long total = queryPrice(jsonObject.getString("id"), 10);
            System.out.println(BoxUtils.computePrice(total));
        }
    }


    public int queryPrice(String itemId, int number) {
        String body = getRequest(ITEM_PRICE, itemId, URLEncodeUtil.encode("剑胆琴心"));
        int remaining = number;
        int price = 0;
        if (StrUtil.isNotEmpty(body)) {
            JSONObject jsonObject = JSONObject.parseObject(body);
            if (jsonObject.getInteger("code") == 0 && jsonObject.get("data") != null) {
                JSONObject data = jsonObject.getObject("data", JSONObject.class);
                if (data.get("prices") == null) {
                    return price;
                }
                List<JSONObject> array = data.getObject("prices", JSONArray.class).toJavaList(JSONObject.class);
                // 从小大排序
                array.sort(Comparator.comparingInt(o -> o.getIntValue("unit_price", 0)));
                for (JSONObject object : array) {
                    if (remaining <= 0) break;
                    int count = object.getIntValue("n_count", 0);
                    int sub = count - remaining > 0 ? 0 : remaining - count;
                    price += (remaining - sub) * object.getIntValue("unit_price", 0);
                    remaining -= sub == 0 ? remaining : sub;
                }
            }
        }
        return price;
    }

    @Test
    void testGetCraftPrice() {
        Map<String, Integer> craftPrice = getCraftPrice();

    }

    public Map<String, Integer> getCraftPrice() {
        Map<String, Integer> cache = new HashMap<>();
        String body = getRequest(CRAFT_PRICE);
        if (StrUtil.isNotEmpty(body)) {
            List<JSONObject> array = JSONArray.parseArray(body, JSONObject.class);
            for (JSONObject jsonObject : array) {
                cache.put(StrUtil.format("{}_{}", 5, jsonObject.getString("ItemIndex")), jsonObject.getIntValue("Price", 0));
            }
        }
        return cache;
    }


    public ItemPriceDto itemPriceAdapter(JSONObject jsonObject) {
        ItemPriceDto dto = new ItemPriceDto();
        dto.setServer(jsonObject.getString("server"));
        dto.setCount(Integer.parseInt(jsonObject.getString("n_count")));
        dto.setUnitPrice(Integer.parseInt(jsonObject.getString("unit_price")));
        if (jsonObject.get("created") != null) {
            dto.setCreated(jsonObject.getDate("created", new Date()));
        }
        return dto;
    }

    public JSONObject queryItem(String name) {
        JSONObject json = new JSONObject();
        String body = getRequest(ITEM_SEARCH, name);
        if (StrUtil.isNotEmpty(body)) {
            JSONObject jsonObject = JSONObject.parseObject(body);
            if (jsonObject.get("data") != null) {
                JSONObject data = jsonObject.getObject("data", JSONObject.class);
                List<JSONObject> array = JSONArray.parseArray(JSONObject.toJSONString(data.get("data")), JSONObject.class);
                if (CollectionUtil.isNotEmpty(array)) {
                    json = array.stream().filter(obj -> Objects.equals(obj.getString("Name"), name))
                            .filter(obj -> !obj.getString("Desc").contains("已过期"))
                            .reduce((first, second) -> second)
                            .orElse(null);
                    if (json == null) {
                        json = array.get(0);
                    }
                }
            }
        }
        return json;
    }

    public Material convertMaterial(JSONObject jsonObject) {
        Material material = new Material();
        material.setId(jsonObject.getString("id"));
        material.setUiId(jsonObject.getString("UiID"));
        material.setIconId(jsonObject.getString("IconID"));
        material.setSourceId(jsonObject.getString("SourceID"));
        material.setName(jsonObject.getString("Name"));
        material.setDesc(jsonObject.getString("Desc"));
        material.setLink(jsonObject.getString("Link"));
        return material;
    }

    public String getRequest(String format, Object... param) {
        String url = StrUtil.format(format, param);
        String body = HttpRequest.get(url).header(Header.ACCEPT, "gzip, deflate, br")
                .timeout(5000)
                .execute().body();
        return "[]".equals(body) ? "" : body;
    }


    @Test
    void testComputePrice() {
        String price = "25054496";
        String computePrice = BoxUtils.computePrice(price);
        System.out.println(computePrice);
    }


    @Test
    void testFormulas() {
        // 商人材料价格
        Map<String, Integer> craftPrice = getCraftPrice();
        int needNumber = 14;
        String needName = "酱料";
        Formulas formulas = queryFormulas(FormulasEnum.COOKING, needName, needNumber, craftPrice);
        if (formulas == null) {
            System.out.println("啥也没查到");
            return;
        }
        System.out.println("需求名称：" + needName);
        System.out.println("需求数量：" + needNumber);
        System.out.println("交易行价格：" + BoxUtils.computePrice(formulas.getTradingPrice()));
        System.out.println("制作次数：" + formulas.getTimes());
        System.out.println("制作成本：" + BoxUtils.computePrice(formulas.getPrice()));
        System.out.println("制作精力：" + formulas.getEnergies());
        BigDecimal fees = new BigDecimal(formulas.getTradingPrice()).divide(new BigDecimal(needNumber), 6, RoundingMode.HALF_UP).multiply(new BigDecimal("0.05")).multiply(new BigDecimal(needNumber));
        System.out.println("交易手续费：" + BoxUtils.computePrice(fees.longValue()));
        System.out.println("预计收益：" + BoxUtils.computePrice(formulas.getTradingPrice() - formulas.getPrice() - fees.intValue()));
    }


    public Formulas queryFormulas(FormulasEnum type, String name, Integer number, Map<String, Integer> craftPrice) {
        Formulas formulas = null;
        String body = getRequest(ITEM_FORMULAS, type.getType(), name);
        if (StrUtil.isNotEmpty(body)) {
            List<JSONObject> list = JSONArray.parseArray(body, JSONObject.class);
            if (CollUtil.isEmpty(list)) {
                return formulas;
            }
            JSONObject jsonObject = list.stream().filter(item -> StrUtil.equals(item.getString("Name"), name)).findFirst().orElse(null);
            if (jsonObject == null) {
                return formulas;
            }
            formulas = new Formulas();
            formulas.setFormulaName(jsonObject.getString("Name"));
            formulas.setEnergies(jsonObject.getIntValue("CostVigor", 0));
            formulas.setCreateMin(jsonObject.getIntValue("CreateItemMin1", 0));
            formulas.setCreateMax(jsonObject.getIntValue("CreateItemMax1", 0));
            JSONObject item = queryItem(formulas.getFormulaName());
            formulas.setMaterialId(item.getString("id"));
            formulas.setTradingPrice(queryPrice(formulas.getMaterialId(), number));
            // 总计需要制作次数
            int totalTimes = randomNumber(number, formulas.getCreateMin(), formulas.getCreateMax());
            formulas.setTimes(totalTimes);
            int totalPrice = 0;
            int energies = formulas.getEnergies() * totalTimes;
            List<Material> itemList = new ArrayList<>();
            for (int i = 1; i < 9; i++) {
                Object requireItemType = jsonObject.get("RequireItemType" + i);
                Object requireItemIndex = jsonObject.get("RequireItemIndex" + i);
                if (requireItemType == null || requireItemIndex == null) {
                    break;
                }
                String id = StrUtil.format("{}_{}", requireItemType, requireItemIndex);
                Material material = queryMaterialById(id);
                material.setNumber(jsonObject.getIntValue("RequireItemCount" + i, 0));
                boolean isNpcMaterial = craftPrice.get(material.getId()) != null;
                // 商人材料查询固定价格
                if (isNpcMaterial) {
                    material.setPrice(craftPrice.get(material.getId()) * material.getNumber());
                    totalPrice += material.getPrice() * totalTimes;
                }
                // 中间产物查询配方
                if (!isNpcMaterial && StrUtil.isNotEmpty(material.getName())) {
                    Formulas itemFormulas = queryFormulas(type, material.getName(), material.getNumber(), craftPrice);
                    material.setFormulas(itemFormulas);
                    energies += itemFormulas == null ? 0 : itemFormulas.getEnergies() * totalTimes;
                }
                // 基础材料查询交易行
                if (!isNpcMaterial && material.getFormulas() == null) {
                    material.setPrice(queryPrice(material.getId(), material.getNumber()));
                    totalPrice += material.getPrice() * totalTimes;
                }
                itemList.add(material);
            }
            formulas.setItems(itemList);
            formulas.setPrice(totalPrice);
            formulas.setEnergies(energies);
        }
        return formulas;
    }


    private int getMaterialNumber(JSONObject jsonObject, int start, int end) {
        int n = (end + start) / 2;
        if (jsonObject.get("RequireItemType" + n) == null) {
            return getMaterialNumber(jsonObject, start, n - 1);
        }
        if (jsonObject.get("RequireItemType" + (n + 1)) != null) {
            return getMaterialNumber(jsonObject, n + 1, end);
        }
        return n;
    }

    private static void setMaterialNumber(Map<String, Material> required, Material material, int number) {
        Material mt1 = required.getOrDefault(material.getName(), null);
        if (mt1 == null) {
            mt1 = new Material();
            mt1.setName(material.getName());
            mt1.setId(material.getId());
            mt1.setSourceId(material.getSourceId());
            mt1.setNumber(number);
        } else {
            mt1.setNumber(mt1.getNumber() + number);
        }
        required.put(material.getName(), mt1);
    }

    private int randomNumber(int number, Integer createMin, Integer createMax) {
        // 一次制作成本totalPrice 获取min-max个 概率计算次数
        int num = 0;
        while (number > 0) {
            number -= RandomUtil.randomInt(2, 3, true, true);
            num++;
        }
        return num;
    }


    private Material queryMaterialById(String id) {
        Material material = new Material();
        String body = getRequest(ITEM_MERGED, id);
        if (StrUtil.isNotEmpty(body)) {
            JSONObject jsonObject = JSONObject.parseObject(body);
            material = convertMaterial(jsonObject);
        }
        return material;
    }


}
