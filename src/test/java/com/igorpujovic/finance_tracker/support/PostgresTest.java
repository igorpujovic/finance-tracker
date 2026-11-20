package com.igorpujovic.finance_tracker.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ADR-0006: the decisions this project makes live in queries and constraints, so the
 * tests that matter run against a real PostgreSQL.
 * <p>
 * The container is a plain static field started once in a static initializer rather
 * than a JUnit-managed {@code @Container}, which would stop and restart it for every
 * test class. One container serves the whole suite, and Flyway runs against it, so a
 * broken migration fails the build rather than failing {@code docker compose up}.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PostgresTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }
}
