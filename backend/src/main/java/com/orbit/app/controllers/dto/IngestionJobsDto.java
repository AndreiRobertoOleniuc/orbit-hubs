package com.orbit.app.controllers.dto;

import java.util.List;

/**
 * Progress of several ingestion jobs.
 *
 * @param jobs    the requested jobs that belong to the caller
 * @param settled every one of them has finished, and so has the enrichment
 *                they triggered — the profile summary and tags are up to date
 */
public record IngestionJobsDto(List<IngestionJobDto> jobs, boolean settled) {
}
