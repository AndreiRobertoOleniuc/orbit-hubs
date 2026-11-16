package com.orbit.app.search.dto;

/**
 * An entity's position when the semantic leg is ranked per entity instead of
 * per chunk.
 *
 * @param entityId       the entity
 * @param displayName    its display name
 * @param bestChunkRank  rank of its best chunk among all scoped chunks (1 = closest)
 * @param bestSimilarity similarity of that chunk to the query
 */
public record SemanticEntityRank(
        Long entityId,
        String displayName,
        int bestChunkRank,
        double bestSimilarity) {
}
