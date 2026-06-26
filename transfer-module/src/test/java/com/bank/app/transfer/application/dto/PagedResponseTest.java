package com.bank.app.transfer.application.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class PagedResponseTest {

    @Test
    void shouldCalculateTotalPagesCorrectly() {
        List<String> items = List.of("a", "b", "c");
        PagedResponse<String> response = new PagedResponse<>(items, 0, 10, 42);

        assertEquals(items, response.items());
        assertEquals(0, response.page());
        assertEquals(10, response.size());
        assertEquals(42, response.totalItems());
        assertEquals(5, response.totalPages());
    }

    @Test
    void shouldReturnZeroTotalPagesWhenTotalItemsIsZero() {
        PagedResponse<String> response = new PagedResponse<>(List.of(), 0, 20, 0);

        assertEquals(0, response.totalItems());
        assertEquals(0, response.totalPages());
    }

    @Test
    void shouldReturnOneTotalPageWhenItemsFitInSinglePage() {
        PagedResponse<String> response = new PagedResponse<>(List.of("a", "b"), 0, 10, 2);

        assertEquals(2, response.totalItems());
        assertEquals(1, response.totalPages());
    }

    @Test
    void shouldReturnExactTotalPagesWhenTotalIsMultipleOfSize() {
        PagedResponse<String> response = new PagedResponse<>(List.of("a", "b"), 0, 2, 6);

        assertEquals(3, response.totalPages());
    }

    @Test
    void shouldReturnPartialPageWhenTotalIsNotMultipleOfSize() {
        PagedResponse<String> response = new PagedResponse<>(List.of("a"), 1, 10, 11);

        assertEquals(2, response.totalPages());
    }

    @Test
    void shouldHandlePageAndSizeArbitraryValues() {
        PagedResponse<String> response = new PagedResponse<>(List.of(), 3, 25, 101);

        assertEquals(3, response.page());
        assertEquals(25, response.size());
        assertEquals(101, response.totalItems());
        assertEquals(5, response.totalPages());
    }

    @Test
    void shouldReturnZeroTotalPagesWhenSizeIsZero() {
        PagedResponse<String> response = new PagedResponse<>(List.of("a"), 0, 0, 10);

        assertEquals(0, response.totalPages());
    }

    @Test
    void shouldReturnZeroTotalPagesWhenSizeIsNegative() {
        PagedResponse<String> response = new PagedResponse<>(List.of("a"), 0, -1, 10);

        assertEquals(0, response.totalPages());
    }
}
