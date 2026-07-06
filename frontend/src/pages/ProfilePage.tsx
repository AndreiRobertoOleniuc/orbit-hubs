import { useParams } from "react-router-dom"

import { OwnProfilePage } from "@/pages/OwnProfilePage"
import { PublicProfilePage } from "@/pages/PublicProfilePage"

/**
 * Delegates to the public or own profile page based on URL params.
 * `/profile/:entityId` → public profile (token-free).
 * `/profile`           → own profile (auth-guarded).
 */
export function ProfilePage() {
  const { entityId } = useParams<{ entityId?: string }>()
  return entityId ? (
    <PublicProfilePage entityId={entityId} />
  ) : (
    <OwnProfilePage />
  )
}