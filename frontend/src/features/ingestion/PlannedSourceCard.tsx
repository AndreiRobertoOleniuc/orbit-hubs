import { Badge } from "@/components/ui/badge"
import { SourceGlyph } from "@/components/SourceGlyph"
import type { PlannedSourceDef } from "@/features/ingestion/types"

/** Roadmap card — visible but not active in the MVP. */
export function PlannedSourceCard({ def }: { def: PlannedSourceDef }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-dashed bg-card px-4 py-3 opacity-70">
      <SourceGlyph glyph={def.glyph} />
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold">{def.title}</span>
        <span className="block text-xs text-muted-foreground">{def.hint}</span>
      </span>
      <Badge variant="outline">bald verfügbar</Badge>
    </div>
  )
}
