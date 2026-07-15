import { useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { Loader2, Search } from "lucide-react"

import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"
import { RequestCard } from "@/features/requests/RequestCard"
import { daysUntil, scopeLabel } from "@/features/requests/requestUtils"
import { useGetBoardQuery } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { RequestSummaryDto, RequestUrgency } from "@/types/api"

const FIELD =
  "h-9 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"

const URGENCY_RANK: Record<RequestUrgency, number> = {
  urgent: 0,
  soon: 1,
  flexible: 2,
}

type SortKey = "newest" | "urgency" | "deadline"

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: "newest", label: "Neueste" },
  { value: "urgency", label: "Dringlichkeit" },
  { value: "deadline", label: "Frist zuerst" },
]

function sortRequests(list: RequestSummaryDto[], sort: SortKey) {
  const byNewest = (a: RequestSummaryDto, b: RequestSummaryDto) =>
    (b.createdAt ?? "").localeCompare(a.createdAt ?? "")
  const sorted = [...list]
  if (sort === "urgency") {
    sorted.sort(
      (a, b) =>
        URGENCY_RANK[a.urgency] - URGENCY_RANK[b.urgency] || byNewest(a, b)
    )
  } else if (sort === "deadline") {
    sorted.sort((a, b) => {
      const da = daysUntil(a.deadline) ?? Infinity
      const db = daysUntil(b.deadline) ?? Infinity
      return da - db || byNewest(a, b)
    })
  } else {
    sorted.sort(byNewest)
  }
  return sorted
}

/**
 * The public Anfrage-Board (design: BoardScreen) — every open request, with
 * client-side text search, scope/urgency/tag filters, and sorting. Demand
 * flows in via "Anfrage ausschreiben"; supply reads and reacts.
 */
export function BoardView() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const { data, isLoading, isError } = useGetBoardQuery()

  const [text, setText] = useState("")
  const [urgency, setUrgency] = useState<"all" | RequestUrgency>("all")
  const [scope, setScope] = useState("all")
  const [activeTag, setActiveTag] = useState<string | null>(null)
  const [sort, setSort] = useState<SortKey>("newest")

  const requests = useMemo(() => data ?? [], [data])

  /** Tags actually present on the board, most frequent first. */
  const boardTags = useMemo(() => {
    const counts = new Map<string, number>()
    for (const r of requests)
      for (const t of r.tags)
        counts.set(t.label, (counts.get(t.label) ?? 0) + 1)
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 12)
      .map(([label]) => label)
  }, [requests])

  /** Scopes present on the board (labels), for the scope filter. */
  const boardScopes = useMemo(
    () => [...new Set(requests.map((r) => scopeLabel(r)))].sort(),
    [requests]
  )

  const shown = useMemo(() => {
    let list = requests
    if (activeTag)
      list = list.filter((r) => r.tags.some((t) => t.label === activeTag))
    if (urgency !== "all") list = list.filter((r) => r.urgency === urgency)
    if (scope !== "all") list = list.filter((r) => scopeLabel(r) === scope)
    const terms = text.trim().toLowerCase().split(/\s+/).filter(Boolean)
    if (terms.length > 0) {
      list = list.filter((r) => {
        const hay = [
          r.title,
          r.description,
          r.queryText,
          r.authorName,
          r.authorOrgName,
          ...r.tags.map((t) => t.label),
        ]
          .filter(Boolean)
          .join(" ")
          .toLowerCase()
        return terms.every((t) => hay.includes(t))
      })
    }
    return sortRequests(list, sort)
  }, [requests, activeTag, urgency, scope, text, sort])

  const hasFilters =
    text.trim() !== "" || urgency !== "all" || scope !== "all" || activeTag

  return (
    <div className="mx-auto max-w-3xl py-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">
          Offene Anfragen aus der Region
        </h1>
        <p className="mt-1.5 max-w-xl text-[15px] leading-relaxed text-muted-foreground">
          Antworte direkt oder stöbere nach Themen. Angebote werden hier nicht
          ausgeschrieben — dein Profil wird gefunden.
        </p>
      </div>

      {/* Search + filters */}
      <div className="mt-6 flex flex-wrap items-center gap-2.5">
        <div className="relative min-w-56 flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            aria-label="Anfragen durchsuchen"
            placeholder="Anfragen durchsuchen…"
            className={cn(FIELD, "w-full pl-8")}
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
        </div>
        <select
          aria-label="Nach Hub / Region filtern"
          className={FIELD}
          value={scope}
          onChange={(e) => setScope(e.target.value)}
        >
          <option value="all">Hub / Region: Alle</option>
          {boardScopes.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <select
          aria-label="Nach Dringlichkeit filtern"
          className={FIELD}
          value={urgency}
          onChange={(e) => setUrgency(e.target.value as "all" | RequestUrgency)}
        >
          <option value="all">Dringlichkeit: Alle</option>
          <option value="urgent">Dringend</option>
          <option value="soon">Bald</option>
          <option value="flexible">Flexibel</option>
        </select>
        <select
          aria-label="Sortierung"
          className={FIELD}
          value={sort}
          onChange={(e) => setSort(e.target.value as SortKey)}
        >
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              Sortieren: {o.label}
            </option>
          ))}
        </select>
      </div>

      {boardTags.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {boardTags.map((tag) => {
            const on = activeTag === tag
            return (
              <button
                key={tag}
                type="button"
                onClick={() => setActiveTag(on ? null : tag)}
                className={cn(
                  "rounded-full border px-3 py-1 text-xs transition-colors",
                  on
                    ? "border-primary bg-primary/10 text-primary"
                    : "text-muted-foreground hover:border-foreground/30 hover:text-foreground"
                )}
              >
                {tag}
              </button>
            )
          })}
        </div>
      )}

      {/* Results */}
      {isLoading && (
        <div className="flex flex-col items-center gap-4 py-24 text-center">
          <Loader2 className="size-6 animate-spin text-primary" />
          <p className="text-sm text-muted-foreground">
            Offene Anfragen werden geladen…
          </p>
        </div>
      )}

      {!isLoading && isError && (
        <p className="py-16 text-center text-sm text-muted-foreground">
          Das Board konnte nicht geladen werden. Bitte versuche es erneut.
        </p>
      )}

      {!isLoading && !isError && (
        <div className="mt-6 flex flex-col gap-4">
          {shown.map((r) => (
            <RequestCard key={r.id} request={r} />
          ))}

          {shown.length === 0 && requests.length > 0 && (
            <p className="py-16 text-center text-sm text-muted-foreground">
              Keine offenen Anfragen
              {hasFilters ? " für die gewählten Filter" : ""}.
            </p>
          )}

          {requests.length === 0 && (
            <div className="mx-auto mt-8 max-w-xl rounded-xl bg-card p-8 text-center ring-1 ring-foreground/10">
              <h2 className="text-xl font-bold">Noch keine offenen Anfragen</h2>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
                Sobald jemand auf der Suchseite ein Anliegen ausschreibt,
                erscheint es hier. Abonniere Themen in deinem Profil, um bei
                passenden neuen Anfragen benachrichtigt zu werden.
              </p>
              {isAuthenticated && (
                <Button
                  variant="outline"
                  className="mt-6"
                  onClick={() => navigate("/profile")}
                >
                  Themen abonnieren
                </Button>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
