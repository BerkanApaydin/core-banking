package com.bank.app.common.application.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (size > 0) ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResponse<>(
                content, page, size, totalElements, totalPages,
                page == 0, page >= totalPages - 1);
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        // An empty page is always last, but first only when page 0 was
        // requested: page=5 with no results previously reported first=true.
        return new PageResponse<>(List.of(), page, size, 0, 0, page == 0, true);
    }
}
