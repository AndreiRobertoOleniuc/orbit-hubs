import { useState } from "react"
import { useNavigate } from "react-router"
import { Clock3, Lock, MapPin, Trash2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { useAuth } from "@/features/auth/useAuth"
import { HubRegionSelect } from "@/features/search/HubRegionSelect"
import { scopeHint } from "@/features/search/scopeUtils"
import { InfoHint } from "@/components/InfoHint"
import { useGetHubsQuery, useGetRegionsQuery } from "@/lib/api"
import { todayIso } from "@/lib/utils"
import type { SearchHistoryEntry } from "@/features/search/searchSlice"

const FIELD =
  "h-9 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"

const CAP =
  "text-[11px] font-semibold tracking-wide text-muted-foreground uppercase"

export interface SearchFormState {
  query: string
  scope: string
  wantFrom: string
  wantTo: string
  wantPensum: string
}

interface SearchFormProps extends SearchFormState {
  history: SearchHistoryEntry[]
  onChange: (patch: Partial<SearchFormState>) => void
  onSearch: () => void
  onRestoreHistory: (id: string) => void
  onRemoveHistory: (id: string) => void
}

/** The centered search entry (design: "Wen suchst du?") — query + the three filters. */
export function SearchForm({
  query,
  scope,
  wantFrom,
  wantTo,
  wantPensum,
  history,
  onChange,
  onSearch,
  onRestoreHistory,
  onRemoveHistory,
}: SearchFormProps) {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const { data: regions = [] } = useGetRegionsQuery()
  const { data: hubs = [] } = useGetHubsQuery()
  const hint = scopeHint(scope, regions, hubs)
  const [attemptedSearch, setAttemptedSearch] = useState(false)
  const queryMissing = attemptedSearch && !query.trim()

  const handleSearch = () => {
    setAttemptedSearch(true)
    if (!query.trim()) return
    onSearch()
  }

  return (
    <div className="mx-auto max-w-2xl py-6 sm:py-10">
      <h1 className="mb-3 text-center text-2xl font-bold tracking-tight sm:text-3xl">
        Wen suchst du?
      </h1>
      <p className="mx-auto mb-6 max-w-md text-center text-[15px] leading-relaxed text-muted-foreground">
        Beschreibe dein Anliegen in eigenen Worten. Wir gleichen es mit belegten
        Kompetenzen aus öffentlichen Quellen ab — <strong>Hub / Region</strong>{" "}
        steuert, wo wir zuerst suchen.
      </p>

      <p className="mb-2 text-xs text-muted-foreground">
        <span className="text-destructive">*</span> Pflichtfeld
      </p>
      <label htmlFor="search-query" className={CAP}>
        Was suchst du? <span className="text-destructive">*</span>
      </label>
      <Textarea
        id="search-query"
        rows={3}
        autoFocus
        className="mt-1.5 text-base"
        placeholder="Beschreibe in ein bis zwei Sätzen, was du brauchst, z. B. «Ich suche eine Person, die Nutzerfeedback zu Requirements verdichtet, ca. 20 % ab August.»"
        value={query}
        aria-invalid={queryMissing}
        aria-describedby={queryMissing ? "search-query-error" : undefined}
        onChange={(e) => onChange({ query: e.target.value })}
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) handleSearch()
        }}
      />
      {queryMissing && (
        <p id="search-query-error" className="mt-1.5 text-sm text-destructive">
          Bitte beschreibe kurz, welche Expertise du suchst.
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-end gap-4">
        <div className="flex w-full min-w-0 flex-1 flex-col gap-1.5 sm:min-w-52">
          <span className={`${CAP} inline-flex items-center gap-1`}>
            Hub / Region
            <InfoHint term="hub" />
          </span>
          <HubRegionSelect
            value={scope}
            onChange={(v) => onChange({ scope: v })}
          />
        </div>
        <div className="flex w-full flex-col gap-1.5 sm:w-auto">
          <span className={CAP}>Zeitraum</span>
          <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
            <input
              type="date"
              aria-label="Verfügbar von"
              className={`${FIELD} w-full min-w-0`}
              min={todayIso()}
              max={wantTo || undefined}
              value={wantFrom}
              onChange={(e) => onChange({ wantFrom: e.target.value })}
            />
            <span aria-hidden="true" className="text-muted-foreground">
              –
            </span>
            <input
              type="date"
              aria-label="Verfügbar bis"
              className={`${FIELD} w-full min-w-0`}
              min={wantFrom || todayIso()}
              value={wantTo}
              onChange={(e) => onChange({ wantTo: e.target.value })}
            />
          </div>
        </div>
        <div className="flex w-full flex-col gap-1.5 sm:w-auto">
          <span className={`${CAP} inline-flex items-center gap-1`}>
            Pensum ca. %
            <InfoHint term="pensum" />
          </span>
          <input
            type="number"
            min={0}
            max={100}
            step={10}
            inputMode="numeric"
            aria-label="Pensum in Prozent"
            placeholder="z. B. 50"
            className={`${FIELD} w-full sm:w-24`}
            value={wantPensum}
            onChange={(e) => onChange({ wantPensum: e.target.value })}
          />
        </div>
      </div>

      <p className="mt-2.5 text-[12.5px] text-muted-foreground">
        Leer lassen = keine Zeit-Einschränkung. Beeinflusst die Reihenfolge,
        filtert nicht hart.
      </p>

      <Button className="mt-4 w-full" onClick={handleSearch}>
        Suchen
      </Button>

      <p className="mx-auto mt-3 max-w-md text-center text-[13px] leading-relaxed text-muted-foreground">
        <MapPin className="mr-1 inline size-3.5 align-[-2px]" />
        <strong className="text-foreground/80">{hint.name}</strong> —{" "}
        {hint.hint}
      </p>
      <p className="mt-4 text-center text-sm text-muted-foreground">
        Kein Konto nötig zum Suchen.
      </p>

      {history.length > 0 && (
        <section
          className="mt-6 border-t pt-5"
          aria-labelledby="search-history-title"
        >
          <div className="flex items-center justify-between gap-3">
            <h2
              id="search-history-title"
              className="inline-flex items-center gap-2 text-sm font-semibold"
            >
              <Clock3 className="size-4 text-muted-foreground" />
              Letzte Suchen
            </h2>
            <span className="text-xs text-muted-foreground">
              Auf diesem Gerät gespeichert
            </span>
          </div>
          <div className="mt-3 space-y-2">
            {history.map((entry) => (
              <div
                key={entry.id}
                className="flex min-w-0 items-center gap-2 rounded-lg border bg-card p-2"
              >
                <button
                  type="button"
                  className="min-w-0 flex-1 rounded-md px-2 py-1.5 text-left hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none"
                  onClick={() => onRestoreHistory(entry.id)}
                >
                  <span className="block truncate text-sm font-medium">
                    {entry.form.query}
                  </span>
                  <span className="mt-0.5 block text-xs text-muted-foreground">
                    {entry.results.results.length} Treffer ·{" "}
                    {new Intl.DateTimeFormat("de-CH", {
                      day: "2-digit",
                      month: "2-digit",
                      hour: "2-digit",
                      minute: "2-digit",
                    }).format(new Date(entry.searchedAt))}
                  </span>
                </button>
                <Button
                  type="button"
                  size="icon-sm"
                  variant="ghost"
                  aria-label={`Suche «${entry.form.query}» entfernen`}
                  onClick={() => onRemoveHistory(entry.id)}
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
            ))}
          </div>
        </section>
      )}

      <div className="mx-auto my-8 flex max-w-sm items-center gap-4">
        <span className="h-px flex-1 bg-border" />
        <span className="text-xs text-muted-foreground">oder</span>
        <span className="h-px flex-1 bg-border" />
      </div>

      <div className="text-center">
        <p className="mx-auto mb-3 max-w-md text-sm text-muted-foreground">
          Du weisst schon, wen du brauchst? Schreibe eine Anfrage aus — passende
          Profile werden benachrichtigt.
        </p>
        {isAuthenticated ? (
          <Button
            variant="outline"
            onClick={() =>
              navigate("/requests/new", {
                state: {
                  queryText: query.trim(),
                  scope,
                  wantFrom,
                  wantTo,
                  wantPensum,
                },
              })
            }
          >
            Anfrage ausschreiben
          </Button>
        ) : (
          <>
            <Button variant="outline" disabled>
              <Lock className="size-3.5" /> Anfrage ausschreiben
            </Button>
            <p className="mt-2 text-xs text-muted-foreground">
              Anmeldung nötig, um eine Anfrage auszuschreiben.
            </p>
          </>
        )}
      </div>
    </div>
  )
}
