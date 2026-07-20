import { Building2, User } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import {
  STATUS_HINT,
  STATUS_LABEL,
  URGENCY_LABEL,
} from "@/features/requests/requestUtils"
import { cn } from "@/lib/utils"
import type { EntityType, RequestStatus, RequestUrgency } from "@/types/api"

const STATUS_STYLE: Record<RequestStatus, string> = {
  open: "bg-green-600/15 text-green-700 dark:text-green-400",
  distributed: "bg-sky-600/15 text-sky-700 dark:text-sky-400",
  contacted: "bg-violet-600/15 text-violet-700 dark:text-violet-400",
  closed: "bg-muted text-muted-foreground",
}

/**
 * Lifecycle pill — "Offen" gets the live dot, "Geschlossen" goes quiet. Carries
 * its own STATUS_HINT tooltip so a standalone pill (list rows, filter chips) is
 * self-explaining, not just on the detail page.
 */
export function RequestStatusBadge({ status }: { status: RequestStatus }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge
          tabIndex={0}
          className={cn(
            "cursor-help hover:bg-current/15 focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none",
            STATUS_STYLE[status]
          )}
        >
          {status === "open" && (
            <span
              aria-hidden="true"
              className="size-1.5 rounded-full bg-green-600 dark:bg-green-400"
            />
          )}
          {STATUS_LABEL[status]}
        </Badge>
      </TooltipTrigger>
      <TooltipContent>{STATUS_HINT[status]}</TooltipContent>
    </Tooltip>
  )
}

const URGENCY_STYLE: Record<RequestUrgency, string> = {
  urgent: "bg-red-600/15 text-red-700 dark:text-red-400",
  soon: "bg-amber-500/15 text-amber-700 dark:text-amber-400",
  flexible: "",
}

/** Urgency pill — renders nothing for "flexibel" (the calm default). */
export function UrgencyBadge({ urgency }: { urgency: RequestUrgency }) {
  if (urgency === "flexible") return null
  return (
    <Badge className={cn("hover:bg-current/15", URGENCY_STYLE[urgency])}>
      {URGENCY_LABEL[urgency]}
    </Badge>
  )
}

/** "Firma" / "Person" author-type chip, as in the board design. */
export function AuthorKindBadge({ type }: { type: EntityType | null }) {
  if (!type) return null
  const org = type === "organization"
  return (
    <Badge variant="outline" className="text-muted-foreground">
      {org ? <Building2 className="size-3" /> : <User className="size-3" />}
      {org ? "Firma" : "Person"}
    </Badge>
  )
}
