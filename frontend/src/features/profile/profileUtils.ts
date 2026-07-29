import { externalHref } from "@/lib/utils"
import type { EntityDto, EntitySourceDto } from "@/types/api"

/** German display name + card glyph per sources.source_type. */
const SOURCE_TYPE_INFO: Record<string, { label: string; glyph: string }> = {
  github: { label: "GitHub", glyph: "GH" },
  orcid_pub: { label: "Publikation", glyph: "iD" },
  zefix: { label: "Handelsregister", glyph: "HR" },
  cv_pdf: { label: "Lebenslauf", glyph: "CV" },
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

/** Website sources persist `{sourceType}:{domain}` instead of an absolute URL. */
const WEBSITE_KEY_PREFIXES = ["personal_site:", "company_site:"]

/**
 * The openable address behind a source, or null.
 *
 * `sources.source_url` is a dedup key, not always a URL: GitHub and ORCID store
 * real links, but websites store `personal_site:fhnw.ch` and Zefix/manual text
 * store keys with no web address at all. Websites are rebuilt into
 * `https://{domain}` exactly as `IngestionOrchestrator` does when it re-fetches
 * them; everything non-http(s) stays null so it renders as plain text.
 */
export function sourceHref(sourceUrl: string | null): string | null {
  const direct = externalHref(sourceUrl)
  if (direct || !sourceUrl) return direct

  const prefix = WEBSITE_KEY_PREFIXES.find((p) => sourceUrl.startsWith(p))
  if (!prefix) return null
  const domain = sourceUrl.slice(prefix.length)
  return externalHref(
    /^https?:\/\//i.test(domain) ? domain : `https://${domain}`
  )
}

function githubAccountHref(sourceUrl: string | null): string | null {
  const direct = externalHref(sourceUrl)
  if (!direct) return null

  try {
    const url = new URL(direct)
    const host = url.hostname.toLowerCase().replace(/^www\./, "")
    if (host !== "github.com") return null
    const owner = url.pathname.split("/").filter(Boolean)[0]
    return owner ? `https://github.com/${owner}` : null
  } catch {
    return null
  }
}

/** {@link sourceHref} for a profile source row. */
export function externalUrl(source: EntitySourceDto): string | null {
  return sourceHref(source.sourceUrl)
}

/**
 * Source cards aggregate multi-row source types (GitHub repos / OpenAlex works).
 * Their link should point to the connected account, not the first evidence item.
 */
export function aggregateSourceUrl(source: EntitySourceDto): string | null {
  if (source.sourceType === "github") {
    return githubAccountHref(source.sourceUrl)
  }
  return externalUrl(source)
}
