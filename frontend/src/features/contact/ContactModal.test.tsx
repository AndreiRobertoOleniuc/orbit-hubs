import { cleanup, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter } from "react-router-dom"
import { afterEach, describe, expect, it, vi } from "vitest"

import { ContactModal } from "@/features/contact/ContactModal"

const createContact = vi.fn(() => ({ unwrap: () => Promise.resolve({}) }))

vi.mock("@/lib/api", () => ({
  useGetContactGatingQuery: () => ({
    data: { allowed: true, missing: [] },
    isLoading: false,
  }),
  useGetMyEntitiesQuery: () => ({
    data: [{ id: 7, displayName: "Grace Hopper", orgName: "FHNW" }],
  }),
  useCreateContactRequestMutation: () => [
    createContact,
    { isLoading: false, isError: false },
  ],
}))

vi.mock("@/features/auth/useAuth", () => ({
  useAuth: () => ({ isAuthenticated: true }),
}))

afterEach(() => {
  cleanup()
  createContact.mockClear()
})

function renderModal(
  props: Partial<React.ComponentProps<typeof ContactModal>> = {}
) {
  return render(
    <MemoryRouter>
      <ContactModal
        toEntityId={42}
        toName="Ada Lovelace"
        quote="Deine Suche: «embedded firmware security review»"
        open
        onOpenChange={() => {}}
        {...props}
      />
    </MemoryRouter>
  )
}

describe("ContactModal", () => {
  it("names the recipient visibly, not only for screen readers", () => {
    renderModal()

    const title = screen.getByText("Kontakt mit Ada Lovelace aufnehmen")
    expect(title.className).not.toContain("sr-only")
    expect(title.closest(".sr-only")).toBeNull()
  })

  it("keeps the search context but not the match rationale", () => {
    renderModal({
      quote: "Deine Suche: «embedded firmware security review»",
    })

    expect(
      screen.getByText("Deine Suche: «embedded firmware security review»")
    ).not.toBeNull()
    expect(screen.queryByText(/Warum passend/)).toBeNull()
  })

  it("keeps the full context available when it is truncated visually", () => {
    const long = `Deine Suche: «${"embedded firmware security review ".repeat(10)}»`
    renderModal({ quote: long })

    const quote = screen.getByTitle(long)
    expect(quote.className).toContain("truncate")
    expect(quote.textContent).toBe(long)
  })

  it("labels the message field and states the expected length", () => {
    renderModal()

    const textarea = screen.getByLabelText("Deine Nachricht")
    expect(textarea.getAttribute("placeholder")).toBe(
      "Beschreibe kurz, worum es geht und was du dir vom Austausch erhoffst."
    )
    const hint = document.getElementById(
      textarea.getAttribute("aria-describedby") ?? ""
    )
    expect(hint?.textContent).toContain("2–4 Sätze genügen")
  })

  it("requires a non-empty message before sending", async () => {
    const user = userEvent.setup()
    renderModal()

    const send = screen.getByRole("button", { name: "Kontaktanfrage senden" })
    expect((send as HTMLButtonElement).disabled).toBe(true)

    await user.type(screen.getByLabelText("Deine Nachricht"), "   ")
    expect((send as HTMLButtonElement).disabled).toBe(true)

    await user.clear(screen.getByLabelText("Deine Nachricht"))
    await user.type(
      screen.getByLabelText("Deine Nachricht"),
      "  Hallo Ada, kurze Frage zu deinem Firmware-Review.  "
    )
    expect((send as HTMLButtonElement).disabled).toBe(false)

    await user.click(send)
    expect(createContact).toHaveBeenCalledWith({
      toEntityId: 42,
      requestId: null,
      message: "Hallo Ada, kurze Frage zu deinem Firmware-Review.",
      searchedText: null,
    })
  })

  it("renders a long recipient name without losing the send action", () => {
    const long =
      "Prof. Dr. Maximiliane von Habsburg-Lothringen-Schwarzenberg-Liechtenstein"
    renderModal({ toName: long })

    expect(screen.getByText(`Kontakt mit ${long} aufnehmen`)).not.toBeNull()
    expect(
      screen.getByRole("button", { name: "Kontaktanfrage senden" })
    ).not.toBeNull()
  })
})
