import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"

import { AuthCompletePage } from "@/pages/AuthCompletePage"
import { entityFixture } from "@/test/entityFixture"
import { useGetMyEntitiesQuery } from "@/lib/api"

vi.mock("@/lib/api", () => ({
  useGetMyEntitiesQuery: vi.fn(),
}))

const mockedEntities = vi.mocked(useGetMyEntitiesQuery)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderPage(entry = "/auth/complete?returnTo=%2Fsearch") {
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/auth/complete" element={<AuthCompletePage />} />
        <Route path="/onboarding" element={<div>Onboarding</div>} />
        <Route path="/search" element={<div>Search</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe("AuthCompletePage", () => {
  it("sends users without an active profile to onboarding", () => {
    mockedEntities.mockReturnValue({
      data: [entityFixture({ status: "draft" })],
      isLoading: false,
      error: undefined,
    } as unknown as ReturnType<typeof useGetMyEntitiesQuery>)

    renderPage()
    expect(screen.getByText("Onboarding")).toBeTruthy()
  })

  it("returns active users to the route that initiated authentication", () => {
    mockedEntities.mockReturnValue({
      data: [entityFixture({ status: "active" })],
      isLoading: false,
      error: undefined,
    } as unknown as ReturnType<typeof useGetMyEntitiesQuery>)

    renderPage()
    expect(screen.getByText("Search")).toBeTruthy()
  })
})
