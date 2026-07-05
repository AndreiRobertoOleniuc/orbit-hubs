package com.orbit.domain.entity;

import jakarta.persistence.*;

/**
 * External identifier for entity resolution / dedup. Maps table {@code entity_identifiers}.
 * <p>
 * One ORCID = one entity. One GitHub login = one entity. When multiple identifiers
 * resolve to the same entity, we have convergence. When they resolve to different
 * entities, that's a merge case (logged in Stage 1, properly merged in Stage 9).
 */
@jakarta.persistence.Entity
@Table(name = "entity_identifiers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"id_type", "id_value"}))
public class EntityIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "id_type", nullable = false)
    private String idType;

    @Column(name = "id_value", nullable = false)
    private String idValue;

    protected EntityIdentifier() {
    }

    public EntityIdentifier(Long entityId, String idType, String idValue) {
        this.entityId = entityId;
        this.idType = idType;
        this.idValue = idValue;
    }

    public Long getId() {
        return id;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getIdType() {
        return idType;
    }

    public String getIdValue() {
        return idValue;
    }
}