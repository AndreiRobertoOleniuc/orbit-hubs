package com.orbit.domain.repository;

import com.orbit.domain.entity.Entity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityRepository extends JpaRepository<Entity, String> {

    /** True when {@code entities.owner_user_id = userId} — the ownership guard for (auth) routes. */
    boolean existsByIdAndOwner_Id(String id, String userId);
}
