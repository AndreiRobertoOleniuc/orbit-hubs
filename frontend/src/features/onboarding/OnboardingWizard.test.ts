import { describe, expect, it } from "vitest"

import { firstIncompleteStep } from "@/features/onboarding/onboardingResume"
import { entityFixture } from "@/test/entityFixture"

describe("firstIncompleteStep", () => {
  it("resumes at evidence selection when imported works are still unselected", () => {
    const entity = entityFixture({
      sources: [
        {
          id: 12,
          sourceType: "github",
          label: "orbit",
          sourceUrl: "https://github.com/orbit/orbit",
          fetchStatus: "ok",
          fetchedAt: null,
          active: false,
        },
      ],
    })

    expect(firstIncompleteStep(entity)).toBe(2)
  })

  it("requires role and conditional company details before expertise", () => {
    expect(
      firstIncompleteStep(
        entityFixture({ headline: null, affiliationKind: null })
      )
    ).toBe(3)
    expect(
      firstIncompleteStep(
        entityFixture({ affiliationKind: "company", orgName: null })
      )
    ).toBe(3)
  })

  it("resumes at expertise when summary or tags are missing", () => {
    expect(firstIncompleteStep(entityFixture({ summary: null }))).toBe(4)
    expect(firstIncompleteStep(entityFixture({ tags: [] }))).toBe(4)
  })

  it("resumes at publication when all essentials are complete", () => {
    expect(firstIncompleteStep(entityFixture())).toBe(5)
  })

  it("skips evidence and competence review for demand drafts", () => {
    expect(
      firstIncompleteStep(
        entityFixture({
          profileMode: "demand",
          summary: null,
          tags: [],
          sources: [
            {
              id: 15,
              sourceType: "cv_pdf",
              label: "Lebenslauf",
              sourceUrl: null,
              fetchStatus: "ok",
              fetchedAt: null,
              active: true,
            },
          ],
        })
      )
    ).toBe(5)
  })

  it("still resumes incomplete demand identity data at the basics step", () => {
    expect(
      firstIncompleteStep(
        entityFixture({ profileMode: "demand", headline: null })
      )
    ).toBe(3)
  })
})
