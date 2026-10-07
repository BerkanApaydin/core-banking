package com.bank.app.user.adapter.in.web.validation;

import com.bank.app.user.domain.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Backs {@link ValidPassword} with the effective {@link PasswordPolicy}.
 *
 * <p>The policy arrives via {@code ObjectProvider} with a {@code DEFAULT}
 * fallback instead of a mandatory injection: narrow web slices
 * ({@code @WebMvcTest}) scan this validator (same adapter package) without
 * loading the {@code PasswordPolicy} bean, and a hard dependency would turn
 * every registration request in such a context into a 500
 * (UnsatisfiedDependencyException). Production always binds the configured
 * bean, so the fallback only applies where no policy was configured.
 */
@Component
public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    private final PasswordPolicy passwordPolicy;

    @Autowired
    public PasswordPolicyValidator(ObjectProvider<PasswordPolicy> policies) {
        this.passwordPolicy = policies.getIfAvailable(() -> PasswordPolicy.DEFAULT);
    }

    /** Fallback for non-Spring instantiation (plain validator unit tests). */
    public PasswordPolicyValidator() {
        this.passwordPolicy = PasswordPolicy.DEFAULT;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        var errors = passwordPolicy.validate(value);
        if (errors.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(String.join("; ", errors))
                .addConstraintViolation();
        return false;
    }
}
