package com.orbit.domain.repository;

import com.orbit.domain.entity.DiscoveryEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoveryEntityRepository extends JpaRepository<DiscoveryEntity, Long> {
}