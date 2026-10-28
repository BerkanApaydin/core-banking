package com.bank.app.common.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("defensive copy")
    class DefensiveCopy {

        @Test
        @DisplayName("should not expose the caller-owned content list")
        void shouldNotExposeCallerOwnedList() {
            List<String> source = new ArrayList<>(List.of("a"));
            PageResponse<String> response = new PageResponse<>(source, 0, 10, 1, 1, true, true);

            source.add("b");

            assertThat(response.content()).containsExactly("a");
            assertThatThrownBy(() -> response.content().add("c"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should reject null content")
        void shouldRejectNullContent() {
            assertThatThrownBy(() -> new PageResponse<String>(null, 0, 10, 0, 0, true, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("content");
        }

        @Test
        @DisplayName("should reject negative page")
        void shouldRejectNegativePage() {
            assertThatThrownBy(() -> new PageResponse<String>(List.of(), -1, 10, 0, 0, false, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page");
        }

        @Test
        @DisplayName("should reject negative size")
        void shouldRejectNegativeSize() {
            assertThatThrownBy(() -> new PageResponse<String>(List.of(), 0, -1, 0, 0, true, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("size");
        }

        @Test
        @DisplayName("should reject negative totalElements")
        void shouldRejectNegativeTotalElements() {
            assertThatThrownBy(() -> new PageResponse<String>(List.of(), 0, 10, -1, 0, true, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("totalElements");
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

        @Test
        @DisplayName("should not be first for empty non-zero page")
        void shouldNotBeFirstForEmptyNonZeroPage() {
            PageResponse<String> response = PageResponse.empty(5, 10);
            assertThat(response.content()).isEmpty();
            assertThat(response.first()).isFalse();
            assertThat(response.last()).isTrue();
        }
    }
}
