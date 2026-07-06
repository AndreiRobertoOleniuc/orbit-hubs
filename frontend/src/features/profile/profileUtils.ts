import type { EntityDto, EntitySourceDto } from "@/types/api"

/** German display name + card glyph per sources.source_type. */
export const SOURCE_TYPE_INFO: Record<
  string,
  { label: string; glyph: string }
> = {
  github: { label: "GitHub", glyph: "GH" },
  orcid_pub: { label: "Publikation", glyph: "iD" },
  zefix: { label: "Handelsregister", glyph: "HR" },
  linkedin_pdf: { label: "LinkedIn-Profil", glyph: "in" },
  personal_site: { label: "Website", glyph: "🌐" },
  company_site: { label: "Firmenwebsite", glyph: "🌐" },
  manual_text: { label: "Eigene Beschreibung", glyph: "✎" },
}

export function sourceTypeInfo(sourceType: string) {
  return SOURCE_TYPE_INFO[sourceType] ?? { label: sourceType, glyph: "•" }
}

/** Distinct, human-readable list of what the profile was built from. */
export function builtFromLabels(entity: EntityDto): string[] {
  const labels = entity.sources.map((s) => sourceTypeInfo(s.sourceType).label)
  return [...new Set(labels)]
}

/** Zefix-backed organizations carry hr_verified in the entity metadata. */
export function isHrVerified(entity: EntityDto): boolean {
  return (
    entity.metadata?.hr_verified === true ||
    entity.sources.some((s) => s.sourceType === "zefix")
  )
}

export function zefixUid(entity: EntityDto): string | null {
  const uid = entity.metadata?.zefix_uid
  return typeof uid === "string" && uid.length > 0 ? uid : null
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w[0])
    .join("")
    .slice(0, 2)
    .toUpperCase()
}

export function sourceYear(source: EntitySourceDto): string {
  if (!source.fetchedAt) return ""
  return new Date(source.fetchedAt).getFullYear().toString()
}

/** Only http(s) source URLs are externally linkable (not zefix:…/manual:… keys). */
export function externalUrl(source: EntitySourceDto): string | null {
  return source.sourceUrl && /^https?:\/\//i.test(source.sourceUrl)
    ? source.sourceUrl
    : null
}
