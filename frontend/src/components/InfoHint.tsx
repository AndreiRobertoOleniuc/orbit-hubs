import { Info } from "lucide-react"

import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { GLOSSARY, type GlossaryTerm } from "@/lib/glossary"
import { cn } from "@/lib/utils"

interface InfoHintProps {
  /** A known glossary term (uses its canonical definition)… */
  term?: GlossaryTerm
  /** …or arbitrary explanation text. One of `term`/`text` is required. */
  text?: string
  /** Accessible label for the trigger — defaults to a generic "Erklärung". */
  label?: string
  className?: string
}

/**
 * A small ⓘ trigger that glosses a coined term inline. Keyboard- and
 * touch-accessible (unlike a bare `title=`), reused everywhere a term first
 * appears. Pass a glossary `term` for canonical copy, or free `text`.
 */
export function InfoHint({ term, text, label, className }: InfoHintProps) {
  const content = text ?? (term ? GLOSSARY[term] : "")
  if (!content) return null

  return (
    <Tooltip>
      <TooltipTrigger
        type="button"
        aria-label={label ?? "Erklärung anzeigen"}
        className={cn(
          "inline-flex size-4 shrink-0 cursor-help items-center justify-center rounded-full text-muted-foreground transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none",
          className
        )}
      >
        <Info className="size-3.5" />
      </TooltipTrigger>
      <TooltipContent>{content}</TooltipContent>
    </Tooltip>
  )
}
