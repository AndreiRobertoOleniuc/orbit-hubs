import { cleanup, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"

import { SearchResultCard } from "@/features/search/SearchResultCard"
import { entityFixture } from "@/test/entityFixture"
import type { SearchResultDto } from "@/types/api"

vi.mock("@/lib/api", () => ({
  useGetContactedEntityIdsQuery: () => ({ data: [] }),
  useGetContactGatingQuery: () => ({
    data: { allowed: true, missing: [] },
    isLoading: false,
  }),
  useGetMyEntitiesQuery: () => ({
    data: [{ id: 7, displayName: "Grace Hopper", orgName: "FHNW" }],
  }),
  useCreateContactRequestMutation: () => [
    vi.fn(() => ({ unwrap: () => Promise.resolve({}) })),
    { isLoading: false, isError: false },
  ],
}))

vi.mock("@/features/auth/useAuth", () => ({
  useAuth: () => ({ isAuthenticated: true }),
}))

afterEach(cleanup)

const RATIONALE =
  "Ada hat mehrfach Firmware für sicherheitskritische Steuerungen auditiert."

function resultFixture(): SearchResultDto {
  return {
    entity: entityFixture({ claimed: true }),
    score: 0.9,
    rationale: RATIONALE,
    citations: [],
    distance: null,
  }
}

describe("SearchResultCard → ContactModal", () => {
  it("hands the search to the dialog without repeating the rationale", async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <SearchResultCard
          result={resultFixture()}
          rank={0}
          query="embedded firmware security review"
          selected={false}
          onSelect={() => {}}
        />
      </MemoryRouter>
    )

    // On the card itself, before the dialog opens.
    expect(screen.getAllByText(RATIONALE)).toHaveLength(1)

    await user.click(screen.getByRole("button", { name: "Kontakt aufnehmen" }))

    expect(
      screen.getByText("Deine Suche: «embedded firmware security review»")
    ).not.toBeNull()
    // Still only the card's copy — the dialog does not carry a second one.
    expect(screen.getAllByText(RATIONALE)).toHaveLength(1)
  })
})

describe("SearchResultCard → top match", () => {
  const renderAt = (rank: number) =>
    render(
      <MemoryRouter>
        <SearchResultCard
          result={resultFixture()}
          rank={rank}
          query="embedded firmware security review"
          selected={false}
          onSelect={() => {}}
        />
      </MemoryRouter>
    )

  it("marks only rank 1 as the top match", () => {
    renderAt(0)
    expect(screen.getByText("Bester Match")).not.toBeNull()

    cleanup()
    renderAt(1)
    expect(screen.queryByText("Bester Match")).toBeNull()
  })
})
