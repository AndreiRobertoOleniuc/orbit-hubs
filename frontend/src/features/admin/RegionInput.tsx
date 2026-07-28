import { useMemo, useState } from "react"
import { Loader2, MapPin, Search } from "lucide-react"

import { Input } from "@/components/ui/input"
import { useGeoAdminSearch } from "@/lib/geoAdmin"

/** The region a hub is attached to — either an existing one or a new place. */
export interface RegionSelection {
  regionId: number | null
  name: string
  latitude: number | null
  longitude: number | null
}

interface Option {
  key: string
  label: string
  kind: string
  selection: RegionSelection
}

/**
 * Region picker for hub management. Suggests the regions that already exist in
 * the Hub → Region → CH tree first, then any Swiss canton, district, or
 * municipality from geo.admin.ch — a picked place that is not in the tree yet is
 * created on demand by the backend.
 */
export function RegionInput({
  id,
  value,
  regions,
  onChange,
}: {
  id?: string
  value: RegionSelection | null
  regions: { id: number; name: string }[]
  onChange: (selection: RegionSelection | null) => void
}) {
  const [open, setOpen] = useState(false)
  const [highlighted, setHighlighted] = useState(0)

  // The typed text *is* the selection: typing yields an unresolved region
  // (regionId null), picking an option resolves it.
  const query = value?.name ?? ""
  const { suggestions, isSearching } = useGeoAdminSearch(query, {
    enabled: open,
    origins: "kantone,district,gg25",
  })

  const options = useMemo<Option[]>(() => {
    const needle = query.trim().toLocaleLowerCase("de-CH")
    const existing = regions
      .filter((region) =>
        region.name.toLocaleLowerCase("de-CH").includes(needle)
      )
      .map((region) => ({
        key: `region-${region.id}`,
        label: region.name,
        kind: "Bestehende Region",
        selection: {
          regionId: region.id,
          name: region.name,
          latitude: null,
          longitude: null,
        },
      }))
    const known = new Set(
      regions.map((region) => region.name.toLocaleLowerCase("de-CH"))
    )
    const places = suggestions
      .filter((s) => !known.has(s.label.toLocaleLowerCase("de-CH")))
      .map((s) => ({
        key: `geo-${s.id}`,
        label: s.label,
        kind: `${s.kind} · neu`,
        selection: {
          regionId: null,
          name: s.label,
          latitude: s.latitude,
          longitude: s.longitude,
        },
      }))
    return [...existing, ...places].slice(0, 10)
  }, [query, regions, suggestions])

  // Clamped rather than reset in an effect — the list shrinks as the user types.
  const activeIndex = Math.min(highlighted, Math.max(options.length - 1, 0))

  const showOptions = open && options.length > 0
  const pick = (option: Option) => {
    onChange(option.selection)
    setOpen(false)
  }

  return (
    <div className="relative">
      <MapPin
        aria-hidden="true"
        className="pointer-events-none absolute top-1/2 left-3 z-10 size-4 -translate-y-1/2 text-muted-foreground"
      />
      <Input
        id={id}
        value={query}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={showOptions}
        aria-controls="region-options"
        aria-activedescendant={
          showOptions ? `region-option-${activeIndex}` : undefined
        }
        autoComplete="off"
        placeholder="Region, Kanton oder Gemeinde"
        className="pr-9 pl-9"
        onChange={(event) => {
          const text = event.target.value
          onChange(
            text
              ? { regionId: null, name: text, latitude: null, longitude: null }
              : null
          )
          setOpen(true)
          setHighlighted(0)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && options.length > 0) {
            event.preventDefault()
            setHighlighted((current) =>
              Math.min(current + 1, options.length - 1)
            )
          } else if (event.key === "ArrowUp" && options.length > 0) {
            event.preventDefault()
            setHighlighted((current) => Math.max(current - 1, 0))
          } else if (event.key === "Enter" && showOptions) {
            event.preventDefault()
            pick(options[activeIndex])
          } else if (event.key === "Escape") {
            setOpen(false)
          }
        }}
      />
      {isSearching ? (
        <Loader2
          aria-label="Regionen werden gesucht"
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 animate-spin text-muted-foreground"
        />
      ) : (
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground"
        />
      )}

      {showOptions && (
        <div
          id="region-options"
          role="listbox"
          className="absolute inset-x-0 top-full z-30 mt-1 max-h-72 overflow-y-auto rounded-md border bg-popover shadow-md"
        >
          {options.map((option, index) => (
            <button
              key={option.key}
              id={`region-option-${index}`}
              type="button"
              role="option"
              aria-selected={option.label === value?.name}
              className={`flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-accent ${
                index === activeIndex ? "bg-accent" : ""
              }`}
              onMouseEnter={() => setHighlighted(index)}
              onMouseDown={(event) => {
                event.preventDefault()
                pick(option)
              }}
            >
              <MapPin className="size-3.5 shrink-0 text-muted-foreground" />
              <span className="min-w-0 flex-1 truncate">{option.label}</span>
              <span className="shrink-0 text-xs text-muted-foreground">
                {option.kind}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
