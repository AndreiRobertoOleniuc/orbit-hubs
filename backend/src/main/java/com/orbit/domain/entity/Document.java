package com.orbit.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evidence you retrieve and cite for an {@link Entity}. Maps table {@code documents}.
 *
 * <p>Its own aggregate root (high, variable cardinality per entity), referenced back to the
 * entity by a managed {@code @ManyToOne}. Pre-V4 schema state: text, embedding, and the
 * generated {@code tsv} live directly on this row. Once {@code V4__document_chunks.sql} lands,
 * the per-chunk {@code text}/{@code embedding} move to a {@code DocumentChunk} child and this
 * row keeps citation metadata only — until then there is no chunk entity.
 */
@jakarta.persistence.Entity
@Table(name = "documents")
public class Document {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_id", nullable = false)
    private Entity entity;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "source_label")
    private String sourceLabel;

    @Column(nullable = false)
    private String text;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    private float[] embedding;

    @Column(name = "text_hash")
    private String textHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String meta;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    protected Document() {
    }

    public Document(String id, Entity entity) {
        this.id = id;
        this.entity = entity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public String getTextHash() {
        return textHash;
    }

    public void setTextHash(String textHash) {
        this.textHash = textHash;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
