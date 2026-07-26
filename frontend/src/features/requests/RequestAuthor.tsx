import { useNavigate } from "react-router"
import { Building2 } from "lucide-react"

import { initials } from "@/features/requests/requestUtils"
import { cn } from "@/lib/utils"
import type { EntityType } from "@/types/api"

interface RequestAuthorProps {
  entityId: number | null
  name: string | null
  orgName: string | null
  type: EntityType | null
  className?: string
}

/**
 * "Who asks" — avatar + name (+ org line) as on the design's board cards.
 * Links to the author's public profile when their entity is known; falls back
 * to a neutral "Ausgeschrieben über Orbit" when the creator has no profile.
 */
export function RequestAuthor({
  entityId,
  name,
  orgName,
  type,
  className,
}: RequestAuthorProps) {
  const navigate = useNavigate()

  if (!name) {
    return (
      <span className={cn("text-sm text-muted-foreground", className)}>
        Ausgeschrieben über Orbit
      </span>
    )
  }

  const org = type === "organization"
  const inner = (
    <>
      <span
        aria-hidden="true"
        className={cn(
          "flex size-7 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold",
          org ? "bg-primary/10 text-primary" : "bg-muted text-muted-foreground"
        )}
      >
        {org ? <Building2 className="size-3.5" /> : initials(name)}
      </span>
      <span className="min-w-0 text-left leading-tight">
        <span className="block truncate text-sm font-medium">{name}</span>
        {!org && orgName && (
          <span className="block truncate text-xs text-muted-foreground">
            {orgName}
          </span>
        )}
      </span>
    </>
  )

  if (entityId == null) {
    return (
      <span className={cn("flex min-w-0 items-center gap-2", className)}>
        {inner}
      </span>
    )
  }

  return (
    <button
      type="button"
      title={`Profil ansehen: ${name}`}
      onClick={(e) => {
        e.stopPropagation()
        navigate(`/profile/${entityId}`)
      }}
      className={cn(
        "flex min-w-0 items-center gap-2 rounded-md transition-opacity hover:opacity-75",
        className
      )}
    >
      {inner}
    </button>
  )
}
