import { useId, useState } from "react"
import { Link, useNavigate } from "react-router"
import { Check, Loader2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { useAuth } from "@/features/auth/useAuth"
import { initials } from "@/features/profile/profileUtils"
import {
  useCreateContactRequestMutation,
  useGetContactGatingQuery,
  useGetMyEntitiesQuery,
} from "@/lib/api"
import type { ContactGatingCheck } from "@/types/api"

const MISSING_LABELS: Record<ContactGatingCheck, string> = {
  entity: "Ein eigenes Profil",
  email: "Eine bestätigte E-Mail-Adresse",
  source: "Mindestens eine Quelle: LinkedIn, Website oder eigene Beschreibung",
}

interface ContactModalProps {
  /** Recipient entity (must be a claimed profile). */
  toEntityId: number
  /** Recipient display name — the dialog headline. */
  toName: string
  /**
   * One-line context, rendered truncated: "Deine Suche: «…»" or «request title».
   * Never the match rationale — that is already on the screen behind the dialog.
   */
  quote?: string | null
  /** Set when initiated from a board request — transitions it to 'kontaktiert'. */
  requestId?: number | null
  /** What the sender searched for — frozen into the sender context. */
  searchedText?: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * The first-contact handoff dialog (design: HandoffModal, reference §11) —
 * "Stell dich einmal vor". The recipient is the headline, because testers kept
 * closing the dialog to re-check whom they were writing to. Everything else is
 * secondary: who you contact as, one truncated line of context, and the message
 * itself. Gates on profile completeness (inline warnings + "Profil
 * vervollständigen" link, never a silently disabled button), and confirms the
 * one-time handoff: the recipient sees message + contact data in their Postfach,
 * everything else runs off-platform.
 */
export function ContactModal({
  toEntityId,
  toName,
  quote,
  requestId,
  searchedText,
  open,
  onOpenChange,
}: ContactModalProps) {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  // One modal per search result is mounted — ids must not collide.
  const fieldId = useId()
  const [message, setMessage] = useState("")
  const [sent, setSent] = useState(false)

  const { data: gating, isLoading: gatingLoading } = useGetContactGatingQuery(
    undefined,
    { skip: !open || !isAuthenticated }
  )
  const { data: myEntities } = useGetMyEntitiesQuery(undefined, {
    skip: !open || !isAuthenticated,
  })
  const [createContact, { isLoading: sending, isError }] =
    useCreateContactRequestMutation()

  // Fresh dialog per opening — a new contact never inherits the previous text.
  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setMessage("")
      setSent(false)
    }
    onOpenChange(next)
  }

  const sender = myEntities?.[0]
  const allowed = gating?.allowed ?? false
  const missing = gating?.missing ?? []

  const send = async () => {
    if (!allowed || !message.trim()) return
    try {
      await createContact({
        toEntityId,
        requestId: requestId ?? null,
        message: message.trim(),
        searchedText: searchedText ?? null,
      }).unwrap()
      setSent(true)
    } catch {
      // isError renders the inline failure note below.
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        {sent ? (
          <div className="py-4 text-center">
            <span className="mx-auto flex size-10 items-center justify-center rounded-full bg-primary/10">
              <Check className="size-5 text-primary" />
            </span>
            <p className="mt-3 font-semibold">Kontaktanfrage gesendet.</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Die Anfrage wartet jetzt auf eine Antwort von {toName}. Nach der
              Annahme setzt ihr das Gespräch per E-Mail fort.
            </p>
            <div className="mt-5 flex items-center justify-center gap-2">
              <Button variant="outline" onClick={() => handleOpenChange(false)}>
                Schliessen
              </Button>
              <Button
                onClick={() => {
                  handleOpenChange(false)
                  navigate("/inbox?tab=sent")
                }}
              >
                Zum Postfach
              </Button>
            </div>
          </div>
        ) : (
          <>
            <DialogHeader>
              {/* pr-8 keeps a wrapped long name clear of the close button. */}
              <DialogTitle className="pr-8 leading-snug break-words">
                Kontakt mit {toName} aufnehmen
              </DialogTitle>
              <DialogDescription>
                Stell dich kurz vor. {toName} entscheidet danach, ob daraus ein
                Gespräch wird.
              </DialogDescription>
            </DialogHeader>

            {/* min-w-0: DialogContent is a grid, so without it the nowrap
                context line sets the column's min-content width and pushes the
                whole dialog body past the panel edge. */}
            <div className="min-w-0 space-y-2">
              {/* Context, one line: the search behind this contact. Truncated
                  visually — the head carries the label, the full value stays in
                  the title and in the text node screen readers announce. */}
              {quote && (
                <p
                  className="truncate text-xs text-muted-foreground"
                  title={quote}
                >
                  {quote}
                </p>
              )}

              {sender && (
                <div className="flex items-start gap-2.5">
                  <span
                    aria-hidden="true"
                    className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[10px] font-semibold text-primary"
                  >
                    {initials(sender.displayName)}
                  </span>
                  <div className="min-w-0 text-xs text-muted-foreground">
                    <p>
                      Du kontaktierst als{" "}
                      <span className="font-medium text-foreground">
                        {sender.displayName}
                      </span>
                      {sender.orgName ? ` · ${sender.orgName}` : ""}
                    </p>
                    <p className="mt-0.5">
                      Der Empfänger sieht deinen Namen, deinen Arbeitgeber und
                      wonach du gesucht hast.
                    </p>
                  </div>
                </div>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor={fieldId}>Deine Nachricht</Label>
              <Textarea
                id={fieldId}
                aria-describedby={`${fieldId}-hint`}
                autoFocus
                rows={4}
                placeholder="Beschreibe kurz, worum es geht und was du dir vom Austausch erhoffst."
                value={message}
                onChange={(e) => setMessage(e.target.value)}
              />
              <p
                id={`${fieldId}-hint`}
                className="text-xs leading-relaxed text-muted-foreground"
              >
                2–4 Sätze genügen. Die Nachricht ist der persönliche Einstieg,
                kein vollständiges Projektbriefing.
              </p>
            </div>

            <div>
              <Button
                className="w-full"
                disabled={
                  !allowed || !message.trim() || sending || gatingLoading
                }
                onClick={send}
              >
                {sending && <Loader2 className="size-4 animate-spin" />}
                Kontaktanfrage senden
              </Button>

              {isError && (
                <p className="mt-2 text-center text-xs text-red-600 dark:text-red-400">
                  Senden fehlgeschlagen. Bitte versuch es noch einmal.
                </p>
              )}

              {!gatingLoading && gating && !allowed ? (
                <div className="mt-3 rounded-lg border border-amber-300/60 bg-amber-50 px-3.5 py-3 text-sm dark:border-amber-400/30 dark:bg-amber-950/40">
                  <p className="font-medium">
                    Bevor du Kontakt aufnimmst, fehlt noch:
                  </p>
                  <ul className="mt-1.5 list-disc space-y-0.5 pl-4 text-muted-foreground">
                    {missing.map((m) => (
                      <li key={m}>{MISSING_LABELS[m]}</li>
                    ))}
                  </ul>
                  <Link
                    to="/profile"
                    className="mt-2 inline-block text-sm font-medium text-primary underline-offset-2 hover:underline"
                    onClick={() => handleOpenChange(false)}
                  >
                    Profil vervollständigen
                  </Link>
                </div>
              ) : (
                <p className="mt-2.5 text-center text-xs leading-relaxed text-muted-foreground">
                  Orbit übermittelt deine Nachricht. Deine E-Mail-Adresse wird
                  erst nach der Annahme freigegeben. Danach setzt ihr das
                  Gespräch per E-Mail fort.
                </p>
              )}
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
