import { useEffect, useState, type CSSProperties } from "react"
import { Button } from "@/components/ui/button"
import type { AvailabilityCriteria } from "@/features/profile/availabilityUtils"
import { ProfileSidePanel } from "@/features/search/ProfileSidePanel"
import { SearchLoadingState } from "@/features/search/SearchLoadingState"
import { SearchResultCard } from "@/features/search/SearchResultCard"
import { nextWiderScope } from "@/features/search/scopeUtils"
import { cn } from "@/lib/utils"
import type {
  EntityDto,
  HubDto,
  RegionDto,
  SearchResponseDto,
} from "@/types/api"

interface SearchResultsProps {
  query: string
  /** Zeitfilter + Pensum of the executed search — compared against each hit. */
  criteria: AvailabilityCriteria
  data: SearchResponseDto | undefined
  isLoading: boolean
  isError: boolean
  scope: string
  regions: RegionDto[]
  hubs: HubDto[]
  onEdit: () => void
  onWiden: (scopeValue: string) => void
  /** Opens the "Anfrage ausschreiben" form prefilled with this search. */
  onCreateRequest: () => void
}

function WidenButton({
  scope,
  regions,
  hubs,
  onWiden,
}: Pick<SearchResultsProps, "scope" | "regions" | "hubs" | "onWiden">) {
  const next = nextWiderScope(scope, regions, hubs)
  if (!next) return null
  return (
    <Button variant="outline" onClick={() => onWiden(next.value)}>
      Auf {next.label} erweitern
    </Button>
  )
}

export function SearchResults({
  query,
  criteria,
  data,
  isLoading,
  isError,
  scope,
  regions,
  hubs,
  onEdit,
  onWiden,
  onCreateRequest,
}: SearchResultsProps) {
  const results = data?.results ?? []
  const hasResults = results.length > 0

  // The selected profile opens in a slide-out panel next to the list, so the
  // (expensive) search stays on screen and results can be compared by
  // switching. The EntityDto itself is stored (not just the id) so the panel
  // survives a widen re-run replacing the result list; panelOpen is separate
  // so the close animation still has content to show.
  const [selected, setSelected] = useState<EntityDto | null>(null)
  const [panelOpen, setPanelOpen] = useState(false)

  const openProfile = (entity: EntityDto) => {
    setSelected(entity)
    setPanelOpen(true)
  }
  const closePanel = () => setPanelOpen(false)

  useEffect(() => {
    if (!panelOpen) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setPanelOpen(false)
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [panelOpen])

  return (
    <div style={{ "--panel-w": "clamp(26rem, 40vw, 46rem)" } as CSSProperties}>
      {/* The list re-centers in the space left of the panel; the padding
          transition makes it glide while the panel slides in */}
      <div
        className={cn(
          "transition-[padding] duration-300 ease-in-out",
          panelOpen && selected && "lg:pr-(--panel-w)"
        )}
      >
        <div className="mx-auto w-full max-w-2xl py-6">
          <div className="flex items-baseline justify-between gap-4 border-b pb-4">
            <p className="truncate text-sm text-muted-foreground">«{query}»</p>
            <button
              type="button"
              onClick={onEdit}
              className="shrink-0 text-sm font-medium text-primary hover:underline"
            >
              Suche ändern
            </button>
          </div>

          {isLoading && <SearchLoadingState />}

          {!isLoading && isError && (
            <p className="py-16 text-center text-sm text-muted-foreground">
              Die Suche ist fehlgeschlagen. Bitte versuche es erneut.
            </p>
          )}

          {!isLoading && !isError && hasResults && (
            <>
              <h2 className="mt-8 mb-1 flex items-baseline gap-2 text-[11px] tracking-wide text-muted-foreground">
                <span className="tabular-nums">{results.length} Treffer</span>
                <span aria-hidden className="text-muted-foreground/40">
                  ·
                </span>
                <span>nach Passung sortiert</span>
              </h2>
              <ol className="mt-3 flex flex-col gap-3">
                {results.map((r, i) => (
                  <li key={r.entity.id}>
                    <SearchResultCard
                      result={r}
                      rank={i}
                      query={query}
                      criteria={criteria}
                      selected={panelOpen && selected?.id === r.entity.id}
                      onSelect={() => openProfile(r.entity)}
                    />
                  </li>
                ))}
              </ol>
              <div className="mt-8 flex flex-col items-center gap-3 text-center">
                {nextWiderScope(scope, regions, hubs) && (
                  <p className="text-sm text-muted-foreground">
                    Nicht das Richtige dabei? Suche breiter:
                  </p>
                )}
                <WidenButton
                  scope={scope}
                  regions={regions}
                  hubs={hubs}
                  onWiden={onWiden}
                />
              </div>
            </>
          )}

          {/* Umformulieren steht vorn und gefüllt: eine zu kurze, stichwortartige
              Anfrage ist die häufigste Ursache für null Treffer, und sie zu
              schärfen ist der billigste nächste Schritt — billiger jedenfalls
              als eine Anfrage auszuschreiben. */}
          {!isLoading && !isError && !hasResults && (
            <div className="mx-auto mt-10 max-w-xl rounded-xl bg-card p-8 text-center ring-1 ring-foreground/10">
              <h2 className="text-xl font-bold">Kein direkter Treffer</h2>
              <p className="mt-2 truncate text-sm text-muted-foreground">
                «{query}»
              </p>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
                Kurze Stichwörter finden oft nichts — beschreibe dein Anliegen
                in ein bis zwei Sätzen. Oder erstelle eine Anfrage: Sie
                erscheint öffentlich unter «Offene Anfragen», passende Profile
                werden benachrichtigt, und du wirst informiert, sobald jemand
                reagiert.
              </p>
              <div className="mt-6 flex flex-col items-center gap-2">
                <Button onClick={onEdit}>Suche umformulieren</Button>
                <WidenButton
                  scope={scope}
                  regions={regions}
                  hubs={hubs}
                  onWiden={onWiden}
                />
                <Button variant="outline" onClick={onCreateRequest}>
                  Anfrage erstellen
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Below lg the panel overlays the list, so dim it */}
      <div
        aria-hidden
        onClick={closePanel}
        className={cn(
          "fixed inset-0 z-40 bg-black/40 transition-opacity duration-300 lg:hidden",
          panelOpen && selected
            ? "opacity-100"
            : "pointer-events-none opacity-0"
        )}
      />

      {/* The panel is pinned to the viewport's right edge below the h-14
          header and slides in from off-screen (Claude-Cowork style) */}
      <aside
        aria-hidden={!panelOpen}
        className={cn(
          "fixed top-14 right-0 bottom-0 z-40 w-full max-w-md p-2 transition-transform duration-300 ease-in-out lg:w-(--panel-w) lg:max-w-none lg:p-3",
          panelOpen && selected ? "translate-x-0" : "translate-x-full"
        )}
      >
        {selected && (
          <ProfileSidePanel
            entity={selected}
            onClose={closePanel}
            className="h-full shadow-xl"
          />
        )}
      </aside>
    </div>
  )
}
