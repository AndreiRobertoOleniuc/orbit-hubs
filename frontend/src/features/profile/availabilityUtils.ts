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
