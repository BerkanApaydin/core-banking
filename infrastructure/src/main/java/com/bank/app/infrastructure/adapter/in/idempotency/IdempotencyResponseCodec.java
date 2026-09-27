package com.bank.app.infrastructure.adapter.in.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Preserves the first response's JSON bytes and status for later replay. */
final class IdempotencyResponseCodec {
    private final ObjectMapper objectMapper;

    IdempotencyResponseCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    StoredResponse serialize(Object response) throws JsonProcessingException {
        if (response instanceof ResponseEntity<?> entity) {
            if (!entity.getStatusCode().is2xxSuccessful()) {
                return new StoredResponse("", entity.getStatusCode().value(), false);
            }
            String json = entity.getBody() == null ? "" : objectMapper.writeValueAsString(entity.getBody());
            return new StoredResponse(json, entity.getStatusCode().value(), true);
        }
        String json = response == null ? "" : objectMapper.writeValueAsString(response);
        return new StoredResponse(json, 200, true);
    }

    Object replay(IdempotencyGuard.IdempotencyResult result) {
        HttpStatusCode status = result.responseStatus() != null
                ? HttpStatusCode.valueOf(result.responseStatus())
                : HttpStatusCode.valueOf(200);
        String body = result.responseBody();
        if (body == null || body.isBlank() || "null".equals(body)) {
            return ResponseEntity.status(status).build();
        }
        // Returning the stored JSON string with JSON content type avoids
        // changing number types, field order or shape on a replay.
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    record StoredResponse(String body, int status, boolean successful) {}
}
