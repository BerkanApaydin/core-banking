package com.bank.app.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
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
}
