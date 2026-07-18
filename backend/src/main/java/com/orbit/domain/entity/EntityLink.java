package com.orbit.domain.entity;

import jakarta.persistence.*;

/**
 * A Person↔Organisation link — a "who works where" signal (§10).
 * <p>
 * {@code source='derived'} — inferred post-ingestion by matching a person's
 * {@code org_name} to an existing organization entity. {@code source='confirmed'}
 * — user-set. A link only exists when a matching org entity exists; it is never
 * a placeholder, and the person's plain {@code org_name} stays the fallback.
 * <p>
 * Maps table {@code entity_links}.
 */
@Entity
@Table(name = "entity_links")
@IdClass(EntityLinkId.class)
public class EntityLink {

    @Id
    @Column(name = "person_entity_id")
    private Long personEntityId;

    @Id
    @Column(name = "org_entity_id")
    private Long orgEntityId;

    @Column(name = "role")
    private String role;

    @Column(name = "source", nullable = false)
    private String source = "derived";

    // created_at is DEFAULT now() in the DB — @IdClass + @Generated conflict in
    // Hibernate 7.x, so the field is read-only and the DB sets it. See EntityTag.
    @Column(name = "created_at", updatable = false, insertable = false)
    private java.time.Instant createdAt;

    protected EntityLink() {
    }

    public EntityLink(Long personEntityId, Long orgEntityId, String role, String source) {
        this.personEntityId = personEntityId;
        this.orgEntityId = orgEntityId;
        this.role = role;
        this.source = source;
    }

    public Long getPersonEntityId() {
        return personEntityId;
    }

    public Long getOrgEntityId() {
        return orgEntityId;
    }

    public String getRole() {
        return role;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }
}
