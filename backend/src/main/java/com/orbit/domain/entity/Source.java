package com.orbit.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * One evidence feed per entity; also the citation layer. Maps table {@code sources}.
 * <p>
 * Each source is ingested independently and idempotently. The {@code source_url} is
 * the idempotency key — re-ingesting the same URL upserts the existing row and
 * rebuilds its chunks. {@code content_hash} short-circuits before any embedding
 * when nothing changed.
 */
@jakarta.persistence.Entity
@Table(name = "sources",
       uniqueConstraints = @UniqueConstraint(columnNames = {"source_url"}))
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "label")
    private String label;

    @Column(name = "provenance", nullable = false)
    private String provenance = "harvested";

    @Column(name = "consent_basis")
    private String consentBasis;

    @Column(name = "raw_content", length = Integer.MAX_VALUE)
    private String rawContent;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "fetch_status")
    private String fetchStatus = "ok";

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected Source() {
    }

    public Source(Long entityId, String sourceType, String sourceUrl, String label,
                  String provenance, String consentBasis, String rawContent,
                  String contentHash, String fetchStatus, Instant fetchedAt) {
        this.entityId = entityId;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.label = label;
        this.provenance = provenance;
        this.consentBasis = consentBasis;
        this.rawContent = rawContent;
        this.contentHash = contentHash;
        this.fetchStatus = fetchStatus;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getLabel() {
        return label;
    }

    public String getProvenance() {
        return provenance;
    }

    public String getConsentBasis() {
        return consentBasis;
    }

    public String getRawContent() {
        return rawContent;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getFetchStatus() {
        return fetchStatus;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}