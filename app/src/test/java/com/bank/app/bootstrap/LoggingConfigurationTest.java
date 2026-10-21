package com.bank.app.bootstrap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigurationTest {

    @Test
    void prodEncoderMustNotInterpolateRawMessagesIntoAJsonPattern() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

        // A raw <pattern> embedding "%message" produces invalid JSON when a
        // message contains quotes/newlines and lets log content forge fields.
        // The structured encoder escapes every value itself.
        assertThat(config).contains("net.logstash.logback.encoder.LogstashEncoder");
        assertThat(config).doesNotContain("LoggingEventCompositeJsonEncoder");
        assertThat(config).doesNotContain("%message");
    }

    @Test
    void prodEncoderProducesJacksonParseableJsonForHostileMessages() throws Exception {
        // R-C02: a string-level check of logback-spring.xml is not proof that
        // the prod encoder emits JSON. Feed it a message containing a quote
        // and a newline, then parse the bytes with the same Jackson version
        // the app uses. A raw pattern would split the event across lines and
        // let the payload forge additional JSON fields.
        String message = "user=\"alice\"\nforged=\"admin\"";
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.setTimeZone("UTC");
        encoder.start();
        try {
            Logger logger = context.getLogger("prod-json-contract");
            byte[] encoded = encoder.encode(new LoggingEvent(
                    LoggingConfigurationTest.class.getName(), logger,
                    Level.INFO, message, null, null));
            String json = new String(encoded, StandardCharsets.UTF_8).strip();

            assertThat(json).doesNotContain("\n");
            JsonNode event = new ObjectMapper().readTree(json);
            assertThat(event.get("message").asText()).isEqualTo(message);
            assertThat(event.get("level").asText()).isEqualTo("INFO");
            assertThat(event.get("logger_name").asText()).isEqualTo("prod-json-contract");
        } finally {
            encoder.stop();
            context.stop();
        }
    }
}
