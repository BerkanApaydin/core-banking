package com.bank.app.common.idempotency;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    /**
     * Header name containing the idempotency key.
     */
    String headerName() default "Idempotency-Key";
}
