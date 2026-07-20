import { useEffect, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import {
  Circle,
  CircleCheck,
  CircleX,
  Loader2,
  RotateCcw,
  Sparkles,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { Skeleton } from "@/components/ui/skeleton"
import { SourceGlyph } from "@/components/SourceGlyph"
import { OnboardingAvailabilityStep } from "@/features/onboarding/OnboardingAvailabilityStep"
import { ProfilePreview } from "@/features/onboarding/ProfilePreview"
import type { JobState, RunPhase } from "@/features/onboarding/useIngestionRun"
import { useGetMyEntitiesQuery, useUpdateEntityModeMutation } from "@/lib/api"

interface IngestionRunViewProps {
  phase: Exclude<RunPhase, "idle">
  jobs: JobState[]
  supply: boolean
  /** Re-run a single failed source. */
  onRetry: (id: string) => void
}

/** Live per-source ingestion progress, then the completion screen with profile preview. */
export function IngestionRunView({
  phase,
  jobs,
  supply,
  onRetry,
}: IngestionRunViewProps) {
  const navigate = useNavigate()
  const failed = jobs.filter((j) => j.status === "error")
  const finished = jobs.filter((j) =>
    ["ok", "skipped", "error"].includes(j.status)
  ).length
  const currentJob = jobs.find((j) => j.status === "running")
  const progress = jobs.length === 0 ? 0 : (finished / jobs.length) * 100
  const [elapsedSeconds, setElapsedSeconds] = useState(0)

  useEffect(() => {
    const startedAt = Date.now()
    const timer = window.setInterval(
      () => setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000)),
      1000
    )
    return () => window.clearInterval(timer)
  }, [])

  // After ingestion completes, fetch the entity to show the profile preview.
  // The ingestion mutations invalidate "MyEntities" so this refetches automatically.
  const { data: myEntities, isLoading: entitiesLoading } =
    useGetMyEntitiesQuery(undefined, { skip: phase !== "done" })

  const entity = myEntities?.[0]

  // Persist the onboarding fork: a demand onboarding sets the created entity to
  // demand mode (default is supply, so the supply path needs no call). Fires once.
  const [updateMode] = useUpdateEntityModeMutation()
  const modeSetRef = useRef(false)
  useEffect(() => {
    if (
      phase === "done" &&
      entity &&
      !supply &&
      entity.profileMode !== "demand" &&
      !modeSetRef.current
    ) {
      modeSetRef.current = true
      void updateMode({ entityId: entity.id, mode: "demand" })
    }
  }, [phase, entity, supply, updateMode])

  return (
    <div className="mx-auto max-w-xl py-10">
      {phase === "running" ? (
        <>
          <div className="flex items-start gap-4">
            <span className="relative flex size-11 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
              <Sparkles className="size-5" />
              <span className="absolute inset-0 animate-ping rounded-full border border-primary/20 motion-reduce:hidden" />
            </span>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">
                Wir bauen dein Profil…
              </h1>
              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                Orbit liest deine Quellen, erkennt Kompetenzen und erstellt ein
                Profil mit nachvollziehbaren Belegen.
              </p>
            </div>
          </div>

          <div
            className="mt-6 rounded-xl border bg-card p-4"
            aria-live="polite"
            aria-busy="true"
          >
            <div className="flex items-center justify-between gap-4 text-sm">
              <span className="font-semibold">
                {currentJob
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
              value={progress}
              className="mt-3 h-1.5"
              aria-label={`${finished} von ${jobs.length} Quellen verarbeitet`}
            />
            <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
              {currentJob
                ? elapsedSeconds >= 15
                  ? "Diese Quelle enthält mehr Informationen. Die Verarbeitung läuft weiter."
                  : "Du kannst dieses Fenster geöffnet lassen — die einzelnen Schritte erscheinen hier automatisch."
                : "Die Verarbeitung startet gleich."}
            </p>
          </div>
        </>
      ) : (
        <>
          <div className="mb-2 text-3xl">✓</div>
          <h1 className="text-2xl font-bold tracking-tight">
            {supply ? "Dein Profil ist live." : "Dein Profil ist angelegt."}
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            {supply
              ? "Es erscheint ab jetzt in Suchergebnissen — du musst nichts weiter tun."
              : "Der Empfänger sieht jetzt, wer anfragt."}
          </p>
        </>
      )}

      <div className="mt-6 space-y-2">
        {jobs.map((job) => (
          <div
            key={job.id}
            className={`flex items-center gap-3 rounded-lg border bg-card px-4 py-3 transition-colors ${
              job.status === "running" ? "border-primary/35 bg-primary/5" : ""
            }`}
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
              <Loader2 className="size-4 shrink-0 animate-spin text-primary" />
            )}
            {job.status === "pending" && (
              <Circle className="size-4 text-muted-foreground/40" />
            )}
            {job.status === "ok" && (
              <CircleCheck className="size-5 text-green-600" />
            )}
            {job.status === "skipped" && (
              <CircleCheck className="size-5 text-muted-foreground" />
            )}
            {job.status === "error" && (
              <div className="flex shrink-0 items-center gap-2">
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

      {/* Profile preview — appears after all sources are ingested */}
      {phase === "done" && (
        <>
          {entitiesLoading ? (
            <div
              className="mt-6 space-y-4"
              aria-label="Profilvorschau wird geladen"
            >
              <div className="rounded-xl border bg-card p-5">
                <div className="flex items-center gap-3">
                  <Skeleton className="size-12 rounded-xl" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-5 w-2/5" />
                    <Skeleton className="h-3.5 w-3/5" />
                  </div>
                </div>
                <Skeleton className="mt-5 h-20 w-full rounded-lg" />
                <div className="mt-4 flex gap-2">
                  <Skeleton className="h-6 w-20 rounded-full" />
                  <Skeleton className="h-6 w-24 rounded-full" />
                  <Skeleton className="h-6 w-16 rounded-full" />
                </div>
              </div>
              <p className="text-center text-sm text-muted-foreground">
                Die fertige Profilvorschau wird geladen…
              </p>
            </div>
          ) : entity ? (
            <div className="mt-6 space-y-6">
              {/* Supply-only: set availability + notification topics before
                  confirming (design step 4). Demand hides both per spec §5.1. */}
              {supply && <OnboardingAvailabilityStep entity={entity} />}
              <div>
                <p className="mb-3 text-sm text-muted-foreground">
                  Wir haben dein Profil aus deinen Quellen abgeleitet — prüfe es
                  und passe es an, falls nötig.
                </p>
                <ProfilePreview
                  entity={entity}
                  onConfirm={() => navigate("/profile")}
                />
              </div>
            </div>
          ) : (
            <div className="mt-8 flex flex-col items-center gap-3">
              {failed.length > 0 && (
                <p className="text-sm text-muted-foreground">
                  {failed.length} Quelle{failed.length > 1 ? "n" : ""} konnte
                  {failed.length > 1 ? "n" : ""} nicht gelesen werden — versuch
                  es oben erneut oder verlinke sie später in deinem Profil.
                </p>
              )}
              <Button
                className="w-full sm:w-auto sm:min-w-60"
                onClick={() => navigate("/profile")}
              >
                Profil ansehen
              </Button>
              <Button variant="ghost" onClick={() => navigate("/")}>
                Zur Startseite
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
