package com.bank.app.common.application;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated Use {@link TransactionalUseCase} or {@link ReadOnlyUseCase} to explicitly define transaction boundaries.
 * Use {@code @UseCase} only for non-transactional orchestrators that coordinate other transactional use cases (e.g. PlaceTransferUseCaseImpl).
 */
@Deprecated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseCase {
}
