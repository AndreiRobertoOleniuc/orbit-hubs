package com.orbit.domain.entity;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * One notified profile per request — who was told about a request, why
 * ({@code profile_match} via tag overlap or {@code subscription} via an
 * abonniertes Thema), and at which widening level. Maps table
 * {@code request_distributions}. See reference doc §8.2.
 * <p>
 * {@code UNIQUE(request_id, entity_id)} — escalation waves only ever add
 * newly in-scope entities; an already-notified entity is never re-inserted.
 */
@Entity
@Table(name = "request_distributions")
public class RequestDistribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** 'profile_match' (tag overlap) | 'subscription' (abonniertes Thema). */
    @Column(name = "reason", nullable = false)
    private String reason;

    /** Tag labels behind the reason — the "Verteilt, weil …" explanation. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "matched_tags", columnDefinition = "text[]")
    private List<String> matchedTags;

    /** Effective scope kind at notify time: 'hub' | 'region' | 'all'. */
    @Column(name = "widening_level", nullable = false)
    private String wideningLevel;

    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt;

    @Column(name = "created_at", updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    protected RequestDistribution() {
    }

    public RequestDistribution(Long requestId, Long entityId, String reason,
                               List<String> matchedTags, String wideningLevel, Instant notifiedAt) {
        this.requestId = requestId;
        this.entityId = entityId;
        this.reason = reason;
        this.matchedTags = matchedTags;
        this.wideningLevel = wideningLevel;
        this.notifiedAt = notifiedAt;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public Long getEntityId() { return entityId; }
    public String getReason() { return reason; }
    public List<String> getMatchedTags() { return matchedTags; }
    public String getWideningLevel() { return wideningLevel; }
    public Instant getNotifiedAt() { return notifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
