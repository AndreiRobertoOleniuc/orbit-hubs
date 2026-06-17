import { Link, Outlet, useLocation } from "react-router-dom"
import { Menu } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { ThemeToggle } from "@/components/layout/ThemeToggle"
import { useAuth } from "@/features/auth/useAuth"
import {
  SignInButton,
  SignUpButton,
  UserButton,
} from "@clerk/react"

const navItems = [
  { to: "/search", label: "Suche" },
  { to: "/board", label: "Board" },
  { to: "/onboarding", label: "Onboarding" },
]

function isActive(path: string, pathname: string): boolean {
  if (path === "/profile" && pathname.startsWith("/profile")) return true
  return pathname === path || pathname.startsWith(`${path}/`)
}

export function AppLayout() {
  const { pathname } = useLocation()
  const { isAuthenticated } = useAuth()
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <div className="flex min-h-svh flex-col bg-background text-foreground">
      <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between gap-4 px-4">
          <Link to="/" className="font-semibold tracking-tight">
            Orbit
          </Link>

          <nav className="hidden items-center gap-1 md:flex">
            {navItems.map((item) => (
              <Button
                key={item.to}
                variant={isActive(item.to, pathname) ? "secondary" : "ghost"}
                size="sm"
                asChild
              >
                <Link to={item.to}>{item.label}</Link>
              </Button>
            ))}
            <Button
              variant={
                pathname.startsWith("/profile") ? "secondary" : "ghost"
              }
              size="sm"
              asChild
            >
              <Link to="/profile">
                {isAuthenticated ? "Mein Profil" : "Profil"}
              </Link>
            </Button>
          </nav>

          <div className="flex items-center gap-2">
            <ThemeToggle />
            <div className="hidden items-center gap-2 md:flex">
              {isAuthenticated ? (
                <UserButton />
              ) : (
                <>
                  <SignInButton>
                    <Button variant="outline" size="sm">
                      Anmelden
                    </Button>
                  </SignInButton>
                  <SignUpButton>
                    <Button size="sm">Registrieren</Button>
                  </SignUpButton>
                </>
              )}
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
              {navItems.map((item) => (
                <Button
                  key={item.to}
                  variant={
                    isActive(item.to, pathname) ? "secondary" : "ghost"
                  }
                  size="sm"
                  asChild
                  className="justify-start"
                  onClick={() => setMobileOpen(false)}
                >
                  <Link to={item.to}>{item.label}</Link>
                </Button>
              ))}
              <Button
                variant={
                  pathname.startsWith("/profile") ? "secondary" : "ghost"
                }
                size="sm"
                asChild
                className="justify-start"
                onClick={() => setMobileOpen(false)}
              >
                <Link to="/profile">
                  {isAuthenticated ? "Mein Profil" : "Profil"}
                </Link>
              </Button>
            </nav>
          </div>
        )}
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t py-4">
        <div className="mx-auto max-w-7xl px-4 text-xs text-muted-foreground">
          Orbit — regionales Innovations-Matchmaking · FHNW P5
        </div>
      </footer>
    </div>
  )
}
