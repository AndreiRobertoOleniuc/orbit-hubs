package com.orbit.domain.repository;

import java.util.List;

import com.orbit.domain.entity.DiscoveryEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoveryEntityRepository extends JpaRepository<DiscoveryEntity, Long> {

    /** All entities created through ingestion by this user (newest first). */
    List<DiscoveryEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    /** All entities of a given type (e.g. "organization") — for Person↔Org matching. */
    List<DiscoveryEntity> findByEntityType(String entityType);
}