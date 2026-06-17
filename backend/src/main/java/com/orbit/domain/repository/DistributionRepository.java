package com.orbit.domain.repository;

import java.util.List;

import com.orbit.domain.entity.Distribution;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributionRepository extends JpaRepository<Distribution, Long> {

    List<Distribution> findByRequest_Id(String requestId);

    List<Distribution> findByEntity_Id(String entityId);
}
