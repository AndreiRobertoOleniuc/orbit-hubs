import { useState } from "react"
import { Link } from "react-router"
import {
  ArrowLeft,
  CalendarClock,
  CalendarX2,
  Check,
  Loader2,
  Lock,
  Mail,
  MapPin,
  Users,
} from "lucide-react"

import { NotFoundState } from "@/components/NotFoundState"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"
import { ContactModal } from "@/features/contact/ContactModal"
import { initials } from "@/features/profile/profileUtils"
import {
  AuthorKindBadge,
  RequestStatusBadge,
  UrgencyBadge,
} from "@/features/requests/RequestBadges"
import { RequestAuthor } from "@/features/requests/RequestAuthor"
import {
  STATUS_HINT,
  STATUS_LABEL,
  STATUS_ORDER,
  daysUntil,
  formatDayYear,
  relativeTime,
  scopeLabel,
  wantWindowLabel,
} from "@/features/requests/requestUtils"
import {
  useGetContactedEntityIdsQuery,
  useGetRequestQuery,
  useUpdateRequestStatusMutation,
} from "@/lib/api"
import { cn } from "@/lib/utils"
import type {
  DistributedProfileDto,
  RequestDto,
  RequestStatus,
} from "@/types/api"

/**
 * The lifecycle as a compact stepper: Offen → Verteilt → Kontaktiert →
 * Geschlossen. Reached stages are filled; upcoming stages stay muted. A
 * request closed early simply shows the terminal stage as reached.
 */
function LifecycleStepper({ status }: { status: RequestStatus }) {
  const current = STATUS_ORDER.indexOf(status)
  return (
    <ol className="flex items-center gap-0">
      {STATUS_ORDER.map((s, i) => {
        const reached = i <= current
        const isCurrent = i === current
        return (
          <li key={s} className="flex flex-1 items-center last:flex-none">
            <span className="flex flex-col items-center gap-1.5">
              <span
                className={cn(
                  "flex size-6 items-center justify-center rounded-full text-[10px] font-semibold transition-colors",
                  reached
                    ? "bg-primary text-primary-foreground"
                    : "bg-muted text-muted-foreground",
                  isCurrent && "ring-2 ring-primary/30"
                )}
              >
                {i < current ? <Check className="size-3.5" /> : i + 1}
              </span>
              <span
                className={cn(
                  "text-[11px] whitespace-nowrap",
                  isCurrent
                    ? "font-semibold text-foreground"
                    : "text-muted-foreground"
                )}
              >
                {STATUS_LABEL[s]}
              </span>
            </span>
            {i < STATUS_ORDER.length - 1 && (
              <span
                aria-hidden="true"
                className={cn(
                  "mx-2 mb-5 h-px flex-1",
                  i < current ? "bg-primary" : "bg-border"
                )}
              />
            )}
          </li>
        )
      })}
    </ol>
  )
}

/** How a profile ended up in the request's distribution (§8.2). */
function distributedReason(profile: DistributedProfileDto): string {
  const tags = (profile.matchedTags ?? []).map((t) => `«${t}»`).join(", ")
  if (profile.reason === "subscription") {
    return tags ? `Abonniertes Thema ${tags}` : "Abonniertes Thema"
  }
  return tags ? `Profil-Tags ${tags}` : "Profil-Match"
}

/** One notified profile — identity, why it matched, and a direct contact. */
function NotifiedProfileRow({
  profile,
  request,
}: {
  profile: DistributedProfileDto
  request: RequestDto
}) {
  const [contactOpen, setContactOpen] = useState(false)
  const name = profile.name ?? "Profil"
  return (
    <li className="flex flex-wrap items-center gap-3 py-3">
      <span
        aria-hidden="true"
        className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/15 text-xs font-semibold text-primary"
      >
        {initials(name)}
      </span>
      <div className="min-w-0 flex-1">
        <div className="text-sm font-medium">
          <Link
            to={`/profile/${profile.entityId}`}
            className="underline-offset-2 hover:underline"
          >
            {name}
          </Link>
          {profile.orgName && (
            <span className="font-normal text-muted-foreground">
              {" · "}
              {profile.orgName}
            </span>
          )}
        </div>
        <p className="mt-0.5 text-xs text-muted-foreground">
          {distributedReason(profile)}
        </p>
      </div>
      <Button size="sm" onClick={() => setContactOpen(true)}>
        Kontaktieren
      </Button>
      <ContactModal
        toEntityId={profile.entityId}
        toName={name}
        quote={`«${request.title?.trim() || request.queryText}»`}
        requestId={request.id}
        searchedText={request.queryText}
        open={contactOpen}
        onOpenChange={setContactOpen}
      />
    </li>
  )
}

/** The notified-profiles list on the owner's request (owner-only). */
function NotifiedProfiles({ request }: { request: RequestDto }) {
  const profiles = request.distributions ?? []
  if (profiles.length === 0) return null
  return (
    <div className="mt-4 border-t pt-4">
      <h3 className="text-sm font-medium">Benachrichtigte Profile</h3>
      <p className="mt-0.5 text-xs text-muted-foreground">
        Diese Profile wurden über deine Anfrage informiert. Du kannst sie auch
        direkt kontaktieren.
      </p>
      <ul className="mt-2 divide-y">
        {profiles.map((p) => (
          <NotifiedProfileRow key={p.entityId} profile={p} request={request} />
        ))}
      </ul>
    </div>
  )
}

function nextLifecycleAction(request: RequestDto): string | null {
  if (request.status === "open") {
    return "Nächster Schritt: Warte auf die erste Verteilung."
  }
  if (request.status === "distributed") {
    return (request.distributionCount ?? 0) > 0
      ? "Nächster Schritt: Prüfe die benachrichtigten Profile und kontaktiere bei Bedarf passende Personen."
      : "Nächster Schritt: Behalte den Status im Blick oder schliesse die Anfrage, wenn sie nicht mehr aktuell ist."
  }
  if (request.status === "contacted") {
    return "Nächster Schritt: Verfolge die Kontaktaufnahme im Postfach."
  }
  return null
}

/** Owner-only lifecycle card: stepper, stage explanation, close action. */
function OwnerLifecycle({ request }: { request: RequestDto }) {
  const [updateStatus, { isLoading }] = useUpdateRequestStatusMutation()
  const closed = request.status === "closed"
  const nextAction = nextLifecycleAction(request)

  return (
    <section className="rounded-xl bg-card p-6 ring-1 ring-foreground/10">
      <h2 className="text-sm font-semibold">Deine Anfrage — Lebenszyklus</h2>
      <div className="mt-5">
        <LifecycleStepper status={request.status} />
      </div>
      <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
        {STATUS_HINT[request.status]}
      </p>
      {nextAction && <p className="mt-1.5 text-sm font-medium">{nextAction}</p>}
      {(request.status === "contacted" || request.status === "closed") && (
        <Link
          to="/inbox"
          className="mt-2 flex w-fit items-center gap-1 text-sm font-medium text-primary underline-offset-2 hover:underline"
        >
          <Mail className="size-4" />
          Kontaktaufnahmen im Postfach ansehen
        </Link>
      )}
      {request.distributionCount != null && request.status !== "open" && (
        <p className="mt-2 flex items-center gap-1.5 text-sm">
          <Users className="size-4 text-muted-foreground" />
          <span className="font-medium">
            {request.distributionCount}{" "}
            {request.distributionCount === 1 ? "Profil" : "Profile"}{" "}
            benachrichtigt
          </span>
          {request.wideningLevel && (
            <span className="text-muted-foreground">
              · Stufe:{" "}
              {request.wideningLevel === "hub"
                ? "Hub"
                : request.wideningLevel === "region"
                  ? "Region"
                  : "Ganze Schweiz"}
            </span>
          )}
        </p>
      )}
      {request.distributionCount === 0 &&
        request.status !== "open" &&
        !closed && (
          <p className="mt-4 rounded-lg border border-dashed px-3.5 py-3 text-sm leading-relaxed text-muted-foreground">
            Zum aktuellen Verteilungszeitpunkt wurde kein vorhandenes Profil
            benachrichtigt. Deine Anfrage bleibt öffentlich auf dem Board
            sichtbar.
          </p>
        )}
      <NotifiedProfiles request={request} />
      {!closed && (
        <div className="mt-4 flex flex-wrap items-center gap-3 border-t pt-4">
          <Button
            variant="outline"
            disabled={isLoading}
            onClick={() => updateStatus({ id: request.id, status: "closed" })}
          >
            {isLoading && <Loader2 className="size-4 animate-spin" />}
            Anfrage schliessen
          </Button>
          <span className="text-xs text-muted-foreground">
            Gelöst oder nicht mehr aktuell? Geschlossene Anfragen verschwinden
            vom Board, bleiben aber für dich sichtbar.
          </span>
        </div>
      )}
    </section>
  )
}

/**
 * Public request detail (design: DetailScreen) — full text, meta, tags, and
 * author. Owners additionally manage the lifecycle here; everyone else can
 * contact the author via the handoff dialog (§11).
 */
export function RequestDetailView({ requestId }: { requestId: string }) {
  const { isAuthenticated, status } = useAuth()
  const [contactOpen, setContactOpen] = useState(false)
  // `mine` is resolved from the caller's token, so the request must not be
  // fetched until Clerk has settled — otherwise a hard reload fires the public
  // GET before the token is attached and the owner sees the read-only view.
  const authSettled = status === "authenticated" || status === "unauthenticated"
  const {
    data: request,
    isLoading,
    isError,
  } = useGetRequestQuery(requestId, { skip: !authSettled })
  const { data: contactedIds } = useGetContactedEntityIdsQuery(undefined, {
    skip: !isAuthenticated,
  })

  if (!authSettled || isLoading) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <Loader2 className="size-6 animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">Anfrage wird geladen…</p>
      </div>
    )
  }

  if (isError || !request) {
    return (
      <NotFoundState
        title="Anfrage nicht gefunden"
        description="Diese Anfrage existiert nicht oder wurde entfernt."
        actionLabel="Zu den offenen Anfragen"
        actionTo="/board"
      />
    )
  }

  const title = request.title?.trim() || request.queryText
  const body =
    request.description?.trim() ||
    (request.title?.trim() ? request.queryText : null)
  const showQuery =
    Boolean(request.title?.trim()) &&
    Boolean(request.description?.trim()) &&
    request.queryText !== request.description
  const window = wantWindowLabel(
    request.wantFrom,
    request.wantTo,
    request.wantPensumPct
  )
  const remaining = daysUntil(request.deadline)

  return (
    <div className="mx-auto max-w-2xl py-6">
      <Link
        to={request.mine ? "/requests" : "/board"}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        {request.mine ? "Meine Anfragen" : "Offene Anfragen"}
      </Link>

      <div className="mt-4 rounded-xl bg-card p-7 ring-1 ring-foreground/10">
        <div className="flex flex-wrap items-center gap-1.5">
          <RequestStatusBadge status={request.status} />
          <UrgencyBadge urgency={request.urgency} />
          <AuthorKindBadge type={request.authorType} />
          <span className="inline-flex h-5 items-center gap-1 rounded-4xl border px-2 text-xs text-muted-foreground">
            <MapPin className="size-3" />
            {scopeLabel(request)}
          </span>
        </div>

        <h1 className="mt-4 text-[22px] leading-snug font-bold tracking-tight">
          {title}
        </h1>

        {body && <p className="mt-3.5 text-[15.5px] leading-relaxed">{body}</p>}

        {showQuery && (
          <p className="mt-3 border-l-2 border-primary/30 pl-3 text-sm text-muted-foreground italic">
            Gesucht: «{request.queryText}»
          </p>
        )}

        {(window || request.deadline) && (
          <div className="mt-5 space-y-1.5 rounded-lg bg-muted/50 px-4 py-3 text-sm">
            {window && (
              <div className="flex items-center gap-2">
                <CalendarClock className="size-4 text-muted-foreground" />
                <span>
                  <span className="text-muted-foreground">Zeitraum:</span>{" "}
                  {window}
                </span>
              </div>
            )}
            {request.deadline && (
              <div className="flex items-center gap-2">
                <CalendarX2 className="size-4 text-muted-foreground" />
                <span>
                  <span className="text-muted-foreground">Antworten bis:</span>{" "}
                  {formatDayYear(request.deadline)}
                  {remaining != null && remaining >= 0 && (
                    <span
                      className={cn(
                        "ml-1.5 font-medium",
                        remaining <= 3
                          ? "text-red-600 dark:text-red-400"
                          : "text-amber-700 dark:text-amber-400"
                      )}
                    >
                      ({remaining === 0 ? "heute" : `noch ${remaining} Tage`})
                    </span>
                  )}
                  {remaining != null && remaining < 0 && (
                    <span className="ml-1.5 text-muted-foreground">
                      (abgelaufen)
                    </span>
                  )}
                </span>
              </div>
            )}
          </div>
        )}

        {request.tags.length > 0 && (
          <div className="mt-5 flex flex-wrap gap-1.5">
            {request.tags.map((t) => (
              <span
                key={t.id}
                className="inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
              >
                {t.label}
              </span>
            ))}
          </div>
        )}

        <div className="mt-6 flex flex-wrap items-center gap-3 border-t pt-4">
          <RequestAuthor
            entityId={request.authorEntityId}
            name={request.authorName}
            orgName={request.authorOrgName}
            type={request.authorType}
          />
          {request.createdAt && (
            <span className="ml-auto text-sm text-muted-foreground">
              Veröffentlicht {relativeTime(request.createdAt)}
            </span>
          )}
        </div>
      </div>

      <div className="mt-5">
        {request.mine ? (
          <OwnerLifecycle request={request} />
        ) : (
          <div className="text-center">
            {(() => {
              const alreadyContacted =
                request.authorEntityId != null &&
                (contactedIds?.includes(request.authorEntityId) ?? false)
              return (
                <Button
                  className="w-full"
                  variant={alreadyContacted ? "outline" : "default"}
                  disabled={!isAuthenticated || request.authorEntityId == null}
                  onClick={() => setContactOpen(true)}
                >
                  {!isAuthenticated && <Lock className="size-3.5" />}
                  {alreadyContacted && <Check className="size-4" />}
                  {alreadyContacted
                    ? "Erneut kontaktieren"
                    : "Kontakt aufnehmen"}
                </Button>
              )
            })()}
            {(!isAuthenticated || request.authorEntityId == null) && (
              <p className="mt-2.5 text-xs text-muted-foreground">
                {!isAuthenticated
                  ? "Anmeldung nötig, um Kontakt aufzunehmen."
                  : "Diese Anfrage hat kein verknüpftes Profil — Kontakt nicht möglich."}
              </p>
            )}
            {request.authorEntityId != null && (
              <ContactModal
                toEntityId={request.authorEntityId}
                toName={request.authorName ?? "Die Person"}
                quote={`«${title}»`}
                requestId={request.id}
                searchedText={request.queryText}
                open={contactOpen}
                onOpenChange={setContactOpen}
              />
            )}
          </div>
        )}
      </div>
    </div>
  )
}
