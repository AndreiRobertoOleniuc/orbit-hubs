import { Check } from "lucide-react"

import { cn } from "@/lib/utils"

export function OnboardingProgress({
  current,
  steps,
}: {
  current: number
  steps: string[]
}) {
  return (
    <ol
      className="flex min-w-max items-center gap-1"
      aria-label={`Schritt ${current + 1} von ${steps.length}: ${steps[current]}`}
    >
      {steps.map((label, index) => (
        <li key={label} className="flex items-center gap-1">
          {index > 0 && (
            <span
              aria-hidden="true"
              className={cn(
                "h-px w-3 bg-border sm:w-5",
                index <= current && "bg-primary/50"
              )}
            />
          )}
          <span className="flex items-center gap-1.5">
            <span
              className={cn(
                "flex size-6 items-center justify-center rounded-full border text-xs font-semibold",
                index < current &&
                  "border-primary/30 bg-primary/10 text-primary",
                index === current &&
                  "border-primary bg-primary text-primary-foreground",
                index > current && "text-muted-foreground"
              )}
            >
              {index < current ? <Check className="size-3.5" /> : index + 1}
            </span>
            <span
              className={cn(
                "hidden text-xs sm:inline",
                index === current
                  ? "font-semibold text-foreground"
                  : "text-muted-foreground"
              )}
            >
              {label}
            </span>
          </span>
        </li>
      ))}
    </ol>
  )
}
