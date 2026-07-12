import type { ReactNode } from "react"
import { useNavigate } from "react-router-dom"
import { SignInButton } from "@clerk/react"

import { useAuth } from "@/features/auth/useAuth"
import type { OnboardingMode } from "@/features/onboarding/types"

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
  const target = to ?? `/onboarding?mode=${mode}`

  const card = (
    <button
      type="button"
      className="flex h-full w-full flex-col items-start gap-3 rounded-xl border bg-card p-6 text-left transition-colors hover:border-primary/50 hover:bg-accent"
      onClick={!gated || isAuthenticated ? () => navigate(target) : undefined}
    >
      {icon}
      <span className="text-lg font-semibold">{title}</span>
      <span className="text-sm text-muted-foreground">{subtitle}</span>
    </button>
  )

  // Gated, unauthenticated visitors sign in first, then land in the chosen flow.
  if (gated && !isAuthenticated) {
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
