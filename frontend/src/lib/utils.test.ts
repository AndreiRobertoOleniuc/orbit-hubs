import { describe, expect, it } from "vitest"

import { ensureUrl } from "@/features/ingestion/requests"
import { sourceHref } from "@/features/profile/profileUtils"
import { externalHref } from "@/lib/utils"

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
