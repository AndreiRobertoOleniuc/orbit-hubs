import { useState } from "react"
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
import { useRespondContactMutation } from "@/lib/api"

export interface ContactRespondModalProps {
  /** The contact request being answered. */
  contactId: number
  /** Who reached out — shown in the header + success line. */
  fromName: string
  /** Optional context quote (what they searched / the request title). */
  quote?: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * The recipient's accept-with-reply handshake (§11). Sending a reply reveals
 * both parties' emails to each other; the exchange then continues off-platform.
 * Declining is handled inline on the inbox card, not here.
 */
export function ContactRespondModal({
  contactId,
  fromName,
  quote,
  open,
  onOpenChange,
}: ContactRespondModalProps) {
  const [message, setMessage] = useState("")
  const [sent, setSent] = useState(false)
  const [respond, { isLoading, isError }] = useRespondContactMutation()

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setMessage("")
      setSent(false)
    }
    onOpenChange(next)
  }

  const send = async () => {
    if (!message.trim()) return
    try {
      await respond({
        id: contactId,
        body: { accept: true, message: message.trim() },
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
            <p className="mt-3 font-semibold">Antwort gesendet.</p>
            <p className="mt-1 text-sm text-muted-foreground">
              {fromName} sieht jetzt deine E-Mail-Adresse, und du seine — ihr
              könnt direkt weiterschreiben.
            </p>
            <Button
              variant="outline"
              className="mt-5"
              onClick={() => handleOpenChange(false)}
            >
              Schliessen
            </Button>
          </div>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>Auf {fromName} antworten</DialogTitle>
              <DialogDescription className="sr-only">
                Antwort an {fromName}
              </DialogDescription>
            </DialogHeader>

            {quote && (
              <blockquote className="border-l-2 border-primary/30 pl-3 text-sm text-muted-foreground italic">
                {quote}
              </blockquote>
            )}

            <Textarea
              autoFocus
              rows={4}
              placeholder="Kurze Antwort…"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
            />

            <div>
              <Button
                className="w-full"
                disabled={!message.trim() || isLoading}
                onClick={send}
              >
                {isLoading && <Loader2 className="size-4 animate-spin" />}
                Antwort senden
              </Button>

              {isError && (
                <p className="mt-2 text-center text-xs text-red-600 dark:text-red-400">
                  Senden fehlgeschlagen — bitte versuch es noch einmal.
                </p>
              )}

              <p className="mt-2.5 text-center text-xs leading-relaxed text-muted-foreground">
                Mit dem Antworten gebt ihr eure E-Mail-Adressen füreinander
                frei. Alles Weitere läuft direkt zwischen euch — ausserhalb der
                Plattform.
              </p>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
