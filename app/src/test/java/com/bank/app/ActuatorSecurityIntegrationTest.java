package com.bank.app;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for K1/D1: the Prometheus alarm pipeline is only alive if the
 * scrape endpoint is exposed AND reachable under the security whitelist.
 *
 * <p>Test profile uses the default whitelist (no explicit prod list), so
 * {@code /actuator/prometheus} must be secured (401/403) — never 404 (not
 * exposed) or 500. Production opens it explicitly via
 * {@code app.security.whitelist-paths} in {@code application-prod.yml}
 * (NetworkPolicy isolates scraping to the monitoring namespace).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorSecurityIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final TestRestTemplate restTemplate;

    @Autowired
    ActuatorSecurityIntegrationTest(TestRestTemplate restTemplate,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.restTemplate = restTemplate;
    }

    @Test
    void healthEndpointIsPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void prometheusEndpointIsExposedButSecuredUnderDefaultWhitelist() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/prometheus", String.class);

        // 401/403 = exposed and denied (correct under default whitelist).
        // 404 = actuator exposure misconfigured (regression).
        // 500 = filter chain broken (regression).
        assertThat(response.getStatusCode())
                .as("prometheus must be exposed-but-secured, not missing or broken")
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
