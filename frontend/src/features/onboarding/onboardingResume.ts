import type { EntityDto } from "@/types/api"

const CURATABLE_TYPES = new Set(["github", "orcid_pub"])

/** Derive the first unfinished post-import step from persisted profile data. */
export function firstIncompleteStep(entity: EntityDto) {
  const selectable = entity.sources.filter((source) =>
    CURATABLE_TYPES.has(source.sourceType)
  )
  if (selectable.length > 0 && selectable.every((source) => !source.active)) {
    return 2
  }
  const identityIncomplete =
    !entity.displayName.trim() ||
    !entity.headline?.trim() ||
    (entity.entityType === "person" &&
      (!entity.affiliationKind ||
        (entity.affiliationKind === "company" && !entity.orgName?.trim())))
  if (identityIncomplete) return 3
  if (!entity.summary?.trim() || entity.tags.length === 0) return 4
  return 5
}
