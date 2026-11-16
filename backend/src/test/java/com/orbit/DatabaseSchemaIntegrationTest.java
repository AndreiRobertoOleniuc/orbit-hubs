package com.orbit;

import com.orbit.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Ensures an empty pgvector database can be migrated and validated by the application. */
@SpringBootTest
class DatabaseSchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayCreatesPgvectorAndTheScopedHybridSearchFunction() {
        String vectorExtension = jdbc.queryForObject(
                "SELECT extname FROM pg_extension WHERE extname = 'vector'", String.class);
        Integer appliedMigrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);
        String searchFunction = jdbc.queryForObject("""
                SELECT to_regprocedure(
                  'hybrid_search(text,vector,vector,bigint[],bigint[],integer,integer,double precision,double precision,double precision,integer)'
                )::text
                """, String.class);

        String jobQueue = jdbc.queryForObject("SELECT to_regclass('ingestion_jobs')::text", String.class);

        assertThat(vectorExtension).isEqualTo("vector");
        assertThat(appliedMigrations).isGreaterThanOrEqualTo(20);
        assertThat(searchFunction).contains("hybrid_search");
        assertThat(jobQueue).isEqualTo("ingestion_jobs");
    }
}
