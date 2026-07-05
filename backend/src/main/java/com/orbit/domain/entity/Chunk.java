package com.orbit.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * The searchable unit — text + embedding + FTS. Maps table {@code chunks}.
 * <p>
 * Chunks are the ceiling on match quality: "a citation can never reference evidence
 * that wasn't retrieved." The {@code entity_id} is denormalized from {@code sources}
 * so scoped search can filter without a join.
 */
@jakarta.persistence.Entity
@Table(name = "chunks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "chunk_index"}))
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false, length = Integer.MAX_VALUE)
    private String content;

    @Column(name = "section")
    private String section;

    @Column(name = "token_count")
    private Integer tokenCount;

    // embedding is vector(1024) in Postgres, mapped via Hibernate VectorType
    @Column(name = "embedding", columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(name = "embedding_model")
    private String embeddingModel = "multilingual-e5-large";

    @Column(name = "embedded_at")
    private Instant embeddedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected Chunk() {
    }

    public Chunk(Long sourceId, Long entityId, int chunkIndex, String content,
                 String section, Integer tokenCount, float[] embedding,
                 String embeddingModel, Instant embeddedAt) {
        this.sourceId = sourceId;
        this.entityId = entityId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.section = section;
        this.tokenCount = tokenCount;
        this.embedding = embedding;
        this.embeddingModel = embeddingModel;
        this.embeddedAt = embeddedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Long getEntityId() {
        return entityId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public String getSection() {
        return section;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public Instant getEmbeddedAt() {
        return embeddedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}