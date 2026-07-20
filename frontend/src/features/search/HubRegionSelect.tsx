import { useMemo } from "react"

import { useGetHubsQuery, useGetRegionsQuery } from "@/lib/api"
import { cn } from "@/lib/utils"
import { regionsBySpecificity } from "@/features/search/scopeUtils"

/**
 * Scope selector — a native `<select>` grouped into Regionen / Hubs (the app has
 * no shadcn select primitive). Its value is the encoded scope string
 * (`all` | `region:{id}` | `hub:{id}`); see scopeUtils.
 */
export function HubRegionSelect({
  value,
  onChange,
  className,
}: {
  value: string
  onChange: (value: string) => void
  className?: string
}) {
  const { data: regions = [] } = useGetRegionsQuery()
  const { data: hubs = [] } = useGetHubsQuery()
  const orderedRegions = useMemo(() => regionsBySpecificity(regions), [regions])

  return (
    <select
      aria-label="Hub / Region"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={cn(
        "h-9 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm transition-colors outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30",
        className
      )}
    >
      <option value="all">Alle (ganze Schweiz)</option>
      {orderedRegions.length > 0 && (
        <optgroup label="Regionen">
          {orderedRegions.map((r) => (
            <option key={r.id} value={`region:${r.id}`}>
              {r.name}
            </option>
          ))}
        </optgroup>
      )}
      {hubs.length > 0 && (
        <optgroup label="Hubs">
          {hubs.map((h) => (
            <option key={h.id} value={`hub:${h.id}`}>
              {h.name}
            </option>
          ))}
        </optgroup>
      )}
    </select>
  )
}
