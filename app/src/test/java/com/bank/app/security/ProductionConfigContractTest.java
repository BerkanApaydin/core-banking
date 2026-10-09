package com.bank.app.security;

import com.bank.app.infrastructure.adapter.in.security.SecurityProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the production configuration contract that the rest of the repository
 * silently depends on, starting with K1/D1 — the single line in
 * {@code application-prod.yml} that keeps the alerting pipeline alive.
 *
 * <p>Why a config-binding test rather than an HTTP test: booting the real
 * {@code prod} profile is impossible in tests by design.
 * {@code ApplicationStartupValidator} rejects {@code prod} combined with
 * {@code test}/{@code testcontainers} and additionally demands a non-default
 * JWT secret, a non-default DB password and live Redis backends. Even if that
 * were bypassed, {@code ActuatorSecurityIntegrationTest} already pins the
 * *default* (non-prod) behaviour: there, {@code /actuator/prometheus} must be
 * denied. So the only unprotected link in the whole chain was the production
 * property file itself — one deleted line and every test still passed while
 * the alarm pipeline was dead again.
 *
 * <p>This test loads {@code application.yml} + {@code application-prod.yml} as
 * Spring Boot layers them, binds the result exactly as the runtime would, and
 * asserts on it. No Spring context, no Testcontainers, no environment variables.
 *
 * <p>Alerting chain being protected, end to end:
 * <ol>
 *   <li>{@code management.endpoints.web.exposure.include} contains
 *       {@code prometheus} — otherwise the endpoint 404s and a correct
 *       whitelist is worthless.</li>
 *   <li>{@code app.security.whitelist-paths} contains
 *       {@code /actuator/prometheus} — otherwise the endpoint answers
 *       401/403 and Prometheus cannot scrape. This is the K1/D1 line.</li>
  *   <li>The prod list is {@code DEFAULT_WHITELIST + prometheus − swagger}:
  *       swagger stays dev-only because springdoc is disabled in prod, so a
  *       static asset added for the login UI cannot silently break prod
  *       while dev keeps working (the 6.3 failure mode).</li>
 * </ol>
 * NetworkPolicy isolation for the exposed endpoint lives in
 * {@code k8s/networkpolicy.yaml} (ingress from the {@code monitoring}
 * namespace only) and is out of scope here.
 */
@DisplayName("Production configuration contract")
class ProductionConfigContractTest {

    private static ConfigurableEnvironment productionEnvironment;

    /**
     * Replays Spring Boot's own two-layer profile resolution: the base
     * {@code application.yml} first, then {@code application-prod.yml} on top
     * (profile-specific sources have higher precedence). Both layers are needed
     * — {@code management.endpoints.web.exposure.include} lives in the base file
     * while {@code app.security.whitelist-paths} lives in the prod file, so
     * asserting either one alone would prove nothing about the assembled
     * production configuration.
     *
     * <p>Precedence is set explicitly rather than by insertion order. In a
     * {@code MutablePropertySources} the <em>last</em> entry is the
     * <em>lowest</em>-precedence one, so naively appending both files would make
     * the base config silently win over prod — the opposite of runtime behaviour,
     * and it would let a stale base value pass as if it were the prod setting.
     */
    @BeforeAll
    static void loadProductionConfiguration() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        File base = mainResource("application.yml");
        for (PropertySource<?> source : loader.load("application.yml", new FileSystemResource(base))) {
            environment.getPropertySources().addLast(source);
        }

        File prod = mainResource("application-prod.yml");
        List<PropertySource<?>> prodSources = loader.load("application-prod", new FileSystemResource(prod));
        assertThat(prodSources).as("%s must parse as YAML", prod).isNotEmpty();
        for (PropertySource<?> source : prodSources) {
            // Profile-specific overrides the base file: highest precedence.
            environment.getPropertySources().addFirst(source);
        }

        productionEnvironment = environment;
    }

    /**
     * Resolves a file from {@code src/main/resources} explicitly rather than via
     * {@code ClassPathResource}.
     *
     * <p>This is not cosmetic. {@code app/src/test/resources/application.yml} also
     * exists, and Maven puts {@code target/test-classes} ahead of
     * {@code target/classes} on the test classpath. A classpath lookup therefore
     * silently returns the <em>test</em> base config, which never carried the
     * production {@code management.endpoints.web.exposure.include} — the assertion
     * would then either fail spuriously or, worse, be satisfied by test settings
     * while proving nothing about what a real prod pod reads. Searching upward
     * from the working directory works both under Maven (cwd = module basedir)
     * and from an IDE (cwd = repository root).
     */
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

    private static String property(String key, String defaultValue) {
        return productionEnvironment.getProperty(key, defaultValue);
    }

    /**
     * Reads a value <em>without</em> resolving {@code ${...}} placeholders, so the
     * literal text as authored in the YAML can be asserted on. Going through
     * {@link ConfigurableEnvironment#getProperty} would substitute the placeholder
     * and hide exactly the property shape these assertions are about.
     */
    private static String rawProperty(String key) {
        for (PropertySource<?> source : productionEnvironment.getPropertySources()) {
            if (source.containsProperty(key)) {
                Object value = source.getProperty(key);
                return value == null ? null : String.valueOf(value);
            }
        }
        return null;
    }

    private static List<String> productionWhitelist() {
        return Binder.get(productionEnvironment)
                .bind("app.security", SecurityProperties.class)
                .orElseThrow(() -> new AssertionError("app.security is not bindable from application-prod.yml"))
                .whitelistPaths();
    }

    @Nested
    @DisplayName("K1/D1 — the Prometheus scrape path")
    class PrometheusScrapePath {

        @Test
        @DisplayName("production whitelist must permit /actuator/prometheus (the K1/D1 line)")
        void productionWhitelistMustExposePrometheusForScraping() {
            assertThat(productionWhitelist())
                    .as("K1/D1: /actuator/prometheus must stay in application-prod.yml's whitelist-paths, "
                            + "otherwise every alert in k8s/prometheus-rules.yaml reads 'no data' forever")
                    .contains("/actuator/prometheus");
        }

        @Test
        @DisplayName("actuator exposure must include prometheus, otherwise the whitelist is moot")
        void productionMustExposePrometheusActuatorEndpoint() {
            String include = property("management.endpoints.web.exposure.include", "");

            assertThat(include)
                    .as("management.endpoints.web.exposure.include must contain prometheus — "
                            + "a whitelisted but unexposed endpoint answers 404 and still kills the alarm line")
                    .contains("prometheus");
        }
    }

    @Nested
    @DisplayName("fail-closed interaction with K2/D2")
    class FailClosedInteraction {

        @Test
        @DisplayName("production whitelist must be explicitly configured and non-empty")
        void productionWhitelistMustBeExplicitAndNonEmpty() {
            String raw = property("app.security.whitelist-paths[0]", null);

            assertThat(raw)
                    .as("application-prod.yml must define app.security.whitelist-paths explicitly; "
                            + "an absent key falls back to SecurityProperties.DEFAULT_WHITELIST, "
                            + "which deliberately does NOT include prometheus (see SecurityPropertiesTest)")
                    .isNotNull();
            assertThat(productionWhitelist())
                    .as("an empty production whitelist would fail closed (K2/D2) and deny the login UI "
                            + "along with the scrape endpoint")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("production whitelist must keep the public login surface")
        void productionWhitelistMustKeepLoginSurface() {
            assertThat(productionWhitelist())
                    .as("the browser login UI is served to anonymous callers")
                    .contains("/api/v1/auth/login",
                            "/api/v1/auth/browser/login",
                            "/api/v1/auth/register",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/browser/refresh",
                            "/actuator/health/**");
        }
    }

    @Nested
    @DisplayName("6.3 — production list must not drift from the default list")
    class ProductionListDrift {

        /**
         * Swagger is enabled in dev (anonymous UI exploration) but disabled in
         * prod. These paths therefore live in {@code DEFAULT_WHITELIST} and
         * must NOT be copied into the prod file — whitelisting 404-only
         * endpoints would be dead config widening the anonymous surface.
         */
        private static final List<String> DEV_ONLY_PATHS = List.of(
                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");

        @Test
        @DisplayName("production whitelist must equal defaults plus prometheus minus disabled swagger")
        void productionWhitelistMustMatchDefaultsPlusPrometheusMinusSwagger() {
            List<String> expected = Stream.concat(
                            SecurityProperties.DEFAULT_WHITELIST.stream(),
                            Stream.of("/actuator/prometheus"))
                    .filter(path -> !DEV_ONLY_PATHS.contains(path))
                    .toList();

            assertThat(productionWhitelist())
                    .as("application-prod.yml duplicates SecurityProperties.DEFAULT_WHITELIST by hand. "
                            + "This test is the seam that keeps the two in step: the production list must be "
                            + "the default list plus the Prometheus scrape endpoint, minus the swagger paths "
                            + "(springdoc is disabled in prod). "
                            + "Without this seam, a static asset or auth route added to the default list reaches "
                            + "dev but not prod, and the login UI breaks only in production (the 6.3 mode)")
                    .containsExactlyInAnyOrderElementsOf(expected);
        }

        @Test
        @DisplayName("production whitelist must not contain swagger paths (springdoc disabled in prod)")
        void productionWhitelistMustNotContainSwaggerPaths() {
            assertThat(productionWhitelist())
                    .as("springdoc is disabled in prod, so its endpoints only answer 404; "
                            + "whitelisting them would widen the anonymous surface for no reason. "
                            + "Dev keeps them via DEFAULT_WHITELIST, where springdoc is enabled.")
                    .doesNotContain("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");
        }

        @Test
        @DisplayName("production whitelist must use asset patterns, not enumerated filenames")
        void productionWhitelistMustUseAssetPatterns() {
            assertThat(productionWhitelist())
                    .as("static assets must be covered by patterns (6.3)")
                    .contains("/*.js", "/*.css", "/assets/**");
            assertThat(productionWhitelist())
                    .as("enumerated static filenames reintroduce the 6.3 breakage risk")
                    .doesNotContain("/app.js", "/boot.js", "/accounts.js", "/transfers.js", "/idempotency.js");
        }
    }

    @Nested
    @DisplayName("attack-surface direction")
    class AttackSurfaceDirection {

        @Test
        @DisplayName("production must not expose or whitelist /actuator/info")
        void productionMustNotExposeInfoEndpoint() {
            assertThat(property("management.endpoints.web.exposure.include", ""))
                    .as("/actuator/info is not used by any scrape or alert; exposing it widens "
                            + "the anonymous surface for no operational gain")
                    .doesNotContain("info");
            assertThat(productionWhitelist())
                    .as("a whitelisted but unexposed endpoint is dead config")
                    .doesNotContain("/actuator/info");
        }

        @Test
        @DisplayName("production must keep swagger disabled")
        void productionMustKeepSwaggerDisabled() {
            assertThat(property("springdoc.api-docs.enabled", ""))
                    .isEqualTo("false");
            assertThat(property("springdoc.swagger-ui.enabled", ""))
                    .isEqualTo("false");
        }
    }

    /**
     * Answers the open question from finding 14.1: does
     * {@code InMemoryAccountInfoCacheAdapter} reach production?
     *
     * <p>{@code TransferBeanConfig} registers it as
     * {@code @ConditionalOnMissingBean(AccountSnapshotCache.class)}, so the
     * infrastructure backend wins whenever it is wired. That backend is
     * {@code RedisAccountSnapshotCacheAdapter}, enabled by
     * {@code SnapshotCacheRedisCondition} on the resolved backend
     * (canonical {@code app.cache.account-info.backend}, legacy
     * {@code app.cache.caffeine.account-info.backend} as fallback).
     *
     * <p>The silent-failure path this guards is: if the prod value ever degrades
     * away from {@code redis}, no backend bean is registered, the conditional
     * fallback quietly takes over, and a per-JVM map serves all replicas. HPA runs
     * up to six pods, so a snapshot cached on pod A would never be invalidated by
     * an eviction on pod B — stale account status/currency up to the 60s TTL,
     * cross-pod, with nothing in the logs to say so. {@code docs/account-snapshot-cache.md}
     * documents that Caffeine/InMemory must never be used with more than one
     * replica; this assertion makes the config unable to do it by accident.
     */
    @Nested
    @DisplayName("14.1 — snapshot cache backend selection")
    class SnapshotCacheBackendSelection {

        @Test
        @DisplayName("production must pin the snapshot cache to the shared Redis backend")
        void productionMustUseSharedSnapshotCacheBackend() {
            assertThat(property("app.cache.account-info.backend", ""))
                    .as("production must use redis for the account snapshot cache. Anything else "
                            + "leaves TransferBeanConfig's @ConditionalOnMissingBean fallback "
                            + "(InMemoryAccountInfoCacheAdapter) active, which is a per-JVM map: "
                            + "with >1 replica, an eviction on one pod never reaches another, so "
                            + "account status/currency reads go stale across the cluster "
                            + "(14.1). The prod value is a literal, not an env placeholder, so an "
                            + "environment override cannot flip it by accident")
                    .isEqualTo("redis");
            assertThat(property("app.cache.caffeine.account-info.backend", ""))
                    .as("legacy alias must mirror the canonical prod pin so raw-key readers "
                            + "and older overrides resolve the same backend")
                    .isEqualTo("redis");
        }

        @Test
        @DisplayName("production must not let an environment variable redirect the snapshot cache")
        void productionSnapshotCacheBackendMustNotBeEnvOverridable() {
            String raw = rawProperty("app.cache.account-info.backend");

            assertThat(raw)
                    .as("if this were ${CACHE_ACCOUNT_INFO_BACKEND:caffeine} the env var would "
                            + "override it in prod and could silently reinstate the in-memory "
                            + "fallback; a literal keeps the Redis decision non-negotiable")
                    .doesNotContain("${");
            assertThat(rawProperty("app.cache.caffeine.account-info.backend"))
                    .as("legacy alias must be a literal for the same reason")
                    .doesNotContain("${");
        }
    }
}
