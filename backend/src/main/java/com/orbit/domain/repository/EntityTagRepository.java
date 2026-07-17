package com.orbit.domain.repository;

import java.util.Collection;
import java.util.List;

import com.orbit.domain.entity.EntityTag;
import com.orbit.domain.entity.EntityTagId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityTagRepository extends JpaRepository<EntityTag, EntityTagId> {

    /** All tags of one entity (both derived and manual). */
    List<EntityTag> findByEntityId(Long entityId);

    /** Only the derived tags of one entity — these get replaced on re-harvest. */
    List<EntityTag> findByEntityIdAndOrigin(Long entityId, String origin);

    /** Reverse lookup for distribution (§8.2): all entities carrying any of these tags. */
    List<EntityTag> findByTagIdIn(Collection<Long> tagIds);
}