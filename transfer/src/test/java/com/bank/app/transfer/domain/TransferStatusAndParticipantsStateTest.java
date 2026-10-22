package com.bank.app.transfer.domain;

import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.exception.InvalidIbanException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * State-mapping edge cases of {@link TransferStatus} and
 * {@link TransferParticipants}.
 *
 * <p>Both are trust boundaries: the status is read back from a database column
 * and the participants carry the IBANs of the two legs. A value that the
 * mapping cannot represent must fail loudly instead of silently degrading to
 * a "valid" transfer.
 */
@SuppressWarnings("null")
@DisplayName("TransferStatus and TransferParticipants state mapping")
class TransferStatusAndParticipantsStateTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("should reject an unknown persisted status name")
    void shouldRejectUnknownStatusName() {
        // A renamed or hand-edited status column must not be read as a
        // transferable state: rehydration has to fail instead of guessing.
        assertThatThrownBy(() -> TransferStatus.valueOf("SETTLED"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TransferStatus.SETTLED");
    }

    @Test
    @DisplayName("should reject a lowercase persisted status name")
    void shouldRejectLowercaseStatusName() {
        assertThatThrownBy(() -> TransferStatus.valueOf("completed"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TransferStatus.completed");
    }

    @Test
    @DisplayName("should reject a persisted status name with surrounding whitespace")
    void shouldRejectStatusNameWithWhitespace() {
        assertThatThrownBy(() -> TransferStatus.valueOf(" COMPLETED"))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject a null persisted status name")
    void shouldRejectNullStatusName() {
        assertThatThrownBy(() -> TransferStatus.valueOf(null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should keep the raw IBAN strings in the participants record")
    void shouldKeepRawIbanStringsInParticipants() {
        // The record stores the IBAN exactly as handed over; normalization and
        // the MOD 97-10 check belong to TransferDomainService. If the record
        // ever starts validating, this pin has to change on purpose.
        TransferParticipants participants = new TransferParticipants(
                new AccountId(1L), " tr770006200000000000000111 ", Currency.TRY,
                new AccountId(2L), "TR870006200000000000000222", Currency.TRY);

        assertThat(participants.senderIban()).isEqualTo(" tr770006200000000000000111 ");
        assertThat(participants.receiverIban()).isEqualTo("TR870006200000000000000222");
    }

    @Test
    @DisplayName("should reject a format-invalid IBAN at the domain boundary, not in the record")
    void shouldRejectFormatInvalidIbanAtTheDomainBoundary() {
        // The participants record is a carrier, not a validator: an IBAN that
        // cannot even be parsed must still be rejected before any money moves.
        TransferParticipants participants = new TransferParticipants(
                new AccountId(1L), "TR1", Currency.TRY,
                new AccountId(2L), "TR870006200000000000000222", Currency.TRY);

        TransferDomainService domainService = new TransferDomainService();

        assertThatThrownBy(() -> domainService.validateAndCreateTransfer(participants,
                com.bank.app.common.domain.Money.of("100.00", Currency.TRY), FIXED_CLOCK))
                .isExactlyInstanceOf(InvalidIbanException.class);
    }
}
