package com.hidewnd.costing.service;


import com.alibaba.fastjson2.JSONObject;
import com.hidewnd.costing.costant.FormulasEnum;
import com.hidewnd.costing.dto.Formulas;
import com.hidewnd.costing.dto.Material;

import java.util.Map;

public interface Jx3BoxRemote {


    /**
     * 查询材料详情
     *
     * @param id 材料ID
     * @return material | null return empty Object
     */
    Material queryMaterialById(String id);

    Material queryMaterialByName(String name);

    /**
     * 查询交易行价格
     *
     * @param server 服务器
     * @param itemId 材料ID
     * @param number 数量
     * @return decimal  BigDecimal.ZERO if null
     */
    long queryPrice(String server, String itemId, int number);


    /**
     * 查询配方及所需材料
     *
     * @param type     所需物品类型
     * @param name     所需物品名称
     * @param number   所需数量
     * @param required 所需材料数量集合
     * @return 配方
     */
    Formulas queryFormulasAndNumber(FormulasEnum type, String name, Integer number, Map<String, Material> required);

    JSONObject getFormulasJSON(FormulasEnum type, String name);
}
