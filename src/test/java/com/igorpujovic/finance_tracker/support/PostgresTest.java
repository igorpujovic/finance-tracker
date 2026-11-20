package com.igorpujovic.finance_tracker.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The two halves of ADR-0006, as the environment every integration test here runs in: a
 * real PostgreSQL, because the decisions this project makes live in queries and
 * constraints, and a real HTTP server standing in for the Kurs API, because the
 * distinction the import rests on is a distinction between HTTP outcomes. See
 * {@link FakeKurs} for the second.
 * <p>
 * The container is a plain static field started once in a static initializer rather
 * than a JUnit-managed {@code @Container}, which would stop and restart it for every
 * test class. One container serves the whole suite, and Flyway runs against it, so a
 * broken migration fails the build rather than failing {@code docker compose up}.
 * <p>
 * Both are registered from here rather than per class so that every test shares one
 * Spring context: a differing property set would be a different context cache key, and
 * the suite would pay for a second application and a second Flyway run.
 */
@SpringBootTest(properties = {
        // The scheduled refresh is a warm-up, and a suite is not a working day. Left
        // enabled it would reach the real National Bank on whatever machine ran the
        // build. `-` is Spring's own disabled-cron marker, so the bean is still wired
        // and `contextLoads` still proves the job's zone and its wiring.
        "app.exchange-rates.refresh-cron=-",
        // Short enough that the read-timeout test costs half a second rather than five,
        // and still two orders of magnitude above a loopback round trip.
        "app.kurs.read-timeout=500ms"
})
@AutoConfigureMockMvc
public abstract class PostgresTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    protected static final FakeKurs KURS;

    static {
        POSTGRES.start();
        KURS = FakeKurs.started();
    }

    @DynamicPropertySource
    static void bindKurs(DynamicPropertyRegistry registry) {
        registry.add("app.kurs.base-url", KURS::baseUrl);
    }

    /**
     * One fake serves the whole suite, so what one test programmed has to be forgotten
     * before the next one runs, or a test would pass on a reply it never set up.
     */
    @BeforeEach
    void forgetWhatKursWasToldToSay() {
        KURS.reset();
    }
}
