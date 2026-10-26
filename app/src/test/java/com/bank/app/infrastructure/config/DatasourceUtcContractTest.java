package com.bank.app.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceUtcContractTest {

    @Test
    void baseDatasourcePinsUtcSessionTimeZone() throws IOException {
        String url = load("application.yml").getProperty("spring.datasource.url");

        assertThat(url)
                .isNotNull()
                .contains("options=-c%20TimeZone%3DUTC");
    }

    @Test
    void devProfileDatasourceKeepsTheUtcSessionTimeZone() throws IOException {
        String baseUrl = load("application.yml").getProperty("spring.datasource.url");
        String devUrl = load("application.yml", "application-dev.yml")
                .getProperty("spring.datasource.url");

        assertThat(devUrl)
                .as("a profile-specific spring.datasource.url replaces the base URL; "
                        + "the dev override must repeat the UTC session option")
                .isEqualTo(baseUrl)
                .contains("options=-c%20TimeZone%3DUTC");
    }

    @Test
    void devProfileKeepsTheUtcJacksonAndHibernateBindings() throws IOException {
        MockEnvironment environment = load("application.yml", "application-dev.yml");

        assertThat(environment.getProperty("spring.jackson.time-zone")).isEqualTo("UTC");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"))
                .isEqualTo("UTC");
    }

    private static MockEnvironment load(String... resourceNames) throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        MockEnvironment environment = new MockEnvironment();

        for (int i = 0; i < resourceNames.length; i++) {
            String resourceName = resourceNames[i];
            List<PropertySource<?>> sources = loader.load(resourceName,
                    new FileSystemResource(mainResource(resourceName)));
            assertThat(sources).as("%s must parse as YAML", resourceName).isNotEmpty();
            for (PropertySource<?> source : sources) {
                if (i == 0) {
                    environment.getPropertySources().addLast(source);
                } else {
                    environment.getPropertySources().addFirst(source);
                }
            }
        }
        return environment;
    }

    private static File mainResource(String fileName) {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            for (String prefix : List.of(Path.of("src", "main", "resources").toString(),
                    Path.of("app", "src", "main", "resources").toString())) {
                Path candidate = directory.resolve(prefix).resolve(fileName);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toFile();
                }
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException(
                "Could not locate " + fileName + " under any src/main/resources tree above "
                        + System.getProperty("user.dir"));
    }
}
