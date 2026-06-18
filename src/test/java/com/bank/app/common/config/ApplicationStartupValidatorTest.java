package com.bank.app.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationStartupValidatorTest {

    @Mock private Environment environment;

    private ApplicationStartupValidator validator;

    private static final String DEFAULT_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        validator = new ApplicationStartupValidator(environment);
    }

    @Test
    void shouldSkipValidationWhenProfileIsNotProd() {
        when(environment.getActiveProfiles())
                .thenReturn(new String[] { "local" });

        assertDoesNotThrow(() -> validator.validateProductionConfig());
    }

    @Test
    void shouldThrowWhenJwtSecretIsDefaultInProd() {
        when(environment.getActiveProfiles())
                .thenReturn(new String[] { "prod" });

        when(environment.getProperty(
                "jwt.secret",
                DEFAULT_JWT_SECRET)).thenReturn(DEFAULT_JWT_SECRET);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator.validateProductionConfig());

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("non-default JWT secret");
    }

    @Test
    void shouldThrowWhenDatabasePasswordIsBlankInProd() {
        when(environment.getActiveProfiles())
                .thenReturn(new String[] { "prod" });

        when(environment.getProperty(
                "jwt.secret",
                DEFAULT_JWT_SECRET)).thenReturn("secure-jwt-secret");

        when(environment.getProperty(
                "spring.datasource.password",
                "")).thenReturn("");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator.validateProductionConfig());

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("secure database password");
    }

    @Test
    void shouldThrowWhenDatabasePasswordIsDefaultInProd() {
        when(environment.getActiveProfiles())
                .thenReturn(new String[] { "prod" });

        when(environment.getProperty(
                "jwt.secret",
                DEFAULT_JWT_SECRET)).thenReturn("secure-jwt-secret");

        when(environment.getProperty(
                "spring.datasource.password",
                "")).thenReturn("bank_password");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator.validateProductionConfig());

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("secure database password");
    }

    @Test
    void shouldPassWhenProductionConfigurationIsValid() {
        when(environment.getActiveProfiles())
                .thenReturn(new String[] { "prod" });

        when(environment.getProperty(
                "jwt.secret",
                DEFAULT_JWT_SECRET)).thenReturn("very-secure-secret");

        when(environment.getProperty(
                "spring.datasource.password",
                "")).thenReturn("very-secure-password");

        assertDoesNotThrow(() -> validator.validateProductionConfig());
    }
}