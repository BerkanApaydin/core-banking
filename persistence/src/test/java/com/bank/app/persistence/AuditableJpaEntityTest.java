package com.bank.app.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditableJpaEntityTest {

    static class TestAuditable extends AuditableJpaEntity {
    }

    @Test
    void shouldCreateAuditableJpaEntity() {
        TestAuditable entity = new TestAuditable();
        LocalDateTime now = LocalDateTime.now();

        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy("creator");
        entity.setUpdatedBy("updater");

        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
        assertEquals("creator", entity.getCreatedBy());
        assertEquals("updater", entity.getUpdatedBy());
    }

    @Test
    void createdAtMustBeAuditedAndImmutable() {
        // created_at is NOT NULL in the schema: without @CreatedDate the
        // listener leaves it null and every insert of the row fails.
        Field createdAt = field("createdAt");

        assertNotNull(createdAt.getAnnotation(CreatedDate.class),
                "created_at must be filled by the auditing listener");
        Column column = createdAt.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals("created_at", column.name());
        assertFalse(column.nullable(), "created_at is NOT NULL in the schema");
        assertFalse(column.updatable(), "a row's creation time must be immutable");
    }

    @Test
    void updatedAtMustBeAudited() {
        Field updatedAt = field("updatedAt");

        assertNotNull(updatedAt.getAnnotation(LastModifiedDate.class));
        assertEquals("updated_at", updatedAt.getAnnotation(Column.class).name());
    }

    @Test
    void actorColumnsMustBeAudited() {
        Field createdBy = field("createdBy");
        Field updatedBy = field("updatedBy");

        assertNotNull(createdBy.getAnnotation(CreatedBy.class));
        assertEquals("created_by", createdBy.getAnnotation(Column.class).name());
        assertNotNull(updatedBy.getAnnotation(LastModifiedBy.class));
        assertEquals("updated_by", updatedBy.getAnnotation(Column.class).name());
    }

    @Test
    void theAuditingListenerMustBeRegisteredOnTheBaseType() {
        // Dropping @EntityListeners(AuditingEntityListener.class) silently
        // nulls every audited column — created_at is NOT NULL, so the first
        // insert of every entity fails with an opaque constraint violation.
        assertNotNull(AuditableJpaEntity.class.getAnnotation(MappedSuperclass.class),
                "the base type must stay a mapped superclass, not an entity");
        EntityListeners listeners = AuditableJpaEntity.class.getAnnotation(EntityListeners.class);

        assertNotNull(listeners, "the auditing listener must be registered");
        assertTrue(List.of(listeners.value()).contains(AuditingEntityListener.class),
                "the auditing listener must be registered: " + Arrays.toString(listeners.value()));
    }

    private static Field field(String name) {
        return Arrays.stream(AuditableJpaEntity.class.getDeclaredFields())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field named " + name));
    }
}
