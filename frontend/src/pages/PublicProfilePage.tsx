import { NotFoundState } from "@/components/NotFoundState"
import { ProfileSkeleton } from "@/components/ProfileSkeleton"
import { PublicProfileView } from "@/features/profile/PublicProfileView"
import { useGetEntityQuery } from "@/lib/api"

/** Public profile at /profile/:entityId (token-free route). */
export function PublicProfilePage({ entityId }: { entityId: string }) {
  const { data: entity, isLoading, error } = useGetEntityQuery(entityId)

  if (isLoading) return <ProfileSkeleton />

  if (error || !entity) {
    return (
      <NotFoundState
        title="Profil nicht gefunden"
        description="Dieses Profil existiert nicht oder wurde entfernt."
        actionLabel="Zur Suche"
        actionTo="/search"
      />
    )
  }

  return (
    <div className="mx-auto max-w-2xl py-4">
      <PublicProfileView entity={entity} />
    </div>
  )
}
