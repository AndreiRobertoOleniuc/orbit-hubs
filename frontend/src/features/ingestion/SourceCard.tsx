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
          {def.guide && !added && !open && (
            <span className="mt-1 inline-flex items-center gap-1 text-xs font-medium text-primary">
              {def.upload ? "PDF-Export erklären" : `${def.title} verknüpfen`}
            </span>
          )}
        </span>
        {busy ? (
          <Loader2 className="size-4 animate-spin text-muted-foreground" />
        ) : added ? (
          <Badge variant="secondary" className="max-w-32 gap-1 sm:max-w-48">
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
            <div className="mx-4 mt-3 space-y-2 rounded-lg border border-primary/20 bg-primary/5 p-3 text-sm">
              <p className="font-semibold text-foreground">{def.guide.intro}</p>
              <ol className="list-decimal space-y-1 pl-5 text-muted-foreground">
                {def.guide.steps.map((step, i) => (
                  <li key={i}>{step}</li>
                ))}
              </ol>
              {def.guide.link && (
                <Button asChild size="sm" variant="outline" className="mt-1">
                  <a
                    href={def.guide.link.href}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {def.guide.link.label}
                    <ExternalLink className="size-3.5" />
                  </a>
                </Button>
              )}
            </div>
          )}
          <div className="flex flex-col gap-2 px-4 py-3 sm:flex-row">
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
                  className="w-full sm:flex-1"
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
