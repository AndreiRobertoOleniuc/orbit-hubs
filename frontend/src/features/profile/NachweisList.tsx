import { ExternalLink } from "lucide-react"

import { SourceGlyph } from "@/components/SourceGlyph"
import {
  externalUrl,
  sourceTypeInfo,
  sourceYear,
} from "@/features/profile/profileUtils"
import { useSetSourceActiveMutation } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { EntitySourceDto } from "@/types/api"

/** Source types that fan out to many rows and are worth curating individually. */
const CURATABLE_TYPES = ["github", "orcid_pub"]

/**
 * Evidence rows ("Nachweise") — the ingested sources are the citable units
 * that back this profile.
 *
 * When {@link entityId} is passed (the owner's own view), curatable sources
 * (GitHub repos / publications) get an on/off toggle: switching one off keeps
 * it on the profile but excludes it from search. Without {@link entityId}
 * (public view), deselected sources are hidden entirely.
 */
export function NachweisList({
  sources,
  entityId,
}: {
  sources: EntitySourceDto[]
  entityId?: number
}) {
  const isOwner = entityId != null
  const visible = isOwner ? sources : sources.filter((s) => s.active)

  if (visible.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Noch keine Nachweise — verknüpfe Quellen, um dein Profil zu belegen.
      </p>
    )
  }
  return (
    <div className="divide-y">
      {visible.map((source) => {
        const info = sourceTypeInfo(source.sourceType)
        const url = externalUrl(source)
        const year = sourceYear(source)
        const curatable = isOwner && CURATABLE_TYPES.includes(source.sourceType)
        const dimmed = isOwner && !source.active
        return (
          <div
            key={source.id}
            className={cn(
              "flex items-center gap-3 py-2.5",
              dimmed && "opacity-50"
            )}
          >
            <SourceGlyph glyph={info.glyph} />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">
                {source.label || info.label}
              </span>
              <span className="block text-xs text-muted-foreground">
                {info.label}
                {source.fetchStatus && source.fetchStatus !== "ok"
                  ? ` · ${source.fetchStatus}`
                  : ""}
                {dimmed ? " · nicht in Suche" : ""}
              </span>
            </span>
            {year && (
              <span className="text-xs text-muted-foreground tabular-nums">
                {year}
              </span>
            )}
            {url && (
              <a
                href={url}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Quelle öffnen"
                className="text-muted-foreground hover:text-foreground"
              >
                <ExternalLink className="size-4" />
              </a>
            )}
            {curatable && entityId != null && (
              <SourceToggle entityId={entityId} source={source} />
            )}
          </div>
        )
      })}
    </div>
  )
}

/** Inline on/off switch for a single source (owner curation). */
function SourceToggle({
  entityId,
  source,
}: {
  entityId: number
  source: EntitySourceDto
}) {
  const [setSourceActive, { isLoading }] = useSetSourceActiveMutation()
  const toggle = () =>
    void setSourceActive({
      entityId,
      sourceId: source.id,
      active: !source.active,
    })
  return (
    <button
      type="button"
      role="switch"
      aria-checked={source.active}
      aria-label={
        source.active
          ? "Aus der Suche entfernen"
          : "Wieder in die Suche aufnehmen"
      }
      title={
        source.active
          ? "In Suche aktiv — klicken zum Deaktivieren"
          : "Deaktiviert — klicken zum Aktivieren"
      }
      disabled={isLoading}
      onClick={toggle}
      className={cn(
        "relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors",
        source.active ? "bg-primary" : "bg-muted-foreground/30",
        isLoading && "opacity-50"
      )}
    >
      <span
        className={cn(
          "inline-block size-4 rounded-full bg-white transition-transform",
          source.active ? "translate-x-4" : "translate-x-0.5"
        )}
      />
    </button>
  )
}
