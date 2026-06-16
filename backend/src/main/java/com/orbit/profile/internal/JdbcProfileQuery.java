package com.orbit.profile.internal;

import javax.sql.DataSource;

import com.orbit.profile.ProfileQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed implementation of the profile module's published query interface.
 */
@Repository
class JdbcProfileQuery implements ProfileQuery {

    private static final Logger log = LoggerFactory.getLogger(JdbcProfileQuery.class);

    private final JdbcClient jdbc;

    JdbcProfileQuery(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public void provisionUser(String userId, String email, String displayName) {
        if (email == null) {
            log.warn("Provisioning user {} from a token without an email claim", userId);
        }

        jdbc.sql("""
                INSERT INTO users (id, email, display_name, created_at)
                VALUES (:id, :email, :displayName, now())
                ON CONFLICT (id) DO UPDATE
                    SET email        = COALESCE(EXCLUDED.email, users.email),
                        display_name = COALESCE(EXCLUDED.display_name, users.display_name)
                    WHERE users.email IS DISTINCT FROM EXCLUDED.email
                       OR users.display_name IS DISTINCT FROM EXCLUDED.display_name
                """)
                .param("id", userId)
                .param("email", email)
                .param("displayName", displayName)
                .update();
    }

    @Override
    public boolean isOwnedBy(String entityId, String userId) {
        return jdbc.sql("""
                SELECT EXISTS(
                    SELECT 1 FROM entities
                    WHERE id = :entityId AND owner_user_id = :userId
                )
                """)
                .param("entityId", entityId)
                .param("userId", userId)
                .query(Boolean.class)
                .single();
    }

}
