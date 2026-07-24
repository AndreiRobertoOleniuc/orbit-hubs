import { useCallback } from "react"

import {
  buildSourceRequest,
  summarizeIngestion,
} from "@/features/ingestion/requests"
import type { IngestOutcome, SourceKind } from "@/features/ingestion/types"
import {
  useIngestSourceMutation,
  useUploadLinkedInPdfMutation,
} from "@/lib/api"
import type { IngestionRequestDto } from "@/types/api"
import type { ProfileMode } from "@/types/api"

const FAILED: IngestOutcome = {
  status: "error",
  detail: "Quelle konnte nicht gelesen werden",
}

/**
 * The one place that talks to the ingestion endpoints. Both the onboarding
 * wizard and the profile "Quelle verlinken" cards go through this hook, so
 * request building and outcome mapping stay identical everywhere.
 */
export function useSourceIngestion() {
  const [ingestSource, ingestState] = useIngestSourceMutation()
  const [uploadLinkedInPdf, uploadState] = useUploadLinkedInPdfMutation()

  /** Fire a prepared POST /ingestion body (zefix, manual_text, …). */
  const ingestRequest = useCallback(
    async (request: IngestionRequestDto): Promise<IngestOutcome> => {
      try {
        return summarizeIngestion(await ingestSource(request).unwrap())
      } catch {
        return FAILED
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
      }
    ): Promise<IngestOutcome> => {
      if (kind === "linkedin") {
        if (!file) return FAILED
        try {
          return summarizeIngestion(
            await uploadLinkedInPdf({
              file,
              onboardingDraft: options.onboardingDraft,
              profileMode: options.profileMode,
            }).unwrap()
          )
        } catch {
          return FAILED
        }
      }
      return ingestRequest({
        ...buildSourceRequest(kind, value, options),
        onboardingDraft: options.onboardingDraft,
        profileMode: options.profileMode,
      })
    },
    [ingestRequest, uploadLinkedInPdf]
  )

  return {
    ingestRequest,
    ingestSourceKind,
    isLoading: ingestState.isLoading || uploadState.isLoading,
  }
}
