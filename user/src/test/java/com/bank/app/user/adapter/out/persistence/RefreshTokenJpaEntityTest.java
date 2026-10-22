package com.bank.app.user.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenJpaEntityTest {

    @Test
    void shouldExposePersistableIdentity() {
        var entity = new RefreshTokenJpaEntity();
        entity.setTokenHash("hash-1");

        assertThat(entity.getId()).isEqualTo("hash-1");
        assertThat(entity.isNew()).isTrue();

        entity.markNotNew();

        assertThat(entity.isNew()).isFalse();
    }

    @Test
    void shouldExposeRevocationState() {
        var entity = new RefreshTokenJpaEntity();

        assertThat(entity.isRevoked()).isFalse();
        entity.setRevoked(true);
        assertThat(entity.isRevoked()).isTrue();
    }
}
