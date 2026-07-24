import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"

import { IngestionRunView } from "@/features/onboarding/IngestionRunView"

afterEach(cleanup)

describe("IngestionRunView", () => {
  it("offers an explicit way forward after all sources finish", () => {
    const onContinue = vi.fn()

    render(
      <IngestionRunView
        phase="done"
        jobs={[
          {
            id: "github",
            label: "GitHub",
            glyph: "GH",
            status: "ok",
          },
        ]}
        onRetry={vi.fn()}
        onContinue={onContinue}
        isContinuing={false}
        continueError={null}
      />
    )

    fireEvent.click(screen.getByRole("button", { name: "Weiter" }))

    expect(onContinue).toHaveBeenCalledOnce()
  })

  it("shows a recoverable error instead of getting stuck silently", () => {
    const view = render(
      <IngestionRunView
        phase="done"
        jobs={[]}
        onRetry={vi.fn()}
        onContinue={vi.fn()}
        isContinuing={false}
        continueError="Der Profilentwurf konnte nicht geladen werden."
      />
    )

    expect(view.getByRole("alert").textContent).toContain(
      "Profilentwurf konnte nicht geladen werden"
    )
    expect(view.getByRole("button", { name: "Weiter" })).toBeTruthy()
  })
})
