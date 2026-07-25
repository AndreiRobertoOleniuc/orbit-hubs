import { cleanup, render, screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"

import { ProfileHeader } from "@/features/profile/ProfileHeader"
import { entityFixture } from "@/test/entityFixture"

vi.mock("@/lib/api", () => ({
  useGetContactedEntityIdsQuery: () => ({ data: [] }),
}))

vi.mock("@/features/auth/useAuth", () => ({
  useAuth: () => ({ isAuthenticated: false }),
}))

afterEach(cleanup)

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
})
