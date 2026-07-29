import { useAuth } from "@/features/auth/useAuth"
import { useGetMyEntitiesQuery, useUpdateEntityModeMutation } from "@/lib/api"
import type { ProfileMode } from "@/types/api"

/**
 * The current profile mode — the single source of truth is the user's own
 * entity's `profileMode` (persisted, set by the onboarding fork and the header
 * mode switch). Drives mode-dependent navigation and profile rendering.
 */
export function useProfileMode(): {
  mode: ProfileMode
  entityId: number | null
  hasEntity: boolean
} {
  const { isAuthenticated } = useAuth()
  const { data: entities } = useGetMyEntitiesQuery(undefined, {
    skip: !isAuthenticated,
  })
  const entity = entities?.find((e) => e.status === "active") ?? entities?.[0]
  const activeEntity = entity?.status === "active" ? entity : null
  return {
    mode: activeEntity?.profileMode ?? entity?.profileMode ?? "supply",
    entityId: activeEntity?.id ?? null,
    hasEntity: Boolean(activeEntity),
  }
}

/**
 * Whether the given entity belongs to the signed-in user. Used to suppress
 * actions that make no sense on your own profile — contacting yourself, above
 * all, which the backend rejects with a 400 and which surfaces in the preview
 * ("So sehen dich andere") and whenever your own profile ranks in your search.
 */
export function useIsOwnEntity(entityId: number | null | undefined): boolean {
  const { isAuthenticated } = useAuth()
  const { data: entities } = useGetMyEntitiesQuery(undefined, {
    skip: !isAuthenticated,
  })
  if (entityId == null) return false
  return (entities ?? []).some((entity) => entity.id === entityId)
}

/**
 * Persists a mode change on the user's entity. The mutation's cache
 * invalidation refreshes everything that reads the mode (header switch,
 * navigation, profile), so callers only need to handle their own navigation.
 */
export function useSwitchProfileMode(): {
  switchMode: (next: ProfileMode) => void
  mode: ProfileMode
  hasEntity: boolean
} {
  const { mode, entityId, hasEntity } = useProfileMode()
  const [updateMode] = useUpdateEntityModeMutation()

  const switchMode = (next: ProfileMode) => {
    if (next === mode || entityId == null) return
    void updateMode({ entityId, mode: next })
  }

  return { switchMode, mode, hasEntity }
}
