/** Compact square icon badge with a short text glyph (e.g. "GH", "iD", "HR"). */
export function SourceGlyph({ glyph }: { glyph: string }) {
  return (
    <span className="flex size-9 shrink-0 items-center justify-center rounded-md border bg-muted text-sm font-semibold">
      {glyph}
    </span>
  )
}
