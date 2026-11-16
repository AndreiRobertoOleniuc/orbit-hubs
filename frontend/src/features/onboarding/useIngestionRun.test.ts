import { act, renderHook } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { useIngestionRun } from "@/features/onboarding/useIngestionRun"
import type { IngestionJobDto } from "@/types/api"

const calls: string[] = []

const job = (
  id: number,
  status: IngestionJobDto["status"]
): IngestionJobDto => ({
  id,
  kind: "source",
  sourceType: "github",
  status,
  attempts: 1,
  entityId: 7,
  result: null,
  error: null,
  createdAt: null,
  finishedAt: null,
})

vi.mock("@/features/ingestion/useSourceIngestion", () => ({
  useSourceIngestion: () => ({
    submitRequest: vi.fn(async () => {
      calls.push("submit:manual")
      return { jobId: 3 }
    }),
    submitSourceKind: vi.fn(async (kind: string) => {
      calls.push(`submit:${kind}`)
      return kind === "linkedin"
        ? { outcome: { status: "error", detail: "Die PDF ist zu gross." } }
        : { jobId: kind === "github" ? 1 : 2 }
    }),
    awaitOutcomes: vi.fn(
      async (ids: number[], onProgress?: (jobs: IngestionJobDto[]) => void) => {
        calls.push(`await:${ids.join(",")}`)
        onProgress?.([job(1, "running"), job(2, "queued")])
        return new Map([
          [1, { status: "ok", detail: "4 Abschnitte ausgewertet" }],
          [2, { status: "skipped", detail: "bereits aktuell" }],
          [3, { status: "ok", detail: "1 Abschnitte ausgewertet" }],
        ])
      }
    ),
  }),
}))

describe("useIngestionRun", () => {
  it("submits every source before waiting once for all of their jobs", async () => {
    const { result } = renderHook(() => useIngestionRun())

    await act(() =>
      result.current.run({
        isOrg: false,
        firm: null,
        sources: [
          { kind: "github", value: "ada" },
          { kind: "orcid", value: "0000-0001" },
          { kind: "linkedin", value: "cv.pdf" },
        ],
        description: "Baut Roboter",
        profileMode: "supply",
      })
    )

    expect(calls).toEqual([
      "submit:github",
      "submit:orcid",
      "submit:linkedin",
      "submit:manual",
      "await:1,2,3", // the rejected upload is not polled
    ])
    expect(result.current.phase).toBe("done")
    expect(result.current.jobs.map((j) => [j.id, j.status, j.detail])).toEqual([
      ["github", "ok", "4 Abschnitte ausgewertet"],
      ["orcid", "skipped", "bereits aktuell"],
      ["linkedin", "error", "Die PDF ist zu gross."],
      ["manual", "ok", "1 Abschnitte ausgewertet"],
    ])
  })
})
