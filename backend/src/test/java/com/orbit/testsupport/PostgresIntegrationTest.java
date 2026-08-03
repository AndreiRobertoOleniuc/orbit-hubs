package com.orbit.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared, disposable PostgreSQL + pgvector substrate for integration tests.
 * Flyway migrates this database on application startup, exactly as it does in
 * the deployed application. No developer-local database or external AI service
 * is used by tests extending this class.
 */
public abstract class PostgresIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("orbit_test")
            .withUsername("orbit")
            .withPassword("orbit");

    static {
        // DynamicPropertySource is read while Spring builds its context, before
        // JUnit's per-class callbacks run. Start here so the mapped JDBC port
        // already exists when Spring resolves spring.datasource.url.
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
