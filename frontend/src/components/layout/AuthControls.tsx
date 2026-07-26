import { SignInButton, SignUpButton, UserButton } from "@clerk/react"
import { useNavigate } from "react-router"
import { UserRound } from "lucide-react"

import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"

/** Closes the surrounding mobile menu, when rendered inside one. */
export function AuthControls({ onNavigate }: { onNavigate?: () => void }) {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  if (isAuthenticated)
    return (
      <UserButton>
        {/* Zwei Dinge, die im Test verwechselt wurden, stehen hier bewusst
            nebeneinander und getrennt benannt: das fachliche Orbit-Profil und
            die Anmeldung (E-Mail, Passwort, verknüpfte Google-/GitHub-Konten).
            Die Reihenfolge ist explizit, damit das Orbit-Profil oben steht.

            Action statt Link: Link rendert einen echten Anker, und der volle
            Reload würde den Redux-Store samt laufender Suche verwerfen. */}
        <UserButton.MenuItems>
          <UserButton.Action
            label="Mein Orbit-Profil"
            labelIcon={<UserRound className="size-4" />}
            onClick={() => {
              onNavigate?.()
              navigate("/profile")
            }}
          />
          <UserButton.Action label="manageAccount" />
          <UserButton.Action label="signOut" />
        </UserButton.MenuItems>
      </UserButton>
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
