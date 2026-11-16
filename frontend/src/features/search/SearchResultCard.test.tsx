import { cleanup, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"

import { SearchResultCard } from "@/features/search/SearchResultCard"
import { entityFixture } from "@/test/entityFixture"
import type {
  SearchCitationDto,
  SearchRationaleDto,
  SearchResultDto,
} from "@/types/api"

const RATIONALE =
  "Ada hat mehrfach Firmware für sicherheitskritische Steuerungen auditiert."

const CITED: SearchCitationDto = {
  sourceId: 1,
  label: "Firmware audit report",
  sourceType: "company_site",
  sourceUrl: null,
}
const UNCITED: SearchCitationDto = {
  sourceId: 2,
  label: "Unrelated repository",
  sourceType: "github",
  sourceUrl: null,
}

const rationaleQuery = vi.fn(
  (): { data?: SearchRationaleDto; isFetching: boolean } => ({
    data: { entityId: 1, rationale: RATIONALE, citations: [CITED] },
    isFetching: false,
  })
)

vi.mock("@/lib/api", () => ({
  useGetSearchRationaleQuery: (...args: unknown[]) =>
    rationaleQuery(...(args as [])),
  useGetContactedEntityIdsQuery: () => ({ data: [] }),
  useGetContactGatingQuery: () => ({
    data: { allowed: true, missing: [] },
    isLoading: false,
  }),
  useGetMyEntitiesQuery: () => ({
    data: [
      {
        id: 7,
        displayName: "Grace Hopper",
        orgName: "FHNW",
        status: "active",
        profileMode: "demand",
      },
    ],
  }),
  useCreateContactRequestMutation: () => [
    vi.fn(() => ({ unwrap: () => Promise.resolve({}) })),
    { isLoading: false, isError: false },
  ],
}))

vi.mock("@/features/auth/useAuth", () => ({
  useAuth: () => ({ isAuthenticated: true }),
}))

afterEach(() => {
  cleanup()
  rationaleQuery.mockClear()
})

function resultFixture(): SearchResultDto {
  return {
    entity: entityFixture({ claimed: true }),
    score: 0.9,
    citations: [CITED, UNCITED],
    distance: null,
  }
}

function renderCard(query = "embedded firmware security review") {
  return render(
    <MemoryRouter>
      <SearchResultCard
        result={resultFixture()}
        rank={0}
        query={query}
        selected={false}
        onSelect={() => {}}
      />
    </MemoryRouter>
  )
}

describe("SearchResultCard → rationale", () => {
  it("asks for the rationale of this result with the search as sent", () => {
    renderCard("  embedded firmware  ")
    expect(rationaleQuery).toHaveBeenCalledWith({
      query: "embedded firmware",
      entityId: resultFixture().entity.id,
    })
  })

  it("shows the summary and every matching source until the rationale is there", () => {
    rationaleQuery.mockReturnValueOnce({ isFetching: true })
    renderCard()

    expect(screen.queryByText(RATIONALE)).toBeNull()
    expect(screen.getByRole("status").textContent).toContain(
      "Begründung wird erstellt"
    )
    expect(screen.getByText("Firmware audit report")).not.toBeNull()
    expect(screen.getByText("Unrelated repository")).not.toBeNull()
  })

  it("then shows the rationale with only the sources it cites", () => {
    renderCard()

    expect(screen.getByText(RATIONALE)).not.toBeNull()
    expect(screen.queryByRole("status")).toBeNull()
    expect(screen.getByText("Firmware audit report")).not.toBeNull()
    expect(screen.queryByText("Unrelated repository")).toBeNull()
  })

  it("keeps every matching source when no rationale could be grounded", () => {
    rationaleQuery.mockReturnValueOnce({
      data: { entityId: 1, rationale: null, citations: [CITED, UNCITED] },
      isFetching: false,
    })
    renderCard()

    expect(screen.queryByText(RATIONALE)).toBeNull()
    expect(screen.getByText("Unrelated repository")).not.toBeNull()
  })
})

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

  it("explains when contact is temporarily unavailable", () => {
    render(
      <MemoryRouter>
        <SearchResultCard
          result={{
            ...resultFixture(),
            entity: entityFixture({ claimed: false }),
          }}
          rank={0}
          query="embedded firmware security review"
          selected={false}
          onSelect={() => {}}
        />
      </MemoryRouter>
    )

    const button = screen.getByRole("button", { name: "Kontakt aufnehmen" })
    expect((button as HTMLButtonElement).disabled).toBe(true)
    expect(
      screen.getByText("Kontakt ist für dieses Profil gerade nicht verfügbar.")
    ).not.toBeNull()
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
