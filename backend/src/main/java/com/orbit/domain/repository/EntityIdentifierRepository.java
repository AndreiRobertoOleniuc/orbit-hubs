package com.orbit.domain.repository;

import java.util.List;
import java.util.Optional;

import com.orbit.domain.entity.EntityIdentifier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntityIdentifierRepository extends JpaRepository<EntityIdentifier, Long> {

    /**
     * Find the entity_id for a specific (id_type, id_value) pair.
     * Used during entity resolution: if a GitHub login or ORCID already
     * exists in entity_identifiers, we reuse that entity.
     */
    @Query("select ei.entityId from EntityIdentifier ei where ei.idType = :idType and ei.idValue = :idValue")
    Optional<Long> findEntityIdByIdTypeAndIdValue(@Param("idType") String idType, @Param("idValue") String idValue);

    /**
     * Check if a specific (id_type, id_value) already exists.
     */
    boolean existsByIdTypeAndIdValue(String idType, String idValue);

    /**
     * Find all identifiers for a given entity.
     */
    List<EntityIdentifier> findByEntityId(Long entityId);
}