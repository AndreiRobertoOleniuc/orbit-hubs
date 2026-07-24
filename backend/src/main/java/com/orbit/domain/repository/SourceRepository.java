package com.orbit.domain.repository;

import java.util.List;
import java.util.Optional;

import com.orbit.domain.entity.Source;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, Long> {

    /**
     * Find a source by its URL — the idempotency key.
     * Re-ingesting the same URL upserts the existing row.
     */
    Optional<Source> findBySourceUrl(String sourceUrl);

    /** All evidence feeds of one entity, oldest first (stable citation order). */
    List<Source> findByEntityIdOrderByCreatedAtAsc(Long entityId);

    /** Publicly selected evidence feeds of one entity, oldest first. */
    List<Source> findByEntityIdAndActiveTrueOrderByCreatedAtAsc(Long entityId);
}
