import { useEffect, useRef, useState } from "react"

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

/** A place suggestion from the federal geo.admin.ch search. */
export interface GeoSuggestion {
  id: number | string
  label: string
  kind: string
  latitude: number | null
  longitude: number | null
}

const KIND_LABEL: Record<string, string> = {
  address: "Adresse",
  zipcode: "Ort / PLZ",
  gg25: "Gemeinde",
  district: "Bezirk",
  kantone: "Kanton",
  gazetteer: "Ort",
}

const EMPTY: GeoSuggestion[] = []

function plainText(html: string) {
  const parsed = new DOMParser().parseFromString(html, "text/html")
  return (parsed.body.textContent ?? "").replace(/\s+/g, " ").trim()
}

/**
 * Debounced Swiss place search against the federal geo.admin.ch API. Shared by
 * the profile {@code LocationInput} (addresses & places) and the admin region
 * picker (municipalities, districts, cantons).
 */
export function useGeoAdminSearch(
  query: string,
  { enabled, origins }: { enabled: boolean; origins: string }
) {
  const [suggestions, setSuggestions] = useState<GeoSuggestion[]>([])
  const [isSearching, setIsSearching] = useState(false)
  const request = useRef<AbortController | null>(null)

  useEffect(() => {
    const searchText = query.trim()
    request.current?.abort()

    if (searchText.length < 2 || !enabled) {
      return
    }

    const controller = new AbortController()
    request.current = controller
    const timeout = setTimeout(async () => {
      setIsSearching(true)
      const params = new URLSearchParams({
        searchText,
        type: "locations",
        origins,
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
              latitude: result.attrs.lat ?? null,
              longitude: result.attrs.lon ?? null,
            }))
            .filter((result) => {
              const key = result.label.toLocaleLowerCase("de-CH")
              if (!result.label || seen.has(key)) return false
              seen.add(key)
              return true
            })
            .slice(0, 8)
        )
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
  }, [enabled, origins, query])

  // A query too short to search shows nothing, without clearing state in the
  // effect (which would cascade a render).
  const tooShort = query.trim().length < 2
  return {
    suggestions: tooShort ? EMPTY : suggestions,
    isSearching: isSearching && !tooShort,
  }
}
