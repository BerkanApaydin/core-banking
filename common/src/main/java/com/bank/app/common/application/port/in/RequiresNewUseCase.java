package com.bank.app.common.application.port.in;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AV-1: marks a use case whose transaction must be REQUIRES_NEW (independent
 * of any outer business transaction).
 *
 * <p>Previously the audit module's REQUIRES_NEW semantics lived as a
 * hardcoded package literal in infrastructure's
 * {@code UseCaseTransactionAspect} ({@code within(com.bank.app.audit..)}),
 * invisible in the owning module and requiring an infrastructure edit for any
 * second BC with the same need. Marker annotations keep the semantics visible
 * where they belong; the aspect only interprets them.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresNewUseCase {
}
