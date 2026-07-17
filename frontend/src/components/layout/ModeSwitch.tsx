import { useLocation, useNavigate } from "react-router-dom"

import { useProfileMode } from "@/features/profile/useProfileMode"
import { useUpdateEntityModeMutation } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { ProfileMode } from "@/types/api"

/** Pages that only exist in one mode — used to re-route on a mode switch.
 *  (Postfach `/inbox` is intentionally absent: it exists in both modes.) */
const DEMAND_PAGES = ["/search", "/requests"]
const SUPPLY_PAGES = ["/board"]

/**
 * The «Ich suche» / «Ich biete» header switch (design: TopNav `nav-mode`).
 * Persists the choice on the user's entity and, if the current page doesn't
 * exist in the new mode, routes to that mode's landing page. Hidden until the
 * user has a profile.
 */
export function ModeSwitch({ className }: { className?: string }) {
  const { mode, entityId, hasEntity } = useProfileMode()
  const [updateMode] = useUpdateEntityModeMutation()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  if (!hasEntity || entityId == null) return null

  const switchMode = (next: ProfileMode) => {
    if (next === mode) return
    void updateMode({ entityId, mode: next })
    if (next === "supply" && DEMAND_PAGES.includes(pathname)) navigate("/board")
    else if (next === "demand" && SUPPLY_PAGES.includes(pathname))
      navigate("/search")
  }

  return (
    <div
      className={cn("flex rounded-lg border p-0.5", className)}
      role="group"
      aria-label="Modus"
    >
      <button
        type="button"
        onClick={() => switchMode("demand")}
        title="Suchen & Anfragen stellen"
        className={cn(
          "rounded-md px-2.5 py-1 text-sm transition-colors",
          mode === "demand"
            ? "bg-secondary font-medium"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        Ich suche
      </button>
      <button
        type="button"
        onClick={() => switchMode("supply")}
        title="Gefunden werden & auf Anfragen antworten"
        className={cn(
          "rounded-md px-2.5 py-1 text-sm transition-colors",
          mode === "supply"
            ? "bg-secondary font-medium"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        Ich biete
      </button>
    </div>
  )
}
