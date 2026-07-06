/** Which linkable source a card represents. */
export type SourceKind = "github" | "orcid" | "linkedin" | "website"

/** A source the user added in the UI, not yet ingested. */
export interface SourceDraft {
  kind: SourceKind
  /** Display value: username, ORCID iD, URL or PDF file name. */
  value: string
  /** Only set for LinkedIn PDF uploads. */
  file?: File
}

export interface SourceDef {
  kind: SourceKind
  title: string
  hint: string
  glyph: string
  placeholder?: string
  upload?: boolean
  /** Explanatory line shown under the collapsed card. */
  helper?: string
}

export interface PlannedSourceDef {
  title: string
  hint: string
  glyph: string
}

/** Normalized result of one ingestion call, for progress/feedback UI. */
export interface IngestOutcome {
  status: "ok" | "skipped" | "error"
  detail?: string
}
