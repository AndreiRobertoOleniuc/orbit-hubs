package com.orbit.domain.repository;

import java.util.Optional;

import com.orbit.domain.entity.Region;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findBySlug(String slug);
}
