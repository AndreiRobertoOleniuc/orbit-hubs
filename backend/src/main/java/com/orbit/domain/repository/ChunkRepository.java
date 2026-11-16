package com.orbit.domain.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orbit.domain.entity.Chunk;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    /**
     * Delete all chunks for a source before re-chunking.
     * Called during the re-chunk step of the ingestion pipeline.
     */
    @Modifying
    @Query("delete from Chunk c where c.sourceId = :sourceId")
    void deleteBySourceId(@Param("sourceId") Long sourceId);


    /**
     * Lightweight query: load only chunk content + source_id for an entity,
     * avoiding the expensive vector(1024) embedding column. Used by the
     * enrichment service to gather source text for the LLM without loading
     * embeddings into memory.
     */
    @Query("select c.content, c.sourceId from Chunk c where c.entityId = :entityId order by c.sourceId asc, c.chunkIndex asc")
    List<Object[]> findContentByEntityId(@Param("entityId") Long entityId);

    /** Chunk text for the explicitly active source ids used during profile enrichment. */
    @Query("select c.content, c.sourceId from Chunk c where c.sourceId in :sourceIds order by c.sourceId asc, c.chunkIndex asc")
    List<Object[]> findContentBySourceIds(@Param("sourceIds") List<Long> sourceIds);

    /**
     * Next page of chunks whose embedding is missing, comes from another model
     * than {@code model}, or is older than {@code before} — as (id, content), in
     * id order after {@code afterId}. Keyset-paged so a chunk that keeps failing
     * is attempted once per run instead of blocking the rest.
     */
    @Query("""
            select c.id, c.content from Chunk c
            where c.id > :afterId
              and (c.embedding is null or c.embeddedAt is null or c.embeddedAt < :before
                   or c.embeddingModel is null or c.embeddingModel <> :model)
            order by c.id asc
            """)
    List<Object[]> findStaleEmbeddings(@Param("before") Instant before,
                                       @Param("model") String model,
                                       @Param("afterId") Long afterId,
                                       Pageable page);

    /**
     * Replace one chunk's embedding in place. {@code embedding} is a pgvector
     * literal ({@code [0.1,0.2,…]}). A native update rather than load + save:
     * {@link Chunk#getEmbedding()} is mapped as a plain {@code float[]}, which
     * Hibernate can write into the vector column but cannot read back.
     *
     * @return the number of rows updated (0 if the chunk no longer exists)
     */
    @Modifying
    @Query(value = """
            UPDATE chunks
            SET embedding = CAST(:embedding AS vector), embedding_model = :model, embedded_at = :embeddedAt
            WHERE id = :id
            """, nativeQuery = true)
    int updateEmbedding(@Param("id") Long id,
                        @Param("embedding") String embedding,
                        @Param("model") String model,
                        @Param("embeddedAt") Instant embeddedAt);
}
