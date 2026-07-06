import { useNavigate } from "react-router-dom"
import { CircleCheck, CircleX, Loader2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { SourceGlyph } from "@/features/ingestion/SourceCard"
import type { JobState, RunPhase } from "@/features/onboarding/useIngestionRun"

interface IngestionRunViewProps {
  phase: Exclude<RunPhase, "idle">
  jobs: JobState[]
  supply: boolean
}

/** Live per-source ingestion progress, then the completion screen. */
export function IngestionRunView({
  phase,
  jobs,
  supply,
}: IngestionRunViewProps) {
  const navigate = useNavigate()
  const failed = jobs.filter((j) => j.status === "error")

  return (
    <div className="mx-auto max-w-xl py-10">
      {phase === "running" ? (
        <>
          <h1 className="text-2xl font-bold tracking-tight">
            Wir bauen dein Profil…
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Das kann einen Moment dauern — wir lesen deine Quellen aus und
            werten sie aus.
          </p>
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
              <Loader2 className="size-4 animate-spin text-muted-foreground" />
            )}
            {job.status === "ok" && (
              <CircleCheck className="size-5 text-green-600" />
            )}
            {job.status === "skipped" && (
              <CircleCheck className="size-5 text-muted-foreground" />
            )}
            {job.status === "error" && (
              <CircleX className="size-5 text-destructive" />
            )}
          </div>
        ))}
      </div>

      {phase === "done" && (
        <div className="mt-8 flex flex-col items-center gap-3">
          {failed.length > 0 && (
            <p className="text-sm text-muted-foreground">
              {failed.length} Quelle{failed.length > 1 ? "n" : ""} konnte
              {failed.length > 1 ? "n" : ""} nicht gelesen werden — du kannst
              sie später in deinem Profil erneut verlinken.
            </p>
          )}
          <Button className="min-w-60" onClick={() => navigate("/profile")}>
            Profil ansehen
          </Button>
          <Button variant="ghost" onClick={() => navigate("/")}>
            Zur Startseite
          </Button>
        </div>
      )}
    </div>
  )
}
