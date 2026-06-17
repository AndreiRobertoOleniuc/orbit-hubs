package com.orbit.domain.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite-PK class for {@link DocumentChunk}. Fields mirror the {@code @Id} members:
 * {@code document} (typed as the referenced {@link Document}'s id) and {@code chunkIndex}.
 */
public class DocumentChunkId implements Serializable {

    private String document;
    private int chunkIndex;

    public DocumentChunkId() {
    }

    public DocumentChunkId(String document, int chunkIndex) {
        this.document = document;
        this.chunkIndex = chunkIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DocumentChunkId that)) {
            return false;
        }
        return chunkIndex == that.chunkIndex && Objects.equals(document, that.document);
    }

    @Override
    public int hashCode() {
        return Objects.hash(document, chunkIndex);
    }
}
