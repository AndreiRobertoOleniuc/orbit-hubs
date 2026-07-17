import { useState } from "react"
import { Check, MapPin, Trophy } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"
import { ContactModal } from "@/features/contact/ContactModal"
import { AvailabilityBadge } from "@/features/profile/AvailabilityBadge"
import { sourceTypeInfo } from "@/features/profile/profileUtils"
import { useGetContactedEntityIdsQuery } from "@/lib/api"
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
  rank: number
  /** The search query — frozen into the contact's sender context. */
  query: string
  selected: boolean
  onSelect: () => void
}

/**
 * One match card (design: MatchCard) — identity + availability pill, the grounded
 * rationale (falls back to the entity summary), cited evidence chips, optional
 * collaboration badges, and a footer with the best-effort distance + profile link.
 *
 * The top result gets a "Bester Treffer" badge; every card shows its rank
 * number so the user can see the ordering at a glance.
 *
 * Selecting a card opens the profile in the slide-out side panel next to the
 * results instead of navigating away — the search stays on screen.
 */
export function SearchResultCard({
  result,
  rank,
  query,
  selected,
  onSelect,
}: SearchResultCardProps) {
  const { isAuthenticated } = useAuth()
  const [contactOpen, setContactOpen] = useState(false)
  const { data: contactedIds } = useGetContactedEntityIdsQuery(undefined, {
    skip: !isAuthenticated,
  })
  const { entity, rationale, citations, distance } = result
  const collab = entity.availability?.preferredCollaboration ?? []
  const description = rationale ?? entity.summary
  const isTopMatch = rank === 0
  const contactable = isAuthenticated && entity.claimed
  const alreadyContacted = contactedIds?.includes(entity.id) ?? false

  return (
    <div
      onClick={onSelect}
      className={cn(
        "cursor-pointer rounded-xl bg-card p-6 text-card-foreground ring-1 transition-shadow",
        selected
          ? "ring-2 ring-primary/60"
          : isTopMatch
            ? "ring-primary/30 hover:ring-primary/40"
            : "ring-foreground/10 hover:ring-foreground/20"
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex min-w-0 items-start gap-3">
          {/* Fixed-width rank gutter so names line up across cards */}
          <span
            aria-label={`Rang ${rank + 1}`}
            className={cn(
              "flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold",
              isTopMatch
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground"
            )}
          >
            {isTopMatch ? <Trophy className="size-3" /> : rank + 1}
          </span>
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
        </div>
        <div className="flex shrink-0 items-center gap-1.5">
          {isTopMatch && (
            <Badge
              variant="outline"
              className="gap-1 border-primary/30 text-primary"
            >
              <Trophy className="size-3" />
              Bester Treffer
            </Badge>
          )}
          <AvailabilityBadge availability={entity.availability} />
        </div>
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
        <span className="flex items-center gap-2">
          <Button variant="outline" onClick={onSelect}>
            Profil ansehen
          </Button>
          <Button
            variant={alreadyContacted ? "outline" : "default"}
            disabled={!contactable}
            title={
              contactable
                ? undefined
                : !isAuthenticated
                  ? "Anmeldung nötig"
                  : "Profil nicht beansprucht — Kontakt nicht möglich"
            }
            onClick={(e) => {
              e.stopPropagation()
              setContactOpen(true)
            }}
          >
            {alreadyContacted && <Check className="size-4" />}
            {alreadyContacted ? "Erneut kontaktieren" : "Kontakt aufnehmen"}
          </Button>
        </span>
      </div>

      {contactable && (
        <span onClick={(e) => e.stopPropagation()}>
          <ContactModal
            toEntityId={entity.id}
            toName={entity.displayName}
            quote={`«${query}»`}
            searchedText={query}
            open={contactOpen}
            onOpenChange={setContactOpen}
          />
        </span>
      )}
    </div>
  )
}
