import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { useProfileMode } from "@/features/profile/useProfileMode"
import { useGetMeQuery } from "@/lib/api"

interface NavItem {
  to: string
  label: string
}

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

/**
 * Mode-dependent navigation (design: TopNav). With a profile, the demand mode
 * shows Suche + Meine Anfragen, the supply mode shows Anfrage-Board + Postfach;
 * Profil (and Admin, when applicable) are always present. Anonymous = just Start.
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
      items.push(
        { to: "/board", label: "Anfrage-Board" },
        { to: "/inbox", label: "Postfach" }
      )
    }
  }
  if (isAuthenticated) items.push({ to: "/profile", label: "Mein Profil" })
  if (isAdmin) items.push({ to: "/admin/hubs", label: "Admin" })

  return (
    <>
      {items.map((item) => (
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
      ))}
    </>
  )
}
