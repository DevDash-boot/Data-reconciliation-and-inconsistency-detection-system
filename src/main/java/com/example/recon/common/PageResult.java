package com.example.recon.common;

import java.util.List;
import java.util.function.Function;

/** 페이지 응답. page 는 0부터 시작. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResult<>(content, page, size, totalElements, totalPages);
    }

    public <R> PageResult<R> map(Function<T, R> f) {
        return new PageResult<>(content.stream().map(f).toList(), page, size, totalElements, totalPages);
    }
}
