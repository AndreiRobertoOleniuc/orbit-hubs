import { useCallback, useRef, useState } from "react"

import {
  buildManualTextRequest,
  buildZefixRequest,
} from "@/features/ingestion/requests"
import { defForKind } from "@/features/ingestion/sourceDefs"
import type { SourceDraft } from "@/features/ingestion/types"
import { useSourceIngestion } from "@/features/ingestion/useSourceIngestion"
import type { FirmDraft } from "@/features/onboarding/types"

export type RunPhase = "idle" | "running" | "done"

export interface JobState {
  id: string
  label: string
  glyph: string
  status: "pending" | "running" | "ok" | "skipped" | "error"
  detail?: string
}

export interface WizardIngestInput {
  isOrg: boolean
  firm: FirmDraft | null
  sources: SourceDraft[]
  description: string
}

interface JobSpec {
  id: string
  label: string
  glyph: string
  exec: () => Promise<{ status: "ok" | "skipped" | "error"; detail?: string }>
}

/**
 * Turns the wizard state into a sequence of real ingestion calls and tracks
 * per-source progress for the run view. Order matters: sources with strong
 * external ids (Zefix, GitHub, ORCID) go first so later sources converge onto
 * the entity they create.
 */
export function useIngestionRun() {
  const { ingestRequest, ingestSourceKind } = useSourceIngestion()
  const [phase, setPhase] = useState<RunPhase>("idle")
  const [jobs, setJobs] = useState<JobState[]>([])
  const startedRef = useRef(false)

  const run = useCallback(
    async ({ isOrg, firm, sources, description }: WizardIngestInput) => {
      // Commit exactly once — ignore double clicks on the final button.
      if (startedRef.current) return
      startedRef.current = true

      const specs: JobSpec[] = []

      if (isOrg && firm) {
        specs.push({
          id: "zefix",
          label: `Handelsregister · ${firm.name}`,
          glyph: "HR",
          exec: () => ingestRequest(buildZefixRequest(firm)),
        })
      }
      for (const source of sources) {
        const def = defForKind(source.kind, isOrg)
        specs.push({
          id: source.kind,
          label: `${def.title} · ${source.value}`,
          glyph: def.glyph,
          exec: () =>
            ingestSourceKind(source.kind, source.value, source.file, {
              org: isOrg,
            }),
        })
      }
      if (description.trim()) {
        specs.push({
          id: "manual",
          label: "Eigene Beschreibung",
          glyph: "✎",
          exec: () => ingestRequest(buildManualTextRequest(description)),
        })
      }

      setJobs(
        specs.map(({ id, label, glyph }) => ({
          id,
          label,
          glyph,
          status: "pending",
        }))
      )
      setPhase("running")

      for (const spec of specs) {
        setJobs((prev) =>
          prev.map((j) => (j.id === spec.id ? { ...j, status: "running" } : j))
        )
        const outcome = await spec.exec()
        setJobs((prev) =>
          prev.map((j) =>
            j.id === spec.id
              ? { ...j, status: outcome.status, detail: outcome.detail }
              : j
          )
        )
      }
      setPhase("done")
    },
    [ingestRequest, ingestSourceKind]
  )

  return { phase, jobs, run }
}
