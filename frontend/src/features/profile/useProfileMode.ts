import { useAuth } from "@/features/auth/useAuth"
import { useGetMyEntitiesQuery } from "@/lib/api"
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
