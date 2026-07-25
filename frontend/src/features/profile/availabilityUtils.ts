import type { UpdateAvailabilityBody } from "@/lib/api"
import { sameSet } from "@/lib/utils"
import type { AvailabilityDto, EntityDto } from "@/types/api"

/** A collaboration type: the persisted value plus the text we show for it. */
export interface CollaborationType {
  /** Stored in `preferredCollaboration` and sent to the API — never change. */
  value: string
  /** Display only. Phrased from the supply side, so it states the direction. */
  label: string
}

/**
 * The collaboration types a supply profile can be open to (spec §6).
 *
 * The bare nouns are ambiguous in a supply profile — a tester read
 * "Bachelorarbeit" as "I am looking for someone to write one" rather than
 * "I can supervise one". The labels resolve that; the values stay untouched
 * because they are already persisted.
 */
export const COLLABORATION_TYPES: readonly CollaborationType[] = [
  { value: "Bachelorarbeit", label: "Bachelorarbeit betreuen" },
  { value: "Innosuisse", label: "Innosuisse-Projekt" },
  { value: "Mandat", label: "Mandat übernehmen" },
  { value: "Forschung", label: "Forschungskooperation" },
  { value: "Praktikum", label: "Praktikum anbieten" },
] as const

/** Prompt shown above the choices — names who is offering what to whom. */
export const COLLABORATION_PROMPT = "Wofür dürfen andere dich kontaktieren?"

/**
 * Display label for a persisted value. Values written before this mapping
 * existed (or by a future backend) fall back to the raw value rather than
 * disappearing from the UI.
 */
export function collaborationLabel(value: string): string {
  return COLLABORATION_TYPES.find((t) => t.value === value)?.label ?? value
}

/**
 * The availability form's own state, shared by the profile editor and the
 * onboarding step so the two surfaces can't drift apart again. Uses form-native
 * types — empty strings rather than nulls — and omits `openToRequests`, which
 * each surface expresses with its own control (a switch vs. a choice card).
 */
export interface AvailabilityDraft {
  availableFrom: string
  availableTo: string
  pensumPct: number
  note: string
  preferredCollaboration: string[]
}

/** Saved availability → editable draft. Missing Pensum starts at full time. */
export function toAvailabilityDraft(
  a: AvailabilityDto | null | undefined
): AvailabilityDraft {
  return {
    availableFrom: a?.availableFrom ?? "",
    availableTo: a?.availableTo ?? "",
    pensumPct: a?.pensumPct ?? 100,
    note: a?.note ?? "",
    preferredCollaboration: a?.preferredCollaboration ?? [],
  }
}

/**
 * Draft → API body. Closing the profile clears the fields that make it look
 * available (Pensum, collaboration types) but keeps the window and note, so
 * toggling back on restores what was typed rather than starting over.
 */
export function toAvailabilityBody(
  openToRequests: boolean,
  d: AvailabilityDraft
): UpdateAvailabilityBody {
  return {
    openToRequests,
    availableFrom: d.availableFrom || null,
    availableTo: d.availableTo || null,
    pensumPct: openToRequests ? d.pensumPct : null,
    note: d.note.trim() || null,
    preferredCollaboration: openToRequests ? d.preferredCollaboration : [],
  }
}

/** Whether the draft still matches what was saved — drives the Speichern state. */
export function availabilityDraftChanged(
  a: AvailabilityDto | null | undefined,
  d: AvailabilityDraft
): boolean {
  const base = toAvailabilityDraft(a)
  return (
    base.availableFrom !== d.availableFrom ||
    base.availableTo !== d.availableTo ||
    base.pensumPct !== d.pensumPct ||
    base.note !== d.note ||
    !sameSet(base.preferredCollaboration, d.preferredCollaboration)
  )
}

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
