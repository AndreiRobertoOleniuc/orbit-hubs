package com.orbit.app.ingestion.dto;

/** Outcome of ingesting one source. */
public enum IngestionStatus {
    INGESTED,
    SKIPPED,
    ERROR
}
