import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { useProfileMode } from "@/features/profile/useProfileMode"
import { useGetMeQuery } from "@/lib/api"

interface NavItem {
  to: string
  label: string
  /** Match the pathname exactly — for routes whose sub-paths are different pages
   *  (e.g. /profile is "mine" but /profile/:id is someone else's). */
  exact?: boolean
}

function isActive(item: NavItem, pathname: string): boolean {
  if (item.exact || item.to === "/") return pathname === item.to
  return pathname === item.to || pathname.startsWith(`${item.to}/`)
}

interface NavLinksProps {
  pathname: string
  isAuthenticated: boolean
  onNavigate?: () => void
  className?: string
}

/**
 * Mode-dependent navigation (design: TopNav). With a profile, the demand mode
 * shows Suche + Meine Anfragen, the supply mode shows Anfrage-Board; the
 * Postfach is shown in both modes (sent + received contacts live there).
 * Profil (and Admin, when applicable) are always present. Without a profile
 * (anonymous or pre-onboarding) the two public surfaces are shown instead.
 */
export function NavLinks({
  pathname,
  isAuthenticated,
  onNavigate,
  className,
}: NavLinksProps) {
  const { data: me } = useGetMeQuery(undefined, { skip: !isAuthenticated })
  const isAdmin = me?.isAdmin ?? false
  const { mode, hasEntity } = useProfileMode()

  const items: NavItem[] = []
  if (hasEntity) {
    if (mode === "demand") {
      items.push(
        { to: "/search", label: "Suche" },
        { to: "/requests", label: "Meine Anfragen" }
      )
    } else {
      items.push({ to: "/board", label: "Anfrage-Board" })
    }
    // Postfach serves both modes: sent contacts + incoming requests/replies.
    items.push({ to: "/inbox", label: "Postfach" })
  } else {
    // Default nav — the token-free public surfaces
    items.push(
      { to: "/search", label: "Suche" },
      { to: "/board", label: "Anfrage-Board" }
    )
  }
  if (isAuthenticated)
    items.push({ to: "/profile", label: "Mein Profil", exact: true })
  if (isAdmin) items.push({ to: "/admin/hubs", label: "Admin" })

  return (
    <>
      {items.map((item) => (
        <Button
          key={item.to}
          variant={isActive(item, pathname) ? "secondary" : "ghost"}
          size="sm"
          asChild
          className={className}
          onClick={onNavigate}
        >
          <Link to={item.to}>{item.label}</Link>
        </Button>
      ))}
    </>
  )
}
