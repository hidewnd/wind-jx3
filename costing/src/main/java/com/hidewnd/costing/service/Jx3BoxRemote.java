package com.hidewnd.costing.service;


import com.hidewnd.costing.costant.FormulasEnum;
import com.hidewnd.costing.dto.Formulas;
import com.hidewnd.costing.dto.Material;
import org.springframework.cache.annotation.Cacheable;

import java.util.Map;

public interface Jx3BoxRemote {


    Material queryMaterialById(String id);

    /**
     * 查询价格
     *
     * @param itemId id
     * @param number 数量
     * @return decimal  BigDecimal.ZERO if null
     */
    long queryPrice(String itemId, int number);


    long queryPrice(String serverName, String itemId, int number);


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

    Map<String, Integer> getCraftPrice();
}
