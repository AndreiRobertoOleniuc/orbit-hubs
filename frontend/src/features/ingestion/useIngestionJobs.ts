import { useCallback } from "react"

import { apiSlice, useLazyGetIngestionJobsQuery } from "@/lib/api"
import { useAppDispatch } from "@/lib/hooks"
import type { IngestionJobDto } from "@/types/api"

/** How often a queued ingestion is polled. */
export const POLL_INTERVAL_MS = 1500
/** Give up waiting after this long (three retries with backoff take ~11 min). */
export const POLL_TIMEOUT_MS = 15 * 60 * 1000

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * Waits for queued ingestion jobs. Ingestion runs in the background on the
 * server: submitting a source only queues it, and this polls until the jobs —
 * and the profile enrichment they trigger — have finished. Then the profile
 * caches are refreshed, because only now did the profile actually change.
 */
export function useIngestionJobs() {
  const [fetchJobs] = useLazyGetIngestionJobsQuery()
  const dispatch = useAppDispatch()

  const awaitJobs = useCallback(
    async (
      ids: number[],
      onProgress?: (jobs: IngestionJobDto[]) => void
    ): Promise<IngestionJobDto[]> => {
      if (ids.length === 0) return []
      const deadline = Date.now() + POLL_TIMEOUT_MS
      let latest: IngestionJobDto[] = []
      let failures = 0
      while (Date.now() < deadline) {
        try {
          const { jobs, settled } = await fetchJobs(ids).unwrap()
          failures = 0
          latest = jobs
          onProgress?.(jobs)
          if (settled) break
        } catch {
          // A dropped poll is not a failed job; give up only if polling keeps failing.
          if (++failures >= 5) break
        }
        await sleep(POLL_INTERVAL_MS)
      }
      const entityIds = [
        ...new Set(
          latest.map((job) => job.entityId).filter((id) => id != null)
        ),
      ]
      dispatch(
        apiSlice.util.invalidateTags([
          "MyEntities",
          ...entityIds.map((id) => ({ type: "Entity" as const, id })),
        ])
      )
      return latest
    },
    [dispatch, fetchJobs]
  )

  return { awaitJobs }
}
