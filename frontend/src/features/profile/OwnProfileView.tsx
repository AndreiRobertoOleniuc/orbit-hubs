import { useState } from "react"
import { Check, Plus, RefreshCw, X } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { SourceGlyph } from "@/components/SourceGlyph"
import { PlannedSourceCard } from "@/features/ingestion/PlannedSourceCard"
import { SourceCard } from "@/features/ingestion/SourceCard"
import { KIND_TO_SOURCE_TYPES } from "@/features/ingestion/requests"
import {
  ORG_PLANNED,
  ORG_SOURCES,
  PERSON_PLANNED,
  PERSON_SOURCES,
} from "@/features/ingestion/sourceDefs"
import type { SourceKind } from "@/features/ingestion/types"
import { useSourceIngestion } from "@/features/ingestion/useSourceIngestion"
import { NachweisList } from "@/features/profile/NachweisList"
import { ProfileHeader } from "@/features/profile/ProfileHeader"
import { ProfileSectionCard } from "@/features/profile/ProfileSectionCard"
import { builtFromLabels } from "@/features/profile/profileUtils"
import {
  useResyncEntityMutation,
  useUpdateEntitySummaryMutation,
  useUpdateEntityTagsMutation,
} from "@/lib/api"
import type { EntityDto } from "@/types/api"

/**
 * The private own-profile view: Kompetenzen (rich summary + editable tags),
 * linked sources (with the ability to link more — re-runs ingestion), and
 * Nachweise.
 */
export function OwnProfileView({ entity }: { entity: EntityDto }) {
  const isOrgEntity = entity.entityType === "organization"
  const sourceDefs = isOrgEntity ? ORG_SOURCES : PERSON_SOURCES
  const plannedDefs = isOrgEntity ? ORG_PLANNED : PERSON_PLANNED
  const builtFrom = builtFromLabels(entity)
  const hasZefix = entity.sources.some((s) => s.sourceType === "zefix")

  const { ingestSourceKind, isLoading } = useSourceIngestion()
  const [busyKind, setBusyKind] = useState<SourceKind | null>(null)
  const [addError, setAddError] = useState<string | null>(null)

  // Tag editing state
  const [editingTags, setEditingTags] = useState(false)
  const [tagDraft, setTagDraft] = useState<string[]>([])
  const [newTag, setNewTag] = useState("")
  const [updateTags] = useUpdateEntityTagsMutation()

  // Summary editing state
  const [editingSummary, setEditingSummary] = useState(false)
  const [summaryDraft, setSummaryDraft] = useState("")
  const [updateSummary] = useUpdateEntitySummaryMutation()

  // Resync state
  const [resyncing, setResyncing] = useState(false)
  const [resyncMessage, setResyncMessage] = useState<string | null>(null)
  const [resyncEntity] = useResyncEntityMutation()

  const linkedSource = (kind: SourceKind) =>
    entity.sources.some((s) =>
      KIND_TO_SOURCE_TYPES[kind].includes(s.sourceType)
    )
      ? { kind, value: "verknüpft" }
      : undefined

  const addSource = async (kind: SourceKind, value: string, file?: File) => {
    setBusyKind(kind)
    setAddError(null)
    const outcome = await ingestSourceKind(kind, value, file, {
      org: isOrgEntity,
    })
    if (outcome.status === "error") setAddError(outcome.detail ?? null)
    setBusyKind(null)
  }

  const startEditTags = () => {
    setTagDraft(entity.tags.map((t) => t.label))
    setEditingTags(true)
  }

  const commitTag = () => {
    const v = newTag.trim()
    if (v && !tagDraft.includes(v)) {
      setTagDraft([...tagDraft, v])
    }
    setNewTag("")
  }

  const saveTags = async () => {
    await updateTags({ entityId: entity.id, tags: tagDraft }).unwrap()
    setEditingTags(false)
  }

  const startEditSummary = () => {
    setSummaryDraft(entity.summary ?? entity.headline ?? "")
    setEditingSummary(true)
  }

  const saveSummary = async () => {
    await updateSummary({
      entityId: entity.id,
      summary: summaryDraft,
    }).unwrap()
    setEditingSummary(false)
  }

  const handleResync = async () => {
    setResyncing(true)
    setResyncMessage(null)
    try {
      const result = await resyncEntity(entity.id).unwrap()
      const parts: string[] = []
      if (result.ingested > 0) parts.push(`${result.ingested} aktualisiert`)
      if (result.skipped > 0) parts.push(`${result.skipped} unverändert`)
      if (result.errors > 0) parts.push(`${result.errors} fehlgeschlagen`)
      setResyncMessage(
        parts.length > 0 ? `✓ ${parts.join(", ")}` : "✓ Profil aktualisiert"
      )
    } catch {
      setResyncMessage("Resync fehlgeschlagen — versuche es später erneut.")
    }
    setResyncing(false)
    setTimeout(() => setResyncMessage(null), 5000)
  }

  return (
    <div className="space-y-5">
      <ProfileHeader entity={entity} />

      <ProfileSectionCard title="Kompetenzen">
        <div className="space-y-4">
          {/* Summary paragraph */}
          {editingSummary ? (
            <div className="space-y-2">
              <textarea
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-[15px] leading-relaxed"
                rows={4}
                value={summaryDraft}
                autoFocus
                onChange={(e) => setSummaryDraft(e.target.value)}
              />
              <div className="flex justify-end gap-2">
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setEditingSummary(false)}
                >
                  Abbrechen
                </Button>
                <Button size="sm" onClick={() => void saveSummary()}>
                  Speichern
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              {entity.summary ? (
                <p className="text-[15px] leading-relaxed">{entity.summary}</p>
              ) : entity.headline ? (
                <p className="text-[15px] leading-relaxed">{entity.headline}</p>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Noch keine Quellen verknüpft — dieses Profil wurde manuell
                  erstellt.
                </p>
              )}
              <button
                className="text-xs text-primary hover:underline"
                onClick={startEditSummary}
              >
                Text anpassen
              </button>
            </div>
          )}

          {/* Tags */}
          {editingTags ? (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                {tagDraft.map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
                  >
                    {tag}
                    <button
                      onClick={() =>
                        setTagDraft(tagDraft.filter((t) => t !== tag))
                      }
                      className="text-primary/60 hover:text-primary"
                      aria-label={`Entfernen: ${tag}`}
                    >
                      <X className="size-3" />
                    </button>
                  </span>
                ))}
                <span className="inline-flex items-center gap-1">
                  <Input
                    placeholder="Kompetenz hinzufügen"
                    value={newTag}
                    onChange={(e) => setNewTag(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault()
                        commitTag()
                      }
                    }}
                    className="h-7 w-40 text-sm"
                  />
                  <Button
                    size="sm"
                    variant="ghost"
                    className="h-7 px-2"
                    onClick={commitTag}
                    disabled={!newTag.trim()}
                  >
                    <Plus className="size-3" />
                  </Button>
                </span>
              </div>
              <div className="flex justify-end gap-2">
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setEditingTags(false)}
                >
                  Abbrechen
                </Button>
                <Button size="sm" onClick={() => void saveTags()}>
                  Speichern
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              {entity.tags.length > 0 ? (
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
              ) : (
                <p className="text-sm text-muted-foreground">
                  Noch keine Tags abgeleitet — verknüpfe weitere Quellen.
                </p>
              )}
              <button
                className="text-xs text-primary hover:underline"
                onClick={startEditTags}
              >
                Tags anpassen
              </button>
              <p className="text-xs text-muted-foreground">
                Tags werden aus deinen Quellen abgeleitet — du kannst sie hier
                manuell ergänzen oder entfernen.
              </p>
            </div>
          )}

          {/* Built-from line + resync button */}
          <div className="space-y-1.5">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <span>
                Erstellt aus:{" "}
                {builtFrom.length > 0
                  ? builtFrom.join(", ")
                  : "manueller Eingabe"}
              </span>
              {(isLoading || resyncing) && (
                <RefreshCw className="size-3.5 animate-spin" />
              )}
              <button
                className="flex items-center gap-1 text-xs text-primary hover:underline disabled:opacity-50"
                onClick={() => void handleResync()}
                disabled={resyncing || isLoading}
                title="Quellen neu auswerten — aktualisiert Kompetenzen und Nachweise aus allen verknüpften Quellen"
              >
                <RefreshCw
                  className={resyncing ? "size-3.5 animate-spin" : "size-3.5"}
                />
                Quellen neu auswerten
              </button>
            </div>
            {resyncMessage && (
              <p className="text-xs text-muted-foreground">{resyncMessage}</p>
            )}
          </div>
        </div>
      </ProfileSectionCard>

      <ProfileSectionCard title="Deine Quellen">
        <p className="mb-4 text-sm leading-relaxed text-muted-foreground">
          Je mehr du verlinkst, desto besser belegt ist dein Profil —
          Kompetenzen und Nachweise leiten wir automatisch ab.
        </p>
        <div className="space-y-2.5">
          {hasZefix && (
            <div className="flex items-center gap-3 rounded-lg border bg-muted/40 px-4 py-3">
              <SourceGlyph glyph="HR" />
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold">
                  Handelsregister
                </span>
                <span className="block text-xs text-muted-foreground">
                  über Zefix verifiziert
                </span>
              </span>
              <Badge variant="secondary">
                <Check className="size-3" /> verknüpft
              </Badge>
            </div>
          )}
          {sourceDefs.map((def) => (
            <SourceCard
              key={def.title}
              def={def}
              added={linkedSource(def.kind)}
              busy={busyKind === def.kind}
              onAdd={(value, file) => void addSource(def.kind, value, file)}
            />
          ))}
          {plannedDefs.length > 0 && (
            <>
              <p className="pt-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                Bald verfügbar
              </p>
              {plannedDefs.map((def) => (
                <PlannedSourceCard key={def.title} def={def} />
              ))}
            </>
          )}
        </div>
        {addError && (
          <p className="mt-3 text-sm text-destructive">{addError}</p>
        )}
      </ProfileSectionCard>

      <ProfileSectionCard title="Nachweise">
        <NachweisList sources={entity.sources} />
      </ProfileSectionCard>
    </div>
  )
}