package com.orbit.domain.repository;

import java.util.Collection;
import java.util.List;

import com.orbit.domain.entity.HubMembership;
import com.orbit.domain.entity.HubMembershipId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HubMembershipRepository extends JpaRepository<HubMembership, HubMembershipId> {

    List<HubMembership> findByEntityId(Long entityId);

    /** Batch scope check for distribution: memberships of a candidate cohort. */
    List<HubMembership> findByEntityIdIn(Collection<Long> entityIds);
}
