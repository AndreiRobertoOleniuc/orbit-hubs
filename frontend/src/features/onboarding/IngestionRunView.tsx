import {
  Circle,
  CircleCheck,
  CircleX,
  Loader2,
  RotateCcw,
  Sparkles,
} from "lucide-react"
import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { SourceGlyph } from "@/components/SourceGlyph"
import type { JobState, RunPhase } from "@/features/onboarding/useIngestionRun"

interface IngestionRunViewProps {
  phase: Exclude<RunPhase, "idle">
  jobs: JobState[]
  onRetry: (id: string) => void
  onContinue: () => void
  isContinuing: boolean
  continueError: string | null
}

/** Focused processing state. Profile editing happens on later wizard steps. */
export function IngestionRunView({
  phase,
  jobs,
  onRetry,
  onContinue,
  isContinuing,
  continueError,
}: IngestionRunViewProps) {
  const finished = jobs.filter((job) =>
    ["ok", "skipped", "error"].includes(job.status)
  ).length
  const currentJob = jobs.find((job) => job.status === "running")
  const progress = jobs.length === 0 ? 0 : (finished / jobs.length) * 100
  const [elapsedSeconds, setElapsedSeconds] = useState(0)

  useEffect(() => {
    if (phase !== "running") return
    const startedAt = Date.now()
    const timer = window.setInterval(
      () => setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000)),
      1000
    )
    return () => window.clearInterval(timer)
  }, [phase])

  return (
    <div>
      <div className="flex items-start gap-4">
        <span className="relative flex size-11 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
          {phase === "running" ? (
            <Sparkles className="size-5" />
          ) : (
            <CircleCheck className="size-5" />
          )}
          {phase === "running" && (
            <span className="absolute inset-0 animate-ping rounded-full border border-primary/20 motion-reduce:hidden" />
          )}
        </span>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            {phase === "running"
              ? "Wir lesen deine Quellen…"
              : "Quellen wurden verarbeitet"}
          </h1>
          <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
            {phase === "running"
              ? "Orbit sammelt zunächst nur die Nachweise. Du entscheidest im nächsten Schritt, welche davon verwendet werden."
              : "Dein geschützter Profilentwurf wird vorbereitet."}
          </p>
        </div>
      </div>

      <div
        className="mt-6 rounded-xl border bg-card p-4"
        aria-live="polite"
        aria-busy={phase === "running"}
      >
        <div className="flex items-center justify-between gap-4 text-sm">
          <span className="font-semibold">
            {phase === "done"
              ? "Profilentwurf wird geladen"
              : currentJob
                ? elapsedSeconds >= 8
                  ? "Inhalte werden ausgewertet"
                  : "Quelle wird eingelesen"
                : "Nächste Quelle wird vorbereitet"}
          </span>
          <span className="shrink-0 text-muted-foreground tabular-nums">
            {finished} von {jobs.length}
          </span>
        </div>
        <Progress
          value={phase === "done" ? 100 : progress}
          className="mt-3 h-1.5"
          aria-label={`${finished} von ${jobs.length} Quellen verarbeitet`}
        />
      </div>

      <div className="mt-4 space-y-2">
        {jobs.map((job) => (
          <div
            key={job.id}
            className="flex items-center gap-3 rounded-lg border bg-card px-4 py-3"
          >
            <SourceGlyph glyph={job.glyph} />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">
                {job.label}
              </span>
              {job.detail && (
                <span className="block text-xs text-muted-foreground">
                  {job.detail}
                </span>
              )}
            </span>
            {job.status === "running" && (
              <Loader2 className="size-4 animate-spin text-primary" />
            )}
            {job.status === "pending" && (
              <Circle className="size-4 text-muted-foreground/40" />
            )}
            {(job.status === "ok" || job.status === "skipped") && (
              <CircleCheck className="size-5 text-green-600" />
            )}
            {job.status === "error" && (
              <div className="flex items-center gap-2">
                <CircleX className="size-5 text-destructive" />
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => onRetry(job.id)}
                >
                  <RotateCcw className="size-3.5" />
                  Erneut versuchen
                </Button>
              </div>
            )}
          </div>
        ))}
      </div>

      {phase === "done" && (
        <div className="mt-6">
          {continueError && (
            <p className="mb-3 text-sm text-destructive" role="alert">
              {continueError}
            </p>
          )}
          <Button
            className="w-full"
            disabled={isContinuing}
            onClick={onContinue}
          >
            {isContinuing && <Loader2 className="size-4 animate-spin" />}
            {isContinuing ? "Profilentwurf wird geladen" : "Weiter"}
          </Button>
        </div>
      )}
    </div>
  )
}
