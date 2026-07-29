import { useState } from "react"
import { Check, Plus, RefreshCw, Trash2, X } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
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
import { AvailabilityEditor } from "@/features/profile/AvailabilityEditor"
import { HubSelector } from "@/features/profile/HubSelector"
import { NachweisList } from "@/features/profile/NachweisList"
import { ProfileCompletenessBanner } from "@/features/profile/ProfileCompletenessBanner"
import { ProfileHeader } from "@/features/profile/ProfileHeader"
import { ProfileMetaEditor } from "@/features/profile/ProfileMetaEditor"
import { ProfileSectionCard } from "@/features/profile/ProfileSectionCard"
import { SubscribedTopics } from "@/features/profile/SubscribedTopics"
import { builtFromLabels, externalUrl } from "@/features/profile/profileUtils"
import {
  useDeleteEntityMutation,
  useResyncEntityMutation,
  useUpdateEntityModeMutation,
  useUpdateEntitySummaryMutation,
  useUpdateEntityTagsMutation,
} from "@/lib/api"
import { sameSet } from "@/lib/utils"
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

  // Mode-dependent profile (spec §5.1): demand hides the completeness banner,
  // the availability editor, and subscribed topics; supply shows the full stack.
  const supply = entity.profileMode !== "demand"
  const [updateMode] = useUpdateEntityModeMutation()
  const switchToSupply = () =>
    void updateMode({ entityId: entity.id, mode: "supply" })
  const [editingMeta, setEditingMeta] = useState(false)

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
  const [deleteEntity, { isLoading: isDeleting }] = useDeleteEntityMutation()
  const [deleteConfirmation, setDeleteConfirmation] = useState("")
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const handleDelete = async () => {
    setDeleteError(null)
    try {
      await deleteEntity(entity.id).unwrap()
    } catch {
      setDeleteError("Das Profil konnte nicht gelöscht werden.")
    }
  }

  // Keep the matched source around: the card shows a placeholder value, but the
  // persisted URL is what makes a linked website openable from here.
  const linkedSource = (kind: SourceKind) => {
    const source = entity.sources.find((s) =>
      KIND_TO_SOURCE_TYPES[kind].includes(s.sourceType)
    )
    if (!source) return undefined
    return { draft: { kind, value: "verknüpft" }, href: externalUrl(source) }
  }

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
      setResyncMessage(
        "Die Quellen konnten nicht neu ausgewertet werden. Versuch es später nochmals."
      )
    }
    setResyncing(false)
    setTimeout(() => setResyncMessage(null), 5000)
  }

  return (
    <div className="space-y-5">
      {supply && <ProfileCompletenessBanner entity={entity} />}

      <ProfileHeader
        entity={entity}
        editable
        onEditMeta={() => setEditingMeta((v) => !v)}
      />

      {editingMeta && (
        <ProfileMetaEditor
          entity={entity}
          onClose={() => setEditingMeta(false)}
        />
      )}

      {!supply && (
        <p className="rounded-lg border border-dashed bg-muted/40 px-4 py-3 text-sm text-muted-foreground">
          Du bist im Modus{" "}
          <strong className="text-foreground">«Ich suche»</strong>. Um gefunden
          zu werden, wechsle in den Modus{" "}
          <button
            className="font-medium text-primary hover:underline"
            onClick={switchToSupply}
          >
            Gefunden werden
          </button>{" "}
          und setze deine Verfügbarkeit.
        </p>
      )}

      <Tabs
        defaultValue={supply ? "verfuegbarkeit" : "kompetenzen"}
        className="w-full"
      >
        <TabsList className="w-full">
          {supply && (
            <TabsTrigger value="verfuegbarkeit">Verfügbarkeit</TabsTrigger>
          )}
          <TabsTrigger value="kompetenzen">
            <span className="sm:hidden">Kompetenzen</span>
            <span className="hidden sm:inline">Kompetenzen &amp; Hubs</span>
          </TabsTrigger>
          <TabsTrigger value="quellen">
            <span className="sm:hidden">Quellen</span>
            <span className="hidden sm:inline">Quellen &amp; Nachweise</span>
          </TabsTrigger>
        </TabsList>

        {supply && (
          <TabsContent value="verfuegbarkeit" className="space-y-5">
            <ProfileSectionCard title="Verfügbarkeit">
              <AvailabilityEditor entity={entity} />
            </ProfileSectionCard>
            <ProfileSectionCard title="Abonnierte Themen">
              <SubscribedTopics entityId={entity.id} />
            </ProfileSectionCard>
          </TabsContent>
        )}

        <TabsContent value="kompetenzen" className="space-y-5">
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
                  <div className="grid gap-2 sm:flex sm:justify-end">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setEditingSummary(false)}
                    >
                      Abbrechen
                    </Button>
                    <Button
                      size="sm"
                      onClick={() => void saveSummary()}
                      disabled={
                        summaryDraft ===
                        (entity.summary ?? entity.headline ?? "")
                      }
                    >
                      Speichern
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="space-y-2">
                  {entity.summary ? (
                    <p className="text-[15px] leading-relaxed">
                      {entity.summary}
                    </p>
                  ) : entity.headline ? (
                    <p className="text-[15px] leading-relaxed">
                      {entity.headline}
                    </p>
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      Noch keine Quellen verknüpft. Dieses Profil wurde manuell
                      angelegt.
                    </p>
                  )}
                  <button
                    className="rounded-sm text-xs text-primary hover:underline focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none"
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
                  <div className="grid gap-2 sm:flex sm:justify-end">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setEditingTags(false)}
                    >
                      Abbrechen
                    </Button>
                    <Button
                      size="sm"
                      onClick={() => void saveTags()}
                      disabled={sameSet(
                        tagDraft,
                        entity.tags.map((t) => t.label)
                      )}
                    >
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
                      Noch keine Tags vorhanden. Verknüpfe weitere Quellen oder
                      füge Tags manuell hinzu.
                    </p>
                  )}
                  <button
                    className="text-xs text-primary hover:underline"
                    onClick={startEditTags}
                  >
                    Tags anpassen
                  </button>
                  <p className="text-xs text-muted-foreground">
                    Orbit leitet Tags aus deinen Quellen ab. Du kannst sie hier
                    ergänzen oder entfernen.
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
                  <button
                    className="flex min-h-10 items-center gap-1 rounded-sm text-xs text-primary hover:underline focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none disabled:opacity-50"
                    onClick={() => void handleResync()}
                    disabled={resyncing || isLoading}
                    title="Quellen neu auswerten und Kompetenzen sowie Nachweise aktualisieren"
                  >
                    <RefreshCw
                      className={
                        resyncing || isLoading
                          ? "size-3.5 animate-spin"
                          : "size-3.5"
                      }
                    />
                    Quellen neu auswerten
                  </button>
                </div>
                {resyncMessage && (
                  <p className="text-xs text-muted-foreground">
                    {resyncMessage}
                  </p>
                )}
              </div>
            </div>
          </ProfileSectionCard>

          <ProfileSectionCard title="Hubs">
            <HubSelector entity={entity} />
          </ProfileSectionCard>
        </TabsContent>

        <TabsContent value="quellen" className="space-y-5">
          <p className="rounded-lg border border-dashed bg-muted/30 px-4 py-3 text-sm leading-relaxed text-muted-foreground">
            <strong className="text-foreground">Quellen</strong> sind, was du
            verlinkst (LinkedIn, GitHub, Website …).{" "}
            <strong className="text-foreground">Nachweise</strong> sind die
            zitierten Belege, die Orbit daraus für dein Profil ableitet.
          </p>
          <ProfileSectionCard title="Deine Quellen">
            <p className="mb-4 text-sm leading-relaxed text-muted-foreground">
              Je mehr du verlinkst, desto besser ist dein Profil belegt.
              Kompetenzen und Nachweise entstehen automatisch aus den Quellen.
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
              {sourceDefs.map((def) => {
                const linked = linkedSource(def.kind)
                return (
                  <SourceCard
                    key={def.title}
                    def={def}
                    added={linked?.draft}
                    href={linked?.href}
                    busy={busyKind === def.kind}
                    onAdd={(value, file) =>
                      void addSource(def.kind, value, file)
                    }
                  />
                )
              })}
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
            <NachweisList sources={entity.sources} entityId={entity.id} />
          </ProfileSectionCard>
        </TabsContent>
      </Tabs>

      <div className="flex justify-end border-t pt-5">
        <Dialog
          onOpenChange={(open) => {
            if (!open) {
              setDeleteConfirmation("")
              setDeleteError(null)
            }
          }}
        >
          <DialogTrigger asChild>
            <Button
              size="sm"
              variant="ghost"
              className="text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
            >
              <Trash2 className="size-3.5" />
              Profil löschen und neu beginnen
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Profil endgültig löschen?</DialogTitle>
              <DialogDescription>
                Quellen, Nachweise, Kompetenzen, Verfügbarkeit, Hubs und
                Kontaktverläufe dieses Profils werden gelöscht. Dein Konto und
                deine eigenen Anfragen bleiben bestehen.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-2">
              <label
                htmlFor="delete-confirmation"
                className="text-sm font-medium"
              >
                Gib <strong>LÖSCHEN</strong> zur Bestätigung ein.
              </label>
              <Input
                id="delete-confirmation"
                value={deleteConfirmation}
                autoComplete="off"
                onChange={(event) => setDeleteConfirmation(event.target.value)}
              />
              {deleteError && (
                <p className="text-sm text-destructive">{deleteError}</p>
              )}
            </div>

            <DialogFooter>
              <DialogClose asChild>
                <Button variant="outline" disabled={isDeleting}>
                  Abbrechen
                </Button>
              </DialogClose>
              <Button
                variant="destructive"
                disabled={deleteConfirmation !== "LÖSCHEN" || isDeleting}
                onClick={() => void handleDelete()}
              >
                {isDeleting ? "Wird gelöscht…" : "Profil endgültig löschen"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  )
}
