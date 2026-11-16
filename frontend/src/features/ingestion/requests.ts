import type {
  IngestOutcome,
  SourceDraft,
  SourceKind,
} from "@/features/ingestion/types"
import { externalHref } from "@/lib/utils"
import type {
  IngestionJobDto,
  IngestionRequestDto,
  IngestionSummaryDto,
} from "@/types/api"

export const MAX_PDF_UPLOAD_BYTES = 20 * 1024 * 1024
export const MAX_PDF_UPLOAD_LABEL = "20 MB"

export function pdfUploadSizeError(file: File): string | null {
  return file.size > MAX_PDF_UPLOAD_BYTES
    ? `Die PDF ist zu gross. Bitte wähle eine Datei mit maximal ${MAX_PDF_UPLOAD_LABEL}.`
    : null
}

/** Which sources.source_type values satisfy a card's source kind. */
export const KIND_TO_SOURCE_TYPES: Record<SourceKind, string[]> = {
  github: ["github"],
  orcid: ["orcid_pub"],
  cv: ["cv_pdf"],
  linkedin: ["linkedin_pdf"],
  website: ["personal_site", "company_site"],
}

export function ensureUrl(value: string): string {
  return /^https?:\/\//i.test(value) ? value : `https://${value}`
}

/**
 * GitHub ingestion works from an account login. Accept either the login itself
 * or a copied github.com profile/repository URL and keep only the owner segment.
 */
export function normalizeGithubLogin(value: string): string {
  const trimmed = value.trim()
  if (!trimmed) return ""

  const withScheme = /^https?:\/\//i.test(trimmed)
    ? trimmed
    : `https://${trimmed}`

  try {
    const url = new URL(withScheme)
    const host = url.hostname.toLowerCase().replace(/^www\./, "")
    if (host === "github.com") {
      return decodeURIComponent(
        url.pathname.split("/").filter(Boolean)[0] ?? ""
      )
    }
  } catch {
    // Not a URL; treat it as a username below.
  }

  return trimmed.replace(/^@/, "")
}

/**
 * Openable URL for a source the user linked but hasn't ingested yet — only the
 * typed draft exists at that point. A PDF upload is a file, never a URL.
 */
export function draftHref(draft: SourceDraft): string | null {
  if (draft.kind !== "website" || draft.file || !draft.value.trim()) return null
  return externalHref(ensureUrl(draft.value.trim()))
}

/**
 * Build the POST /ingestion body for a text-based source kind.
 * CV and LinkedIn are file-based and use multipart endpoints instead.
 */
export function buildSourceRequest(
  kind: Exclude<SourceKind, "cv" | "linkedin">,
  value: string,
  options: { org: boolean }
): IngestionRequestDto {
  switch (kind) {
    case "github":
      return {
        sourceType: "github",
        context: { github_login: normalizeGithubLogin(value) },
      }
    case "orcid":
      return { sourceType: "orcid_pub", context: { orcid: value } }
    case "website":
      return {
        sourceType: options.org ? "company_site" : "personal_site",
        context: { url: ensureUrl(value) },
      }
  }
}

/** Zefix expects the compact CHE\d{9} form, not the display form CHE-NNN.NNN.NNN. */
export function buildZefixRequest(firm: {
  name: string
  uid?: string
}): IngestionRequestDto {
  return {
    sourceType: "zefix",
    context: firm.uid
      ? { zefix_uid: firm.uid.replace(/[^A-Za-z0-9]/g, "") }
      : { company_name: firm.name },
  }
}

export function buildManualTextRequest(
  text: string,
  displayName?: string
): IngestionRequestDto {
  return {
    sourceType: "manual_text",
    context: {
      text: text.trim(),
      ...(displayName?.trim() ? { name: displayName.trim() } : {}),
    },
  }
}

/** Collapse one source's ingestion summary into one UI-facing outcome. */
export function summarizeIngestion(result: IngestionSummaryDto): IngestOutcome {
  if (result.ingested > 0) {
    return {
      status: "ok",
      detail: `${result.totalChunks} Abschnitte ausgewertet`,
    }
  }
  if (result.skipped > 0) {
    return { status: "skipped", detail: "bereits aktuell" }
  }
  return {
    status: "error",
    detail: "Quelle konnte nicht gelesen werden",
  }
}

/** The outcome of a finished job; null while it is still queued or running. */
export function jobOutcome(job: IngestionJobDto): IngestOutcome | null {
  if (job.status === "failed") {
    return { status: "error", detail: "Quelle konnte nicht gelesen werden" }
  }
  if (job.status !== "succeeded") return null
  return job.result
    ? summarizeIngestion(job.result)
    : { status: "ok", detail: "Profil aktualisiert" }
}
