package com.orbit.domain.repository;

import com.orbit.domain.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Idempotently create or refresh a user from an external identity token. Kept as native
     * SQL because the upsert/COALESCE semantics are not expressible as a JPA save, and the
     * behaviour is pinned by {@code ClerkAuthIntegrationTest}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO users (id, email, display_name, created_at)
            VALUES (:id, :email, :displayName, now())
            ON CONFLICT (id) DO UPDATE
                SET email        = COALESCE(EXCLUDED.email, users.email),
                    display_name = COALESCE(EXCLUDED.display_name, users.display_name)
                WHERE users.email IS DISTINCT FROM EXCLUDED.email
                   OR users.display_name IS DISTINCT FROM EXCLUDED.display_name
            """, nativeQuery = true)
    void upsert(@Param("id") String id, @Param("email") String email, @Param("displayName") String displayName);
}
