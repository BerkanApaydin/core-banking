package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.infrastructure.adapter.in.idempotency.IdempotencyGuard.IdempotencyResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replay contract for idempotent responses.
 *
 * <p>A wrong branch here either double-executes a money movement or returns a
 * stale/error payload to the retried caller — both are covered explicitly.
 */
@DisplayName("IdempotencyResponseCodec")
class IdempotencyResponseCodecTest {

    private IdempotencyResponseCodec codec;

    @BeforeEach
    void setUp() {
        codec = new IdempotencyResponseCodec(new ObjectMapper());
    }

    @Nested
    @DisplayName("serialize")
    class Serialize {

        @Test
        @DisplayName("should store 2xx body bytes and status")
        void shouldStoreSuccess() throws Exception {
            // Arrange
            Map<String, Object> body = Map.of("id", 10);

            // Act
            var stored = codec.serialize(ResponseEntity.status(201).body(body));

            // Assert
            assertThat(stored.successful()).isTrue();
            assertThat(stored.status()).isEqualTo(201);
            assertThat(stored.body()).contains("\"id\":10");
        }

        @Test
        @DisplayName("should store empty body for null 2xx payload")
        void shouldStoreEmptyForNullBody() throws Exception {
            // Act
            var stored = codec.serialize(ResponseEntity.ok(null));

            // Assert
            assertThat(stored.successful()).isTrue();
            assertThat(stored.status()).isEqualTo(200);
            assertThat(stored.body()).isEmpty();
        }

        @Test
        @DisplayName("should mark non-2xx as unsuccessful without storing bytes")
        void shouldMarkErrorUnsuccessful() throws Exception {
            // Act
            var stored = codec.serialize(
                    ResponseEntity.status(409).body(Map.of("error", "conflict")));

            // Assert
            assertThat(stored.successful()).isFalse();
            assertThat(stored.status()).isEqualTo(409);
            assertThat(stored.body()).isEmpty();
        }

        @Test
        @DisplayName("should serialize plain (non-entity) responses as 200")
        void shouldSerializePlainResponse() throws Exception {
            // Act
            var stored = codec.serialize(Map.of("ok", true));

            // Assert
            assertThat(stored.successful()).isTrue();
            assertThat(stored.status()).isEqualTo(200);
            assertThat(stored.body()).contains("\"ok\":true");
        }

        @Test
        @DisplayName("should serialize null plain response as empty 200")
        void shouldSerializeNullAsEmpty() throws Exception {
            // Act
            var stored = codec.serialize(null);

            // Assert
            assertThat(stored.successful()).isTrue();
            assertThat(stored.body()).isEmpty();
        }
    }

    @Nested
    @DisplayName("replay")
    class Replay {

        @Test
        @DisplayName("should replay stored JSON with its original status")
        void shouldReplayJsonWithStatus() {
            // Arrange
            var result = IdempotencyResult.completed("{\"id\":10}", 201);

            // Act
            Object replayed = codec.replay(result);

            // Assert
            assertThat(replayed).isInstanceOf(ResponseEntity.class);
            ResponseEntity<?> entity = (ResponseEntity<?>) replayed;
            assertThat(entity.getStatusCode().value()).isEqualTo(201);
            assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(entity.getBody()).isEqualTo("{\"id\":10}");
        }

        @Test
        @DisplayName("should replay empty body without content type")
        void shouldReplayEmptyWithoutContentType() {
            // Arrange
            var result = IdempotencyResult.completed("", 200);

            // Act
            ResponseEntity<?> entity = (ResponseEntity<?>) codec.replay(result);

            // Assert
            assertThat(entity.getStatusCode().value()).isEqualTo(200);
            assertThat(entity.getBody()).isNull();
            assertThat(entity.getHeaders().getContentType()).isNull();
        }

        @Test
        @DisplayName("should treat blank and literal-null bodies as empty")
        void shouldTreatBlankAndNullLiteralAsEmpty() {
            for (String body : new String[]{"   ", "null", null}) {
                // Act
                ResponseEntity<?> entity =
                        (ResponseEntity<?>) codec.replay(IdempotencyResult.completed(body, 200));

                // Assert
                assertThat(entity.getBody())
                        .as("body <%s> must replay empty", body)
                        .isNull();
            }
        }

        @Test
        @DisplayName("should default to 200 when no status was stored")
        void shouldDefaultTo200WithoutStatus() {
            // Act
            ResponseEntity<?> entity =
                    (ResponseEntity<?>) codec.replay(IdempotencyResult.completed("{\"a\":1}", null));

            // Assert
            assertThat(entity.getStatusCode().value()).isEqualTo(200);
            assertThat(entity.getBody()).isEqualTo("{\"a\":1}");
        }
    }
}
