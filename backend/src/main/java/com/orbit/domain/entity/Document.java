package com.orbit.domain.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evidence you retrieve and cite for an {@link Entity}. Maps table {@code documents}.
 *
 * <p>Its own aggregate root (high, variable cardinality per entity), referenced back to the
 * entity by a managed {@code @ManyToOne}. The document row holds citation/source metadata only;
 * the retrievable text and its {@code vector(1024)} embedding live on the {@link DocumentChunk}
 * children (a document is the citable unit, a chunk is the retrieval unit), so re-embedding one
 * document never rewrites another.
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

    @Column(name = "text_hash")
    private String textHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String meta;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chunkIndex ASC")
    private List<DocumentChunk> chunks = new ArrayList<>();

    protected Document() {
    }

    public Document(String id, Entity entity) {
        this.id = id;
        this.entity = entity;
    }

    /** Append a chunk (text + embedding) to this document, wiring the back-reference. */
    public DocumentChunk addChunk(int chunkIndex, String text, float[] embedding) {
        DocumentChunk chunk = new DocumentChunk(this, chunkIndex, text, embedding);
        chunks.add(chunk);
        return chunk;
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

    public List<DocumentChunk> getChunks() {
        return chunks;
    }
}
