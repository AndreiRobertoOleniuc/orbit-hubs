package com.orbit.app.controllers.dto;

/**
 * Progress of the re-evaluation (resync) jobs.
 *
 * @param queued    jobs this call added (0 for a status read)
 * @param waiting   resync jobs not started yet
 * @param running   resync jobs in progress
 * @param succeeded resync jobs finished within the job retention period
 * @param failed    resync jobs that failed for good within the retention period
 */
public record ReindexDto(int queued, long waiting, long running, long succeeded, long failed) {
}
