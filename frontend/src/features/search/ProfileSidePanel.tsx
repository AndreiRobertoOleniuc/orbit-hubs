import { ExternalLink, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { PublicProfileView } from "@/features/profile/PublicProfileView"
import { cn } from "@/lib/utils"
import type { EntityDto } from "@/types/api"

interface ProfileSidePanelProps {
  entity: EntityDto
  onClose: () => void
  className?: string
}

/**
 * The profile detail panel that slides out next to the search results — the
 * result list stays mounted, so inspecting a match never loses the (expensive)
 * search. Renders the same read-only view as /profile/:entityId directly from
 * the EntityDto already embedded in the search result (no extra fetch).
 */
export function ProfileSidePanel({
  entity,
  onClose,
  className,
}: ProfileSidePanelProps) {
  return (
    <section
      aria-label={`Profil von ${entity.displayName}`}
      className={cn(
        "flex flex-col overflow-hidden rounded-xl bg-card text-card-foreground ring-1 ring-foreground/10",
        className
      )}
    >
      <header className="flex shrink-0 items-center justify-between gap-2 border-b px-4 py-2">
        <span className="truncate text-sm font-medium">
          {entity.displayName}
        </span>
        <div className="flex shrink-0 items-center gap-0.5">
          <Button variant="ghost" size="icon-sm" asChild>
            <a
              href={`/profile/${entity.id}`}
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Profil in neuem Tab öffnen"
            >
              <ExternalLink />
            </a>
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={onClose}
            aria-label="Profil schliessen"
          >
            <X />
          </Button>
        </div>
      </header>

      {/* key re-runs the fade when switching between results */}
      <div
        key={entity.id}
        className="min-h-0 flex-1 animate-in overflow-y-auto p-5 duration-300 fade-in"
      >
        <PublicProfileView entity={entity} />
      </div>
    </section>
  )
}
