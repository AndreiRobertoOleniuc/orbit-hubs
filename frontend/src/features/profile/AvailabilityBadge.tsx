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
    <Badge className="bg-green-600/15 text-green-700 hover:bg-green-600/25 dark:text-green-400">
      <CalendarClock className="size-3" /> {text}
    </Badge>
  )
}
