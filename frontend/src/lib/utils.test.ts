import { describe, expect, it } from "vitest"

import { ensureUrl, normalizeGithubLogin } from "@/features/ingestion/requests"
import { aggregateSourceUrl, sourceHref } from "@/features/profile/profileUtils"
import { externalHref } from "@/lib/utils"
import type { EntitySourceDto } from "@/types/api"

describe("externalHref", () => {
  it("accepts http(s) URLs", () => {
    expect(externalHref("https://ada.example.ch")).toBe(
      "https://ada.example.ch"
    )
    expect(externalHref("http://ada.example.ch/blog")).toBe(
      "http://ada.example.ch/blog"
    )
    expect(externalHref("HTTPS://ADA.EXAMPLE.CH")).toBe(
      "HTTPS://ADA.EXAMPLE.CH"
    )
  })

  it("accepts a normalized draft URL", () => {
    expect(externalHref(ensureUrl("www.meine-seite.ch"))).toBe(
      "https://www.meine-seite.ch"
    )
  })

  it("rejects missing, empty and schemeless values", () => {
    expect(externalHref(null)).toBeNull()
    expect(externalHref(undefined)).toBeNull()
    expect(externalHref("")).toBeNull()
    expect(externalHref("www.meine-seite.ch")).toBeNull()
    // ensureUrl of an empty value — a scheme but no host
    expect(externalHref("https://")).toBeNull()
  })

  it("rejects internal source keys and dangerous schemes", () => {
    expect(externalHref("zefix:CHE-123.456.789")).toBeNull()
    expect(externalHref("manual:1")).toBeNull()
    expect(externalHref("javascript:alert(1)")).toBeNull()
  })
})

describe("normalizeGithubLogin", () => {
  it("keeps a plain GitHub username", () => {
    expect(normalizeGithubLogin("mkeller-dev")).toBe("mkeller-dev")
    expect(normalizeGithubLogin("@mkeller-dev")).toBe("mkeller-dev")
  })

  it("extracts the owner from GitHub profile and repository links", () => {
    expect(normalizeGithubLogin("https://github.com/mkeller-dev")).toBe(
      "mkeller-dev"
    )
    expect(normalizeGithubLogin("github.com/mkeller-dev/orbit")).toBe(
      "mkeller-dev"
    )
    expect(
      normalizeGithubLogin(
        "https://www.github.com/mkeller-dev?tab=repositories"
      )
    ).toBe("mkeller-dev")
  })
})

describe("sourceHref", () => {
  it("passes real links through", () => {
    expect(sourceHref("https://openalex.org/W2159827217")).toBe(
      "https://openalex.org/W2159827217"
    )
  })

  it("rebuilds a website URL from its dedup key", () => {
    // `sources.source_url` stores `{sourceType}:{domain}` for websites — the
    // only place the linked domain survives ingestion.
    expect(sourceHref("personal_site:fhnw.ch")).toBe("https://fhnw.ch")
    expect(sourceHref("company_site:soobr.ch")).toBe("https://soobr.ch")
    expect(sourceHref("personal_site:https://ada.example.ch")).toBe(
      "https://ada.example.ch"
    )
  })

  it("stays null for keys that carry no web address", () => {
    expect(sourceHref("zefix:CHE-123.456.789")).toBeNull()
    expect(sourceHref("manual:text:abc123")).toBeNull()
    expect(sourceHref("personal_site:")).toBeNull()
    expect(sourceHref(null)).toBeNull()
  })
})

describe("aggregateSourceUrl", () => {
  const source = (
    overrides: Partial<EntitySourceDto> = {}
  ): EntitySourceDto => ({
    id: 1,
    sourceType: "github",
    label: "github.com/mkeller-dev/orbit",
    sourceUrl: "https://github.com/mkeller-dev/orbit",
    fetchStatus: "ok",
    fetchedAt: null,
    active: true,
    ...overrides,
  })

  it("links the GitHub source card to the account, not the first repo", () => {
    expect(aggregateSourceUrl(source())).toBe("https://github.com/mkeller-dev")
  })

  it("keeps regular single-source links unchanged", () => {
    expect(
      aggregateSourceUrl(
        source({
          sourceType: "personal_site",
          label: "ada.example.ch",
          sourceUrl: "personal_site:ada.example.ch",
        })
      )
    ).toBe("https://ada.example.ch")
  })
})
