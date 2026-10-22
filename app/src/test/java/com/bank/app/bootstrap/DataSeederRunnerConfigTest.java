package com.bank.app.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederRunnerConfigTest {

    @Mock
    private DataSeeder dataSeeder;

    @Test
    void shouldExposeTheSeederRunnerAndRunIt() throws Exception {
        AtomicBoolean seeded = new AtomicBoolean();
        CommandLineRunner seederRunner = args -> seeded.set(true);
        when(dataSeeder.seedData()).thenReturn(seederRunner);

        CommandLineRunner runner = new DataSeederRunnerConfig().seedData(dataSeeder);

        assertThat(runner).isSameAs(seederRunner);
        runner.run();
        assertThat(seeded).isTrue();
        verify(dataSeeder).seedData();
    }
}
