package com.orbit.domain.repository;

import java.util.List;

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
}
