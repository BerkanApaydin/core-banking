package com.bank.app.common.adapter.in.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    String headerName() default "Idempotency-Key";
    boolean publicEndpoint() default false;
    /**
     * When true, requests without the idempotency header are rejected instead
     * of silently bypassing the guard. Use for money-movement endpoints.
     */
    boolean required() default false;
}
