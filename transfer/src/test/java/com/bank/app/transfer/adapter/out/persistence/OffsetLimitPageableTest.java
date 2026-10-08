package com.bank.app.transfer.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OffsetLimitPageableTest {

    @Test
    void shouldExposeOffsetAndLimit() {
        TransferPersistenceAdapter.OffsetLimitPageable page =
                new TransferPersistenceAdapter.OffsetLimitPageable(40, 21);

        assertThat(page.getOffset()).isEqualTo(40L);
        assertThat(page.getPageSize()).isEqualTo(21);
        assertThat(page.getPageNumber()).isEqualTo(1);
        assertThat(page.getSort()).isEqualTo(Sort.unsorted());
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void shouldNavigateWindows() {
        TransferPersistenceAdapter.OffsetLimitPageable page =
                new TransferPersistenceAdapter.OffsetLimitPageable(20, 11);

        assertThat(page.next()).isEqualTo(new TransferPersistenceAdapter.OffsetLimitPageable(31, 11));
        assertThat(page.previousOrFirst()).isEqualTo(new TransferPersistenceAdapter.OffsetLimitPageable(9, 11));
        assertThat(page.first()).isEqualTo(new TransferPersistenceAdapter.OffsetLimitPageable(0, 11));
        assertThat(page.withPage(3)).isEqualTo(new TransferPersistenceAdapter.OffsetLimitPageable(33, 11));
        assertThat(new TransferPersistenceAdapter.OffsetLimitPageable(0, 11).hasPrevious()).isFalse();
        assertThat(new TransferPersistenceAdapter.OffsetLimitPageable(0, 11).previousOrFirst())
                .isEqualTo(new TransferPersistenceAdapter.OffsetLimitPageable(0, 11));
    }

    @Test
    void shouldRejectNegativeOffsetAndNonPositiveLimit() {
        assertThatThrownBy(() -> new TransferPersistenceAdapter.OffsetLimitPageable(-1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferPersistenceAdapter.OffsetLimitPageable(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
