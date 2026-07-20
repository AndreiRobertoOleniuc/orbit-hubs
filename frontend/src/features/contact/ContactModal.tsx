import { useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { Check, Loader2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
  source:
    "Mindestens eine Quelle — LinkedIn, Website oder eine eigene Beschreibung",
}

interface ContactModalProps {
  /** Recipient entity (must be a claimed profile). */
  toEntityId: number
  /** Recipient display name — shown in the success state. */
  toName: string
  /** Collapsed context quote: the search query or «request title». */
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
 * "Stell dich einmal vor". Shows the sender context, gates on profile
 * completeness (inline warnings + "Profil vervollständigen" link, never a
 * silently disabled button), and confirms the one-time handoff: the recipient
 * sees message + contact data in their Postfach, everything else runs
 * off-platform.
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
              {toName} meldet sich direkt bei dir. Du findest sie unter
              «Gesendet» in deinem Postfach.
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
              <DialogTitle>Stell dich einmal vor</DialogTitle>
              <DialogDescription className="sr-only">
                Kontaktanfrage an {toName}
              </DialogDescription>
            </DialogHeader>

            {sender && (
              <div className="flex items-start gap-3 rounded-lg bg-muted/50 px-3.5 py-3">
                <span
                  aria-hidden="true"
                  className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/15 text-xs font-semibold text-primary"
                >
                  {initials(sender.displayName)}
                </span>
                <div className="min-w-0 text-sm">
                  <p>
                    Du kontaktierst als{" "}
                    <span className="font-semibold">{sender.displayName}</span>
                    {sender.orgName ? ` · ${sender.orgName}` : ""}
                  </p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    Der Empfänger sieht deinen Namen, deinen Arbeitgeber und
                    wonach du gesucht hast.
                  </p>
                </div>
              </div>
            )}

            {quote && (
              <blockquote className="border-l-2 border-primary/30 pl-3 text-sm leading-relaxed whitespace-pre-line text-muted-foreground">
                {quote}
              </blockquote>
            )}

            <Textarea
              autoFocus
              rows={4}
              placeholder="Kurze persönliche Nachricht…"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
            />

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
                  Senden fehlgeschlagen — bitte versuch es noch einmal.
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
                  Der Empfänger erhält deine Nachricht und deine Kontaktdaten in
                  seinem Postfach. Alles Weitere läuft direkt zwischen euch —
                  ausserhalb der Plattform.
                </p>
              )}
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
