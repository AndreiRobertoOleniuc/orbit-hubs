import { SignInButton, SignUpButton, UserButton } from "@clerk/react"

import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"

export function AuthControls() {
  const { isAuthenticated } = useAuth()
  if (isAuthenticated)
    return (
      <UserButton
        appearance={{
          elements: {
            userButtonPopoverActionButton__manageAccount: {
              display: "none",
            },
          },
        }}
      />
    )
  return (
    <>
      {/* Preserve the initiating route; `/` is only the fallback when Clerk has
          no return location. */}
      <SignInButton
        mode="modal"
        fallbackRedirectUrl="/"
        signUpFallbackRedirectUrl="/"
      >
        <Button variant="outline" size="sm">
          Anmelden
        </Button>
      </SignInButton>
      <SignUpButton
        mode="modal"
        fallbackRedirectUrl="/"
        signInFallbackRedirectUrl="/"
      >
        <Button size="sm">Registrieren</Button>
      </SignUpButton>
    </>
  )
}
