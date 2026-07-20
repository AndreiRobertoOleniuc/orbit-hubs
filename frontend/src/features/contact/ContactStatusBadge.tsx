import { Check, Clock, X } from "lucide-react"

import { cn } from "@/lib/utils"
import type { ContactStatus } from "@/types/api"

const STATUS_STYLE: Record<
  ContactStatus,
  { label: string; className: string; icon: typeof Clock }
> = {
  pending: {
    label: "Wartet auf Antwort",
    icon: Clock,
    className:
      "border-amber-300/60 bg-amber-50 text-amber-700 dark:border-amber-400/30 dark:bg-amber-950/40 dark:text-amber-300",
  },
  accepted: {
    label: "Angenommen",
    icon: Check,
    className:
      "border-green-300/60 bg-green-50 text-green-700 dark:border-green-400/30 dark:bg-green-950/40 dark:text-green-300",
  },
  declined: {
    label: "Abgelehnt",
    icon: X,
    className: "border-border bg-muted text-muted-foreground",
  },
}

/** The handshake outcome of a contact request (§11), for both Postfach sides. */
export function ContactStatusBadge({ status }: { status: ContactStatus }) {
  const { label, className, icon: Icon } = STATUS_STYLE[status]
  return (
    <span
      className={cn(
        "inline-flex h-5 items-center gap-1 rounded-full border px-2 text-xs font-medium",
        className
      )}
    >
      <Icon className="size-3" />
      {label}
    </span>
  )
}
