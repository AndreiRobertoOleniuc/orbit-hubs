import { useState } from "react"
import { Check, MapPin, TriangleAlert } from "lucide-react"

import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"
import { ContactModal } from "@/features/contact/ContactModal"
import {
  availabilityMismatch,
  availabilityPillText,
  type AvailabilityCriteria,
} from "@/features/profile/availabilityUtils"
import { sourceTypeInfo } from "@/features/profile/profileUtils"
import { cn } from "@/lib/utils"
import { useGetContactedEntityIdsQuery } from "@/lib/api"
import type { SearchCitationDto, SearchResultDto } from "@/types/api"

/** Longer rationales are clamped to three lines with a "weiterlesen" toggle. */
const RATIONALE_CLAMP_CHARS = 240
const CITATIONS_SHOWN = 3

/** Inline "·" between facts — a separator, not a border. */
function Sep() {
  return (
    <span aria-hidden className="text-muted-foreground/40">
      ·
    </span>
  )
}

function Citation({ citation }: { citation: SearchCitationDto }) {
  const info = sourceTypeInfo(citation.sourceType ?? "")
  const label = citation.label ?? info.label
  const external =
    citation.sourceUrl && /^https?:\/\//i.test(citation.sourceUrl)
  const inner = (
    <>
      <span className="w-4 shrink-0 text-[10px] font-semibold text-muted-foreground/60">
        {info.glyph}
      </span>
      <span className="min-w-0 truncate">{label}</span>
    </>
  )
  const base = "flex items-baseline gap-2 text-[13px] text-muted-foreground"
  return external ? (
    <a
      href={citation.sourceUrl!}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(e) => e.stopPropagation()}
      className={cn(
        base,
        "decoration-muted-foreground/40 underline-offset-2 hover:text-foreground hover:underline"
      )}
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
  /** Zeitfilter + Pensum of the search, compared against this profile. */
  criteria?: AvailabilityCriteria
  selected: boolean
  onSelect: () => void
}

/**
 * One match, set as a directory entry rather than a card (design: MatchCard).
 *
 * The reading order is the decision order: who → why → can I get them → proof.
 * Everything is flat type on paper; the only boxed element is the one action,
 * and the only saturated colour is a genuine availability conflict. Selecting
 * the entry opens the profile in the slide-out panel — the search stays put.
 */
export function SearchResultCard({
  result,
  rank,
  query,
  criteria,
  selected,
  onSelect,
}: SearchResultCardProps) {
  const { isAuthenticated } = useAuth()
  const [contactOpen, setContactOpen] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [allCitations, setAllCitations] = useState(false)
  const { data: contactedIds } = useGetContactedEntityIdsQuery(undefined, {
    skip: !isAuthenticated,
  })
  const { entity, rationale, citations, distance } = result
  const isTopMatch = rank === 0
  const contactable = isAuthenticated && entity.claimed
  const alreadyContacted = contactedIds?.includes(entity.id) ?? false

  // "Professor at University of Bern" + org "University of Bern" is the same
  // fact twice — drop the org when the headline already carries it.
  const org =
    entity.orgName &&
    !(entity.headline ?? "")
      .toLowerCase()
      .includes(entity.orgName.toLowerCase())
      ? entity.orgName
      : null
  const subline = [entity.headline, org].filter(Boolean).join(" · ")

  const body = rationale ?? entity.summary
  const clampable = !expanded && (body?.length ?? 0) > RATIONALE_CLAMP_CHARS

  const availability = availabilityPillText(entity.availability)
  const collab = entity.availability?.preferredCollaboration ?? []
  const mismatch = availabilityMismatch(criteria, entity.availability)

  const shownCitations = allCitations
    ? citations
    : citations.slice(0, CITATIONS_SHOWN)
  const restCitations = citations.length - shownCitations.length

  const stop = (fn: () => void) => (e: React.MouseEvent) => {
    e.stopPropagation()
    fn()
  }

  return (
    <div
      onClick={onSelect}
      onKeyDown={(event) => {
        if (
          event.target === event.currentTarget &&
          (event.key === "Enter" || event.key === " ")
        ) {
          event.preventDefault()
          onSelect()
        }
      }}
      role="button"
      tabIndex={0}
      aria-label={`${entity.displayName}, Rang ${rank + 1}, Profil ansehen`}
      aria-pressed={selected}
      className={cn(
        "cursor-pointer rounded-xl bg-card p-5 text-card-foreground ring-1 transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none sm:p-6",
        selected
          ? "ring-2 ring-primary/60"
          : isTopMatch
            ? "ring-primary/25 hover:ring-primary/40"
            : "ring-foreground/10 hover:ring-foreground/20"
      )}
    >
      <div className="flex gap-4 sm:gap-5">
        {/* Index numeral in the margin — rank as a reference number, not a
            trophy. Deliberately the quietest element on the row. */}
        <span
          aria-hidden
          className={cn(
            "hidden w-6 shrink-0 pt-1 text-xs tabular-nums sm:block",
            isTopMatch ? "text-primary" : "text-muted-foreground/50"
          )}
        >
          {String(rank + 1).padStart(2, "0")}
        </span>

        <div className="min-w-0 flex-1">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
            <div className="min-w-0">
              {isTopMatch && (
                <p className="mb-1 text-[11px] font-medium tracking-wide text-primary">
                  Beste Passung
                </p>
              )}
              <h3 className="text-lg leading-tight font-semibold tracking-tight">
                <button
                  type="button"
                  onClick={stop(onSelect)}
                  className="text-left decoration-1 underline-offset-4 hover:underline"
                >
                  {entity.displayName}
                </button>
              </h3>
              {subline && (
                <p className="mt-1 text-sm text-muted-foreground">{subline}</p>
              )}
            </div>

            <div className="flex shrink-0 items-center gap-3 sm:pt-0.5">
              <button
                type="button"
                onClick={stop(onSelect)}
                className="text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
              >
                Profil ansehen
              </button>
              <Button
                size="sm"
                variant={alreadyContacted ? "outline" : "default"}
                disabled={!contactable}
                title={
                  contactable
                    ? undefined
                    : !isAuthenticated
                      ? "Anmeldung nötig"
                      : "Profil nicht beansprucht — Kontakt nicht möglich"
                }
                onClick={stop(() => setContactOpen(true))}
              >
                {alreadyContacted && <Check />}
                {alreadyContacted ? "Erneut kontaktieren" : "Kontakt aufnehmen"}
              </Button>
            </div>
          </div>

          {/* Evidence-forward (spec §13.2): the grounded "why" is the body copy
              of the entry — not a tinted panel that reads as a chat bubble. */}
          {body && (
            <>
              <p
                className={cn(
                  "mt-3 max-w-[64ch] text-[15px] leading-relaxed text-foreground/85",
                  clampable && "line-clamp-3"
                )}
              >
                {body}
              </p>
              {(body.length > RATIONALE_CLAMP_CHARS || expanded) && (
                <button
                  type="button"
                  onClick={stop(() => setExpanded((v) => !v))}
                  className="mt-1 text-[13px] text-primary underline-offset-4 hover:underline"
                >
                  {expanded ? "weniger" : "weiterlesen"}
                </button>
              )}
            </>
          )}

          {/* One fact line, same position on every entry: where, when, how —
              and the conflict with this search, which is the only colour. */}
          <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px] text-muted-foreground">
            {distance && (
              <span className="inline-flex items-center gap-1">
                <MapPin aria-hidden className="size-3.5" />
                {distance}
              </span>
            )}
            {distance && availability && <Sep />}
            {availability && <span>{availability}</span>}
            {collab.length > 0 && (availability || distance) && <Sep />}
            {collab.length > 0 && <span>{collab.join(", ")}</span>}
            {mismatch.pensum && (
              <span
                className="inline-flex items-center gap-1 font-medium text-amber-700 dark:text-amber-500"
                aria-label={`Abweichung von deiner Suche: gesuchtes Pensum ${mismatch.pensum.wantedPct} Prozent, verfügbar ${mismatch.pensum.availablePct} Prozent`}
              >
                <TriangleAlert aria-hidden className="size-3.5" />
                Pensum: gesucht {mismatch.pensum.wantedPct} %, verfügbar{" "}
                {mismatch.pensum.availablePct} %
              </span>
            )}
            {mismatch.noDateOverlap && (
              <span
                className="inline-flex items-center gap-1 font-medium text-amber-700 dark:text-amber-500"
                aria-label="Abweichung von deiner Suche: Zeitraum überschneidet sich nicht"
              >
                <TriangleAlert aria-hidden className="size-3.5" />
                Zeitraum überschneidet sich nicht
              </span>
            )}
          </div>

          {citations.length > 0 && (
            <div className="mt-4 flex gap-4">
              {/* Sidenote label in the margin instead of a heading above */}
              <span className="hidden w-12 shrink-0 pt-px text-[11px] text-muted-foreground/60 sm:block">
                Belege
              </span>
              <ul className="min-w-0 flex-1 space-y-1">
                {shownCitations.map((c) => (
                  <li key={c.sourceId} className="min-w-0">
                    <Citation citation={c} />
                  </li>
                ))}
                {restCitations > 0 && (
                  <li>
                    <button
                      type="button"
                      onClick={stop(() => setAllCitations(true))}
                      className="text-[13px] text-primary underline-offset-4 hover:underline"
                    >
                      +{restCitations} weitere
                    </button>
                  </li>
                )}
              </ul>
            </div>
          )}
        </div>
      </div>

      {contactable && (
        <span onClick={(e) => e.stopPropagation()}>
          <ContactModal
            toEntityId={entity.id}
            toName={entity.displayName}
            quote={
              rationale
                ? `Gesucht: «${query}»\nWarum passend: ${rationale}`
                : `Gesucht: «${query}»`
            }
            searchedText={query}
            open={contactOpen}
            onOpenChange={setContactOpen}
          />
        </span>
      )}
    </div>
  )
}
