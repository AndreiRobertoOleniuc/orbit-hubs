import { Badge } from "@/components/ui/badge"
import { SourceGlyph } from "@/components/SourceGlyph"
import type { PlannedSourceDef } from "@/features/ingestion/types"

/** Roadmap card — visible but not active in the MVP. */
export function PlannedSourceCard({ def }: { def: PlannedSourceDef }) {
  return (
    <div
      aria-disabled="true"
      className="flex cursor-not-allowed items-center gap-3 rounded-lg border border-dashed bg-muted/25 px-4 py-3 opacity-60"
    >
      <span className="grayscale">
        <SourceGlyph glyph={def.glyph} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold">{def.title}</span>
        <span className="block text-xs text-muted-foreground">{def.hint}</span>
      </span>
      <Badge variant="outline" className="shrink-0 bg-background/60">
        Bald verfügbar
      </Badge>
    </div>
  )
}
