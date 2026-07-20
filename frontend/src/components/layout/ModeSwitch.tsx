import { useLocation, useNavigate } from "react-router-dom"
import { ChevronDown } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useProfileMode } from "@/features/profile/useProfileMode"
import { useUpdateEntityModeMutation } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { ProfileMode } from "@/types/api"

/** Pages that only exist in one mode — used to re-route on a mode switch.
 *  (Postfach `/inbox` is intentionally absent: it exists in both modes.) */
const DEMAND_PAGES = ["/search", "/requests"]
const SUPPLY_PAGES = ["/board"]

const MODE_LABEL: Record<ProfileMode, string> = {
  demand: "Ich suche",
  supply: "Ich biete",
}

/**
 * The «Ich suche» / «Ich biete» mode switch — a deliberate dropdown (not a
 * one-click header toggle, which testers flipped by accident). Persists the
 * choice on the user's entity and, if the current page doesn't exist in the new
 * mode, routes to that mode's landing page. Hidden until the user has a profile.
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
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className={cn("gap-1.5", className)}
          aria-label="Modus wechseln"
        >
          {MODE_LABEL[mode]}
          <ChevronDown className="size-3.5 opacity-60" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>Modus</DropdownMenuLabel>
        <DropdownMenuCheckboxItem
          checked={mode === "demand"}
          onSelect={() => switchMode("demand")}
        >
          Ich suche
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          checked={mode === "supply"}
          onSelect={() => switchMode("supply")}
        >
          Ich biete
        </DropdownMenuCheckboxItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
