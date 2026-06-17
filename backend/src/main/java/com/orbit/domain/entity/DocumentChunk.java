package com.orbit.domain.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An embedded slice of a {@link Document} — the unit hybrid retrieval actually scores. Maps table
 * {@code document_chunks} with composite PK {@code (document_id, chunk_index)} via {@link IdClass}.
 * A child of its {@code Document}; the {@code vector(1024)} embedding and generated {@code tsv}
 * live here, per chunk.
 */
@jakarta.persistence.Entity
@Table(name = "document_chunks")
@IdClass(DocumentChunkId.class)
public class DocumentChunk {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Id
    @Column(name = "chunk_index")
    private int chunkIndex;

    @Column(nullable = false)
    private String text;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    private float[] embedding;

    // tsv is GENERATED in the DB; left unmapped (used only by native FTS queries).

    protected DocumentChunk() {
    }

    public DocumentChunk(Document document, int chunkIndex, String text, float[] embedding) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.embedding = embedding;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
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
}
