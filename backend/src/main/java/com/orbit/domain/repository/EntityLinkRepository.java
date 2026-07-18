package com.orbit.domain.repository;

import java.util.List;
import java.util.Optional;

import com.orbit.domain.entity.EntityLink;
import com.orbit.domain.entity.EntityLinkId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityLinkRepository extends JpaRepository<EntityLink, EntityLinkId> {

    /** The org link for a person (at most one is surfaced on the profile). */
    Optional<EntityLink> findFirstByPersonEntityId(Long personEntityId);

    /** People linked to an organization (for the org profile's "team"). */
    List<EntityLink> findByOrgEntityId(Long orgEntityId);
}
