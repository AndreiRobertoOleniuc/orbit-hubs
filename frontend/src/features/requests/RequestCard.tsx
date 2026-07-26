import { useNavigate } from "react-router"
import { CalendarClock, MapPin } from "lucide-react"

import {
  AuthorKindBadge,
  UrgencyBadge,
} from "@/features/requests/RequestBadges"
import { RequestAuthor } from "@/features/requests/RequestAuthor"
import {
  daysUntil,
  relativeTime,
  scopeLabel,
  wantWindowLabel,
} from "@/features/requests/requestUtils"
import { cn } from "@/lib/utils"
import type { RequestSummaryDto } from "@/types/api"

/**
 * One open request on the board (design: BoardCard) — title + urgency/author
 * badges, the description, tag chips, and a footer with the author identity,
 * scope, age, and the deadline countdown.
 */
export function RequestCard({ request }: { request: RequestSummaryDto }) {
  const navigate = useNavigate()
  const remaining = daysUntil(request.deadline)
  const window = wantWindowLabel(
    request.wantFrom,
    request.wantTo,
    request.wantPensumPct
  )
  const title = request.title?.trim() || request.queryText
  const description =
    request.description?.trim() ||
    (request.title?.trim() ? request.queryText : null)

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => navigate(`/board/${request.id}`)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault()
          navigate(`/board/${request.id}`)
        }
      }}
      className="cursor-pointer rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10 transition-shadow outline-none hover:ring-foreground/20 focus-visible:ring-2 focus-visible:ring-ring sm:p-6"
    >
      <div className="flex flex-col items-start gap-2 sm:flex-row sm:justify-between sm:gap-4">
        <h3 className="min-w-0 font-semibold">{title}</h3>
        <div className="flex flex-wrap items-center gap-1.5 sm:shrink-0">
          <UrgencyBadge urgency={request.urgency} />
          <AuthorKindBadge type={request.authorType} />
        </div>
      </div>

      {description && (
        <p className="mt-2.5 line-clamp-3 text-[15px] leading-relaxed text-muted-foreground">
          {description}
        </p>
      )}

      {(request.tags.length > 0 || window) && (
        <div className="mt-3.5 flex flex-wrap items-center gap-1.5">
          {request.tags.map((t) => (
            <span
              key={t.id}
              className="inline-flex items-center rounded-full bg-primary/10 px-2.5 py-0.5 text-xs text-primary"
            >
              {t.label}
            </span>
          ))}
          {window && (
            <span className="inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs text-muted-foreground">
              <CalendarClock className="size-3" />
              {window}
            </span>
          )}
        </div>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-2 border-t pt-4 text-sm text-muted-foreground">
        <RequestAuthor
          entityId={request.authorEntityId}
          name={request.authorName}
          orgName={request.authorOrgName}
          type={request.authorType}
        />
        <span className="inline-flex items-center gap-1 sm:ml-auto">
          <MapPin className="size-3.5" />
          {scopeLabel(request)}
        </span>
        {request.createdAt && <span>{relativeTime(request.createdAt)}</span>}
        {remaining != null && remaining >= 0 && (
          <span
            className={cn(
              "font-medium",
              remaining <= 3
                ? "text-red-600 dark:text-red-400"
                : "text-amber-700 dark:text-amber-400"
            )}
          >
            {remaining === 0 ? "läuft heute ab" : `noch ${remaining} Tage`}
          </span>
        )}
        {remaining != null && remaining < 0 && (
          <span className="font-medium">Frist abgelaufen</span>
        )}
      </div>
    </div>
  )
}
