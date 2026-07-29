import type { ReactNode } from "react"
import { useNavigate } from "react-router"
import { SignInButton } from "@clerk/react"

import { useAuth } from "@/features/auth/useAuth"
import type { OnboardingMode } from "@/features/onboarding/types"
import { useGetMyEntitiesQuery, useUpdateEntityModeMutation } from "@/lib/api"

interface StartCardProps {
  mode: OnboardingMode
  icon: ReactNode
  title: string
  subtitle: string
  /** Override the navigation target (defaults to the onboarding flow for `mode`). */
  to?: string
  /** When false, the card navigates without requiring sign-in (e.g. public search). */
  gated?: boolean
}

export function StartCard({
  mode,
  icon,
  title,
  subtitle,
  to,
  gated = true,
}: StartCardProps) {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const { data: entities, isLoading: isLoadingEntities } =
    useGetMyEntitiesQuery(undefined, { skip: !isAuthenticated })
  const [updateMode, { isLoading: isUpdatingMode, isError }] =
    useUpdateEntityModeMutation()
  const entity = entities?.[0]
  const target = to ?? `/onboarding?mode=${mode}`
  const existingProfileTarget =
    mode === "demand" ? (to ?? "/search") : "/profile"

  const handleClick = async () => {
    if (entity) {
      if (entity.profileMode !== mode) {
        try {
          await updateMode({ entityId: entity.id, mode }).unwrap()
        } catch {
          return
        }
      }
      navigate(existingProfileTarget)
      return
    }

    navigate(target)
  }

  const card = (
    <button
      type="button"
      className="flex h-full w-full flex-col items-start gap-3 rounded-xl border bg-card p-6 text-left transition-colors hover:border-primary/50 hover:bg-accent"
      onClick={!gated || isAuthenticated ? () => void handleClick() : undefined}
      disabled={isAuthenticated && (isLoadingEntities || isUpdatingMode)}
      aria-busy={isUpdatingMode}
    >
      {icon}
      <span className="text-lg font-semibold">{title}</span>
      <span className="text-sm text-muted-foreground">{subtitle}</span>
      {isError && (
        <span className="text-sm text-destructive" role="alert">
          Der Modus konnte nicht gewechselt werden. Bitte versuche es erneut.
        </span>
      )}
    </button>
  )

  // Gated, unauthenticated visitors sign in first, then land in the chosen flow.
  if (gated && !isAuthenticated) {
    const authCompleteUrl = `/auth/complete?returnTo=${encodeURIComponent(existingProfileTarget)}`
    return (
      <SignInButton
        mode="modal"
        forceRedirectUrl={authCompleteUrl}
        signUpForceRedirectUrl={authCompleteUrl}
      >
        {card}
      </SignInButton>
    )
  }
  return card
}
