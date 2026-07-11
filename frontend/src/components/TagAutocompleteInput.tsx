import { useEffect, useState } from "react"
import { Plus } from "lucide-react"

import { Input } from "@/components/ui/input"
import { useLazySearchTagsQuery } from "@/lib/api"
import { cn } from "@/lib/utils"

interface TagAutocompleteInputProps {
  /** Already-chosen labels, excluded from the suggestion list. */
  selected: string[]
  /** Called with the chosen (existing or new) label. */
  onAdd: (label: string) => void
  placeholder?: string
}

/**
 * Text input with tag autocomplete over the controlled vocabulary
 * (GET /tags?q=…). As you type, matching existing tags are suggested; you can
 * pick one, or add a brand-new tag. Keyboard: ↑/↓ to move, Enter to choose,
 * Esc to close. Reusable for subscriptions and Kompetenzen alike.
 */
export function TagAutocompleteInput({
  selected,
  onAdd,
  placeholder,
}: TagAutocompleteInputProps) {
  const [value, setValue] = useState("")
  const [open, setOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)
  const [trigger, { data }] = useLazySearchTagsQuery()

  // Debounce the lookup so we don't fire a request on every keystroke. Opening
  // and closing the dropdown is handled in the event handlers (not here) to
  // avoid a synchronous setState inside the effect body.
  useEffect(() => {
    const q = value.trim()
    if (!q) return
    const t = setTimeout(() => {
      void trigger(q)
      setHighlight(0)
    }, 200)
    return () => clearTimeout(t)
  }, [value, trigger])

  const suggestions = (data ?? []).filter((s) => !selected.includes(s.label))
  const typed = value.trim()
  const exactExists = suggestions.some(
    (s) => s.label.toLowerCase() === typed.toLowerCase()
  )

  const commit = (label: string) => {
    const v = label.trim()
    if (v && !selected.includes(v)) onAdd(v)
    setValue("")
    setOpen(false)
    setHighlight(0)
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (!open || suggestions.length === 0) {
      if (e.key === "Enter") {
        e.preventDefault()
        commit(value)
      }
      return
    }
    if (e.key === "ArrowDown") {
      e.preventDefault()
      setHighlight((h) => Math.min(h + 1, suggestions.length - 1))
    } else if (e.key === "ArrowUp") {
      e.preventDefault()
      setHighlight((h) => Math.max(h - 1, 0))
    } else if (e.key === "Enter") {
      e.preventDefault()
      commit(suggestions[highlight]?.label ?? value)
    } else if (e.key === "Escape") {
      setOpen(false)
    }
  }

  return (
    <div className="relative w-64">
      <Input
        value={value}
        placeholder={placeholder}
        onChange={(e) => {
          const v = e.target.value
          setValue(v)
          setOpen(v.trim().length > 0)
        }}
        onKeyDown={onKeyDown}
        onBlur={() => setTimeout(() => setOpen(false), 120)}
        className="h-8 text-sm"
      />
      {open && (suggestions.length > 0 || (typed && !exactExists)) && (
        <ul className="absolute z-20 mt-1 max-h-56 w-full overflow-auto rounded-md border bg-background shadow-md">
          {suggestions.map((s, i) => (
            <li key={s.id}>
              <button
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => commit(s.label)}
                className={cn(
                  "w-full px-3 py-1.5 text-left text-sm",
                  i === highlight ? "bg-accent" : "hover:bg-accent"
                )}
              >
                {s.label}
              </button>
            </li>
          ))}
          {typed && !exactExists && (
            <li>
              <button
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => commit(typed)}
                className="flex w-full items-center gap-1 px-3 py-1.5 text-left text-sm text-primary hover:bg-accent"
              >
                <Plus className="size-3" />„{typed}" hinzufügen
              </button>
            </li>
          )}
        </ul>
      )}
    </div>
  )
}
