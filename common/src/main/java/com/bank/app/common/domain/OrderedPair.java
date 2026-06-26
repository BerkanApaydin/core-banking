package com.bank.app.common.domain;

import java.util.function.Supplier;

public record OrderedPair<T>(T lowerIdItem, T higherIdItem) {

    public static <T> OrderedPair<T> from(Long id1, Supplier<T> supplier1, Long id2, Supplier<T> supplier2) {
        if (id1.compareTo(id2) < 0) {
            return new OrderedPair<>(supplier1.get(), supplier2.get());
        }
        return new OrderedPair<>(supplier2.get(), supplier1.get());
    }
}
