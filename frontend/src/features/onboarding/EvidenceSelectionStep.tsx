import { ExternalLink, FileCheck2, Loader2 } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { SourceGlyph } from "@/components/SourceGlyph"
import { sourceTypeInfo } from "@/features/profile/profileUtils"
import { useReplaceSourceSelectionMutation } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { EntityDto, EntitySourceDto } from "@/types/api"

const CURATABLE = new Set(["github", "orcid_pub"])

export function EvidenceSelectionStep({
  entity,
  onContinue,
}: {
  entity: EntityDto
  onContinue: (entity: EntityDto) => void
}) {
  const selectable = entity.sources.filter((source) =>
    CURATABLE.has(source.sourceType)
  )
  const fixed = entity.sources.filter(
    (source) => !CURATABLE.has(source.sourceType) && source.active
  )
  const [selected, setSelected] = useState<number[]>(
    selectable.filter((source) => source.active).map((source) => source.id)
  )
  const [error, setError] = useState<string | null>(null)
  const [replaceSelection, { isLoading }] = useReplaceSourceSelectionMutation()

  const hasEvidence = selected.length > 0 || fixed.length > 0

  const toggle = (id: number) =>
    setSelected((current) =>
      current.includes(id)
        ? current.filter((sourceId) => sourceId !== id)
        : [...current, id]
    )

  const save = async () => {
    setError(null)
    try {
      const updated = await replaceSelection({
        entityId: entity.id,
        activeSourceIds: selected,
      }).unwrap()
      onContinue(updated)
    } catch {
      setError(
        "Die Auswahl konnte nicht gespeichert werden. Bitte versuch es erneut."
      )
    }
  }

  return (
    <div>
      <div className="text-center">
        <FileCheck2 className="mx-auto size-9 text-primary" />
        <h1 className="mt-3 text-2xl font-bold tracking-tight">
          Welche Nachweise sollen dein Profil belegen?
        </h1>
        <p className="mx-auto mt-2 max-w-lg text-sm leading-relaxed text-muted-foreground">
          Wähle bewusst aus, welche GitHub-Projekte und Publikationen öffentlich
          verwendet werden. Nur ausgewählte Nachweise beeinflussen dein Profil
          und das Matching.
        </p>
      </div>

      {fixed.length > 0 && (
        <div className="mt-6 rounded-lg border bg-muted/30 px-4 py-3">
          <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            Bereits verwendet
          </p>
          <p className="mt-1 text-sm">
            {fixed
              .map((source) => sourceTypeInfo(source.sourceType).label)
              .join(", ")}
          </p>
        </div>
      )}

      <div className="mt-4 space-y-2">
        {selectable.length > 0 ? (
          selectable.map((source) => (
            <EvidenceRow
              key={source.id}
              source={source}
              selected={selected.includes(source.id)}
              onToggle={() => toggle(source.id)}
            />
          ))
        ) : (
          <div className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
            Deine verbundenen Quellen enthalten keine einzelnen GitHub-Projekte
            oder ORCID-Publikationen. Du kannst direkt weitergehen.
          </div>
        )}
      </div>

      {!hasEvidence && (
        <p className="mt-3 text-sm text-destructive" role="alert">
          Wähle mindestens einen Nachweis aus.
        </p>
      )}
      {error && (
        <p className="mt-3 text-sm text-destructive" role="alert">
          {error}
        </p>
      )}

      <Button
        className="mt-6 w-full"
        disabled={!hasEvidence || isLoading}
        onClick={() => void save()}
      >
        {isLoading && <Loader2 className="size-4 animate-spin" />}
        Auswahl speichern und weiter
      </Button>
    </div>
  )
}

function EvidenceRow({
  source,
  selected,
  onToggle,
}: {
  source: EntitySourceDto
  selected: boolean
  onToggle: () => void
}) {
  const info = sourceTypeInfo(source.sourceType)
  return (
    <div
      className={cn(
        "flex items-center gap-3 rounded-lg border bg-card px-4 py-3 transition-colors",
        selected && "border-primary/50 bg-primary/5"
      )}
    >
      <label
        htmlFor={`evidence-${source.id}`}
        className="flex min-w-0 flex-1 cursor-pointer items-center gap-3"
      >
        <input
          id={`evidence-${source.id}`}
          type="checkbox"
          checked={selected}
          onChange={onToggle}
          className="size-4 accent-primary"
        />
        <SourceGlyph glyph={info.glyph} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium">
            {source.label || info.label}
          </span>
          <span className="block text-xs text-muted-foreground">
            {info.label}
          </span>
        </span>
      </label>
      {source.sourceUrl && (
        <a
          href={source.sourceUrl}
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Quelle öffnen"
          className="text-muted-foreground hover:text-foreground"
          onClick={(event) => event.stopPropagation()}
        >
          <ExternalLink className="size-4" />
        </a>
      )}
    </div>
  )
}
