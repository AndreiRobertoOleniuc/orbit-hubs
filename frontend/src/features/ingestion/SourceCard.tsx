import { useRef, useState } from "react"
import { Check, Loader2, Minus, Plus, X } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import type {
  PlannedSourceDef,
  SourceDef,
  SourceDraft,
} from "@/features/ingestion/types"
import { cn } from "@/lib/utils"

export function SourceGlyph({ glyph }: { glyph: string }) {
  return (
    <span className="flex size-9 shrink-0 items-center justify-center rounded-md border bg-muted text-sm font-semibold">
      {glyph}
    </span>
  )
}

interface SourceCardProps {
  def: SourceDef
  added?: SourceDraft
  helper?: string
  /** Shown while an add triggered from this card is in flight. */
  busy?: boolean
  onAdd: (value: string, file?: File) => void
  /** Omit to hide the remove control (e.g. persisted sources can't be deleted yet). */
  onRemove?: () => void
}

/**
 * Expandable "Quelle verlinken" card: closed shows title + hint, open shows an
 * input (or PDF picker), added shows the value with a remove button.
 */
export function SourceCard({
  def,
  added,
  helper,
  busy,
  onAdd,
  onRemove,
}: SourceCardProps) {
  const [open, setOpen] = useState(false)
  const [value, setValue] = useState("")
  const fileRef = useRef<HTMLInputElement>(null)

  const commit = (v: string, file?: File) => {
    onAdd(v, file)
    setOpen(false)
    setValue("")
  }

  return (
    <div className="rounded-lg border bg-card">
      <button
        type="button"
        className={cn(
          "flex w-full items-center gap-3 px-4 py-3 text-left",
          added && "cursor-default"
        )}
        onClick={() => {
          if (!added) setOpen(!open)
        }}
      >
        <SourceGlyph glyph={def.glyph} />
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold">{def.title}</span>
          <span className="block text-xs text-muted-foreground">
            {def.hint}
          </span>
        </span>
        {busy ? (
          <Loader2 className="size-4 animate-spin text-muted-foreground" />
        ) : added ? (
          <Badge variant="secondary" className="max-w-48 gap-1">
            <Check className="size-3" />
            <span className="truncate">{added.value}</span>
            {onRemove && (
              <span
                role="button"
                tabIndex={0}
                aria-label={`${def.title} entfernen`}
                className="ml-1 cursor-pointer rounded-sm hover:text-destructive"
                onClick={(e) => {
                  e.stopPropagation()
                  onRemove()
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.stopPropagation()
                    onRemove()
                  }
                }}
              >
                <X className="size-3" />
              </span>
            )}
          </Badge>
        ) : open ? (
          <Minus className="size-4 text-muted-foreground" />
        ) : (
          <Plus className="size-4 text-muted-foreground" />
        )}
      </button>

      {helper && !added && !open && (
        <p className="px-4 pb-3 text-xs text-muted-foreground">{helper}</p>
      )}

      {open && !added && (
        <div className="flex gap-2 border-t px-4 py-3">
          {def.upload ? (
            <>
              <input
                ref={fileRef}
                type="file"
                accept=".pdf"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) commit(file.name, file)
                }}
              />
              <Button
                type="button"
                variant="outline"
                className="flex-1"
                onClick={() => fileRef.current?.click()}
              >
                PDF auswählen…
              </Button>
            </>
          ) : (
            <>
              <Input
                autoFocus
                placeholder={def.placeholder}
                value={value}
                onChange={(e) => setValue(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && value.trim()) commit(value.trim())
                }}
              />
              <Button
                type="button"
                variant="outline"
                disabled={!value.trim()}
                onClick={() => commit(value.trim())}
              >
                Hinzufügen
              </Button>
            </>
          )}
        </div>
      )}
    </div>
  )
}

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
