import { BadgeCheck } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  initials,
  isHrVerified,
  zefixUid,
} from "@/features/profile/profileUtils"
import type { EntityDto } from "@/types/api"

interface ProfileHeaderProps {
  entity: EntityDto
  /** Public view shows the (not yet functional) contact call-to-action. */
  showContact?: boolean
}

export function ProfileHeader({ entity, showContact }: ProfileHeaderProps) {
  const isOrgEntity = entity.entityType === "organization"
  const uid = zefixUid(entity)

  return (
    <header className="flex flex-wrap items-start gap-5">
      {isOrgEntity && (
        <div
          aria-hidden="true"
          className="flex size-16 items-center justify-center rounded-xl border bg-muted text-xl font-bold text-muted-foreground"
        >
          {initials(entity.displayName)}
        </div>
      )}
      <div className="min-w-56 flex-1">
        <h2 className="text-2xl font-bold tracking-tight">
          {entity.displayName}
        </h2>
        {entity.headline && (
          <p className="mt-0.5 text-[15px] text-muted-foreground">
            {entity.headline}
          </p>
        )}
        {!isOrgEntity && entity.orgName && (
          <p className="mt-2 text-sm text-muted-foreground">{entity.orgName}</p>
        )}
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Badge variant="secondary">
            {isOrgEntity ? "Organisation" : "Person"}
          </Badge>
          {isHrVerified(entity) && (
            <a
              href="https://www.zefix.ch"
              target="_blank"
              rel="noopener noreferrer"
              title={
                uid
                  ? `${uid} — Handelsregister (Zefix)`
                  : "Handelsregister (Zefix)"
              }
            >
              <Badge className="bg-green-600/15 text-green-700 hover:bg-green-600/25 dark:text-green-400">
                <BadgeCheck className="size-3" /> HR-verifiziert
              </Badge>
            </a>
          )}
        </div>
      </div>
      {showContact && (
        <Button disabled title="Bald verfügbar">
          Kontakt aufnehmen
        </Button>
      )}
    </header>
  )
}
