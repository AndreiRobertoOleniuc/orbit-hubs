import { Check } from "lucide-react"

import { Progress } from "@/components/ui/progress"
import { completenessCriteria } from "@/features/profile/availabilityUtils"
import type { EntityDto } from "@/types/api"

/**
 * Client-computed profile completeness — "Profil zu X % · N von 5", with a
 * checklist of the missing supply signals. Fully derived from the entity read
 * model; no backend endpoint. Hidden once the profile is complete.
 */
export function ProfileCompletenessBanner({ entity }: { entity: EntityDto }) {
  const criteria = completenessCriteria(entity)
  const done = criteria.filter((c) => c.done).length
  const total = criteria.length
  const pct = Math.round((done / total) * 100)

  if (done === total) return null

  return (
    <div className="rounded-xl border bg-muted/40 p-4">
      <div className="flex flex-col items-start gap-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
        <p className="text-sm font-medium">
          Profil zu {pct} % · {done} von {total}
        </p>
      </div>
      <Progress value={pct} className="mt-2 h-2" />
      <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5">
        {criteria.map((c) => (
          <li
            key={c.label}
            className={
              c.done
                ? "inline-flex items-center gap-1 text-xs text-muted-foreground"
                : "inline-flex items-center gap-1 text-xs font-medium text-foreground"
            }
          >
            <span
              className={
                c.done
                  ? "flex size-4 items-center justify-center rounded-full bg-green-600/20 text-green-700 dark:text-green-400"
                  : "flex size-4 items-center justify-center rounded-full border border-dashed border-muted-foreground/50"
              }
            >
              {c.done && <Check className="size-2.5" />}
            </span>
            {c.label}
          </li>
        ))}
      </ul>
    </div>
  )
}
