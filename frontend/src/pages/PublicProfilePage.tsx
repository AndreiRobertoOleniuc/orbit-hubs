import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { ProfileSkeleton } from "@/components/ProfileSkeleton"
import { PublicProfileView } from "@/features/profile/PublicProfileView"
import { useGetEntityQuery } from "@/lib/api"

/** Public profile at /profile/:entityId (token-free route). */
export function PublicProfilePage({ entityId }: { entityId: string }) {
  const { data: entity, isLoading, error } = useGetEntityQuery(entityId)

  if (isLoading) return <ProfileSkeleton />

  if (error || !entity) {
    return (
      <div className="mx-auto max-w-xl py-16 text-center">
        <h1 className="text-2xl font-semibold">Profil nicht gefunden</h1>
        <p className="mt-2 text-muted-foreground">
          Dieses Profil existiert nicht oder wurde entfernt.
        </p>
        <Button asChild variant="outline" className="mt-6">
          <Link to="/">Zur Startseite</Link>
        </Button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl py-4">
      <PublicProfileView entity={entity} />
    </div>
  )
}