import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"

import { ProfileHeader } from "@/features/profile/ProfileHeader"
import { entityFixture } from "@/test/entityFixture"

const api = vi.hoisted(() => ({
  myEntities: [] as { id: number; displayName: string }[],
}))

vi.mock("@/lib/api", () => ({
  useGetContactedEntityIdsQuery: () => ({ data: [] }),
  useGetMyEntitiesQuery: () => ({ data: api.myEntities }),
  useGetContactGatingQuery: () => ({
    data: { allowed: true, missing: [] },
    isLoading: false,
  }),
  useCreateContactRequestMutation: () => [
    vi.fn(() => ({ unwrap: () => Promise.resolve({}) })),
    { isLoading: false, isError: false },
  ],
}))

const auth = vi.hoisted(() => ({ isAuthenticated: false }))

vi.mock("@/features/auth/useAuth", () => ({
  useAuth: () => ({ isAuthenticated: auth.isAuthenticated }),
}))

afterEach(() => {
  cleanup()
  api.myEntities = []
  auth.isAuthenticated = false
})

describe("ProfileHeader", () => {
  it("names the website link after the profile it belongs to", () => {
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
      ],
    })

    render(<ProfileHeader entity={entity} />)

    const link = screen.getByRole("link", {
      name: "Website von Ada Lovelace öffnen",
    })
    expect(link.getAttribute("href")).toBe("https://ada.example.ch")
    expect(link.getAttribute("target")).toBe("_blank")
    expect(link.getAttribute("rel")).toBe("noopener noreferrer")
  })

  it("shows no website link when no source carries an external URL", () => {
    render(<ProfileHeader entity={entityFixture()} />)

    expect(screen.queryByRole("link", { name: /Website von/i })).toBeNull()
  })

  it("does not let you contact yourself from your own preview", () => {
    auth.isAuthenticated = true
    const entity = entityFixture({ claimed: true })
    api.myEntities = [{ id: entity.id, displayName: entity.displayName }]

    render(<ProfileHeader entity={entity} showContact />)

    const button = screen.getByRole("button", { name: /Kontakt aufnehmen/i })
    expect((button as HTMLButtonElement).disabled).toBe(true)
    expect(button.getAttribute("title")).toBe("Das ist dein eigenes Profil")
  })

  it("keeps contacting someone else's claimed profile available", () => {
    auth.isAuthenticated = true
    const entity = entityFixture({ claimed: true })
    api.myEntities = [{ id: entity.id + 1, displayName: "Grace Hopper" }]

    render(
      <MemoryRouter>
        <ProfileHeader entity={entity} showContact />
      </MemoryRouter>
    )

    const button = screen.getByRole("button", { name: /Kontakt aufnehmen/i })
    expect((button as HTMLButtonElement).disabled).toBe(false)
  })
})
