import { useMemo, useState } from "react"
import { useNavigate } from "react-router"
import { ChevronRight, Loader2, MapPin, Plus } from "lucide-react"

import { Button } from "@/components/ui/button"
import { RequestStatusBadge } from "@/features/requests/RequestBadges"
import {
  STATUS_LABEL,
  STATUS_ORDER,
  daysUntil,
  relativeTime,
  scopeLabel,
} from "@/features/requests/requestUtils"
import { useGetMyRequestsQuery } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { RequestStatus, RequestSummaryDto } from "@/types/api"

/** One row in "Meine Anfragen" — compact, whole row navigates to the detail. */
function MyRequestRow({ request }: { request: RequestSummaryDto }) {
  const navigate = useNavigate()
  const remaining = daysUntil(request.deadline)
  return (
    <button
      type="button"
      onClick={() => navigate(`/board/${request.id}`)}
      className="group flex w-full items-center gap-4 rounded-xl bg-card p-5 text-left ring-1 ring-foreground/10 transition-shadow hover:ring-foreground/20"
    >
      <div className="min-w-0 flex-1">
        <div className="truncate font-medium">
          {request.title?.trim() || request.queryText}
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1">
            <MapPin className="size-3" />
            {scopeLabel(request)}
          </span>
          {request.createdAt && (
            <span>erstellt {relativeTime(request.createdAt)}</span>
          )}
          {remaining != null &&
            remaining >= 0 &&
            request.status !== "closed" && (
              <span
                className={cn(
                  "font-medium",
                  remaining <= 3
                    ? "text-red-600 dark:text-red-400"
                    : "text-amber-700 dark:text-amber-400"
                )}
              >
                {remaining === 0 ? "Frist heute" : `noch ${remaining} Tage`}
              </span>
            )}
          {request.tags.length > 0 && (
            <span className="truncate">
              {request.tags.map((t) => t.label).join(" · ")}
            </span>
          )}
        </div>
      </div>
      <RequestStatusBadge status={request.status} />
      <ChevronRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
    </button>
  )
}

/**
 * "Meine Anfragen" (design: MyRequestsScreen) — every request the user has
 * posted, filterable by lifecycle stage. Managing a request (closing it)
 * happens on its detail page.
 */
export function MyRequestsView() {
  const navigate = useNavigate()
  const { data, isLoading, isError } = useGetMyRequestsQuery()
  const [filter, setFilter] = useState<"all" | RequestStatus>("all")

  const requests = useMemo(() => data ?? [], [data])
  const shown =
    filter === "all" ? requests : requests.filter((r) => r.status === filter)

  /** Counts per stage, shown in the filter chips. */
  const counts = useMemo(() => {
    const c = new Map<RequestStatus, number>()
    for (const r of requests) c.set(r.status, (c.get(r.status) ?? 0) + 1)
    return c
  }, [requests])

  return (
    <div className="mx-auto max-w-3xl py-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Meine Anfragen</h1>
          <p className="mt-1.5 max-w-xl text-[15px] leading-relaxed text-muted-foreground">
            Eine <strong>Anfrage</strong> ist eine Ausschreibung: Du
            beschreibst, was oder wen du suchst — passende Profile werden
            benachrichtigt und können sich bei dir melden. Hier verfolgst du den
            Status.
          </p>
        </div>
        <Button onClick={() => navigate("/requests/new")}>
          <Plus className="size-4" /> Neue Anfrage
        </Button>
      </div>

      <div
        role="group"
        aria-label="Nach Status filtern"
        className="mt-6 flex flex-wrap gap-1.5"
      >
        {(["all", ...STATUS_ORDER] as const).map((s) => {
          const on = filter === s
          const count = s === "all" ? requests.length : (counts.get(s) ?? 0)
          return (
            <button
              key={s}
              type="button"
              onClick={() => setFilter(s)}
              className={cn(
                "rounded-full border px-3 py-1 text-xs transition-colors",
                on
                  ? "border-primary bg-primary/10 text-primary"
                  : "text-muted-foreground hover:border-foreground/30 hover:text-foreground"
              )}
            >
              {s === "all" ? "Alle" : STATUS_LABEL[s]}
              {count > 0 && (
                <span className={cn("ml-1", !on && "opacity-60")}>{count}</span>
              )}
            </button>
          )
        })}
      </div>

      {isLoading && (
        <div className="flex flex-col items-center gap-4 py-24 text-center">
          <Loader2 className="size-6 animate-spin text-primary" />
          <p className="text-sm text-muted-foreground">
            Deine Anfragen werden geladen…
          </p>
        </div>
      )}

      {!isLoading && isError && (
        <p className="py-16 text-center text-sm text-muted-foreground">
          Deine Anfragen konnten nicht geladen werden. Bitte versuche es erneut.
        </p>
      )}

      {!isLoading && !isError && (
        <div className="mt-5 flex flex-col gap-3">
          {shown.map((r) => (
            <MyRequestRow key={r.id} request={r} />
          ))}

          {shown.length === 0 && requests.length > 0 && (
            <p className="py-12 text-center text-sm text-muted-foreground">
              Keine Anfragen in dieser Ansicht.
            </p>
          )}

          {requests.length === 0 && (
            <div className="mx-auto mt-8 max-w-xl rounded-xl bg-card p-8 text-center ring-1 ring-foreground/10">
              <h2 className="text-xl font-bold">Noch keine Anfragen</h2>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
                Starte mit der Suche — findet sie nichts Passendes, machst du
                daraus mit einem Klick eine Anfrage. Oder schreibe direkt eine
                aus.
              </p>
              <div className="mt-6 flex flex-wrap items-center justify-center gap-2">
                <Button variant="outline" onClick={() => navigate("/search")}>
                  Zur Suche
                </Button>
                <Button onClick={() => navigate("/requests/new")}>
                  Anfrage ausschreiben
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
