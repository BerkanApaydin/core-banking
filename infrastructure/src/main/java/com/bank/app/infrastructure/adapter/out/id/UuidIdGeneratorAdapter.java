package com.bank.app.infrastructure.adapter.out.id;

import com.bank.app.common.application.port.out.IdGeneratorPort;
import java.util.UUID;

/**
 * D-1: production {@link IdGeneratorPort} backed by random UUIDs.
 *
 * <p>Wired as an explicit bean in {@code CommonBeanConfig}. Not a
 * {@code @Component}: platform seams are declared beans so the composition
 * root stays visible in one place.
 */
public class UuidIdGeneratorAdapter implements IdGeneratorPort {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
