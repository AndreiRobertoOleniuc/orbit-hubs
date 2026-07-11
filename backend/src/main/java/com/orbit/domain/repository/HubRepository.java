package com.orbit.domain.repository;

import java.util.List;
import java.util.Optional;

import com.orbit.domain.entity.Hub;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HubRepository extends JpaRepository<Hub, Long> {

    List<Hub> findByRegionId(Long regionId);

    Optional<Hub> findBySlug(String slug);

    List<Hub> findAllByOrderByNameAsc();
}
