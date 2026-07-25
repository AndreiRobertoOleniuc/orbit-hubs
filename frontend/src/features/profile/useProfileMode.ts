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
  const entity = entities?.[0]
  return {
    mode: entity?.profileMode ?? "supply",
    entityId: entity?.id ?? null,
    hasEntity: Boolean(entity),
  }
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
