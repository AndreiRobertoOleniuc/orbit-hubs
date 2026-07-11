package com.orbit.domain.entity;

import jakarta.persistence.*;

/**
 * "Abonniertes Thema" — an entity's subscription to a tag. A subscriber is
 * notified when a matching Anfrage is distributed even if their profile wasn't
 * a direct skill match (§8.2 bySubs). Maps table {@code tag_subscriptions}.
 * Follows the {@link EntityTag} composite-key pattern.
 */
@Entity
@Table(name = "tag_subscriptions")
@IdClass(TagSubscriptionId.class)
public class TagSubscription {

    @Id
    @Column(name = "entity_id")
    private Long entityId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    // created_at is DEFAULT now() in the DB — see EntityTag for why @Generated is
    // avoided with @IdClass in Hibernate 7.x.
    @Column(name = "created_at", updatable = false, insertable = false)
    private java.time.Instant createdAt;

    protected TagSubscription() {
    }

    public TagSubscription(Long entityId, Long tagId) {
        this.entityId = entityId;
        this.tagId = tagId;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Long getTagId() {
        return tagId;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }
}
