package com.orbit.domain.entity;

import jakarta.persistence.*;

/**
 * entity↔hub join backing the Hub → Region → CH hard scope filter. Maps table
 * {@code hub_memberships}. See reference doc §5.
 * <p>
 * {@code source='derived'} — proposed by ingestion; {@code source='confirmed'}
 * — the user confirmed the hub in their profile. Follows the {@link EntityTag}
 * composite-key pattern.
 */
@Entity
@Table(name = "hub_memberships")
@IdClass(HubMembershipId.class)
public class HubMembership {

    @Id
    @Column(name = "entity_id")
    private Long entityId;

    @Id
    @Column(name = "hub_id")
    private Long hubId;

    @Column(name = "affiliation")
    private String affiliation;

    @Column(name = "source", nullable = false)
    private String source = "derived";

    // joined_at is DEFAULT now() in the DB — see EntityTag for why @Generated is
    // avoided with @IdClass in Hibernate 7.x.
    @Column(name = "joined_at", updatable = false, insertable = false)
    private java.time.Instant joinedAt;

    protected HubMembership() {
    }

    public HubMembership(Long entityId, Long hubId, String affiliation, String source) {
        this.entityId = entityId;
        this.hubId = hubId;
        this.affiliation = affiliation;
        this.source = source;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Long getHubId() {
        return hubId;
    }

    public String getAffiliation() {
        return affiliation;
    }

    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public java.time.Instant getJoinedAt() {
        return joinedAt;
    }
}
