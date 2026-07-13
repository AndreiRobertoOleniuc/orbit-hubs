import { MapPin } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { AvailabilityBadge } from "@/features/profile/AvailabilityBadge"
import { sourceTypeInfo } from "@/features/profile/profileUtils"
import { cn } from "@/lib/utils"
import type { SearchCitationDto, SearchResultDto } from "@/types/api"

function CitationChip({ citation }: { citation: SearchCitationDto }) {
  const info = sourceTypeInfo(citation.sourceType ?? "")
  const label = citation.label ?? info.label
  const external =
    citation.sourceUrl && /^https?:\/\//i.test(citation.sourceUrl)
  const base =
    "inline-flex max-w-full items-center gap-1.5 rounded-full border px-3 py-1 text-xs text-muted-foreground"
  const inner = (
    <>
      <span className="text-[10px] font-semibold">{info.glyph}</span>
      <span className="truncate">{label}</span>
    </>
  )
  return external ? (
    <a
      href={citation.sourceUrl!}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(e) => e.stopPropagation()}
      className={`${base} transition-colors hover:border-foreground/30 hover:text-foreground`}
    >
      {inner}
    </a>
  ) : (
    <span className={base}>{inner}</span>
  )
}

interface SearchResultCardProps {
  result: SearchResultDto
  selected: boolean
  onSelect: () => void
}

/**
 * One match card (design: MatchCard) — identity + availability pill, the grounded
 * rationale (falls back to the entity summary), cited evidence chips, optional
 * collaboration badges, and a footer with the best-effort distance + profile link.
 *
 * Selecting a card opens the profile in the slide-out side panel next to the
 * results instead of navigating away — the search stays on screen.
 */
export function SearchResultCard({
  result,
  selected,
  onSelect,
}: SearchResultCardProps) {
  const { entity, rationale, citations, distance } = result
  const collab = entity.availability?.preferredCollaboration ?? []
  const description = rationale ?? entity.summary

  return (
    <div
      onClick={onSelect}
      className={cn(
        "cursor-pointer rounded-xl bg-card p-6 text-card-foreground ring-1 transition-shadow",
        selected
          ? "ring-2 ring-primary/60"
          : "ring-foreground/10 hover:ring-foreground/20"
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="font-semibold">
            <button
              type="button"
              onClick={onSelect}
              className="underline-offset-2 hover:underline"
            >
              {entity.displayName}
            </button>
            {entity.headline && (
              <span className="font-normal text-muted-foreground">
                {" · "}
                {entity.headline}
              </span>
            )}
          </div>
          {entity.orgName && (
            <div className="mt-0.5 text-sm text-muted-foreground">
              {entity.orgName}
            </div>
          )}
        </div>
        <AvailabilityBadge availability={entity.availability} />
      </div>

      {description && (
        <p className="mt-4 text-[15px] leading-relaxed">{description}</p>
      )}

      {citations.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2">
          {citations.map((c) => (
            <CitationChip key={c.sourceId} citation={c} />
          ))}
        </div>
      )}

      {collab.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {collab.map((c) => (
            <Badge key={c} variant="secondary">
              {c}
            </Badge>
          ))}
        </div>
      )}

      <div className="mt-5 flex items-center justify-between gap-3 border-t pt-4">
        <span className="inline-flex items-center gap-1 text-sm text-muted-foreground">
          {distance && (
            <>
              <MapPin className="size-3.5" />
              {distance}
            </>
          )}
        </span>
        <Button variant="outline" onClick={onSelect}>
          Profil ansehen
        </Button>
      </div>
    </div>
  )
}
