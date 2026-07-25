import { cleanup, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, describe, expect, it, vi } from "vitest"

import { SourceCard } from "@/features/ingestion/SourceCard"
import {
  COMPANY_WEBSITE_SOURCE,
  WEBSITE_SOURCE,
} from "@/features/ingestion/sourceDefs"
import type { SourceDraft } from "@/features/ingestion/types"

afterEach(cleanup)

const linked: SourceDraft = { kind: "website", value: "verknüpft" }

describe("SourceCard", () => {
  it("explains the person/organization split before a URL is entered", () => {
    render(<SourceCard def={WEBSITE_SOURCE} onAdd={vi.fn()} />)

    // No `helper` prop: the def's own copy has to carry it, otherwise the
    // own-profile source section shows nothing (only SourcesStep passed one).
    expect(
      screen.getByText(/Eine Firmenwebsite gehört in ein Organisationsprofil/i)
    ).toBeTruthy()
  })

  it("keeps the org flavour's copy on its own card", () => {
    render(<SourceCard def={COMPANY_WEBSITE_SOURCE} onAdd={vi.fn()} />)

    expect(screen.getByText(/Wir lesen eure Website aus/i)).toBeTruthy()
  })

  it("opens a linked website in a new tab", () => {
    render(
      <SourceCard
        def={WEBSITE_SOURCE}
        added={linked}
        href="https://ada.example.ch"
        onAdd={vi.fn()}
      />
    )

    const link = screen.getByRole("link", { name: "Website öffnen" })
    expect(link.getAttribute("href")).toBe("https://ada.example.ch")
    expect(link.getAttribute("target")).toBe("_blank")
    expect(link.getAttribute("rel")).toBe("noopener noreferrer")
  })

  it("names the link after the source, so org cards read as such", () => {
    render(
      <SourceCard
        def={COMPANY_WEBSITE_SOURCE}
        added={linked}
        href="https://firma.example.ch"
        onAdd={vi.fn()}
      />
    )

    expect(
      screen.getByRole("link", { name: "Firmenwebsite öffnen" })
    ).toBeTruthy()
  })

  it("renders plain text when there is no usable URL", () => {
    const { rerender } = render(
      <SourceCard def={WEBSITE_SOURCE} added={linked} onAdd={vi.fn()} />
    )
    expect(screen.queryByRole("link")).toBeNull()

    // A non-http(s) source key must never become an anchor.
    rerender(
      <SourceCard
        def={WEBSITE_SOURCE}
        added={linked}
        href="zefix:CHE-123.456.789"
        onAdd={vi.fn()}
      />
    )
    expect(screen.queryByRole("link")).toBeNull()
  })

  it("does not expand the card when the website link is clicked", async () => {
    const user = userEvent.setup()
    render(
      <SourceCard
        def={WEBSITE_SOURCE}
        added={linked}
        href="https://ada.example.ch"
        onAdd={vi.fn()}
      />
    )

    await user.click(screen.getByRole("link", { name: "Website öffnen" }))

    // The URL input only exists while the card is expanded.
    expect(screen.queryByRole("textbox")).toBeNull()
    expect(screen.queryByRole("button", { name: /Hinzufügen/i })).toBeNull()
  })
})
