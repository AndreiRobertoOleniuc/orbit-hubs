import type {
  PlannedSourceDef,
  SourceDef,
  SourceKind,
} from "@/features/ingestion/types"

export const GITHUB_SOURCE: SourceDef = {
  kind: "github",
  title: "GitHub",
  hint: "Username",
  glyph: "GH",
  placeholder: "z.B. mkeller-dev",
}

export const ORCID_SOURCE: SourceDef = {
  kind: "orcid",
  title: "ORCID",
  hint: "iD",
  glyph: "iD",
  placeholder: "0000-0002-1825-0097",
}

export const LINKEDIN_SOURCE: SourceDef = {
  kind: "linkedin",
  title: "LinkedIn",
  hint: "PDF-Upload",
  glyph: "in",
  upload: true,
}

export const WEBSITE_SOURCE: SourceDef = {
  kind: "website",
  title: "Website",
  hint: "URL",
  glyph: "🌐",
  placeholder: "www.meine-seite.ch",
}

export const COMPANY_WEBSITE_SOURCE: SourceDef = {
  kind: "website",
  title: "Firmenwebsite",
  hint: "URL",
  glyph: "🌐",
  placeholder: "www.firma.ch",
  helper: "Wir lesen eure Website aus und bauen daraus euer Profil.",
}

/** Canonical def per kind — the org flag picks the website flavour. */
export function defForKind(kind: SourceKind, org: boolean): SourceDef {
  switch (kind) {
    case "github":
      return GITHUB_SOURCE
    case "orcid":
      return ORCID_SOURCE
    case "linkedin":
      return LINKEDIN_SOURCE
    case "website":
      return org ? COMPANY_WEBSITE_SOURCE : WEBSITE_SOURCE
  }
}

/** Supply-side person: full source set. */
export const PERSON_SOURCES: SourceDef[] = [
  GITHUB_SOURCE,
  ORCID_SOURCE,
  LINKEDIN_SOURCE,
  WEBSITE_SOURCE,
]

/** Demand-side: only "wer fragt an" sources. */
export const DEMAND_SOURCES: SourceDef[] = [LINKEDIN_SOURCE, WEBSITE_SOURCE]

/** Supply-side organization: the website carries the profile. */
export const ORG_SOURCES: SourceDef[] = [COMPANY_WEBSITE_SOURCE]

/** Visible as roadmap, not active in the MVP. */
export const PERSON_PLANNED: PlannedSourceDef[] = [
  { title: "Google Scholar", hint: "Publikationsabgleich", glyph: "GS" },
]

export const ORG_PLANNED: PlannedSourceDef[] = [
  {
    title: "LinkedIn-Unternehmensseite",
    hint: "Automatischer Import",
    glyph: "in",
  },
  {
    title: "Referenzen / Case Studies",
    hint: "PDF & Case-Study-Auswertung",
    glyph: "📄",
  },
]
