package com.gauravacharya.nimbus;

import com.gauravacharya.nimbus.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifies the application context loads against a real PostgreSQL instance
 * with all Flyway migrations applied.
 */
@SpringBootTest(properties = "nimbus.worker.scheduled=false")
@Import(PostgresTestContainer.class)
class NimbusApplicationTests {

    @Test
    void contextLoads() {
    }
}
