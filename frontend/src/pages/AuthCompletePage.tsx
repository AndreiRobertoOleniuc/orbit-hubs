import { Navigate, useSearchParams } from "react-router"

import { useGetMyEntitiesQuery } from "@/lib/api"

/**
 * One-time post-auth handoff. New users and users with only a draft enter
 * onboarding; active users return to the page from which they authenticated.
 * This is intentionally not a global route lock.
 */
export function AuthCompletePage() {
  const [searchParams] = useSearchParams()
  const { data: entities, isLoading, error } = useGetMyEntitiesQuery()

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
        Anmeldung wird abgeschlossen …
      </div>
    )
  }
  if (error) {
    return (
      <div className="mx-auto max-w-lg py-16 text-center">
        <h1 className="text-2xl font-semibold">
          Profilstatus konnte nicht geladen werden
        </h1>
        <p className="mt-2 text-muted-foreground">
          Bitte lade die Seite neu oder versuche es später nochmals.
        </p>
      </div>
    )
  }

  const hasActiveProfile = entities?.some(
    (entity) => entity.status === "active"
  )
  if (!hasActiveProfile) return <Navigate to="/onboarding" replace />

  const requested = searchParams.get("returnTo")
  const returnTo =
    requested?.startsWith("/") && !requested.startsWith("//") ? requested : "/"
  return (
    <Navigate to={returnTo === "/auth/complete" ? "/" : returnTo} replace />
  )
}
