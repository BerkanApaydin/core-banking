package com.bank.app.security;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closes the remaining half of finding 6.3.
 *
 * <p>{@code SecurityProperties.DEFAULT_WHITELIST} covers static assets with the
 * patterns {@code /*.js}, {@code /*.css} and {@code /assets/**} instead of
 * enumerating filenames. {@code SecurityPropertiesTest} pins that those patterns
 * are <em>present in the list</em>, but it cannot prove Spring Security's request
 * matcher actually honours {@code /*.js} against {@code /app.js} — a
 * list-content assertion never exercises matcher semantics.
 *
 * <p>This test asks the real filter chain instead. It enumerates every file that
 * exists under {@code classpath:static/} and requests each one anonymously, so
 * it is self-maintaining: drop a new {@code foo.js} into {@code static/} and it
 * is covered on the next build without this file being edited. That is exactly the
 * failure mode the report described — "you add a frontend file and the login UI
 * silently breaks and no test notices" — with the direction of protection
 * reversed: a new asset cannot be added without being proven reachable.
 *
 * <p>Runs against the default (non-prod) whitelist. The production list is
 * separately pinned to {@code DEFAULT_WHITELIST + /actuator/prometheus} by
 * {@code ProductionConfigContractTest}, so both layers are covered.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Static assets are anonymously reachable")
class StaticAssetPublicAccessIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final TestRestTemplate restTemplate;

    @Autowired
    StaticAssetPublicAccessIntegrationTest(TestRestTemplate restTemplate,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.restTemplate = restTemplate;
    }

    /**
     * Maps a resolved classpath resource to its public URL path.
     *
     * <p>{@code Resource#getDescription()} returns different shapes depending on
     * whether the resource was read from a JAR or from the file system
     * ({@code file [/abs/path/…]}, {@code jar:file:/…!/static/…}). Normalising the
     * separators and anchoring on the last {@code static/} segment makes both work,
     * and the trailing {@code ]} of the file description is trimmed.
     */
    private static String publicPath(Resource resource) {
        String description = resource.getDescription().replace('\\', '/');
        int marker = description.lastIndexOf("static/");
        if (marker < 0) {
            throw new IllegalStateException(
                    "Resource is not under a static/ directory: " + description);
        }
        return "/" + description.substring(marker + "static/".length()).replaceAll("[\\]\\s]+$", "");
    }

    private static List<String> staticAssetPaths() throws IOException {
        return Stream.of(new PathMatchingResourcePatternResolver().getResources("classpath*:static/**"))
                .filter(Resource::isReadable)
                .map(StaticAssetPublicAccessIntegrationTest::publicPath)
                .distinct()
                .sorted()
                .toList();
    }

    private String readIndexHtml() throws IOException {
        try (InputStream in = new PathMatchingResourcePatternResolver()
                .getResource("classpath:static/index.html").getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    /**
     * Extracts the asset URLs {@code index.html} pulls in. References are
     * document-relative ({@code boot.js?v=15}), so they are resolved against the
     * site root and the cache-busting query string is dropped.
     */
    private static List<String> assetsReferencedBy(String html) {
        return Stream.of(html.split("\""))
                .map(String::trim)
                .map(reference -> reference.split("[?#]")[0])
                .filter(reference -> reference.endsWith(".js") || reference.endsWith(".css"))
                .map(reference -> reference.startsWith("/") ? reference : "/" + reference)
                .filter(reference -> !reference.contains("data:"))
                .distinct()
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("every file under static/ is served without authentication")
    void everyStaticAssetIsPubliclyReadable() throws IOException {
        List<String> paths = staticAssetPaths();

        assertThat(paths)
                .as("static asset enumeration must actually find the UI bundle — an empty list "
                        + "would make this test vacuously green")
                .isNotEmpty();

        for (String path : paths) {
            ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

            assertThat(response.getStatusCode())
                    .as("static asset %s must be anonymous-readable; a 401/403 here means the "
                            + "whitelist patterns no longer cover it and the login UI breaks for "
                            + "every visitor (6.3)", path)
                    .isEqualTo(HttpStatus.OK);
            assertThat(response.getBody())
                    .as("static asset %s must not be served empty", path)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("every bundle index.html references exists and is anonymously reachable")
    void bundlesReferencedByIndexHtmlArePublic() throws IOException {
        List<String> referenced = assetsReferencedBy(readIndexHtml());

        assertThat(referenced)
                .as("index.html must reference at least one JS/CSS bundle, otherwise this test "
                        + "is vacuous")
                .isNotEmpty();

        assertThat(referenced)
                .as("every asset referenced by index.html must exist under static/ — a missing "
                        + "bundle is a 404 for anonymous visitors even when the whitelist pattern "
                        + "matches the path")
                .isSubsetOf(staticAssetPaths());

        for (String reference : referenced) {
            assertThat(restTemplate.getForEntity(reference, String.class).getStatusCode())
                    .as("asset %s referenced by index.html must be anonymous-readable", reference)
                    .isEqualTo(HttpStatus.OK);
        }
    }
}
