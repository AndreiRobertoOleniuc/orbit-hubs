package com.orbit.domain.entity;

import jakarta.persistence.*;

/**
 * An entity's Kompetenz (skill) — the many-to-many join between entities and
 * the controlled tag vocabulary, with an {@code origin} flag.
 * <p>
 * {@code origin='derived'} — AI-derived from source content during ingestion.
 * These are replaced on each re-harvest but can be promoted to manual.
 * <p>
 * {@code origin='manual'} — user-added or user-confirmed. Sticky across
 * re-harvests: a later ingestion never removes a manual tag.
 * <p>
 * Maps table {@code entity_tags}. See reference doc §9 / §4.2 step 7.
 */
@Entity
@Table(name = "entity_tags")
@IdClass(EntityTagId.class)
public class EntityTag {

    @Id
    @Column(name = "entity_id")
    private Long entityId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    @Column(name = "origin", nullable = false)
    private String origin = "derived";

    // created_at is DEFAULT now() in the DB — no @Generated needed.
    // @Generated(event = EventType.INSERT) caused a PSQLException with @IdClass
    // in Hibernate 7.x (post-INSERT SELECT for generated values conflicts with
    // composite keys). The field stays null in Java; the DB sets it automatically.
    @Column(name = "created_at", updatable = false, insertable = false)
    private java.time.Instant createdAt;

    protected EntityTag() {
    }

    public EntityTag(Long entityId, Long tagId, String origin) {
        this.entityId = entityId;
        this.tagId = tagId;
        this.origin = origin;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Long getTagId() {
        return tagId;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }
}