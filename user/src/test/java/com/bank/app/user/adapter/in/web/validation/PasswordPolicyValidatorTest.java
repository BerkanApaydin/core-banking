package com.bank.app.user.adapter.in.web.validation;

import com.bank.app.user.domain.PasswordPolicy;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Mock private ConstraintValidatorContext context;
    @Mock private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @Test
    void shouldAcceptNullAndBlankWithoutTouchingContext() {
        // Kills the BooleanFalse mutant on the early-true branch: null/blank
        // are delegated to @NotBlank, so the validator must pass them.
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("   ", context)).isTrue();
        verify(context, never()).disableDefaultConstraintViolation();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void shouldAcceptStrongPassword() {
        assertThat(validator.isValid("Str0ngPassw0rd!", context)).isTrue();
        verify(context, never()).disableDefaultConstraintViolation();
    }

    @Test
    void shouldFallBackToDefaultWhenNoPolicyBeanAvailable() {
        // Kills the NullReturnVals mutant on the getIfAvailable fallback
        // lambda: with no bean available the DEFAULT policy must apply.
        ObjectProvider<PasswordPolicy> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable(any()))
                .thenAnswer(inv -> ((Supplier<PasswordPolicy>) inv.getArgument(0)).get());
        var fallback = new PasswordPolicyValidator(empty);
        assertThat(fallback.isValid("Str0ngPassw0rd!", context)).isTrue();
    }

    @Test
    void shouldRejectWeakPasswordWithCustomViolation() {
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);

        // Kills both the disableDefault VoidMethodCall mutant and the
        // BooleanFalse mutant on the final return: an invalid password must
        // disable the default message, register the joined policy message,
        // and return false.
        assertThat(validator.isValid("weak", context)).isFalse();

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                argThat((String t) -> t != null && !t.isBlank()));
        verify(builder).addConstraintViolation();
    }
}
