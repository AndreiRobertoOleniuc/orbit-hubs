/** Which linkable source a card represents. */
export type SourceKind = "github" | "orcid" | "cv" | "linkedin" | "website"

/** A source the user added in the UI, not yet ingested. */
export interface SourceDraft {
  kind: SourceKind
  /** Display value: username, ORCID iD, URL or PDF file name. */
  value: string
  /** Only set for CV and LinkedIn PDF uploads. */
  file?: File
}

/** Step-by-step "how to obtain this" guidance for a source. */
export interface SourceGuide {
  /** One-line intro, e.g. "So findest du deine ORCID iD:". */
  intro: string
  /** Ordered how-to steps, rendered as a compact numbered list. */
  steps: string[]
  /** Optional deep link to the right page (opens in a new tab). */
  link?: { label: string; href: string }
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
  /** Step-by-step "how to obtain this" guidance, shown in the expanded card. */
  guide?: SourceGuide
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
