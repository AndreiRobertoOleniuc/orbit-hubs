import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { ProfileDetailsStep } from "@/features/onboarding/ProfileDetailsStep"
import { entityFixture } from "@/test/entityFixture"

const api = vi.hoisted(() => ({
  updateMeta: vi.fn(() => ({ unwrap: vi.fn() })),
  updateHubs: vi.fn(() => ({ unwrap: vi.fn() })),
}))

vi.mock("@/lib/api", () => ({
  useGetHubsQuery: () => ({ data: [], isLoading: false }),
  useUpdateProfileMetaMutation: () => [api.updateMeta, { isLoading: false }],
  useUpdateEntityHubsMutation: () => [api.updateHubs, { isLoading: false }],
  useLazySearchZefixQuery: () => [vi.fn(), { data: [], isFetching: false }],
}))

describe("ProfileDetailsStep", () => {
  it("requires role and a company only for employed people", async () => {
    const user = userEvent.setup()
    const entity = entityFixture({
      headline: null,
      affiliationKind: null,
      orgName: null,
    })
    render(<ProfileDetailsStep entity={entity} onContinue={vi.fn()} />)

    const continueButton = screen.getByRole("button", {
      name: /Profildaten speichern und weiter/i,
    })
    expect((continueButton as HTMLButtonElement).disabled).toBe(true)

    await user.type(screen.getByLabelText(/Rolle \/ Funktion/i), "Engineer")
    await user.click(screen.getByRole("button", { name: /Angestellt/i }))
    expect((continueButton as HTMLButtonElement).disabled).toBe(true)

    await user.type(
      screen.getByLabelText(/Arbeitgeber \/ Organisation/i),
      "Orbit AG"
    )
    expect((continueButton as HTMLButtonElement).disabled).toBe(false)
  })
})
