import type { IngestOutcome, SourceKind } from "@/features/ingestion/types"
import type { IngestionRequestDto, IngestionResponseDto } from "@/types/api"

/** Which sources.source_type values satisfy a card's source kind. */
export const KIND_TO_SOURCE_TYPES: Record<SourceKind, string[]> = {
  github: ["github"],
  orcid: ["orcid_pub"],
  linkedin: ["linkedin_pdf"],
  website: ["personal_site", "company_site"],
}

export function ensureUrl(value: string): string {
  return /^https?:\/\//i.test(value) ? value : `https://${value}`
}

/**
 * Build the POST /ingestion body for a text-based source kind.
 * LinkedIn is file-based and goes through the multipart endpoint instead.
 */
export function buildSourceRequest(
  kind: Exclude<SourceKind, "linkedin">,
  value: string,
  options: { org: boolean }
): IngestionRequestDto {
  switch (kind) {
    case "github":
      return { sourceType: "github", context: { github_login: value } }
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

export function buildManualTextRequest(text: string): IngestionRequestDto {
  return { sourceType: "manual_text", context: { text: text.trim() } }
}

/** Collapse the per-call ingestion response into one UI-facing outcome. */
export function summarizeIngestion(
  result: IngestionResponseDto
): IngestOutcome {
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
    detail: result.error ?? "Quelle konnte nicht gelesen werden",
  }
}
