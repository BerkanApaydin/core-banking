package com.bank.app.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSeedPropertiesTest {

    @Test
    void shouldExposeTheConfiguredSeedPasswords() {
        DemoSeedProperties properties =
                new DemoSeedProperties("Ahmet12345678", "Ayse12345678");

        assertThat(properties.ahmetPassword()).isEqualTo("Ahmet12345678");
        assertThat(properties.aysePassword()).isEqualTo("Ayse12345678");
    }

    @Test
    void shouldBindUnderTheDemoSeedPrefix() {
        assertThat(DemoSeedProperties.class.getAnnotation(ConfigurationProperties.class).prefix())
                .isEqualTo("app.demo.seed");
    }

    /**
     * The bound defaults are blank, which is exactly what the seeder has to
     * reject: enabling the demo profile without a password must fail loudly
     * instead of creating a demo user with an empty password.
     */
    @Test
    void shouldBindBlankPasswordsByDefault() {
        // Binder needs at least one present property under the prefix before
        // it instantiates a value object, so each half is bound explicitly and
        // the other half must come from @DefaultValue("").
        DemoSeedProperties ahmetBound = bind(Map.of("app.demo.seed.ahmet-password", ""));
        DemoSeedProperties ayseBound = bind(Map.of("app.demo.seed.ayse-password", ""));

        assertThat(ahmetBound.ahmetPassword()).isEmpty();
        assertThat(ahmetBound.aysePassword()).isEmpty();
        assertThat(ayseBound.ahmetPassword()).isEmpty();
        assertThat(ayseBound.aysePassword()).isEmpty();
    }

    private static DemoSeedProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("app.demo.seed", DemoSeedProperties.class)
                .orElseThrow(() -> new AssertionError("app.demo.seed must bind to defaults"));
    }

    @Test
    void shouldExposeTheBoundPasswordsAsBlankWhenBothAreUnset() {
        DemoSeedProperties unset = new DemoSeedProperties("", "");

        assertThat(unset.ahmetPassword()).isBlank();
        assertThat(unset.aysePassword()).isBlank();
    }
}
