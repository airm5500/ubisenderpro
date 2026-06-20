package com.ubisenderpro.dto;

import java.util.List;

/**
 * Résultat paginé compatible avec les stores ExtJS (data + total).
 */
public class PageResult<T> {
    private List<T> data;
    private long total;

    public PageResult() {
    }

    public PageResult(List<T> data, long total) {
        this.data = data;
        this.total = total;
    }

    public List<T> getData() { return data; }
    public void setData(List<T> data) { this.data = data; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
