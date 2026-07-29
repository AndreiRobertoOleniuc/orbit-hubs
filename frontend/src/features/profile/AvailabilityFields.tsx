import { useState } from "react"
import { X } from "lucide-react"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { InfoHint } from "@/components/InfoHint"
import {
  addCollaborationType,
  COLLABORATION_MAX_LENGTH,
  COLLABORATION_PROMPT,
  COLLABORATION_TYPES,
  isSuggestedCollaboration,
  type AvailabilityDraft,
} from "@/features/profile/availabilityUtils"
import { cn, todayIso } from "@/lib/utils"

interface AvailabilityFieldsProps {
  value: AvailabilityDraft
  onChange: (patch: Partial<AvailabilityDraft>) => void
  /** Namespaces the element ids, so two instances can coexist on a page. */
  idPrefix: string
}

/**
 * The availability form itself — window, Pensum, collaboration types, note —
 * shared by the profile editor and the onboarding publish step.
 *
 * Each group states what it actually does downstream, because usability testing
 * showed people filling these in blind: one tester had to ask out loud whether
 * the values filter, sort, or only display. Only Zeitraum and Pensum reach the
 * ranking (as a soft re-rank, never a hard filter); the rest is display.
 */
export function AvailabilityFields({
  value,
  onChange,
  idPrefix,
}: AvailabilityFieldsProps) {
  const id = (suffix: string) => `${idPrefix}-${suffix}`
  const { availableFrom, availableTo, pensumPct, note } = value
  const collaboration = value.preferredCollaboration
  const [customDraft, setCustomDraft] = useState("")

  /** Free-text entries — everything the suggestion chips don't already cover. */
  const customTypes = collaboration.filter((t) => !isSuggestedCollaboration(t))

  const toggleCollab = (type: string) =>
    onChange({
      preferredCollaboration: collaboration.includes(type)
        ? collaboration.filter((t) => t !== type)
        : [...collaboration, type],
    })

  const addCustom = () => {
    onChange({
      preferredCollaboration: addCollaborationType(collaboration, customDraft),
    })
    setCustomDraft("")
  }

  return (
    <div className="space-y-5">
      {/* Zeitraum + Pensum — the only two fields the ranking reads. */}
      <div className="space-y-3">
        <FieldGroupHint>
          Zeitraum und Pensum helfen beim Sortieren, wenn Suchende einen
          Zeitraum angeben.
        </FieldGroupHint>

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor={id("from")}>Verfügbar ab</Label>
            <Input
              id={id("from")}
              type="date"
              min={todayIso()}
              max={availableTo || undefined}
              value={availableFrom}
              onChange={(e) => onChange({ availableFrom: e.target.value })}
              aria-describedby={id("window-hint")}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor={id("to")}>Verfügbar bis</Label>
            <Input
              id={id("to")}
              type="date"
              min={availableFrom || todayIso()}
              value={availableTo}
              onChange={(e) => onChange({ availableTo: e.target.value })}
              aria-describedby={id("window-hint")}
            />
          </div>
        </div>
        <FieldGroupHint id={id("window-hint")}>
          Optional. Leer lassen heisst: unbefristet.
        </FieldGroupHint>

        <div className="space-y-1.5">
          <Label htmlFor={id("pensum")}>
            Pensum: <span className="font-semibold">{pensumPct} %</span>
            <InfoHint term="pensum" />
          </Label>
          <input
            id={id("pensum")}
            type="range"
            min={0}
            max={100}
            step={10}
            value={pensumPct}
            onChange={(e) => onChange({ pensumPct: Number(e.target.value) })}
            className="w-full accent-primary"
            aria-describedby={pensumPct === 0 ? id("pensum-zero") : undefined}
          />
          {pensumPct === 0 && (
            <p
              id={id("pensum-zero")}
              className="text-xs text-amber-700 dark:text-amber-500"
            >
              0 % wirkt widersprüchlich, wenn du offen für Anfragen bist. Ein
              kleines Pensum passt meistens besser.
            </p>
          )}
        </div>
      </div>

      {/* Collaboration types */}
      <div className="space-y-1.5">
        <Label id={id("collab-label")}>Art der Zusammenarbeit</Label>
        <p id={id("collab-prompt")} className="text-xs text-muted-foreground">
          {COLLABORATION_PROMPT}
        </p>
        <div
          role="group"
          aria-labelledby={id("collab-label")}
          aria-describedby={id("collab-prompt")}
          className="flex flex-wrap gap-2"
        >
          {COLLABORATION_TYPES.map(({ value: collabValue, label }) => {
            const active = collaboration.includes(collabValue)
            return (
              <button
                key={collabValue}
                type="button"
                aria-pressed={active}
                onClick={() => toggleCollab(collabValue)}
                className={cn(
                  "rounded-full border px-3 py-1 text-sm transition-colors",
                  active
                    ? "border-primary bg-primary/10 text-primary"
                    : "border-input text-muted-foreground hover:bg-muted"
                )}
              >
                {label}
              </button>
            )
          })}
          {customTypes.map((type) => (
            <span
              key={type}
              className="inline-flex items-center gap-1 rounded-full border border-primary bg-primary/10 py-1 pr-1.5 pl-3 text-sm text-primary"
            >
              {type}
              <button
                type="button"
                onClick={() => toggleCollab(type)}
                aria-label={`${type} entfernen`}
                className="rounded-full p-0.5 hover:bg-primary/20"
              >
                <X className="size-3.5" />
              </button>
            </span>
          ))}
        </div>
        <div className="flex gap-2">
          <Input
            id={id("collab-custom")}
            value={customDraft}
            maxLength={COLLABORATION_MAX_LENGTH}
            placeholder="Eigene Art der Zusammenarbeit"
            aria-label="Eigene Art der Zusammenarbeit hinzufügen"
            onChange={(e) => setCustomDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key !== "Enter") return
              // The field sits inside the profile form — Enter adds the chip
              // instead of submitting everything.
              e.preventDefault()
              addCustom()
            }}
          />
          <button
            type="button"
            onClick={addCustom}
            disabled={!customDraft.trim()}
            className="rounded-md border border-input px-3 text-sm hover:bg-muted disabled:opacity-50"
          >
            Hinzufügen
          </button>
        </div>
        <FieldGroupHint>
          Vorschläge anklicken oder eigene Begriffe schreiben. Das erscheint auf
          deinem Profil, sortiert die Treffer aber nicht. Innosuisse = staatlich
          geförderte Innovationsprojekte · Mandat = bezahlter Auftrag.
        </FieldGroupHint>
      </div>

      {/* Note */}
      <div className="space-y-1.5">
        <Label htmlFor={id("note")}>Notiz (optional)</Label>
        <Input
          id={id("note")}
          placeholder="Beispiel: remote bevorzugt, Termine nach Absprache"
          value={note}
          onChange={(e) => onChange({ note: e.target.value })}
          aria-describedby={id("note-hint")}
        />
        <FieldGroupHint id={id("note-hint")}>
          Ein kurzer Hinweis für Suchende. Er erscheint neben deiner
          Verfügbarkeit.
        </FieldGroupHint>
      </div>
    </div>
  )
}

/** A one-line explanation of what a group of fields actually does downstream. */
function FieldGroupHint({
  id,
  children,
}: {
  id?: string
  children: React.ReactNode
}) {
  return (
    <p id={id} className="text-xs text-muted-foreground">
      {children}
    </p>
  )
}
