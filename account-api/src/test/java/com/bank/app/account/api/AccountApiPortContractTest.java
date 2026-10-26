package com.bank.app.account.api;

import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the published Account port.
 *
 * <p>The transfer context programs its anti-corruption layer against
 * {@link AccountApi}. A signature change must fail HERE — in the module that
 * owns the contract — instead of surfacing as a compile error in a downstream
 * context that merely consumes the published language.
 */
class AccountApiPortContractTest {

    @Test
    @DisplayName("the published port exposes exactly the five account operations")
    void shouldExposeExactlyThePublishedOperations() {
        assertThat(declaredMethodNames())
                .containsExactlyInAnyOrder(
                        "getSnapshotById",
                        "getSnapshotByIban",
                        "getIbansForAccounts",
                        "adjustBalances",
                        "reverseForCancellation");
    }

    @Test
    @DisplayName("every published operation stays abstract")
    void shouldExposeOnlyAbstractOperations() {
        // A new default method would silently change the behaviour of every
        // implementor (the account module and any test double) without a
        // compile error anywhere.
        for (Method method : AccountApi.class.getDeclaredMethods()) {
            assertThat(method.isDefault())
                    .as("%s.%s must stay abstract",
                            AccountApi.class.getSimpleName(), method.getName())
                    .isFalse();
        }
    }

    @Test
    void shouldPinThePublishedReturnTypes() throws Exception {
        assertThat(AccountApi.class.getMethod("getSnapshotById", Long.class).getReturnType())
                .isEqualTo(AccountSnapshot.class);
        assertThat(AccountApi.class.getMethod("getSnapshotByIban", String.class).getReturnType())
                .isEqualTo(AccountSnapshot.class);
        assertThat(AccountApi.class.getMethod("getIbansForAccounts", Collection.class).getReturnType())
                .isEqualTo(Map.class);
        assertThat(AccountApi.class
                .getMethod("adjustBalances", Long.class, Long.class, Money.class).getReturnType())
                .isEqualTo(AccountAdjustmentResult.class);
        assertThat(AccountApi.class
                .getMethod("reverseForCancellation", Long.class, Long.class, Money.class).getReturnType())
                .isEqualTo(AccountAdjustmentResult.class);
    }

    @Test
    void shouldPinTheBatchIbanLookupToIdToIbanStrings() throws Exception {
        Method method = AccountApi.class.getMethod("getIbansForAccounts", Collection.class);

        assertThat(method.getGenericParameterTypes()[0].getTypeName())
                .as("the batch lookup must keep taking account ids")
                .isEqualTo("java.util.Collection<java.lang.Long>");
        assertThat(method.getGenericReturnType().getTypeName())
                .as("the batch lookup must keep returning id -> IBAN")
                .isEqualTo("java.util.Map<java.lang.Long, java.lang.String>");
    }

    @Test
    void shouldPinTheMoneyMovementOperationsToAccountIdsAndAnAmount() throws Exception {
        for (String name : List.of("adjustBalances", "reverseForCancellation")) {
            Method method = AccountApi.class.getMethod(name, Long.class, Long.class, Money.class);

            assertThat(method.getParameterTypes())
                    .as("%s must keep taking (senderId, receiverId, amount)", name)
                    .containsExactly(Long.class, Long.class, Money.class);
        }
    }

    private static List<String> declaredMethodNames() {
        return Arrays.stream(AccountApi.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .toList();
    }
}
