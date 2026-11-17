import { useCallback, useRef, useState } from "react"

import {
  buildManualTextRequest,
  buildZefixRequest,
  jobOutcome,
} from "@/features/ingestion/requests"
import { defForKind } from "@/features/ingestion/sourceDefs"
import type { SourceDraft } from "@/features/ingestion/types"
import type { IngestOutcome } from "@/features/ingestion/types"
import {
  type Submission,
  useSourceIngestion,
} from "@/features/ingestion/useSourceIngestion"
import type { FirmDraft } from "@/features/onboarding/types"
import type { IngestionJobDto, ProfileMode } from "@/types/api"

export type RunPhase = "idle" | "running" | "done"

export interface JobState {
  id: string
  label: string
  glyph: string
  status: "pending" | "running" | "ok" | "skipped" | "error"
  detail?: string
  retryable?: boolean
}

export interface WizardIngestInput {
  isOrg: boolean
  firm: FirmDraft | null
  sources: SourceDraft[]
  description: string
  displayName?: string
  profileMode: ProfileMode
}

interface JobSpec {
  id: string
  label: string
  glyph: string
  submit: () => Promise<Submission>
}

/** Queued → pending, running → running; finished jobs get their outcome. */
function liveStatus(job: IngestionJobDto): JobState["status"] | null {
  if (job.status === "queued") return "pending"
  if (job.status === "running") return "running"
  return null
}

/**
 * Turns the wizard state into queued ingestion jobs and tracks per-source
 * progress for the run view. Every source is submitted first, then the run
 * waits once: the server enriches the profile after the last source instead
 * of after each. Order matters: sources with strong external ids (Zefix,
 * GitHub, ORCID) are submitted first, and the server runs one user's jobs in
 * submission order, so later sources converge onto the entity they create.
 */
export function useIngestionRun() {
  const { submitRequest, submitSourceKind, awaitOutcomes } =
    useSourceIngestion()
  const [phase, setPhase] = useState<RunPhase>("idle")
  const [jobs, setJobs] = useState<JobState[]>([])
  const startedRef = useRef(false)
  // Kept so a failed source can be re-run without rebuilding the wizard state.
  const specsRef = useRef<JobSpec[]>([])

  const setJob = useCallback((id: string, patch: Partial<JobState>) => {
    setJobs((prev) => prev.map((j) => (j.id === id ? { ...j, ...patch } : j)))
  }, [])

  /** Submit the specs in order, then follow their jobs until all settled. */
  const runSpecs = useCallback(
    async (specs: JobSpec[]) => {
      const specByJob = new Map<number, string>()
      for (const spec of specs) {
        const submission = await spec.submit()
        if ("outcome" in submission) {
          setJob(spec.id, outcomePatch(submission.outcome))
        } else {
          specByJob.set(submission.jobId, spec.id)
        }
      }
      if (specByJob.size === 0) return

      const outcomes = await awaitOutcomes([...specByJob.keys()], (polled) => {
        for (const job of polled) {
          const specId = specByJob.get(job.id)
          if (!specId) continue
          const status = liveStatus(job)
          const finished = jobOutcome(job)
          if (status) setJob(specId, { status })
          else if (finished) setJob(specId, outcomePatch(finished))
        }
      })
      for (const [jobId, outcome] of outcomes) {
        const specId = specByJob.get(jobId)
        if (specId) setJob(specId, outcomePatch(outcome))
      }
    },
    [awaitOutcomes, setJob]
  )

  const run = useCallback(
    async ({
      isOrg,
      firm,
      sources,
      description,
      displayName,
      profileMode,
    }: WizardIngestInput) => {
      // Commit exactly once — ignore double clicks on the final button.
      if (startedRef.current) return
      startedRef.current = true

      const specs: JobSpec[] = []

      if (isOrg && firm) {
        specs.push({
          id: "zefix",
          label: `Handelsregister · ${firm.name}`,
          glyph: "HR",
          submit: () =>
            submitRequest({
              ...buildZefixRequest(firm),
              onboardingDraft: true,
              profileMode,
            }),
        })
      }
      for (const source of sources) {
        const def = defForKind(source.kind, isOrg)
        specs.push({
          id: source.kind,
          label: `${def.title} · ${source.value}`,
          glyph: def.glyph,
          submit: () =>
            submitSourceKind(source.kind, source.value, source.file, {
              org: isOrg,
              onboardingDraft: true,
              profileMode,
              displayName,
            }),
        })
      }
      if (description.trim()) {
        specs.push({
          id: "manual",
          label: "Eigene Beschreibung",
          glyph: "✎",
          submit: () =>
            submitRequest({
              ...buildManualTextRequest(description, displayName),
              onboardingDraft: true,
              profileMode,
            }),
        })
      }

      specsRef.current = specs
      setJobs(
        specs.map(({ id, label, glyph }) => ({
          id,
          label,
          glyph,
          status: "pending",
        }))
      )
      setPhase("running")

      await runSpecs(specs)
      setPhase("done")
    },
    [submitRequest, submitSourceKind, runSpecs]
  )

  /** Re-run a single source that failed, updating just that job's status. */
  const retry = useCallback(
    async (id: string) => {
      const spec = specsRef.current.find((s) => s.id === id)
      if (!spec) return
      setJob(id, { status: "pending", detail: undefined, retryable: undefined })
      await runSpecs([spec])
    },
    [runSpecs, setJob]
  )

  return { phase, jobs, run, retry }
}

function outcomePatch(outcome: IngestOutcome): Partial<JobState> {
  return {
    status: outcome.status,
    detail: outcome.detail,
    retryable: outcome.retryable,
  }
}
