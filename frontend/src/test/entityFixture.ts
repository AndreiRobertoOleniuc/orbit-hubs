import type { EntityDto } from "@/types/api"

export function entityFixture(overrides: Partial<EntityDto> = {}): EntityDto {
  return {
    id: 1,
    entityType: "person",
    displayName: "Ada Lovelace",
    headline: "Software Engineer",
    orgName: null,
    summary: "Builds reliable software systems.",
    status: "draft",
    profileMode: "supply",
    metadata: null,
    createdAt: null,
    sources: [
      {
        id: 11,
        sourceType: "manual_text",
        label: "Eigene Beschreibung",
        sourceUrl: null,
        fetchStatus: "ok",
        fetchedAt: null,
        active: true,
      },
    ],
    tags: [{ id: 2, slug: "software", label: "Software", origin: "manual" }],
    availability: {
      openToRequests: false,
      availableFrom: null,
      availableTo: null,
      pensumPct: null,
      note: null,
      preferredCollaboration: [],
    },
    affiliationKind: "self_employed",
    languages: [],
    locationLabel: null,
    hubs: [],
    homeRegion: null,
    orgLink: null,
    claimed: true,
    ...overrides,
  }
}
