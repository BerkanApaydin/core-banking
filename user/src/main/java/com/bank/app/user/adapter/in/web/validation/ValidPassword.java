package com.bank.app.user.adapter.in.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registration password check against the live {@code PasswordPolicy} bean
 * (built from {@code PasswordPolicyProperties}, not a hardcoded length).
 *
 * <p>The DTO-level {@code @Size(min)} can only mirror the default and drifts
 * whenever operations raise {@code app.security.password.min-length}. This
 * constraint delegates to the same policy the use case enforces, so the web
 * contract and the domain rule cannot diverge. Null/blank handling stays with
 * {@code @NotBlank}: this validator passes null through so each failure is
 * reported once, by the owning constraint.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "{validation.password.weak}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
