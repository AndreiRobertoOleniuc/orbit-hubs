import { cleanup, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { EvidenceSelectionStep } from "@/features/onboarding/EvidenceSelectionStep"
import { entityFixture } from "@/test/entityFixture"

const api = vi.hoisted(() => ({
  replace: vi.fn(),
  unwrap: vi.fn(),
}))

vi.mock("@/lib/api", () => ({
  useReplaceSourceSelectionMutation: () => [api.replace, { isLoading: false }],
}))

afterEach(cleanup)

describe("EvidenceSelectionStep", () => {
  beforeEach(() => {
    api.replace.mockReset()
    api.unwrap.mockReset()
    api.replace.mockReturnValue({ unwrap: api.unwrap })
  })

  it("requires a conscious work selection and saves it as one batch", async () => {
    const user = userEvent.setup()
    const entity = entityFixture({
      sources: [
        {
          id: 21,
          sourceType: "github",
          label: "orbit-ui",
          sourceUrl: "https://github.com/orbit/orbit-ui",
          fetchStatus: "ok",
          fetchedAt: null,
          active: false,
        },
      ],
    })
    api.unwrap.mockResolvedValue(entity)
    const onContinue = vi.fn()

    render(<EvidenceSelectionStep entity={entity} onContinue={onContinue} />)

    const continueButton = screen.getByRole("button", {
      name: /Auswahl speichern und weiter/i,
    })
    expect((continueButton as HTMLButtonElement).disabled).toBe(true)

    await user.click(screen.getByRole("checkbox", { name: /orbit-ui/i }))
    expect((continueButton as HTMLButtonElement).disabled).toBe(false)
    await user.click(continueButton)

    expect(api.replace).toHaveBeenCalledWith({
      entityId: entity.id,
      activeSourceIds: [21],
    })
    expect(onContinue).toHaveBeenCalledWith(entity)
  })

  it("lets the already-used website be opened, unlike a non-http source key", () => {
    const entity = entityFixture({
      sources: [
        {
          id: 31,
          sourceType: "personal_site",
          label: "ada.example.ch",
          sourceUrl: "personal_site:ada.example.ch",
          fetchStatus: "ok",
          fetchedAt: null,
          active: true,
        },
        {
          id: 32,
          sourceType: "zefix",
          label: "Muster AG",
          sourceUrl: "zefix:CHE-123.456.789",
          fetchStatus: "ok",
          fetchedAt: null,
          active: true,
        },
      ],
    })

    render(<EvidenceSelectionStep entity={entity} onContinue={vi.fn()} />)

    const link = screen.getByRole("link", { name: "Website öffnen" })
    expect(link.getAttribute("href")).toBe("https://ada.example.ch")
    expect(link.getAttribute("target")).toBe("_blank")
    expect(link.getAttribute("rel")).toBe("noopener noreferrer")
    expect(screen.queryByRole("link", { name: /Handelsregister/i })).toBeNull()
  })

  it("does not link a selectable source whose URL is not http(s)", () => {
    const entity = entityFixture({
      sources: [
        {
          id: 33,
          sourceType: "github",
          label: "orbit-ui",
          sourceUrl: "manual:33",
          fetchStatus: "ok",
          fetchedAt: null,
          active: true,
        },
      ],
    })

    render(<EvidenceSelectionStep entity={entity} onContinue={vi.fn()} />)

    expect(screen.getByRole("checkbox", { name: /orbit-ui/i })).toBeTruthy()
    expect(screen.queryByRole("link")).toBeNull()
  })
})
