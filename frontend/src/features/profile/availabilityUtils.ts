import type { AvailabilityDto, EntityDto } from "@/types/api"

/** The collaboration types a supply profile can be open to (spec §6). */
export const COLLABORATION_TYPES = [
  "Bachelorarbeit",
  "Innosuisse",
  "Mandat",
  "Forschung",
  "Praktikum",
] as const

const MONTHS_DE = [
  "Jan",
  "Feb",
  "Mär",
  "Apr",
  "Mai",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Okt",
  "Nov",
  "Dez",
]

function monthLabel(iso: string | null): string | null {
  if (!iso) return null
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return null
  return MONTHS_DE[d.getMonth()]
}

/** "Aug–Okt" | "ab Aug" | "bis Okt" | null when no window is set. */
export function formatMonthRange(
  from: string | null,
  to: string | null
): string | null {
  const f = monthLabel(from)
  const t = monthLabel(to)
  if (f && t) return `${f}–${t}`
  if (f) return `ab ${f}`
  if (t) return `bis ${t}`
  return null
}

/**
 * Short availability pill text, e.g. "Offen für Anfragen · 60 %, Aug–Okt".
 * Returns null when the profile is not open to requests.
 */
export function availabilityPillText(a: AvailabilityDto | null): string | null {
  if (!a || !a.openToRequests) return null
  const parts: string[] = []
  if (a.pensumPct != null) parts.push(`${a.pensumPct} %`)
  const range = formatMonthRange(a.availableFrom, a.availableTo)
  if (range) parts.push(range)
  return parts.length > 0
    ? `Offen für Anfragen · ${parts.join(", ")}`
    : "Offen für Anfragen"
}

/** What the searcher asked for — the demand-side half of an availability check. */
export interface AvailabilityCriteria {
  wantFrom: string | null
  wantTo: string | null
  wantPensumPct: number | null
}

/** The ways a profile's availability can contradict the search criteria. */
export interface AvailabilityMismatch {
  /** Set when the wanted Pensum exceeds what the profile offers. */
  pensum: { wantedPct: number; availablePct: number } | null
  /** True only when both sides give enough dates to *prove* there is no overlap. */
  noDateOverlap: boolean
}

const NO_MISMATCH: AvailabilityMismatch = { pensum: null, noDateOverlap: false }

function timestamp(iso: string | null | undefined): number | null {
  if (!iso) return null
  const t = new Date(iso).getTime()
  return Number.isNaN(t) ? null : t
}

/**
 * True when the two windows demonstrably cannot overlap. Missing bounds are
 * open-ended (an unset `to` means "indefinitely"), so a window with no dates
 * never conflicts — unknown is not a mismatch.
 */
function windowsCannotOverlap(
  criteria: AvailabilityCriteria,
  a: AvailabilityDto
): boolean {
  const wantFrom = timestamp(criteria.wantFrom)
  const wantTo = timestamp(criteria.wantTo)
  const availFrom = timestamp(a.availableFrom)
  const availTo = timestamp(a.availableTo)
  if (wantFrom != null && availTo != null && wantFrom > availTo) return true
  if (availFrom != null && wantTo != null && availFrom > wantTo) return true
  return false
}

/**
 * Compares a search's Zeitfilter/Pensum against a candidate's advertised
 * availability, so the result card can surface the conflict as its own badge
 * instead of leaving it buried in the rationale paragraph.
 *
 * Only proven contradictions count: when either side leaves a value unset (or
 * the profile isn't advertising availability at all) nothing is reported,
 * because unknown is not the same as a mismatch.
 */
export function availabilityMismatch(
  criteria: AvailabilityCriteria | null | undefined,
  availability: AvailabilityDto | null | undefined
): AvailabilityMismatch {
  if (!criteria || !availability || !availability.openToRequests) {
    return NO_MISMATCH
  }

  const wantedPct = criteria.wantPensumPct
  const availablePct = availability.pensumPct
  const pensum =
    wantedPct != null &&
    Number.isFinite(wantedPct) &&
    availablePct != null &&
    wantedPct > availablePct
      ? { wantedPct, availablePct }
      : null

  return { pensum, noDateOverlap: windowsCannotOverlap(criteria, availability) }
}

/** Whether any mismatch was found — the render guard for the warning badges. */
export function hasAvailabilityMismatch(m: AvailabilityMismatch): boolean {
  return m.pensum != null || m.noDateOverlap
}

/**
 * Profile completeness criteria for a supply profile — each true item counts
 * toward the "X von 5" banner. Computed purely from the entity read model.
 */
export function completenessCriteria(
  entity: EntityDto
): { label: string; done: boolean }[] {
  return [
    { label: "Quelle verknüpft", done: entity.sources.length > 0 },
    {
      label: "Verfügbarkeit gesetzt",
      done: entity.availability?.openToRequests === true,
    },
    { label: "Kompetenz-Tag", done: entity.tags.length > 0 },
    { label: "Kurzprofil", done: Boolean(entity.summary) },
    { label: "Hub gewählt", done: entity.hubs.length > 0 },
  ]
}
