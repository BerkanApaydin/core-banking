package com.bank.app.common.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageResponse")
class PageResponseTest {

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("should return zero totalPages when size is zero")
        void shouldReturnZeroTotalPagesWhenSizeIsZero() {
            PageResponse<String> response = PageResponse.of(List.of("a"), 0, 0, 10);
            assertThat(response.totalPages()).isZero();
        }

        @Test
        @DisplayName("should return zero totalPages when totalElements is zero")
        void shouldReturnZeroTotalPagesWhenTotalElementsIsZero() {
            PageResponse<String> response = PageResponse.of(List.of(), 0, 10, 0);
            assertThat(response.totalPages()).isZero();
        }

        @Test
        @DisplayName("should be first and last for single page")
        void shouldBeFirstAndLastForSinglePage() {
            PageResponse<String> response = PageResponse.of(List.of("a", "b", "c", "d", "e"), 0, 10, 5);
            assertThat(response.first()).isTrue();
            assertThat(response.last()).isTrue();
            assertThat(response.totalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("should be first and not last for first page of multiple")
        void shouldBeFirstAndNotLastForFirstPageOfMultiple() {
            PageResponse<String> response = PageResponse.of(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), 0, 10, 20);
            assertThat(response.first()).isTrue();
            assertThat(response.last()).isFalse();
            assertThat(response.totalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("should be last and not first for last page")
        void shouldBeLastAndNotFirstForLastPage() {
            PageResponse<String> response = PageResponse.of(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), 1, 10, 20);
            assertThat(response.first()).isFalse();
            assertThat(response.last()).isTrue();
            assertThat(response.totalPages()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("empty")
    class Empty {

        @Test
        @DisplayName("should return empty response with zero totals")
        void shouldReturnEmptyResponse() {
            PageResponse<String> response = PageResponse.empty(0, 10);
            assertThat(response.content()).isEmpty();
            assertThat(response.totalElements()).isZero();
            assertThat(response.totalPages()).isZero();
            assertThat(response.first()).isTrue();
            assertThat(response.last()).isTrue();
        }
    }
}
