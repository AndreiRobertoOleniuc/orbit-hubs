import type { HubDto, RegionDto, ScopeSelection } from "@/types/api"

/**
 * The scope selector encodes its choice as a single string value:
 * `"all"` | `"region:{id}"` | `"hub:{id}"`. These helpers convert to/from the
 * {@link ScopeSelection} the API expects and compute the manual widen ladder
 * (Hub → Region → CH → Alle), since the app uses strict scope (reference §5.4)
 * and lets the user widen explicitly.
 */

export const SCOPE_ALL = "all"

export function decodeScope(value: string): ScopeSelection {
  if (value.startsWith("hub:")) {
    return { kind: "hub", hubId: Number(value.slice(4)) }
  }
  if (value.startsWith("region:")) {
    return { kind: "region", regionId: Number(value.slice(7)) }
  }
  return { kind: "all" }
}

/** Depth of a region in the tree (root = 0), used to order most-specific first. */
function regionDepth(region: RegionDto, byId: Map<number, RegionDto>): number {
  let depth = 0
  let current: RegionDto | undefined = region
  while (current?.parentRegionId != null) {
    current = byId.get(current.parentRegionId)
    depth++
    if (depth > 20) break // guard against cycles
  }
  return depth
}

/** Regions ordered most-specific first (e.g. Region Baden → Aargau → Schweiz). */
export function regionsBySpecificity(regions: RegionDto[]): RegionDto[] {
  const byId = new Map(regions.map((r) => [r.id, r]))
  return [...regions].sort(
    (a, b) => regionDepth(b, byId) - regionDepth(a, byId)
  )
}

interface WiderScope {
  value: string
  /** Human label of the wider target, e.g. "Aargau" or "alle Profile". */
  label: string
}

/**
 * The next wider scope for the manual "erweitern" control, or null when already
 * at the broadest level. Hub → its region; region → parent region; root region
 * → all (drops the geo filter entirely, reaching hub-/region-less profiles too).
 */
export function nextWiderScope(
  value: string,
  regions: RegionDto[],
  hubs: HubDto[]
): WiderScope | null {
  if (value === SCOPE_ALL) return null

  if (value.startsWith("hub:")) {
    const hub = hubs.find((h) => h.id === Number(value.slice(4)))
    if (!hub) return null
    const region = regions.find((r) => r.id === hub.regionId)
    return { value: `region:${hub.regionId}`, label: region?.name ?? "Region" }
  }

  if (value.startsWith("region:")) {
    const region = regions.find((r) => r.id === Number(value.slice(7)))
    if (!region) return null
    if (region.parentRegionId != null) {
      const parent = regions.find((r) => r.id === region.parentRegionId)
      return {
        value: `region:${region.parentRegionId}`,
        label: parent?.name ?? "Region",
      }
    }
    return { value: SCOPE_ALL, label: "alle Profile" }
  }

  return null
}

/** Short label + hint for the currently selected scope, shown under the form. */
export function scopeHint(
  value: string,
  regions: RegionDto[],
  hubs: HubDto[]
): { name: string; hint: string } {
  if (value === SCOPE_ALL) {
    return {
      name: "Ganze Schweiz / Online",
      hint: "Profile aus der ganzen Schweiz, auch für ortsunabhängige Zusammenarbeit.",
    }
  }
  if (value.startsWith("hub:")) {
    const hub = hubs.find((h) => h.id === Number(value.slice(4)))
    const name = hub?.name ?? "Hub"
    return {
      name,
      hint: `Zuerst Profile mit ${name}-Bezug. Findest du zu wenig, kannst du auf die Region und die ganze Schweiz erweitern.`,
    }
  }
  const region = regions.find((r) => r.id === Number(value.slice(7)))
  const name = region?.name ?? "Region"
  return {
    name,
    hint: `Zuerst Profile aus ${name}. Findest du zu wenig, kannst du breiter suchen.`,
  }
}
