import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { COLLABORATION_TYPES } from "@/features/profile/availabilityUtils"
import { useUpdateAvailabilityMutation } from "@/lib/api"
import { cn, sameSet, todayIso } from "@/lib/utils"
import type { EntityDto } from "@/types/api"

/**
 * Editor for structured availability — the "Offen für Anfragen" toggle plus the
 * window, Pensum %, preferred collaboration types, and a free-text note. Built
 * from existing primitives + native date/range inputs (no new shadcn deps).
 */
export function AvailabilityEditor({ entity }: { entity: EntityDto }) {
  const a = entity.availability
  const [open, setOpen] = useState(a?.openToRequests ?? false)
  const [from, setFrom] = useState(a?.availableFrom ?? "")
  const [to, setTo] = useState(a?.availableTo ?? "")
  const [pensum, setPensum] = useState<number>(a?.pensumPct ?? 100)
  const [note, setNote] = useState(a?.note ?? "")
  const [collaboration, setCollaboration] = useState<string[]>(
    a?.preferredCollaboration ?? []
  )
  const [saved, setSaved] = useState(false)
  const [updateAvailability, { isLoading }] = useUpdateAvailabilityMutation()

  const toggleCollab = (type: string) =>
    setCollaboration((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]
    )

  // Only enable Speichern once something actually changed vs. the saved state.
  const dirty =
    open !== (a?.openToRequests ?? false) ||
    from !== (a?.availableFrom ?? "") ||
    to !== (a?.availableTo ?? "") ||
    pensum !== (a?.pensumPct ?? 100) ||
    note !== (a?.note ?? "") ||
    !sameSet(collaboration, a?.preferredCollaboration ?? [])

  const save = async () => {
    setSaved(false)
    await updateAvailability({
      entityId: entity.id,
      body: {
        openToRequests: open,
        availableFrom: from || null,
        availableTo: to || null,
        pensumPct: open ? pensum : null,
        note: note.trim() || null,
        preferredCollaboration: open ? collaboration : [],
      },
    }).unwrap()
    setSaved(true)
    setTimeout(() => setSaved(false), 3000)
  }

  return (
    <div className="space-y-4">
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
        <div className="space-y-4 border-t pt-4">
          {/* Window */}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="avail-from">Verfügbar ab</Label>
              <Input
                id="avail-from"
                type="date"
                min={todayIso()}
                max={to || undefined}
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="avail-to">Verfügbar bis</Label>
              <Input
                id="avail-to"
                type="date"
                min={from || todayIso()}
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            </div>
          </div>

          {/* Pensum */}
          <div className="space-y-1.5">
            <Label htmlFor="avail-pensum">
              Pensum: <span className="font-semibold">{pensum} %</span>
            </Label>
            <input
              id="avail-pensum"
              type="range"
              min={0}
              max={100}
              step={10}
              value={pensum}
              onChange={(e) => setPensum(Number(e.target.value))}
              className="w-full accent-primary"
            />
          </div>

          {/* Collaboration types */}
          <div className="space-y-1.5">
            <Label>Offen für</Label>
            <div className="flex flex-wrap gap-2">
              {COLLABORATION_TYPES.map((type) => {
                const active = collaboration.includes(type)
                return (
                  <button
                    key={type}
                    type="button"
                    onClick={() => toggleCollab(type)}
                    className={cn(
                      "rounded-full border px-3 py-1 text-sm transition-colors",
                      active
                        ? "border-primary bg-primary/10 text-primary"
                        : "border-input text-muted-foreground hover:bg-muted"
                    )}
                  >
                    {type}
                  </button>
                )
              })}
            </div>
          </div>

          {/* Note */}
          <div className="space-y-1.5">
            <Label htmlFor="avail-note">Notiz (optional)</Label>
            <Input
              id="avail-note"
              placeholder="z.B. ab Studienende, remote bevorzugt"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </div>
        </div>
      )}

      <div className="flex items-center justify-end gap-3">
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
