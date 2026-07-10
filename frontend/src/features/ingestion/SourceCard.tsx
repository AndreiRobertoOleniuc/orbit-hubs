import { useRef, useState } from "react"
import { Check, ExternalLink, Loader2, Minus, Plus, X } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { SourceGlyph } from "@/components/SourceGlyph"
import type { SourceDef, SourceDraft } from "@/features/ingestion/types"
import { cn } from "@/lib/utils"

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
        <div className="border-t">
          {def.guide && (
            <div className="space-y-1 px-4 pt-3 text-xs text-muted-foreground">
              <p className="font-medium text-foreground">{def.guide.intro}</p>
              <ol className="list-decimal space-y-0.5 pl-4">
                {def.guide.steps.map((step, i) => (
                  <li key={i}>{step}</li>
                ))}
              </ol>
              {def.guide.link && (
                <a
                  href={def.guide.link.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-primary hover:underline"
                >
                  {def.guide.link.label}
                  <ExternalLink className="size-3" />
                </a>
              )}
            </div>
          )}
          <div className="flex gap-2 px-4 py-3">
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
        </div>
      )}
    </div>
  )
}
