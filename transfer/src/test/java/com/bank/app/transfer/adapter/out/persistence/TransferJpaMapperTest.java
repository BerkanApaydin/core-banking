package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.persistence.AuditableJpaEntity;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("TransferJpaMapper")
class TransferJpaMapperTest {

    private TransferJpaMapper mapper;

    private static final Long ID = 1L;
    private static final AccountId SENDER_ACCOUNT_ID = new AccountId(10L);
    private static final AccountId RECEIVER_ACCOUNT_ID = new AccountId(20L);
    private static final BigDecimal AMOUNT_VALUE = new BigDecimal("100.00");
    private static final Currency CURRENCY = Currency.TRY;
    private static final Money AMOUNT = Money.of(AMOUNT_VALUE, CURRENCY);
    private static final TransferStatus STATUS = TransferStatus.PENDING;
    private static final Long VERSION = 3L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 24, 12, 0);

    @BeforeEach
    void setUp() {
        mapper = new TransferJpaMapper();
    }

    private static void setAuditCreatedAt(TransferJpaEntity entity, LocalDateTime value) {
        // R-C18: auditing setters are protected; tests outside the
        // persistence package inject the JPA-managed field via reflection.
        try {
            Field field = AuditableJpaEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not set createdAt for test fixture", e);
        }
    }

    @Nested
    @DisplayName("toJpaEntity")
    class ToJpaEntity {

        @Test
        @DisplayName("should map Transfer to TransferJpaEntity")
        void shouldMapToJpaEntity() {
            Transfer transfer = Transfer.rehydrate(ID, SENDER_ACCOUNT_ID, RECEIVER_ACCOUNT_ID, AMOUNT, STATUS, CREATED_AT, VERSION);

            TransferJpaEntity entity = mapper.toJpaEntity(transfer);

            assertThat(entity.getId()).isEqualTo(ID);
            assertThat(entity.getSenderAccountId()).isEqualTo(SENDER_ACCOUNT_ID.value());
            assertThat(entity.getReceiverAccountId()).isEqualTo(RECEIVER_ACCOUNT_ID.value());
            assertThat(entity.getAmount()).isEqualByComparingTo(AMOUNT_VALUE);
            assertThat(entity.getCurrency()).isEqualTo(CURRENCY);
            assertThat(entity.getStatus()).isEqualTo(STATUS);
            assertThat(entity.getBusinessCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(entity.getVersion()).isEqualTo(VERSION);
        }

        @Test
        @DisplayName("should throw when transfer is null")
        void shouldThrowOnNullTransfer() {
            assertThatThrownBy(() -> mapper.toJpaEntity(null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transfer must not be null");
        }
    }

    @Nested
    @DisplayName("toDomain")
    class ToDomain {

        @Test
        @DisplayName("should map TransferJpaEntity to Transfer")
        void shouldMapToDomain() {
            TransferJpaEntity entity = new TransferJpaEntity(ID, SENDER_ACCOUNT_ID.value(), RECEIVER_ACCOUNT_ID.value(),
                    AMOUNT_VALUE, CURRENCY, STATUS, VERSION);
            entity.setBusinessCreatedAt(CREATED_AT);
            setAuditCreatedAt(entity, CREATED_AT.plusHours(1));

            Transfer transfer = mapper.toDomain(entity);

            assertThat(transfer.getId()).isEqualTo(ID);
            assertThat(transfer.getSenderAccountId()).isEqualTo(SENDER_ACCOUNT_ID);
            assertThat(transfer.getReceiverAccountId()).isEqualTo(RECEIVER_ACCOUNT_ID);
            assertThat(transfer.getAmount().amount()).isEqualByComparingTo(AMOUNT_VALUE);
            assertThat(transfer.getAmount().currency()).isEqualTo(CURRENCY);
            assertThat(transfer.getStatus()).isEqualTo(STATUS);
            assertThat(transfer.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(transfer.getVersion()).isEqualTo(VERSION);
        }

        @Test
        @DisplayName("should fall back to auditing timestamp for pre-V20 rows")
        void shouldFallBackToAuditingTimestamp() {
            TransferJpaEntity entity = new TransferJpaEntity(ID, SENDER_ACCOUNT_ID.value(), RECEIVER_ACCOUNT_ID.value(),
                    AMOUNT_VALUE, CURRENCY, STATUS, VERSION);
            setAuditCreatedAt(entity, CREATED_AT);

            Transfer transfer = mapper.toDomain(entity);

            assertThat(transfer.getCreatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("should throw when entity is null")
        void shouldThrowOnNullEntity() {
            assertThatThrownBy(() -> mapper.toDomain(null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Entity must not be null");
        }
    }

    @Nested
    @DisplayName("mapping completeness")
    class MappingCompleteness {

        // Every persistent column of TransferJpaEntity, explicitly enumerated.
        // A new column breaks the subset assertion below and forces a conscious
        // decision: map it in toJpaEntity/toDomain (or explicitly leave it
        // unmapped).
        private static final Set<String> KNOWN_COLUMNS = Set.of(
                "id", "senderAccountId", "receiverAccountId", "amount",
                "currency", "status", "businessCreatedAt", "version");

        @Test
        @DisplayName("should round-trip every domain field")
        void shouldRoundTripEveryField() {
            Transfer transfer = Transfer.rehydrate(ID, SENDER_ACCOUNT_ID, RECEIVER_ACCOUNT_ID, AMOUNT,
                    TransferStatus.COMPLETED, CREATED_AT, VERSION);

            Transfer reloaded = mapper.toDomain(mapper.toJpaEntity(transfer));

            assertThat(reloaded.getId()).isEqualTo(transfer.getId());
            assertThat(reloaded.getSenderAccountId()).isEqualTo(transfer.getSenderAccountId());
            assertThat(reloaded.getReceiverAccountId()).isEqualTo(transfer.getReceiverAccountId());
            assertThat(reloaded.getAmount()).isEqualTo(transfer.getAmount());
            assertThat(reloaded.getStatus()).isEqualTo(transfer.getStatus());
            assertThat(reloaded.getCreatedAt()).isEqualTo(transfer.getCreatedAt());
            assertThat(reloaded.getVersion()).isEqualTo(transfer.getVersion());
        }

        @Test
        @DisplayName("should fail when a new entity column is added without mapping decision")
        void shouldRejectUnmappedColumns() {
            Set<String> declared = new HashSet<>();
            for (Field field : TransferJpaEntity.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    declared.add(field.getName());
                }
            }

            assertThat(declared)
                    .as("new TransferJpaEntity column requires an explicit mapping decision in this test")
                    .isSubsetOf(KNOWN_COLUMNS);
        }
    }
}
