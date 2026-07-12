import { Navigate } from "react-router-dom"
import { Loader2, Search, Sparkles } from "lucide-react"

import { StartCard } from "@/components/StartCard"
import { useAuth } from "@/features/auth/useAuth"
import { useGetMyEntitiesQuery } from "@/lib/api"

/**
 * Public entry: the design's "Was möchtest du tun?" start screen.
 * Signed-in users who already onboarded are routed to their profile —
 * onboarding is a one-time flow.
 */
export function StartPage() {
  const { isAuthenticated, status } = useAuth()
  const { data: myEntities, isLoading } = useGetMyEntitiesQuery(undefined, {
    skip: !isAuthenticated,
  })

  if (status === "loading" || (isAuthenticated && isLoading)) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (isAuthenticated && myEntities && myEntities.length > 0) {
    return <Navigate to="/profile" replace />
  }

  return (
    <div className="mx-auto max-w-2xl py-10">
      <h1 className="mb-8 text-center text-3xl font-bold tracking-tight">
        Was möchtest du tun?
      </h1>
      <div className="grid gap-4 sm:grid-cols-2">
        <StartCard
          mode="demand"
          to="/search"
          gated={false}
          icon={<Search className="size-8 text-primary" />}
          title="Ich suche jemanden"
          subtitle="Beschreibe, was du brauchst — wir finden passende Profile."
        />
        <StartCard
          mode="supply"
          icon={<Sparkles className="size-8 text-primary" />}
          title="Ich möchte gefunden werden"
          subtitle="Bau dein Profil auf und sag, wofür du offen bist."
        />
      </div>
      <p className="mt-6 text-center text-sm text-muted-foreground">
        Du kannst später jederzeit zwischen beiden wechseln.
      </p>
    </div>
  )
}
