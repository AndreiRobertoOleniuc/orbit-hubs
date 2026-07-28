import { useState } from "react"
import { Loader2, MapPin, Search } from "lucide-react"

import { Input } from "@/components/ui/input"
import { useGeoAdminSearch } from "@/lib/geoAdmin"
import type { GeoSuggestion } from "@/lib/geoAdmin"

/** Real Swiss place and address search powered by the federal geo.admin.ch API. */
export function LocationInput({
  id,
  value,
  onChange,
}: {
  id?: string
  value: string
  onChange: (value: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [highlighted, setHighlighted] = useState(0)
  const { suggestions, isSearching } = useGeoAdminSearch(value, {
    enabled: open,
    origins: "address,zipcode,gg25,gazetteer",
  })

  // Clamped rather than reset in an effect — the list shrinks as the user types.
  const activeIndex = Math.min(highlighted, Math.max(suggestions.length - 1, 0))

  const showSuggestions = open && suggestions.length > 0
  const pick = (suggestion: GeoSuggestion) => {
    onChange(suggestion.label)
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
        value={value}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={showSuggestions}
        aria-controls="location-suggestions"
        aria-activedescendant={
          showSuggestions ? `location-option-${activeIndex}` : undefined
        }
        autoComplete="off"
        placeholder="Adresse, Ort oder PLZ suchen"
        className="pr-9 pl-9"
        onChange={(event) => {
          onChange(event.target.value)
          setOpen(true)
          setHighlighted(0)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && suggestions.length > 0) {
            event.preventDefault()
            setHighlighted((current) =>
              Math.min(current + 1, suggestions.length - 1)
            )
          } else if (event.key === "ArrowUp" && suggestions.length > 0) {
            event.preventDefault()
            setHighlighted((current) => Math.max(current - 1, 0))
          } else if (event.key === "Enter" && showSuggestions) {
            event.preventDefault()
            pick(suggestions[activeIndex])
          } else if (event.key === "Escape") {
            setOpen(false)
          }
        }}
      />
      {isSearching ? (
        <Loader2
          aria-label="Standorte werden gesucht"
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 animate-spin text-muted-foreground"
        />
      ) : (
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground"
        />
      )}

      {showSuggestions && (
        <div
          id="location-suggestions"
          role="listbox"
          className="absolute inset-x-0 top-full z-20 mt-1 overflow-hidden rounded-md border bg-popover shadow-md"
        >
          {suggestions.map((suggestion, index) => (
            <button
              key={`${suggestion.id}-${suggestion.label}`}
              id={`location-option-${index}`}
              type="button"
              role="option"
              aria-selected={suggestion.label === value}
              className={`flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-accent ${
                index === activeIndex ? "bg-accent" : ""
              }`}
              onMouseEnter={() => setHighlighted(index)}
              onMouseDown={(event) => {
                event.preventDefault()
                pick(suggestion)
              }}
            >
              <MapPin className="size-3.5 shrink-0 text-muted-foreground" />
              <span className="min-w-0 flex-1 truncate">
                {suggestion.label}
              </span>
              <span className="shrink-0 text-xs text-muted-foreground">
                {suggestion.kind}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
