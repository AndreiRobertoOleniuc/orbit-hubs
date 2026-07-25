import { CalendarClock } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { availabilityPillText } from "@/features/profile/availabilityUtils"
import type { AvailabilityDto } from "@/types/api"

/**
 * Read-only availability pill ("Offen für Anfragen · 60 %, Aug–Okt"), shown in
 * the profile header. Renders nothing when the profile isn't open to requests.
 */
export function AvailabilityBadge({
  availability,
}: {
  availability: AvailabilityDto | null
}) {
  const text = availabilityPillText(availability)
  if (!text) return null
  return (
    // h-auto + whitespace-normal so the pill wraps its own text in a narrow
    // column instead of forcing the column wider (badges are overflow-hidden).
    <Badge className="h-auto max-w-full items-start bg-green-600/15 py-1 leading-snug whitespace-normal text-green-700 hover:bg-green-600/25 dark:text-green-400">
      <CalendarClock className="mt-0.5 size-3 shrink-0" /> {text}
    </Badge>
  )
}
