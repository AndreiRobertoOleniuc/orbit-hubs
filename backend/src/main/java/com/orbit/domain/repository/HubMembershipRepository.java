package com.orbit.domain.repository;

import java.util.List;

import com.orbit.domain.entity.HubMembership;
import com.orbit.domain.entity.HubMembershipId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HubMembershipRepository extends JpaRepository<HubMembership, HubMembershipId> {

    List<HubMembership> findByEntityId(Long entityId);
}
