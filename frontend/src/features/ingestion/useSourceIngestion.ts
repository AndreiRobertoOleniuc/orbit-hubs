import { useCallback, useState } from "react"

import {
  buildSourceRequest,
  jobOutcome,
  pdfUploadSizeError,
} from "@/features/ingestion/requests"
import type { IngestOutcome, SourceKind } from "@/features/ingestion/types"
import { useIngestionJobs } from "@/features/ingestion/useIngestionJobs"
import {
  useIngestSourceMutation,
  useUploadCvPdfMutation,
  useUploadLinkedInPdfMutation,
} from "@/lib/api"
import type {
  ApiErrorDto,
  IngestionJobDto,
  IngestionRequestDto,
  ProfileMode,
} from "@/types/api"

const FAILED: IngestOutcome = {
  status: "error",
  detail: "Quelle konnte nicht gelesen werden",
}

const TIMED_OUT: IngestOutcome = {
  status: "error",
  detail: "Dauert länger als erwartet — schau später im Profil nach",
}

function failedOutcome(error: unknown): IngestOutcome {
  if (typeof error !== "object" || error === null || !("data" in error)) {
    return FAILED
  }
  const data = error.data as Partial<ApiErrorDto> | undefined
  return typeof data?.message === "string"
    ? { status: "error", detail: data.message }
    : FAILED
}

/** A submission: the queued job, or the outcome when it was rejected outright. */
export type Submission = { jobId: number } | { outcome: IngestOutcome }

export interface SourceKindOptions {
  org: boolean
  onboardingDraft?: boolean
  profileMode?: ProfileMode
  displayName?: string
}

/**
 * The one place that talks to the ingestion endpoints. Both the onboarding
 * wizard and the profile "Quelle verlinken" cards go through this hook, so
 * request building and outcome mapping stay identical everywhere.
 *
 * Ingestion is a background job on the server. `submit*` queues a source and
 * returns at once; `awaitOutcomes` waits for queued jobs. Submit every source
 * first and wait once: the server then enriches the profile once for all of
 * them. `ingestRequest` / `ingestSourceKind` do both for a single source.
 */
export function useSourceIngestion() {
  const [ingestSource, ingestState] = useIngestSourceMutation()
  const [uploadLinkedInPdf, uploadState] = useUploadLinkedInPdfMutation()
  const [uploadCvPdf, cvUploadState] = useUploadCvPdfMutation()
  const { awaitJobs } = useIngestionJobs()
  const [pending, setPending] = useState(0)

  const submitted = async (
    request: Promise<IngestionJobDto>
  ): Promise<Submission> => {
    try {
      return { jobId: (await request).id }
    } catch (error) {
      return { outcome: failedOutcome(error) }
    }
  }

  /** Queue a prepared POST /ingestion body (zefix, manual_text, …). */
  const submitRequest = useCallback(
    (request: IngestionRequestDto): Promise<Submission> =>
      submitted(ingestSource(request).unwrap()),
    [ingestSource]
  )

  /** Queue a user-entered source card value (or a PDF upload). */
  const submitSourceKind = useCallback(
    async (
      kind: SourceKind,
      value: string,
      file: File | undefined,
      options: SourceKindOptions
    ): Promise<Submission> => {
      if (kind === "linkedin" || kind === "cv") {
        if (!file) return { outcome: FAILED }
        const sizeError = pdfUploadSizeError(file)
        if (sizeError)
          return { outcome: { status: "error", detail: sizeError } }
        return submitted(
          kind === "linkedin"
            ? uploadLinkedInPdf({
                file,
                onboardingDraft: options.onboardingDraft,
                profileMode: options.profileMode,
              }).unwrap()
            : uploadCvPdf({
                file,
                onboardingDraft: options.onboardingDraft,
                profileMode: options.profileMode,
                displayName: options.displayName,
              }).unwrap()
        )
      }
      return submitRequest({
        ...buildSourceRequest(kind, value, options),
        onboardingDraft: options.onboardingDraft,
        profileMode: options.profileMode,
      })
    },
    [submitRequest, uploadCvPdf, uploadLinkedInPdf]
  )

  /**
   * Wait until the jobs and the enrichment they trigger have finished.
   * `onProgress` sees every poll. Jobs that never finished come back as an
   * error outcome.
   */
  const awaitOutcomes = useCallback(
    async (
      jobIds: number[],
      onProgress?: (jobs: IngestionJobDto[]) => void
    ): Promise<Map<number, IngestOutcome>> => {
      setPending((n) => n + 1)
      try {
        const jobs = await awaitJobs(jobIds, onProgress)
        const outcomes = new Map<number, IngestOutcome>()
        for (const id of jobIds) {
          const job = jobs.find((j) => j.id === id)
          outcomes.set(id, (job && jobOutcome(job)) ?? TIMED_OUT)
        }
        return outcomes
      } finally {
        setPending((n) => n - 1)
      }
    },
    [awaitJobs]
  )

  const settle = useCallback(
    async (submission: Submission): Promise<IngestOutcome> => {
      if ("outcome" in submission) return submission.outcome
      const outcomes = await awaitOutcomes([submission.jobId])
      return outcomes.get(submission.jobId) ?? TIMED_OUT
    },
    [awaitOutcomes]
  )

  /** Queue one prepared request and wait for it. */
  const ingestRequest = useCallback(
    async (request: IngestionRequestDto) =>
      settle(await submitRequest(request)),
    [settle, submitRequest]
  )

  /** Queue one source card value and wait for it. */
  const ingestSourceKind = useCallback(
    async (
      kind: SourceKind,
      value: string,
      file: File | undefined,
      options: SourceKindOptions
    ) => settle(await submitSourceKind(kind, value, file, options)),
    [settle, submitSourceKind]
  )

  return {
    submitRequest,
    submitSourceKind,
    awaitOutcomes,
    ingestRequest,
    ingestSourceKind,
    isLoading:
      pending > 0 ||
      ingestState.isLoading ||
      uploadState.isLoading ||
      cvUploadState.isLoading,
  }
}
