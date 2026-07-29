import { useCallback } from "react"

import {
  buildSourceRequest,
  pdfUploadSizeError,
  summarizeIngestion,
} from "@/features/ingestion/requests"
import type { IngestOutcome, SourceKind } from "@/features/ingestion/types"
import {
  useIngestSourceMutation,
  useUploadCvPdfMutation,
  useUploadLinkedInPdfMutation,
} from "@/lib/api"
import type { ApiErrorDto, IngestionRequestDto, ProfileMode } from "@/types/api"

const FAILED: IngestOutcome = {
  status: "error",
  detail: "Quelle konnte nicht gelesen werden",
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

/**
 * The one place that talks to the ingestion endpoints. Both the onboarding
 * wizard and the profile "Quelle verlinken" cards go through this hook, so
 * request building and outcome mapping stay identical everywhere.
 */
export function useSourceIngestion() {
  const [ingestSource, ingestState] = useIngestSourceMutation()
  const [uploadLinkedInPdf, uploadState] = useUploadLinkedInPdfMutation()
  const [uploadCvPdf, cvUploadState] = useUploadCvPdfMutation()

  /** Fire a prepared POST /ingestion body (zefix, manual_text, …). */
  const ingestRequest = useCallback(
    async (request: IngestionRequestDto): Promise<IngestOutcome> => {
      try {
        return summarizeIngestion(await ingestSource(request).unwrap())
      } catch (error) {
        return failedOutcome(error)
      }
    },
    [ingestSource]
  )

  /** Ingest a user-entered source card value (or LinkedIn PDF upload). */
  const ingestSourceKind = useCallback(
    async (
      kind: SourceKind,
      value: string,
      file: File | undefined,
      options: {
        org: boolean
        onboardingDraft?: boolean
        profileMode?: ProfileMode
        displayName?: string
      }
    ): Promise<IngestOutcome> => {
      if (kind === "linkedin") {
        if (!file) return FAILED
        const sizeError = pdfUploadSizeError(file)
        if (sizeError) return { status: "error", detail: sizeError }
        try {
          return summarizeIngestion(
            await uploadLinkedInPdf({
              file,
              onboardingDraft: options.onboardingDraft,
              profileMode: options.profileMode,
            }).unwrap()
          )
        } catch (error) {
          return failedOutcome(error)
        }
      }
      if (kind === "cv") {
        if (!file) return FAILED
        const sizeError = pdfUploadSizeError(file)
        if (sizeError) return { status: "error", detail: sizeError }
        try {
          return summarizeIngestion(
            await uploadCvPdf({
              file,
              onboardingDraft: options.onboardingDraft,
              profileMode: options.profileMode,
              displayName: options.displayName,
            }).unwrap()
          )
        } catch (error) {
          return failedOutcome(error)
        }
      }
      return ingestRequest({
        ...buildSourceRequest(kind, value, options),
        onboardingDraft: options.onboardingDraft,
        profileMode: options.profileMode,
      })
    },
    [ingestRequest, uploadCvPdf, uploadLinkedInPdf]
  )

  return {
    ingestRequest,
    ingestSourceKind,
    isLoading:
      ingestState.isLoading || uploadState.isLoading || cvUploadState.isLoading,
  }
}
