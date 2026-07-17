package com.orbit.domain.repository;

import java.util.Collection;
import java.util.List;

import com.orbit.domain.entity.RequestDistribution;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestDistributionRepository extends JpaRepository<RequestDistribution, Long> {

    /** All distributions of one request — used for wave dedup + owner count. */
    List<RequestDistribution> findByRequestId(Long requestId);

    /** The inbox view: everything distributed to the current user's entities. */
    List<RequestDistribution> findByEntityIdInOrderByCreatedAtDesc(Collection<Long> entityIds);

    /** "X Profile benachrichtigt" on the owner's request detail. */
    long countByRequestId(Long requestId);
}
