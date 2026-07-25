import { cleanup, render, screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"

import { NachweisList } from "@/features/profile/NachweisList"
import type { EntitySourceDto } from "@/types/api"

vi.mock("@/lib/api", () => ({
  useSetSourceActiveMutation: () => [vi.fn(), { isLoading: false }],
}))

afterEach(cleanup)

function source(overrides: Partial<EntitySourceDto> = {}): EntitySourceDto {
  return {
    id: 1,
    sourceType: "personal_site",
    label: "ada.example.ch",
    // What ingestion actually persists for a website: `{sourceType}:{domain}`.
    sourceUrl: "personal_site:ada.example.ch",
    fetchStatus: "ok",
    fetchedAt: null,
    active: true,
    ...overrides,
  }
}

describe("NachweisList", () => {
  it("opens a linked personal site from the owner's own view", () => {
    render(<NachweisList sources={[source()]} entityId={7} />)

    const link = screen.getByRole("link", { name: "Website öffnen" })
    expect(link.getAttribute("href")).toBe("https://ada.example.ch")
    expect(link.getAttribute("target")).toBe("_blank")
    expect(link.getAttribute("rel")).toBe("noopener noreferrer")
  })

  it("opens a linked personal site from the public view", () => {
    render(<NachweisList sources={[source()]} />)

    expect(
      screen.getByRole("link", { name: "Website öffnen" }).getAttribute("href")
    ).toBe("https://ada.example.ch")
  })

  it("opens a company site on an organization profile", () => {
    render(
      <NachweisList
        sources={[
          source({
            sourceType: "company_site",
            label: "firma.example.ch",
            sourceUrl: "company_site:firma.example.ch",
          }),
        ]}
      />
    )

    expect(
      screen
        .getByRole("link", { name: "Firmenwebsite öffnen" })
        .getAttribute("href")
    ).toBe("https://firma.example.ch")
  })

  it("renders sources without a usable URL as plain text", () => {
    render(
      <NachweisList
        sources={[
          source({ id: 1, sourceUrl: null }),
          source({
            id: 2,
            sourceType: "zefix",
            label: "Muster AG",
            sourceUrl: "zefix:CHE-123.456.789",
          }),
        ]}
      />
    )

    expect(screen.queryByRole("link")).toBeNull()
    expect(screen.getByText("ada.example.ch")).toBeTruthy()
    expect(screen.getByText("Muster AG")).toBeTruthy()
  })
})
