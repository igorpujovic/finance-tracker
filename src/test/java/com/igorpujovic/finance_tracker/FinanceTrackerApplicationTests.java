package com.igorpujovic.finance_tracker;

import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.junit.jupiter.api.Test;

class FinanceTrackerApplicationTests extends PostgresTest {

    @Test
    void contextLoads() {
        // Loading the context runs Flyway against a real PostgreSQL and then has
        // Hibernate validate its own mapping against the migrated schema, so a drift
        // between an entity and a migration fails here rather than at runtime.
    }
}
