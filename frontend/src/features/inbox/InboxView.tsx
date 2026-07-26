import { useState } from "react"
import { Link, useSearchParams } from "react-router"
import { Check, ClipboardCopy, Inbox, Loader2, Mail, Send } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ContactModal } from "@/features/contact/ContactModal"
import { ContactRespondModal } from "@/features/contact/ContactRespondModal"
import { ContactStatusBadge } from "@/features/contact/ContactStatusBadge"
import { initials } from "@/features/profile/profileUtils"
import { relativeTime } from "@/features/requests/requestUtils"
import { useGetInboxQuery, useRespondContactMutation } from "@/lib/api"
import type {
  InboxContactDto,
  InboxDistributionDto,
  InboxSentContactDto,
} from "@/types/api"

/** "Verteilt, weil …" — the exact, tag-derived reason string (§8.2). */
function distributionReason(d: InboxDistributionDto): string {
  const tags = (d.matchedTags ?? []).map((t) => `«${t}»`).join(", ")
  if (d.reason === "subscription") {
    return tags
      ? `Verteilt über dein abonniertes Thema ${tags}.`
      : "Verteilt über eines deiner abonnierten Themen."
  }
  return tags
    ? `Verteilt, weil dein Profil die Tags ${tags} trägt.`
    : "Verteilt, weil dein Profil zu den gesuchten Themen passt."
}

/** Revealed email actions — the conversation continues outside Orbit. */
function EmailActions({ email }: { email: string }) {
  const [copyState, setCopyState] = useState<"idle" | "copied" | "error">(
    "idle"
  )

  const copyEmail = async () => {
    try {
      await navigator.clipboard.writeText(email)
      setCopyState("copied")
      window.setTimeout(() => setCopyState("idle"), 2000)
    } catch {
      setCopyState("error")
    }
  }

  return (
    <div className="flex min-w-0 flex-col gap-2 sm:items-end">
      <div className="min-w-0 text-sm">
        <span className="text-muted-foreground">E-Mail: </span>
        <span className="font-medium break-all select-all">{email}</span>
      </div>
      <p className="text-xs text-muted-foreground">
        Orbit-Übergabe abgeschlossen. Schreibe per E-Mail weiter.
      </p>
      <div className="grid w-full grid-cols-1 gap-2 sm:flex sm:w-auto">
        <Button size="sm" asChild>
          <a href={`mailto:${email}`}>
            <Mail className="size-4" /> E-Mail schreiben
          </a>
        </Button>
        <Button
          size="sm"
          variant="outline"
          onClick={() => void copyEmail()}
          aria-live="polite"
        >
          {copyState === "copied" ? (
            <>
              <Check className="size-4" /> Kopiert
            </>
          ) : (
            <>
              <ClipboardCopy className="size-4" />
              {copyState === "error"
                ? "Kopieren fehlgeschlagen"
                : "Adresse kopieren"}
            </>
          )}
        </Button>
      </div>
    </div>
  )
}

/** One incoming contact request — respond (accept/decline) while pending. */
function ReceivedCard({ contact }: { contact: InboxContactDto }) {
  const ctx = contact.senderContext
  const name = ctx?.name ?? contact.fromName ?? "Unbekannt"
  const org = ctx?.org ?? contact.fromOrgName
  const searched = ctx?.searchedText
  const [replyOpen, setReplyOpen] = useState(false)
  const [confirmDecline, setConfirmDecline] = useState(false)
  const [respond, { isLoading }] = useRespondContactMutation()

  return (
    <div className="rounded-xl bg-card p-4 ring-1 ring-foreground/10 sm:p-5">
      <div className="flex flex-col items-start gap-3 sm:flex-row sm:justify-between">
        <div className="flex items-start gap-3">
          <span
            aria-hidden="true"
            className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/15 text-sm font-semibold text-primary"
          >
            {initials(name)}
          </span>
          <div className="min-w-0">
            <div className="font-medium">
              <Link
                to={`/profile/${contact.fromEntityId}`}
                className="underline-offset-2 hover:underline"
              >
                {name}
              </Link>
              {org && (
                <span className="font-normal text-muted-foreground">
                  {" · "}
                  {org}
                </span>
              )}
            </div>
            {ctx?.role && (
              <p className="mt-0.5 text-sm text-muted-foreground">{ctx.role}</p>
            )}
          </div>
        </div>
        <ContactStatusBadge status={contact.status} />
      </div>

      {searched && (
        <p className="mt-3.5 text-sm">
          <span className="text-muted-foreground">Gesucht:</span> «{searched}»
        </p>
      )}

      <blockquote className="mt-3 border-l-2 border-primary/30 pl-3 text-[15px] leading-relaxed">
        {contact.message}
      </blockquote>

      {contact.status === "accepted" && contact.replyMessage && (
        <div className="mt-3 rounded-lg bg-primary/5 px-3.5 py-2.5 text-sm">
          <span className="text-muted-foreground">Deine Antwort:</span>{" "}
          {contact.replyMessage}
        </div>
      )}

      <div className="mt-4 flex flex-col items-stretch gap-3 border-t pt-4 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
        <span className="text-xs text-muted-foreground">
          {contact.requestId != null && contact.requestTitle && (
            <>
              zu deiner Anfrage{" "}
              <Link
                to={`/board/${contact.requestId}`}
                className="text-foreground underline-offset-2 hover:underline"
              >
                «{contact.requestTitle}»
              </Link>
              {" · "}
            </>
          )}
          {contact.createdAt && relativeTime(contact.createdAt)}
        </span>

        {contact.status === "pending" ? (
          confirmDecline ? (
            <span className="flex flex-col items-stretch gap-2 text-sm sm:flex-row sm:items-center">
              <span className="text-muted-foreground">Wirklich ablehnen?</span>
              <Button
                variant="outline"
                size="sm"
                disabled={isLoading}
                onClick={() =>
                  respond({ id: contact.id, body: { accept: false } })
                }
              >
                Ja, ablehnen
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setConfirmDecline(false)}
              >
                Abbrechen
              </Button>
            </span>
          ) : (
            <span className="grid grid-cols-1 gap-2 sm:flex sm:items-center">
              <Button className="w-full sm:w-auto" variant="outline" asChild>
                <Link to={`/profile/${contact.fromEntityId}`}>
                  Profil ansehen
                </Link>
              </Button>
              <Button
                className="w-full sm:w-auto"
                variant="ghost"
                onClick={() => setConfirmDecline(true)}
              >
                Ablehnen
              </Button>
              <Button
                className="w-full sm:w-auto"
                onClick={() => setReplyOpen(true)}
              >
                Antworten
              </Button>
            </span>
          )
        ) : contact.status === "accepted" && contact.fromEmail ? (
          <EmailActions email={contact.fromEmail} />
        ) : null}
      </div>

      <ContactRespondModal
        contactId={contact.id}
        fromName={name}
        quote={searched ? `«${searched}»` : null}
        open={replyOpen}
        onOpenChange={setReplyOpen}
      />
    </div>
  )
}

/** One outgoing contact request — status + (once accepted) reply + email. */
function SentCard({ contact }: { contact: InboxSentContactDto }) {
  const name = contact.toName ?? "Unbekannt"
  return (
    <div className="rounded-xl bg-card p-4 ring-1 ring-foreground/10 sm:p-5">
      <div className="flex flex-col items-start gap-3 sm:flex-row sm:justify-between">
        <div className="min-w-0">
          <div className="font-medium">
            <Link
              to={`/profile/${contact.toEntityId}`}
              className="underline-offset-2 hover:underline"
            >
              {name}
            </Link>
            {contact.toOrgName && (
              <span className="font-normal text-muted-foreground">
                {" · "}
                {contact.toOrgName}
              </span>
            )}
          </div>
        </div>
        <ContactStatusBadge status={contact.status} />
      </div>

      <blockquote className="mt-3 border-l-2 border-primary/30 pl-3 text-[15px] leading-relaxed">
        {contact.message}
      </blockquote>

      {contact.status === "accepted" && contact.replyMessage && (
        <div className="mt-3 rounded-lg bg-green-500/5 px-3.5 py-2.5 text-sm">
          <span className="text-muted-foreground">{name} antwortete:</span>{" "}
          {contact.replyMessage}
        </div>
      )}

      <div className="mt-4 flex flex-col items-stretch gap-3 border-t pt-4 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
        <span className="text-xs text-muted-foreground">
          {contact.requestId != null && contact.requestTitle && (
            <>
              zu{" "}
              <Link
                to={`/board/${contact.requestId}`}
                className="text-foreground underline-offset-2 hover:underline"
              >
                «{contact.requestTitle}»
              </Link>
              {" · "}
            </>
          )}
          {contact.status === "pending"
            ? "Warte auf Antwort"
            : contact.respondedAt &&
              `beantwortet ${relativeTime(contact.respondedAt)}`}
        </span>
        {contact.status === "accepted" && contact.toEmail && (
          <EmailActions email={contact.toEmail} />
        )}
      </div>
    </div>
  )
}

/** One distributed request — the explainable reason + reply via handshake. */
function DistributionCard({
  distribution,
  onReply,
}: {
  distribution: InboxDistributionDto
  onReply: (d: InboxDistributionDto) => void
}) {
  const title = distribution.title?.trim() || distribution.queryText
  return (
    <div className="rounded-xl bg-card p-4 ring-1 ring-foreground/10 sm:p-5">
      <div className="font-medium">
        <Link
          to={`/board/${distribution.requestId}`}
          className="underline-offset-2 hover:underline"
        >
          {title}
        </Link>
      </div>
      {(distribution.authorName || distribution.authorOrgName) && (
        <p className="mt-1 text-sm text-muted-foreground">
          {[distribution.authorName, distribution.authorOrgName]
            .filter(Boolean)
            .join(" · ")}
        </p>
      )}
      <p className="mt-2.5 rounded-lg bg-primary/5 px-3 py-2 text-sm text-primary/90">
        {distributionReason(distribution)}
      </p>
      <div className="mt-4 flex flex-col items-stretch gap-3 border-t pt-4 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
        <span className="text-xs text-muted-foreground">
          {distribution.notifiedAt && relativeTime(distribution.notifiedAt)}
        </span>
        <span className="grid grid-cols-1 gap-2 sm:flex sm:items-center">
          <Button className="w-full sm:w-auto" variant="outline" asChild>
            <Link to={`/board/${distribution.requestId}`}>Anfrage ansehen</Link>
          </Button>
          <Button
            className="w-full sm:w-auto"
            disabled={distribution.authorEntityId == null}
            title={
              distribution.authorEntityId == null
                ? "Kein verknüpftes Profil — Kontakt nicht möglich"
                : undefined
            }
            onClick={() => onReply(distribution)}
          >
            Antworten
          </Button>
        </span>
      </div>
    </div>
  )
}

function EmptyRow({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-dashed px-5 py-6 text-sm text-muted-foreground">
      <Inbox className="size-5" />
      {children}
    </div>
  )
}

/**
 * The Postfach (design: InboxScreen) — served in both modes. Three sections:
 * contact requests I received (answer with accept/decline), contact requests I
 * sent (with their handshake outcome), and requests distributed to my profile.
 * Emails are exchanged only once a request is accepted; everything after runs
 * off-platform.
 */
const TABS = ["received", "sent", "matched"] as const
type InboxTab = (typeof TABS)[number]

export function InboxView() {
  const { data, isLoading, isError } = useGetInboxQuery()
  const [reply, setReply] = useState<InboxDistributionDto | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedTab = searchParams.get("tab")
  const activeTab: InboxTab = TABS.includes(requestedTab as InboxTab)
    ? (requestedTab as InboxTab)
    : "received"

  if (isLoading) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <Loader2 className="size-6 animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">Postfach wird geladen…</p>
      </div>
    )
  }

  if (isError || !data) {
    return (
      <p className="py-16 text-center text-sm text-muted-foreground">
        Das Postfach konnte nicht geladen werden. Bitte versuche es erneut.
      </p>
    )
  }

  const openDistributions = data.distributions.filter(
    (d) => d.requestStatus !== "closed"
  )

  return (
    <div className="mx-auto max-w-3xl py-4 sm:py-6">
      <h1 className="text-2xl font-bold tracking-tight">Postfach</h1>
      <p className="mt-1.5 max-w-xl text-[15px] leading-relaxed text-muted-foreground">
        Kontaktanfragen an dich und von dir, sowie Anfragen, die zu deinem
        Profil passen.
      </p>

      <Tabs
        value={activeTab}
        onValueChange={(v) => {
          searchParams.set("tab", v)
          setSearchParams(searchParams, { replace: true })
        }}
        className="mt-6"
      >
        <TabsList className="w-full">
          <TabsTrigger value="received">
            Erhalten ({data.received.length})
          </TabsTrigger>
          <TabsTrigger value="sent">Gesendet ({data.sent.length})</TabsTrigger>
          <TabsTrigger value="matched">
            <span className="sm:hidden">
              Passend ({openDistributions.length})
            </span>
            <span className="hidden sm:inline">
              Passende Anfragen ({openDistributions.length})
            </span>
          </TabsTrigger>
        </TabsList>

        <TabsContent value="received">
          <div className="flex flex-col gap-4">
            {data.received.length === 0 ? (
              <EmptyRow>
                Noch keine Kontaktanfragen — halte dein Profil aktuell, um
                gefunden zu werden.
              </EmptyRow>
            ) : (
              data.received.map((c) => <ReceivedCard key={c.id} contact={c} />)
            )}
          </div>
        </TabsContent>

        <TabsContent value="sent">
          <div className="flex flex-col gap-4">
            {data.sent.length === 0 ? (
              <EmptyRow>
                <span className="inline-flex items-center gap-2">
                  <Send className="size-4" />
                  Du hast noch niemanden kontaktiert. Über die Suche findest du
                  passende Profile.
                </span>
              </EmptyRow>
            ) : (
              data.sent.map((c) => <SentCard key={c.id} contact={c} />)
            )}
          </div>
        </TabsContent>

        <TabsContent value="matched">
          <div className="flex flex-col gap-4">
            {openDistributions.length === 0 ? (
              <EmptyRow>
                Zurzeit keine passenden Anfragen. Abonniere Themen auf deinem
                Profil, um bei neuen Anfragen benachrichtigt zu werden.
              </EmptyRow>
            ) : (
              openDistributions.map((d) => (
                <DistributionCard
                  key={`${d.requestId}`}
                  distribution={d}
                  onReply={setReply}
                />
              ))
            )}
          </div>
        </TabsContent>
      </Tabs>

      {reply && reply.authorEntityId != null && (
        <ContactModal
          toEntityId={reply.authorEntityId}
          toName={reply.authorName ?? "Die Person"}
          quote={`«${reply.title?.trim() || reply.queryText}»`}
          requestId={reply.requestId}
          searchedText={reply.queryText}
          open
          onOpenChange={(open) => {
            if (!open) setReply(null)
          }}
        />
      )}
    </div>
  )
}
