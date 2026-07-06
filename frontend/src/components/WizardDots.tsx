import { cn } from "@/lib/utils"

/** Step indicator dots for the onboarding wizard. */
export function WizardDots({ total, current }: { total: number; current: number }) {
  return (
    <div
      className="flex items-center gap-2"
      aria-label={`Schritt ${current + 1} von ${total}`}
    >
      {Array.from({ length: total }, (_, i) => (
        <span
          key={i}
          className={cn(
            "size-2 rounded-full bg-border transition-colors",
            i < current && "bg-primary/40",
            i === current && "bg-primary"
          )}
        />
      ))}
    </div>
  )
}