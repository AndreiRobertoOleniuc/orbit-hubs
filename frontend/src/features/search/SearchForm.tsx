import { useNavigate } from "react-router-dom"
import { Lock, MapPin } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { useAuth } from "@/features/auth/useAuth"
import { HubRegionSelect } from "@/features/search/HubRegionSelect"
import { scopeHint } from "@/features/search/scopeUtils"
import { useGetHubsQuery, useGetRegionsQuery } from "@/lib/api"
import { todayIso } from "@/lib/utils"

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
  onChange: (patch: Partial<SearchFormState>) => void
  onSearch: () => void
}

/** The centered search entry (design: "Wen suchst du?") — query + the three filters. */
export function SearchForm({
  query,
  scope,
  wantFrom,
  wantTo,
  wantPensum,
  onChange,
  onSearch,
}: SearchFormProps) {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const { data: regions = [] } = useGetRegionsQuery()
  const { data: hubs = [] } = useGetHubsQuery()
  const hint = scopeHint(scope, regions, hubs)

  return (
    <div className="mx-auto max-w-2xl py-10">
      <h1 className="mb-3 text-center text-3xl font-bold tracking-tight">
        Wen suchst du?
      </h1>
      <p className="mx-auto mb-6 max-w-md text-center text-[15px] leading-relaxed text-muted-foreground">
        Beschreibe dein Anliegen in eigenen Worten. Wir gleichen es mit belegten
        Kompetenzen aus öffentlichen Quellen ab — <strong>Hub / Region</strong>{" "}
        steuert, wo wir zuerst suchen.
      </p>

      <Textarea
        rows={3}
        autoFocus
        className="text-base"
        placeholder="Beschreibe, was du brauchst, z. B. «Embedded-Firmware-Entwickler für 2 Monate»"
        value={query}
        onChange={(e) => onChange({ query: e.target.value })}
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) onSearch()
        }}
      />

      <div className="mt-4 flex flex-wrap items-end gap-4">
        <div className="flex min-w-52 flex-1 flex-col gap-1.5">
          <span className={CAP}>Hub / Region</span>
          <HubRegionSelect
            value={scope}
            onChange={(v) => onChange({ scope: v })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <span className={CAP}>Zeitraum (optional)</span>
          <div className="flex items-center gap-2">
            <input
              type="date"
              aria-label="Verfügbar von"
              className={FIELD}
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
              className={FIELD}
              min={wantFrom || todayIso()}
              value={wantTo}
              onChange={(e) => onChange({ wantTo: e.target.value })}
            />
          </div>
        </div>
        <div className="flex flex-col gap-1.5">
          <span className={CAP}>Umfang ca. %</span>
          <input
            type="number"
            min={0}
            max={100}
            step={10}
            inputMode="numeric"
            aria-label="Umfang in Prozent"
            placeholder="z. B. 50"
            className={`${FIELD} w-24`}
            value={wantPensum}
            onChange={(e) => onChange({ wantPensum: e.target.value })}
          />
        </div>
      </div>

      <p className="mt-2.5 text-[12.5px] text-muted-foreground">
        Leer lassen = keine Zeit-Einschränkung. Beeinflusst die Reihenfolge,
        filtert nicht hart.
      </p>

      <Button
        className="mt-4 w-full"
        disabled={!query.trim()}
        onClick={onSearch}
      >
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
