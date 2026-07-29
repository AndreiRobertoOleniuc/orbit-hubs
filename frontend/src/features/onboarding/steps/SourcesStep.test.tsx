import { cleanup, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, describe, expect, it, vi } from "vitest"

import { SourcesStep } from "@/features/onboarding/steps/SourcesStep"
import type { SourceDraft } from "@/features/ingestion/types"

afterEach(cleanup)

function renderDemandPerson(sources: SourceDraft[] = []) {
  const onFinish = vi.fn()
  render(
    <SourcesStep
      supply={false}
      isOrg={false}
      firm={null}
      sources={sources}
      description=""
      onAddSource={vi.fn()}
      onRemoveSource={vi.fn()}
      onDescriptionChange={vi.fn()}
      onFinish={onFinish}
    />
  )
  return { onFinish }
}

describe("Demand SourcesStep", () => {
  it("accepts a regular CV as the required identity basis", async () => {
    const { onFinish } = renderDemandPerson([
      {
        kind: "cv",
        value: "cv.pdf",
        file: new File(["cv"], "cv.pdf", { type: "application/pdf" }),
      },
    ])

    await userEvent.click(
      screen.getByRole("button", { name: "Quellen einlesen" })
    )
    expect(onFinish).toHaveBeenCalledOnce()
  })

  it("keeps a website optional instead of accepting it as the basis", () => {
    renderDemandPerson([{ kind: "website", value: "ada.example.ch" }])

    expect(
      screen
        .getByRole("button", { name: "Quellen einlesen" })
        .hasAttribute("disabled")
    ).toBe(true)
    expect(
      screen.getByText(/Lebenslauf, LinkedIn-PDF oder Kurzbeschreibung/i)
    ).toBeTruthy()
  })
})
