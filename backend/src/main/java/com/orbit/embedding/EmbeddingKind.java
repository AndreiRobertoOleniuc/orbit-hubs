package com.orbit.embedding;

/**
 * Kind of text to embed. Models are often prompted differently for short
 * queries versus long passages, so callers must declare their intent.
 */
public enum EmbeddingKind {
    QUERY,
    PASSAGE
}
