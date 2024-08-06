package com.hidewnd.common.base.response;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lyne
 * @date 2022/10/7
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PageObject<T> extends Page<T> implements Serializable {

    /**
     * 查询对象
     */
    private T query;

    public PageObject(int current, int size) {
        super(current, size, 0);
    }


    public Map<String, Object> getPageData() {
        HashMap<String, Object> result = new HashMap<>();
        // 当前页
        result.put("current", getCurrent());
        // 页大小
        result.put("size", getSize());
        // 总页数
        result.put("pages", getPages());
        // 总数
        result.put("total", getTotal());
        // 元素
        result.put("element", getRecords());
        return result;
    }

    public boolean isEndPage() {
        return getCurrent() > getPages();
    }
}
