import { AvailabilityBadge } from "@/features/profile/AvailabilityBadge"
import { collaborationLabel } from "@/features/profile/availabilityUtils"
import type { AvailabilityDto } from "@/types/api"

/**
 * The draft rendered the way a searcher sees it. Answers "what did I just set?"
 * with the values themselves rather than with the input labels — which is what
 * made placeholders readable as saved content in usability testing.
 *
 * Renders the same badge + detail line as the profile header, from unsaved
 * state, so the preview and the real thing can't say different things.
 */
export function AvailabilityPreview({
  availability,
}: {
  availability: AvailabilityDto
}) {
  const collab = availability.preferredCollaboration ?? []
  const detail = [collab.map(collaborationLabel).join(", "), availability.note]
    .filter(Boolean)
    .join(" · ")

  return (
    <div className="rounded-lg border border-dashed bg-muted/30 p-3">
      <p className="text-xs font-medium text-muted-foreground">
        Vorschau — so sehen andere deine Verfügbarkeit
      </p>
      {availability.openToRequests ? (
        <div className="mt-2 space-y-1">
          <AvailabilityBadge availability={availability} />
          {detail && <p className="text-sm text-muted-foreground">{detail}</p>}
        </div>
      ) : (
        <p className="mt-2 text-sm text-muted-foreground">
          Kein Verfügbarkeits-Badge. Dein Profil bleibt auffindbar, wird in
          Suchergebnissen aber tiefer eingestuft.
        </p>
      )}
    </div>
  )
}
