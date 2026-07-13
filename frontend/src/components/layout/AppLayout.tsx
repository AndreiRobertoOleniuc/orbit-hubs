import { useState } from "react"
import { Link, Outlet, useLocation } from "react-router-dom"
import { Menu } from "lucide-react"

import { OrbitLogo } from "@/components/branding/OrbitLogo"
import { AuthControls } from "@/components/layout/AuthControls"
import { ModeSwitch } from "@/components/layout/ModeSwitch"
import { NavLinks } from "@/components/layout/NavLinks"
import { ThemeToggle } from "@/components/layout/ThemeToggle"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"
import { cn } from "@/lib/utils"

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
            <ModeSwitch className="hidden md:flex" />
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
                <ModeSwitch />
                <AuthControls />
              </div>
            </nav>
          </div>
        )}
      </header>

      {/* /search goes full-bleed so its slide-out profile panel can pin to
          the viewport edge and use the whole width on large screens */}
      <main
        className={cn(
          "w-full flex-1 px-4 py-6",
          pathname !== "/search" && "mx-auto max-w-6xl"
        )}
      >
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
