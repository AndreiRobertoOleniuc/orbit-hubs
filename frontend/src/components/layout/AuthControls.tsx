import { SignInButton, SignUpButton, UserButton } from "@clerk/react"

import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"

export function AuthControls() {
  const { isAuthenticated } = useAuth()
  if (isAuthenticated) return <UserButton />
  return (
    <>
      {/* Land on the start page after auth: it routes to onboarding or profile. */}
      <SignInButton
        mode="modal"
        forceRedirectUrl="/"
        signUpForceRedirectUrl="/"
      >
        <Button variant="outline" size="sm">
          Anmelden
        </Button>
      </SignInButton>
      <SignUpButton
        mode="modal"
        forceRedirectUrl="/"
        signInForceRedirectUrl="/"
      >
        <Button size="sm">Registrieren</Button>
      </SignUpButton>
    </>
  )
}