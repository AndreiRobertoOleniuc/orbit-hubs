package com.orbit.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Who was notified about a request and why. Maps table {@code request_distributions}
 * ({@code bigserial} PK → {@link Long}). Its own root (independent lifecycle, drives the
 * notification fan-out), referenced to {@link Request} and {@link Entity} by managed refs.
 */
@jakarta.persistence.Entity
@Table(name = "request_distributions")
public class Distribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_id", nullable = false)
    private Entity entity;

    private String reason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "matched_tags", columnDefinition = "text[]")
    private String[] matchedTags;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected Distribution() {
    }

    public Distribution(Request request, Entity entity, String reason, String[] matchedTags) {
        this.request = request;
        this.entity = entity;
        this.reason = reason;
        this.matchedTags = matchedTags;
    }

    public Long getId() {
        return id;
    }

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String[] getMatchedTags() {
        return matchedTags;
    }

    public void setMatchedTags(String[] matchedTags) {
        this.matchedTags = matchedTags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
