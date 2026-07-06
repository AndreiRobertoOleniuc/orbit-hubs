import { useState } from "react"
import { Check, RefreshCw } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import {
  PlannedSourceCard,
  SourceCard,
  SourceGlyph,
} from "@/features/ingestion/SourceCard"
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
import { ProfileSectionCard } from "@/features/profile/PublicProfileView"
import { builtFromLabels } from "@/features/profile/profileUtils"
import type { EntityDto } from "@/types/api"

/**
 * The private own-profile view: Kompetenzen, linked sources (with the ability
 * to link more — re-runs the ingestion pipeline) and Nachweise.
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

  return (
    <div className="space-y-5">
      <ProfileHeader entity={entity} />

      <ProfileSectionCard title="Kompetenzen">
        <div className="space-y-3">
          {entity.headline ? (
            <p className="text-[15px] leading-relaxed">{entity.headline}</p>
          ) : (
            <p className="text-sm text-muted-foreground">
              Noch keine Quellen verknüpft — dieses Profil wurde manuell
              erstellt.
            </p>
          )}
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <span>
              Erstellt aus:{" "}
              {builtFrom.length > 0
                ? builtFrom.join(", ")
                : "manueller Eingabe"}
            </span>
            {isLoading && <RefreshCw className="size-3.5 animate-spin" />}
          </p>
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
