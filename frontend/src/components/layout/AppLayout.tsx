import { useState } from "react"
import { Link, Outlet, useLocation } from "react-router-dom"
import { Menu } from "lucide-react"
import { SignInButton, SignUpButton, UserButton } from "@clerk/react"

import { OrbitLogo } from "@/components/branding/OrbitLogo"
import { ThemeToggle } from "@/components/layout/ThemeToggle"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"

interface NavItem {
  to: string
  label: string
  authOnly?: boolean
}

/** Only surfaces that actually exist — no placeholder tabs. */
const NAV_ITEMS: NavItem[] = [
  { to: "/", label: "Start" },
  { to: "/profile", label: "Mein Profil", authOnly: true },
]

function isActive(path: string, pathname: string): boolean {
  if (path === "/") return pathname === "/"
  return pathname === path || pathname.startsWith(`${path}/`)
}

function NavLinks({
  pathname,
  isAuthenticated,
  onNavigate,
  className,
}: {
  pathname: string
  isAuthenticated: boolean
  onNavigate?: () => void
  className?: string
}) {
  return (
    <>
      {NAV_ITEMS.filter((item) => !item.authOnly || isAuthenticated).map(
        (item) => (
          <Button
            key={item.to}
            variant={isActive(item.to, pathname) ? "secondary" : "ghost"}
            size="sm"
            asChild
            className={className}
            onClick={onNavigate}
          >
            <Link to={item.to}>{item.label}</Link>
          </Button>
        )
      )}
    </>
  )
}

function AuthControls() {
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

export function AppLayout() {
  const { pathname } = useLocation()
  const { isAuthenticated } = useAuth()
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <div className="flex min-h-svh flex-col bg-background text-foreground">
      <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/75">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
          <Link to="/" aria-label="Orbit — Startseite">
            <OrbitLogo />
          </Link>

          <nav className="hidden flex-1 items-center gap-1 md:flex">
            <NavLinks pathname={pathname} isAuthenticated={isAuthenticated} />
          </nav>

          <div className="flex flex-1 items-center justify-end gap-2 md:flex-none">
            <ThemeToggle />
            <div className="hidden items-center gap-2 md:flex">
              <AuthControls />
            </div>
            <Button
              variant="ghost"
              size="icon-sm"
              className="md:hidden"
              onClick={() => setMobileOpen((open) => !open)}
              aria-label="Navigation umschalten"
            >
              <Menu />
            </Button>
          </div>
        </div>

        {mobileOpen && (
          <div className="border-b px-4 py-3 md:hidden">
            <nav className="flex flex-col gap-1">
              <NavLinks
                pathname={pathname}
                isAuthenticated={isAuthenticated}
                className="justify-start"
                onNavigate={() => setMobileOpen(false)}
              />
              <div className="mt-2 flex items-center gap-2 border-t pt-3">
                <AuthControls />
              </div>
            </nav>
          </div>
        )}
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t py-4">
        <div className="mx-auto max-w-6xl px-4 text-xs text-muted-foreground">
          Orbit — regionales Innovations-Matchmaking · FHNW P5
        </div>
      </footer>
    </div>
  )
}
