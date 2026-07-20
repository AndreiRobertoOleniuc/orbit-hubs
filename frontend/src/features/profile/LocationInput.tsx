import { useEffect, useRef, useState } from "react"
import { Loader2, MapPin, Search } from "lucide-react"

import { Input } from "@/components/ui/input"

interface GeoAdminResult {
  id: number | string
  attrs: {
    label: string
    origin: string
    lat?: number
    lon?: number
  }
}

interface GeoAdminResponse {
  results: GeoAdminResult[]
}

interface LocationSuggestion {
  id: number | string
  label: string
  kind: string
}

const KIND_LABEL: Record<string, string> = {
  address: "Adresse",
  zipcode: "Ort / PLZ",
  gg25: "Gemeinde",
  gazetteer: "Ort",
}

function plainText(html: string) {
  const parsed = new DOMParser().parseFromString(html, "text/html")
  return (parsed.body.textContent ?? "").replace(/\s+/g, " ").trim()
}

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
  const [activeIndex, setActiveIndex] = useState(0)
  const [suggestions, setSuggestions] = useState<LocationSuggestion[]>([])
  const [isSearching, setIsSearching] = useState(false)
  const request = useRef<AbortController | null>(null)

  useEffect(() => {
    const query = value.trim()
    request.current?.abort()

    if (query.length < 2 || !open) {
      return
    }

    const controller = new AbortController()
    request.current = controller
    const timeout = setTimeout(async () => {
      setIsSearching(true)
      const params = new URLSearchParams({
        searchText: query,
        type: "locations",
        origins: "address,zipcode,gg25,gazetteer",
        returnGeometry: "false",
        lang: "de",
        limit: "8",
      })

      try {
        const response = await fetch(
          `https://api3.geo.admin.ch/rest/services/ech/SearchServer?${params}`,
          { signal: controller.signal }
        )
        if (!response.ok) throw new Error("location search failed")
        const data = (await response.json()) as GeoAdminResponse
        const seen = new Set<string>()
        setSuggestions(
          data.results
            .map((result) => ({
              id: result.id,
              label: plainText(result.attrs.label),
              kind: KIND_LABEL[result.attrs.origin] ?? "Ort",
            }))
            .filter((result) => {
              const key = result.label.toLocaleLowerCase("de-CH")
              if (!result.label || seen.has(key)) return false
              seen.add(key)
              return true
            })
            .slice(0, 8)
        )
        setActiveIndex(0)
      } catch (error) {
        if (!(error instanceof DOMException && error.name === "AbortError")) {
          setSuggestions([])
        }
      } finally {
        if (!controller.signal.aborted) setIsSearching(false)
      }
    }, 300)

    return () => {
      clearTimeout(timeout)
      controller.abort()
    }
  }, [open, value])

  const showSuggestions = open && suggestions.length > 0
  const pick = (suggestion: LocationSuggestion) => {
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
          const nextValue = event.target.value
          onChange(nextValue)
          if (nextValue.trim().length < 2) {
            setSuggestions([])
            setIsSearching(false)
          }
          setOpen(true)
          setActiveIndex(0)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && suggestions.length > 0) {
            event.preventDefault()
            setActiveIndex((current) =>
              Math.min(current + 1, suggestions.length - 1)
            )
          } else if (event.key === "ArrowUp" && suggestions.length > 0) {
            event.preventDefault()
            setActiveIndex((current) => Math.max(current - 1, 0))
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
              onMouseEnter={() => setActiveIndex(index)}
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
