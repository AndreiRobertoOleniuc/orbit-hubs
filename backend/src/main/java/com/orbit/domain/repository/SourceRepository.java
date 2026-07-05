package com.orbit.domain.repository;

import java.util.Optional;

import com.orbit.domain.entity.Source;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, Long> {

    /**
     * Find a source by its URL — the idempotency key.
     * Re-ingesting the same URL upserts the existing row.
     */
    Optional<Source> findBySourceUrl(String sourceUrl);
}