package com.yangke.forum.common;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {

    private long total;
    private int page;
    private int size;
    private List<T> records;

    public PageResult(long total, int page, int size, List<T> records) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.records = records;
    }
}
