package com.bank.app.common.adapter.in.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class ApiRoutesTest {

    @Test
    void shouldHaveConstantV1() {
        assertEquals("/api/v1", ApiRoutes.V1);
    }

    @Test
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<ApiRoutes> constructor = ApiRoutes.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Test
    void shouldBeFinalClass() {
        assertTrue(Modifier.isFinal(ApiRoutes.class.getModifiers()));
    }
}
