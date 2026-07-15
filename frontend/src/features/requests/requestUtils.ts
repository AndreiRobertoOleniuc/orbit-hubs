import type {
  RequestStatus,
  RequestSummaryDto,
  RequestUrgency,
} from "@/types/api"

/**
 * Shared display logic for the Anfrage lifecycle (reference §8): status and
 * urgency labels, Swiss date formatting, the deadline countdown, and the
 * "vor 2 Tagen" relative timestamps the board cards show.
 */

/** Lifecycle order — also the stepper on the detail page. */
export const STATUS_ORDER: RequestStatus[] = [
  "open",
  "distributed",
  "contacted",
  "closed",
]

export const STATUS_LABEL: Record<RequestStatus, string> = {
  open: "Offen",
  distributed: "Verteilt",
  contacted: "Kontaktiert",
  closed: "Geschlossen",
}

/** What each stage means — shown to the owner under the lifecycle stepper. */
export const STATUS_HINT: Record<RequestStatus, string> = {
  open: "Deine Anfrage ist live und öffentlich auf dem Board sichtbar.",
  distributed: "Passende Profile und Themen-Abonnenten wurden benachrichtigt.",
  contacted: "Mindestens eine Kontaktaufnahme ist erfolgt.",
  closed: "Diese Anfrage ist abgeschlossen und nicht mehr auf dem Board.",
}

export const URGENCY_LABEL: Record<RequestUrgency, string> = {
  flexible: "Flexibel",
  soon: "Bald",
  urgent: "Dringend",
}

/** Distribution strategy per urgency — shown transparently on creation (§8.3). */
export const URGENCY_STRATEGY: Record<RequestUrgency, string> = {
  flexible:
    "Passende Profile werden benachrichtigt. Keine Eskalation — die Anfrage bleibt ruhig auf dem Board liegen.",
  soon: "Passende Profile im gewählten Umkreis werden benachrichtigt, mit einer Erinnerung nach 5 Tagen.",
  urgent:
    "Alle passenden Profile im engsten Umkreis werden sofort benachrichtigt. Ohne Rückmeldung weiten wir nach 48 h automatisch aus (Hub → Region → Schweiz).",
}

const dateFmt = new Intl.DateTimeFormat("de-CH", {
  day: "numeric",
  month: "short",
})

const dateFmtYear = new Intl.DateTimeFormat("de-CH", {
  day: "numeric",
  month: "short",
  year: "numeric",
})

/** "1. Aug." — short Swiss date, no year. */
export function formatDay(iso: string): string {
  return dateFmt.format(new Date(iso))
}

/** "1. Aug. 2026" — full Swiss date. */
export function formatDayYear(iso: string): string {
  return dateFmtYear.format(new Date(iso))
}

/** "1. Aug. – 31. Okt. · ~60 %" from the want-window fields, or null. */
export function wantWindowLabel(
  wantFrom: string | null,
  wantTo: string | null,
  wantPensumPct: number | null
): string | null {
  const parts: string[] = []
  if (wantFrom && wantTo) {
    parts.push(`${formatDay(wantFrom)} – ${formatDay(wantTo)}`)
  } else if (wantFrom) {
    parts.push(`ab ${formatDay(wantFrom)}`)
  } else if (wantTo) {
    parts.push(`bis ${formatDay(wantTo)}`)
  }
  if (wantPensumPct != null) parts.push(`~${wantPensumPct} %`)
  return parts.length > 0 ? parts.join(" · ") : null
}

/** Whole days from today until the deadline (negative = passed), or null. */
export function daysUntil(deadline: string | null): number | null {
  if (!deadline) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(deadline)
  target.setHours(0, 0, 0, 0)
  return Math.round((target.getTime() - today.getTime()) / 86_400_000)
}

/** "heute" / "gestern" / "vor 3 Tagen" / "vor 2 Wochen" / "vor 4 Monaten". */
export function relativeTime(iso: string | null): string | null {
  if (!iso) return null
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000)
  if (days <= 0) return "heute"
  if (days === 1) return "gestern"
  if (days < 7) return `vor ${days} Tagen`
  if (days < 30) {
    const weeks = Math.floor(days / 7)
    return weeks === 1 ? "vor 1 Woche" : `vor ${weeks} Wochen`
  }
  const months = Math.floor(days / 30)
  return months === 1 ? "vor 1 Monat" : `vor ${months} Monaten`
}

/** The scope shown on a card: hub name, region name, or the whole country. */
export function scopeLabel(
  r: Pick<RequestSummaryDto, "scopeKind" | "scopeHubName" | "scopeRegionName">
): string {
  if (r.scopeKind === "hub" && r.scopeHubName) return r.scopeHubName
  if (r.scopeKind === "region" && r.scopeRegionName) return r.scopeRegionName
  return "Ganze Schweiz"
}

/** Initials for the author avatar ("Helvetia Analytics AG" → "HA"). */
export function initials(name: string): string {
  return name
    .replace(/[^\p{L} ]/gu, " ")
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w[0])
    .join("")
    .slice(0, 2)
    .toUpperCase()
}
