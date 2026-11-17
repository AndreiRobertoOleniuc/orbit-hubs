import { describe, expect, it } from "vitest"

import { jobOutcome } from "@/features/ingestion/requests"
import type { IngestionJobDto } from "@/types/api"

const base: IngestionJobDto = {
  id: 1,
  kind: "source",
  sourceType: "github",
  status: "queued",
  attempts: 0,
  entityId: null,
  result: null,
  error: null,
  message: null,
  createdAt: null,
  finishedAt: null,
}

const summary = (ingested: number, skipped: number) => ({
  sourceType: "github",
  sources: ingested + skipped,
  ingested,
  skipped,
  errors: 0,
  totalChunks: ingested * 3,
  results: [],
})

describe("jobOutcome", () => {
  it("has no outcome while a job is queued or running", () => {
    expect(jobOutcome(base)).toBeNull()
    expect(jobOutcome({ ...base, status: "running" })).toBeNull()
  })

  it("reads a succeeded job's summary", () => {
    expect(
      jobOutcome({ ...base, status: "succeeded", result: summary(2, 0) })
    ).toEqual({ status: "ok", detail: "6 Abschnitte ausgewertet" })
    expect(
      jobOutcome({ ...base, status: "succeeded", result: summary(0, 3) })
    ).toEqual({ status: "skipped", detail: "bereits aktuell" })
    expect(
      jobOutcome({ ...base, status: "succeeded", result: summary(0, 0) })
    ).toEqual({ status: "error", detail: "Quelle konnte nicht gelesen werden" })
  })

  it("reports a failed job without exposing the server's error text", () => {
    expect(
      jobOutcome({
        ...base,
        status: "failed",
        error: "IllegalStateException: x",
      })
    ).toEqual({ status: "error", detail: "Quelle konnte nicht gelesen werden" })
  })

  it("shows why the server refused a source, without offering a retry", () => {
    const message =
      "Diese Quelle gehört bereits zu einem Profil eines anderen Orbit-Kontos."
    expect(
      jobOutcome({
        ...base,
        status: "failed",
        error: `IngestionRejectedException: ${message}`,
        message,
      })
    ).toEqual({ status: "error", detail: message, retryable: false })
  })
})
