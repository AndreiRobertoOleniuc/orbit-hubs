import { ExternalLink } from "lucide-react"

import { SourceGlyph } from "@/components/SourceGlyph"
import {
  externalUrl,
  sourceTypeInfo,
  sourceYear,
} from "@/features/profile/profileUtils"
import type { EntitySourceDto } from "@/types/api"

/**
 * Evidence rows ("Nachweise") — the ingested sources are the citable units
 * that back this profile.
 */
export function NachweisList({ sources }: { sources: EntitySourceDto[] }) {
  if (sources.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Noch keine Nachweise — verknüpfe Quellen, um dein Profil zu belegen.
      </p>
    )
  }
  return (
    <div className="divide-y">
      {sources.map((source) => {
        const info = sourceTypeInfo(source.sourceType)
        const url = externalUrl(source)
        const year = sourceYear(source)
        return (
          <div key={source.id} className="flex items-center gap-3 py-2.5">
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
          </div>
        )
      })}
    </div>
  )
}
