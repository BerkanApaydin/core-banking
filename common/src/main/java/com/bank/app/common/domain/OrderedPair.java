package com.bank.app.common.domain;

import java.util.Objects;
import java.util.function.Supplier;

public record OrderedPair<T>(T lowerIdItem, T higherIdItem) {

    public static <T> OrderedPair<T> from(Long id1, Supplier<T> supplier1, Long id2, Supplier<T> supplier2) {
        Objects.requireNonNull(id1, "id1 must not be null");
        Objects.requireNonNull(id2, "id2 must not be null");
        Objects.requireNonNull(supplier1, "supplier1 must not be null");
        Objects.requireNonNull(supplier2, "supplier2 must not be null");
        // Invoke suppliers in stable ID order so pessimistic locks are always
        // acquired in the same sequence (deadlock prevention). Suppliers must
        // NOT be invoked before the comparison.
        if (id1.compareTo(id2) < 0) {
            T first = supplier1.get();
            T second = supplier2.get();
            Objects.requireNonNull(first, "supplier1 must not return null");
            Objects.requireNonNull(second, "supplier2 must not return null");
            return new OrderedPair<>(first, second);
        }
        T first = supplier2.get();
        T second = supplier1.get();
        Objects.requireNonNull(first, "supplier must not return null");
        Objects.requireNonNull(second, "supplier must not return null");
        return new OrderedPair<>(first, second);
    }
}
