package com.hidewnd.costing.service.impl;

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
import com.hidewnd.costing.dto.Material;
import com.hidewnd.costing.service.CacheService;
import com.hidewnd.costing.service.Jx3BoxRemote;
import com.hidewnd.costing.utils.BoxUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Jx3BoxRemoteImpl implements Jx3BoxRemote {

    public static final String ITEM_SEARCH = "https://helper.jx3box.com/api/item/search?keyword={}&page=1&limit=20";
    public static final String ITEM_PRICE = "https://next2.jx3box.com/api/item-price/{}/detail?server={}";
    public static final String ITEM_FORMULAS = "https://node.jx3box.com/manufactures?client=std&type={}&name={}";
    public static final String ITEM_MERGED = "https://node.jx3box.com/resource/std/item_merged.{}";
    public static final String CRAFT_PRICE = "https://node.jx3box.com/craft/price?client=std";

    public static final String CACHE_NAME_SPACE = "box:craftL:price:";
    public static final String MANUFACTURES_TYPE = "box:manufactures:type:";


    @Autowired
    @Qualifier("redisCacheService")
    private CacheService cacheService;

    @Value("${box.default.server: '剑胆琴心'}")
    private String defaultServer;


    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;

    @PostConstruct
    public void initBean() {
        initCraftPrice();
        initFormulasType();
    }

    @Async
    public void initCraftPrice() {
        Map<String, Integer> craftPrice = getCraftPrice();
        for (Map.Entry<String, Integer> entry : craftPrice.entrySet()) {
            cacheService.set(CACHE_NAME_SPACE + entry.getKey(), entry.getValue());
        }
    }

    @Async
    public void initFormulasType() {
        List<JSONObject> list = queryFormulas(null, "");
        for (JSONObject jsonObject : list) {
            String name = jsonObject.getString("Name");
            String type = jsonObject.getString("__TabType");
            if (StrUtil.isNotEmpty(name) && StrUtil.isNotEmpty(type)) {
                cacheService.set(MANUFACTURES_TYPE + jsonObject.getString("Name"), type);
            }
        }
    }

    @Override
    public Material queryMaterialById(String id) {
        Material material = new Material();
        String body = getRequest(ITEM_MERGED, id);
        if (StrUtil.isNotEmpty(body)) {
            JSONObject jsonObject = JSONObject.parseObject(body);
            material = convertMaterial(jsonObject);
        }
        return material;
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


    @Override
    public long queryPrice(String itemId, int number) {
        return queryPrice(defaultServer, itemId, number);
    }

    @Override
    public long queryPrice(String serverName, String itemId, int number) {
        String body = getRequest(ITEM_PRICE, itemId, URLEncodeUtil.encode(serverName));
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


    @Override
    public Formulas queryFormulasAndNumber(FormulasEnum type, String name, Integer number, Map<String, Material> required) {
        Formulas formulas = null;
        List<JSONObject> list = queryFormulas(type, name);
        if (CollUtil.isNotEmpty(list)) {
            JSONObject jsonObject = list.stream().filter(item -> StrUtil.equals(item.getString("Name"), name))
                    .filter(item -> item.get("nLevel") != null)
                    .findFirst().orElse(null);
            if (jsonObject == null) {
                return formulas;
            }
            formulas = new Formulas();
            formulas.setFormulaName(jsonObject.getString("Name"));
            formulas.setEnergies(jsonObject.getIntValue("CostVigor", 0));
            formulas.setCreateMin(jsonObject.getIntValue("CreateItemMin1", 0));
            formulas.setCreateMax(jsonObject.getIntValue("CreateItemMax1", 0));
            if (type == null) {
                type = BoxUtils.getFormulasEnum(jsonObject.getString("__TabType"));
                cacheService.set(MANUFACTURES_TYPE + name, jsonObject.getString("__TabType"));
            }
            JSONObject item = queryItem(formulas.getFormulaName());
            formulas.setMaterialId(item.getString("id"));
            formulas.setTradingPrice(queryPrice(formulas.getMaterialId(), number));
            // 总计需要制作次数
            int totalTimes = randomNumber(number, formulas.getCreateMin(), formulas.getCreateMax());
            formulas.setTimes(totalTimes);
            int energies = formulas.getEnergies() * totalTimes;
            List<Material> itemList = new ArrayList<>();
            for (int i = 1; i < 6; i++) {
                Object requireItemType = jsonObject.get("RequireItemType" + i);
                Object requireItemIndex = jsonObject.get("RequireItemIndex" + i);
                if (requireItemType == null || requireItemIndex == null) {
                    break;
                }
                String id = StrUtil.format("{}_{}", requireItemType, requireItemIndex);
                Material material = queryMaterialById(id);
                material.setNumber(jsonObject.getIntValue("RequireItemCount" + i, 0));
                Formulas itemFormulas = queryFormulasAndNumber(type, material.getName(), material.getNumber() * totalTimes, required);
                // 中间产物查询配方
                if (itemFormulas != null) {
                    material.setFormulas(itemFormulas);
                    energies += itemFormulas.getEnergies() * totalTimes;
                }
                // 基础材料查询交易行
                if (material.getFormulas() == null) {
                    setMaterialNumber(required, material, material.getNumber() * totalTimes);
                }
                itemList.add(material);
            }
            formulas.setItems(itemList);
            formulas.setEnergies(energies);
        }
        return formulas;
    }

    private List<JSONObject> queryFormulas(FormulasEnum type, String name) {
        List<JSONObject> list = new ArrayList<>();
        String body = getRequest(ITEM_FORMULAS, type == null ? "" : type.getType(), name);
        if (StrUtil.isNotEmpty(body)) {
            list = JSONArray.parseArray(body, JSONObject.class);
            if (!CollUtil.isEmpty(list)) {
                return list;
            }
        }
        return list;
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


    @Override
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

    public String getRequest(String format, Object... param) {
        String url = StrUtil.format(format, param);
        String body = HttpRequest.get(url).header(Header.ACCEPT, "gzip, deflate, br")
                .timeout(5000)
                .execute().body();
        return "[]".equals(body) ? "" : body;
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


}
