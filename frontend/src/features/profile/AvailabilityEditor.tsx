import { useState } from "react"

import { Button } from "@/components/ui/button"
import { AvailabilityFields } from "@/features/profile/AvailabilityFields"
import { AvailabilityPreview } from "@/features/profile/AvailabilityPreview"
import {
  availabilityDraftChanged,
  toAvailabilityBody,
  toAvailabilityDraft,
  type AvailabilityDraft,
} from "@/features/profile/availabilityUtils"
import { useUpdateAvailabilityMutation } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { EntityDto } from "@/types/api"

/**
 * Editor for structured availability — the "Offen für Anfragen" toggle plus the
 * shared availability form and a live preview of the result.
 *
 * The form itself lives in {@link AvailabilityFields}, shared with the
 * onboarding publish step so both surfaces explain the fields the same way.
 */
export function AvailabilityEditor({ entity }: { entity: EntityDto }) {
  const a = entity.availability
  const [open, setOpen] = useState(a?.openToRequests ?? false)
  const [draft, setDraft] = useState<AvailabilityDraft>(() =>
    toAvailabilityDraft(a)
  )
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [updateAvailability, { isLoading }] = useUpdateAvailabilityMutation()

  const patch = (p: Partial<AvailabilityDraft>) =>
    setDraft((d) => ({ ...d, ...p }))

  // Only enable Speichern once something actually changed vs. the saved state.
  const dirty =
    open !== (a?.openToRequests ?? false) || availabilityDraftChanged(a, draft)

  const body = toAvailabilityBody(open, draft)

  const save = async () => {
    setSaved(false)
    setError(null)
    try {
      await updateAvailability({ entityId: entity.id, body }).unwrap()
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    } catch {
      setError(
        "Deine Verfügbarkeit konnte nicht gespeichert werden. Versuch es nochmals."
      )
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Deine Verfügbarkeit ist ein Signal, kein Filter: Sie bestimmt, wie weit
        oben du bei passenden Suchen erscheinst — ausgeschlossen wirst du nie.
      </p>

      {/* Open toggle */}
      <button
        type="button"
        role="switch"
        aria-checked={open}
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center justify-between gap-4 text-left"
      >
        <span>
          <span className="block text-sm font-medium">Offen für Anfragen</span>
          <span className="block text-xs text-muted-foreground">
            Erscheint in Suchergebnissen und Verteilungen.
          </span>
        </span>
        <span
          className={cn(
            "relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors",
            open ? "bg-primary" : "bg-input"
          )}
        >
          <span
            className={cn(
              "inline-block size-5 rounded-full bg-background shadow transition-transform",
              open ? "translate-x-5" : "translate-x-0.5"
            )}
          />
        </span>
      </button>

      {open && (
        <div className="border-t pt-4">
          <AvailabilityFields value={draft} onChange={patch} idPrefix="avail" />
        </div>
      )}

      <AvailabilityPreview availability={body} />

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}

      <div className="flex flex-wrap items-center justify-end gap-3">
        {dirty && !isLoading && (
          <span className="text-xs text-amber-700 dark:text-amber-500">
            Nicht gespeicherte Änderungen
          </span>
        )}
        {saved && (
          <span className="text-xs text-muted-foreground">✓ gespeichert</span>
        )}
        <Button
          size="sm"
          onClick={() => void save()}
          disabled={!dirty || isLoading}
        >
          Speichern
        </Button>
      </div>
    </div>
  )
}
