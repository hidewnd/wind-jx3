package com.hidewnd.costing.handler;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hidewnd.costing.dto.CostDetailDto;
import com.hidewnd.costing.dto.Formulas;
import com.hidewnd.costing.dto.Material;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FormulaBuilder {

    /**
     * 产出是否随机
     */
    private boolean rangeCreate;

    /**
     * 配方列表
     */
    private final Map<String, Formulas> formulasMap;

    /**
     * 制作需求数
     */
    private final Map<String, Integer> numberMap;

    /**
     * 所需材料
     */
    @Getter
    private final Map<String, Material> materialMap;

    /**
     * 制作轮次记录
     */
    @Getter
    private List<CostDetailDto> makeList;

    /**
     * 各配方实际产出数量缓存
     */
    @Getter
    private final Map<String, Integer> actualNumberMap;

    /**
     * 总消耗精力
     */
    @Getter
    private Integer totalEnergies;


    private FormulaBuilder(boolean rangeCreate) {
        this.rangeCreate = rangeCreate;
        this.numberMap = new ConcurrentHashMap<>();
        this.materialMap = new ConcurrentHashMap<>();
        this.formulasMap = new ConcurrentHashMap<>();
        this.actualNumberMap = new ConcurrentHashMap<>();
    }

    public static FormulaBuilder create(boolean rangeCreate) {
        return new FormulaBuilder(rangeCreate);
    }

    public FormulaBuilder addFormula(String formulaName, Integer number) {
        formulaName = normalizeFormulaName(formulaName);
        if (StrUtil.isNotEmpty(formulaName)) {
            this.numberMap.put(formulaName, number);
        }
        return this;
    }

    public FormulaBuilder parseFormulas(FormulaParseAdapter adapter) {
        for (String name : this.numberMap.keySet()) {
            Formulas formulas = adapter.parse(name);
            this.numberMap.computeIfAbsent(name, _ -> formulas.getCreateMin());
            this.formulasMap.put(name, formulas);
        }
        return this;
    }

    public FormulaBuilder analysisMaterial() {
        this.makeList = new ArrayList<>(); // 制作轮次记录
        this.actualNumberMap.clear(); // 清空实际产出数量缓存
        this.totalEnergies = 0;
        Map<String, Integer> intermediateMap = new HashMap<>();  // 中间产物需求数暂存
        for (Map.Entry<String, Integer> entry : this.numberMap.entrySet()) {
            String formulaName = entry.getKey();
            Integer requireNumber = entry.getValue();
            Formulas formulas = formulasMap.get(formulaName);
            if(formulas != null) {
                analysisMaterial(formulas, requireNumber, intermediateMap);
            }
        }
        // 中间半成品最后统一合并模拟
        while (!intermediateMap.isEmpty()) {
            Map<String, Integer> nextIntermediateMap = new HashMap<>();
            for (Map.Entry<String, Integer> entry : intermediateMap.entrySet()) {
                String formulaName = entry.getKey();
                Integer requireNumber = entry.getValue();
                Formulas formulas = formulasMap.get(formulaName);
                if (formulas != null) {
                    analysisMaterial(formulas, requireNumber, nextIntermediateMap);
                }
            }
            intermediateMap = nextIntermediateMap;
        }
        return this;
    }

    private void analysisMaterial(Formulas formulas, Integer requireNumber, Map<String, Integer> intermediateMap) {
        // 模拟总共需要制作的次数
        Integer times = calculateTimes(formulas, requireNumber);
        this.totalEnergies += formulas.getEnergies() * times;
        // 循环解析配方
        for (Material item : formulas.getItems()) {
            int requiredNum = item.getNumber() * times;
            // 中间材料
            if (intermediateMap != null && item.getFormulas() != null) {
                intermediateMap.put(item.getName(), requiredNum);
                this.formulasMap.put(item.getName(), item.getFormulas());
                continue;
            }
            // 基础材料
            Material material = this.materialMap.computeIfAbsent(item.getName(), _ -> {
                Material m = new Material();
                m.setName(item.getName());
                m.setId(item.getId());
                m.setSourceId(item.getSourceId());
                m.setNumber(0);
                return m;
            });
            material.setNumber(material.getNumber() + requiredNum);
        }
    }

    public Formulas getByName(String name) {
        return this.formulasMap.get(normalizeFormulaName(name));
    }

    /**
     * 获取配方的实际产出数量
     *
     * @param formulaName 配方名称
     * @return 实际产出数量，如果不存在则返回0
     */
    public Integer getActualNumber(String formulaName) {
        return this.actualNumberMap.getOrDefault(normalizeFormulaName(formulaName), 0);
    }

    /**
     * 计算制作轮次数
     *
     * @param formulas    配方
     * @param requireNums 所需数量
     * @return 伦茨
     */
    private Integer calculateTimes(Formulas formulas, Integer requireNums) {
        int times = 0, remaining = requireNums;
        int min = formulas.getCreateMin(), max = formulas.getCreateMax();
        int totalProduced = 0;
        while (remaining > 0) {
            int makeNum = this.rangeCreate && min < max ? RandomUtil.randomInt(min, max + 1) : min;
            this.makeList.add(new CostDetailDto(times++, formulas.getFormulaName(), makeNum));
            totalProduced += makeNum;
            remaining -= makeNum;
        }
        // 更新实际产出数量缓存
        this.actualNumberMap.merge(formulas.getFormulaName(), totalProduced, Integer::sum);
        return times;
    }


    /**
     * 格式化参数 去除标签
     */
    private String normalizeFormulaName(String formulaName) {
        String normalized = StrUtil.emptyIfNull(formulaName).trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }
}
