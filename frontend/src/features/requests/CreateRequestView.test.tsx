import { cleanup, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter, Route, Routes } from "react-router"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { CreateRequestView } from "@/features/requests/CreateRequestView"

const api = vi.hoisted(() => ({ createRequest: vi.fn(), unwrap: vi.fn() }))

vi.mock("@/lib/api", () => ({
  useCreateRequestMutation: () => [api.createRequest, { isLoading: false, isError: false }],
}))

vi.mock("@/features/profile/useProfileMode", () => ({
  useSwitchProfileMode: () => ({ switchMode: vi.fn(), hasEntity: true, mode: "demand" }),
}))

vi.mock("@/components/TagAutocompleteInput", () => ({
  TagAutocompleteInput: ({ onAdd }: { onAdd: (tag: string) => void }) => (
    <button type="button" onClick={() => onAdd("Firmware")}>Firmware hinzufügen</button>
  ),
}))

vi.mock("@/features/search/HubRegionSelect", () => ({
  HubRegionSelect: ({ value, onChange }: { value: string; onChange: (next: string) => void }) => (
    <select aria-label="Hub / Region" value={value} onChange={(event) => onChange(event.target.value)}>
      <option value="all">Ganze Schweiz / Online</option>
      <option value="region:3">Region Baden</option>
    </select>
  ),
}))

afterEach(cleanup)

beforeEach(() => {
  api.createRequest.mockReset()
  api.unwrap.mockReset()
  api.createRequest.mockReturnValue({ unwrap: api.unwrap })
})

function renderView() {
  return render(
    <MemoryRouter initialEntries={["/requests/new"]}>
      <Routes>
        <Route path="/requests/new" element={<CreateRequestView />} />
        <Route path="/board/:id" element={<p>Board detail</p>} />
      </Routes>
    </MemoryRouter>
  )
}

describe("CreateRequestView", () => {
  it("shows both required-field explanations before it sends anything", async () => {
    const user = userEvent.setup()
    renderView()

    await user.click(screen.getByRole("button", { name: "Anfrage veröffentlichen" }))

    expect(api.createRequest).not.toHaveBeenCalled()
    expect(screen.getByText(/welche Expertise du suchst/i)).toBeTruthy()
    expect(screen.getByText(/mindestens einen Tag/i)).toBeTruthy()
  })

  it("sends the trimmed, structured request and opens its board detail", async () => {
    const user = userEvent.setup()
    api.unwrap.mockResolvedValue({ id: 42 })
    renderView()

    await user.type(screen.getByLabelText(/Was suchst du/i), "  Firmware review  ")
    await user.click(screen.getByRole("button", { name: "Firmware hinzufügen" }))
    await user.selectOptions(screen.getByLabelText("Hub / Region"), "region:3")
    await user.click(screen.getByRole("radio", { name: "Dringend" }))
    await user.click(screen.getByRole("button", { name: "Anfrage veröffentlichen" }))

    expect(api.createRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        queryText: "Firmware review",
        scope: { kind: "region", regionId: 3 },
        urgency: "urgent",
        tags: ["Firmware"],
      })
    )
    expect(screen.getByText("Board detail")).toBeTruthy()
  })
})
