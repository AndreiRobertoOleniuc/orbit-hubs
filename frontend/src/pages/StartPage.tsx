import type { ReactNode } from "react"
import { Navigate, useNavigate } from "react-router-dom"
import { Loader2, Search, Sparkles } from "lucide-react"
import { SignInButton } from "@clerk/react"

import { useAuth } from "@/features/auth/useAuth"
import type { OnboardingMode } from "@/features/onboarding/types"
import { useGetMyEntitiesQuery } from "@/lib/api"

interface StartCardProps {
  mode: OnboardingMode
  icon: ReactNode
  title: string
  subtitle: string
}

function StartCard({ mode, icon, title, subtitle }: StartCardProps) {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const target = `/onboarding?mode=${mode}`

  const card = (
    <button
      type="button"
      className="flex h-full w-full flex-col items-start gap-3 rounded-xl border bg-card p-6 text-left transition-colors hover:border-primary/50 hover:bg-accent"
      onClick={isAuthenticated ? () => navigate(target) : undefined}
    >
      {icon}
      <span className="text-lg font-semibold">{title}</span>
      <span className="text-sm text-muted-foreground">{subtitle}</span>
    </button>
  )

  // Unauthenticated visitors sign in first, then land in the chosen flow.
  if (!isAuthenticated) {
    return (
      <SignInButton
        mode="modal"
        forceRedirectUrl={target}
        signUpForceRedirectUrl={target}
      >
        {card}
      </SignInButton>
    )
  }
  return card
}

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
