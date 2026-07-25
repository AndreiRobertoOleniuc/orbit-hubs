import { useEffect, useState } from "react"
import { Check, Database, FileSearch, Sparkles } from "lucide-react"

import { Skeleton } from "@/components/ui/skeleton"
import { cn } from "@/lib/utils"

const SEARCH_STAGES = [
  {
    label: "Profile werden durchsucht",
    detail: "Wir gleichen dein Anliegen mit belegten Kompetenzen ab.",
    icon: Database,
  },
  {
    label: "Die besten Treffer werden sortiert",
    detail: "Verfügbarkeit, Region und fachliche Nähe fliessen jetzt ein.",
    icon: FileSearch,
  },
  {
    label: "Belege werden geprüft",
    detail:
      "Orbit formuliert für jeden Treffer eine nachvollziehbare Begründung.",
    icon: Sparkles,
  },
] as const

/** Mirrors the shape of a SearchResultCard entry, so nothing jumps on arrival. */
function SearchResultSkeleton({ compact = false }: { compact?: boolean }) {
  return (
    <li
      aria-hidden="true"
      className={cn(
        "flex gap-4 rounded-xl bg-card p-5 ring-1 ring-foreground/10 sm:gap-5 sm:p-6",
        compact && "opacity-60"
      )}
    >
      <Skeleton className="mt-1 hidden h-3 w-6 shrink-0 sm:block" />
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-6">
          <div className="min-w-0 flex-1 space-y-2">
            <Skeleton className="h-5 w-2/5" />
            <Skeleton className="h-3 w-1/4" />
          </div>
          <Skeleton className="h-7 w-32 shrink-0" />
        </div>
        <div className="mt-4 space-y-2">
          <Skeleton className="h-3.5 w-full" />
          <Skeleton className="h-3.5 w-11/12" />
          <Skeleton className="h-3.5 w-3/5" />
        </div>
        <Skeleton className="mt-4 h-3 w-1/3" />
      </div>
    </li>
  )
}

/** Layout-stable search feedback with honest, time-based pipeline context. */
export function SearchLoadingState() {
  const [elapsedSeconds, setElapsedSeconds] = useState(0)

  useEffect(() => {
    const startedAt = Date.now()
    const timer = window.setInterval(
      () => setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000)),
      1000
    )
    return () => window.clearInterval(timer)
  }, [])

  const stageIndex = elapsedSeconds >= 8 ? 2 : elapsedSeconds >= 3 ? 1 : 0
  const activeStage = SEARCH_STAGES[stageIndex]

  return (
    <section className="pt-6" aria-live="polite" aria-busy="true">
      <div className="mb-5 overflow-hidden rounded-xl border bg-card p-4 sm:p-5">
        <div className="flex items-start gap-3">
          <span className="relative mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
            <activeStage.icon className="size-4" />
            <span className="absolute inset-0 animate-ping rounded-full border border-primary/25 motion-reduce:hidden" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center justify-between gap-4">
              <p className="text-sm font-semibold">{activeStage.label}</p>
              <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
                {elapsedSeconds < 2 ? "gestartet" : `${elapsedSeconds} s`}
              </span>
            </div>
            <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
              {activeStage.detail}
            </p>
          </div>
        </div>

        <div className="mt-4 grid grid-cols-3 gap-1" aria-hidden="true">
          {SEARCH_STAGES.map((stage, index) => (
            <div key={stage.label} className="space-y-1.5">
              <div
                className={cn(
                  "h-1 overflow-hidden rounded-full bg-muted",
                  index < stageIndex && "bg-primary/35",
                  index === stageIndex && "bg-primary/15"
                )}
              >
                {index === stageIndex && (
                  <span className="block h-full w-1/2 animate-[search-sweep_1.4s_ease-in-out_infinite] rounded-full bg-primary motion-reduce:w-full motion-reduce:animate-none" />
                )}
              </div>
              <span className="hidden items-center gap-1 text-[11px] text-muted-foreground sm:flex">
                {index < stageIndex && (
                  <Check className="size-3 text-primary" />
                )}
                {index === 0
                  ? "Abgleichen"
                  : index === 1
                    ? "Sortieren"
                    : "Begründen"}
              </span>
            </div>
          ))}
        </div>
      </div>

      <p className="mb-1 text-[11px] tracking-wide text-muted-foreground">
        Passende Profile werden vorbereitet
      </p>
      <ul className="mt-3 flex flex-col gap-3">
        <SearchResultSkeleton />
        <SearchResultSkeleton compact />
      </ul>
    </section>
  )
}
