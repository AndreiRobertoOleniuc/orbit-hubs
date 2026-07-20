import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { useProfileMode } from "@/features/profile/useProfileMode"
import { useGetInboxQuery, useGetMeQuery } from "@/lib/api"
import { cn } from "@/lib/utils"

interface NavItem {
  to: string
  label: string
  /** Match the pathname exactly — for routes whose sub-paths are different pages
   *  (e.g. /profile is "mine" but /profile/:id is someone else's). */
  exact?: boolean
  /** Extra path prefixes that also mark this item active — for detail pages
   *  that live under a different route (e.g. a request detail at /board/:id
   *  belongs to "Meine Anfragen" in demand mode). */
  match?: string[]
  /** Unread/actionable count shown as a notification badge (e.g. Postfach). */
  badge?: number
}

/** Active when the pathname equals a prefix or is nested beneath it (`/x` or `/x/…`). */
function matchesPrefix(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`)
}

function isActive(item: NavItem, pathname: string): boolean {
  if (item.exact || item.to === "/") return pathname === item.to
  return [item.to, ...(item.match ?? [])].some((p) =>
    matchesPrefix(pathname, p)
  )
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
  const { data: inbox } = useGetInboxQuery(undefined, {
    skip: !isAuthenticated || !hasEntity,
  })

  // Incoming contact requests awaiting a response — the actionable Postfach count.
  const pendingReceived =
    inbox?.received.filter((c) => c.status === "pending").length ?? 0

  const items: NavItem[] = []
  if (hasEntity) {
    if (mode === "demand") {
      items.push(
        { to: "/search", label: "Suche" },
        // Request details live at /board/:id — reached from here in demand mode.
        { to: "/requests", label: "Meine Anfragen", match: ["/board"] }
      )
    } else {
      items.push({ to: "/board", label: "Anfrage-Board" })
    }
    // Postfach serves both modes: sent contacts + incoming requests/replies.
    items.push({ to: "/inbox", label: "Postfach", badge: pendingReceived })
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
          <Link to={item.to}>
            {item.label}
            {item.badge != null && item.badge > 0 && (
              <span
                aria-label={`${item.badge} neue Kontaktanfragen`}
                className={cn(
                  "ml-1 inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1 text-xs font-semibold",
                  isActive(item, pathname)
                    ? "bg-primary text-primary-foreground"
                    : "bg-primary/15 text-primary"
                )}
              >
                {item.badge}
              </span>
            )}
          </Link>
        </Button>
      ))}
    </>
  )
}
