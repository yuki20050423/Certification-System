package com.swjtu.certification.vo;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> data;
    private Integer currentPage;
    private Integer pageSize;
    private Integer totalPages;
    private Long total;

    public PageResult(List<T> data, Integer currentPage, Integer pageSize, Long total) {
        this.data = data;
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }

    public static <T> PageResult<T> of(List<T> data, Integer currentPage, Integer pageSize, Long total) {
        return new PageResult<>(data, currentPage, pageSize, total);
    }
}
