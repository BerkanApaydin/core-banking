package com.bank.app.transfer.application.dto;

import java.util.List;

public record PagedResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {
    public PagedResponse(List<T> items, int page, int size, long totalItems) {
        this(items, page, size, totalItems, calculateTotalPages(totalItems, size));
    }

    private static int calculateTotalPages(long totalItems, int size) {
        if (size <= 0) return 0;
        return (int) Math.ceil((double) totalItems / size);
    }
}
