import { useState } from "react"
import { Plus, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import type { EntityDto } from "@/types/api"
import {
  useUpdateEntitySummaryMutation,
  useUpdateEntityTagsMutation,
} from "@/lib/api"
import { builtFromLabels } from "@/features/profile/profileUtils"

interface ProfilePreviewProps {
  entity: EntityDto
  /** Called when the user confirms the preview (either after editing or as-is). */
  onConfirm: () => void
}

/**
 * Profile preview shown after the ingestion run completes. Displays the
 * AI-generated competence summary and derived tags, lets the user edit
 * both (tags add/remove, summary text), and saves changes via the API.
 *
 * Based on the Claude design's WizProfilePreview component:
 * - Typewriter animation on the freshly generated summary
 * - Tag chips with add/remove
 * - "Anpassen" button to enter edit mode
 * - "Fertig" button to confirm and proceed
 */
export function ProfilePreview({ entity, onConfirm }: ProfilePreviewProps) {
  const [editing, setEditing] = useState(false)
  const [summaryDraft, setSummaryDraft] = useState(entity.summary ?? "")
  const [tagsDraft, setTagsDraft] = useState<string[]>(
    entity.tags.map((t) => t.label)
  )
  const [newTag, setNewTag] = useState("")
  const [saved, setSaved] = useState(false)

  const [updateSummary] = useUpdateEntitySummaryMutation()
  const [updateTags] = useUpdateEntityTagsMutation()

  const builtFrom = builtFromLabels(entity)

  const startEdit = () => {
    setSummaryDraft(entity.summary ?? "")
    setTagsDraft(entity.tags.map((t) => t.label))
    setEditing(true)
  }

  const addTag = () => {
    const v = newTag.trim()
    if (v && !tagsDraft.includes(v)) {
      setTagsDraft([...tagsDraft, v])
    }
    setNewTag("")
  }

  const removeTag = (tag: string) => {
    setTagsDraft(tagsDraft.filter((t) => t !== tag))
  }

  const finishEditing = async () => {
    // Save both summary and tags
    const promises: Promise<unknown>[] = []
    if (summaryDraft !== (entity.summary ?? "")) {
      promises.push(
        updateSummary({ entityId: entity.id, summary: summaryDraft }).unwrap()
      )
    }
    const currentLabels = entity.tags.map((t) => t.label)
    if (
      JSON.stringify([...tagsDraft].sort()) !==
      JSON.stringify([...currentLabels].sort())
    ) {
      promises.push(
        updateTags({ entityId: entity.id, tags: tagsDraft }).unwrap()
      )
    }
    await Promise.all(promises)
    setEditing(false)
    setSaved(true)
  }

  const confirm = () => {
    onConfirm()
  }

  return (
    <div className="space-y-4 rounded-lg border bg-card p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-bold tracking-wide text-muted-foreground uppercase">
          Profil-Vorschau
        </span>
        {saved && !editing && (
          <span className="text-xs text-green-600">✓ manuell angepasst</span>
        )}
        {!editing && !saved && (
          <button
            className="text-sm text-primary hover:underline"
            onClick={startEdit}
          >
            Anpassen
          </button>
        )}
        {editing && (
          <button
            className="text-sm text-primary hover:underline"
            onClick={() => void finishEditing()}
          >
            Fertig
          </button>
        )}
      </div>

      {/* Tags */}
      {editing ? (
        <div className="flex flex-wrap items-center gap-2">
          {tagsDraft.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
            >
              {tag}
              <button
                onClick={() => removeTag(tag)}
                className="text-primary/60 hover:text-primary"
                aria-label={`Entfernen: ${tag}`}
              >
                <X className="size-3" />
              </button>
            </span>
          ))}
          <span className="inline-flex items-center gap-1">
            <Input
              placeholder="Tag hinzufügen"
              value={newTag}
              onChange={(e) => setNewTag(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault()
                  addTag()
                }
              }}
              className="h-7 w-36 text-sm"
            />
            <Button
              size="sm"
              variant="ghost"
              className="h-7 px-2"
              onClick={addTag}
              disabled={!newTag.trim()}
            >
              <Plus className="size-3" />
            </Button>
          </span>
        </div>
      ) : (
        entity.tags.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {entity.tags.map((tag) => (
              <span
                key={tag.id}
                className="inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
              >
                {tag.label}
              </span>
            ))}
          </div>
        )
      )}

      {/* Summary */}
      {editing ? (
        <Textarea
          rows={4}
          value={summaryDraft}
          onChange={(e) => setSummaryDraft(e.target.value)}
          placeholder="Beschreib in eigenen Worten, woran du arbeitest und was dich ausmacht…"
          className="text-[15px] leading-relaxed"
        />
      ) : (
        <p className="text-[15px] leading-relaxed text-muted-foreground">
          {entity.summary ?? entity.headline ?? "Keine Beschreibung verfügbar."}
        </p>
      )}

      {/* Built-from line */}
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <span>
          Erstellt aus:{" "}
          {builtFrom.length > 0 ? builtFrom.join(", ") : "manueller Eingabe"}
        </span>
      </div>

      {/* Helper text */}
      {!editing && !saved && (
        <p className="text-xs text-muted-foreground">
          Nicht ganz richtig? Du kannst Tags und Text manuell anpassen.
        </p>
      )}

      {/* Confirm button */}
      <Button className="w-full" onClick={confirm}>
        {saved ? "Profil ansehen" : "Bestätigen & weiter"}
      </Button>
    </div>
  )
}