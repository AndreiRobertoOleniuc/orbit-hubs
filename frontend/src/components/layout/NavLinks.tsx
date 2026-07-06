import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"

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

interface NavLinksProps {
  pathname: string
  isAuthenticated: boolean
  onNavigate?: () => void
  className?: string
}

export function NavLinks({
  pathname,
  isAuthenticated,
  onNavigate,
  className,
}: NavLinksProps) {
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