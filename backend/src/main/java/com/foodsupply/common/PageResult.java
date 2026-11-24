package com.foodsupply.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.function.Function;

/**
 * 分页返回结构。列表接口统一用它，不把 IPage 直接暴露给前端。
 */
@Getter
@Setter
public class PageResult<T> {

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long total, long pageNum, long pageSize, List<T> records) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.records = records;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        List<T> list = page.getRecords().stream().map(converter).toList();
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }
}
