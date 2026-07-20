import { useState } from "react"
import { Link } from "react-router-dom"
import {
  BadgeCheck,
  Check,
  Languages,
  Lock,
  MapPin,
  Pencil,
} from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/features/auth/useAuth"
import { ContactModal } from "@/features/contact/ContactModal"
import { AvailabilityBadge } from "@/features/profile/AvailabilityBadge"
import {
  initials,
  isHrVerified,
  zefixUid,
} from "@/features/profile/profileUtils"
import { useGetContactedEntityIdsQuery } from "@/lib/api"
import type { EntityDto } from "@/types/api"

interface ProfileHeaderProps {
  entity: EntityDto
  /** Public view shows the contact call-to-action (handoff dialog, §11). */
  showContact?: boolean
  /** Own private view: show the Kopf-edit pencil (affiliation/languages/location). */
  editable?: boolean
  onEditMeta?: () => void
}

export function ProfileHeader({
  entity,
  showContact,
  editable,
  onEditMeta,
}: ProfileHeaderProps) {
  const { isAuthenticated } = useAuth()
  const [contactOpen, setContactOpen] = useState(false)
  const isOrgEntity = entity.entityType === "organization"
  const uid = zefixUid(entity)
  const languages = entity.languages ?? []
  const contactable = isAuthenticated && entity.claimed
  const { data: contactedIds } = useGetContactedEntityIdsQuery(undefined, {
    skip: !isAuthenticated,
  })
  const alreadyContacted = contactedIds?.includes(entity.id) ?? false

  return (
    <header className="flex min-w-0 flex-col items-start gap-4 sm:flex-row sm:flex-wrap sm:gap-5">
      {isOrgEntity && (
        <div
          aria-hidden="true"
          className="flex size-16 items-center justify-center rounded-xl border bg-muted text-xl font-bold text-muted-foreground"
        >
          {initials(entity.displayName)}
        </div>
      )}
      <div className="w-full min-w-0 flex-1 sm:w-auto">
        <h2 className="text-2xl font-bold tracking-tight break-words">
          {entity.displayName}
        </h2>
        {entity.headline && (
          <p className="mt-0.5 text-[15px] text-muted-foreground">
            {entity.headline}
          </p>
        )}
        {!isOrgEntity && (entity.orgLink || entity.orgName) && (
          <p className="mt-2 text-sm text-muted-foreground">
            {entity.orgLink ? (
              <Link
                to={`/profile/${entity.orgLink.entityId}`}
                className="inline-flex items-center gap-1 hover:text-foreground hover:underline"
                title="Zum Organisationsprofil"
              >
                {entity.orgLink.name}
                {entity.orgLink.hrVerified && (
                  <BadgeCheck className="size-3.5 text-green-600 dark:text-green-400" />
                )}
              </Link>
            ) : (
              entity.orgName
            )}
          </p>
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
          <AvailabilityBadge availability={entity.availability} />
          {editable && (
            <button
              type="button"
              onClick={onEditMeta}
              aria-label="Profilinformationen bearbeiten"
              className="inline-flex h-7 items-center gap-1 rounded-md px-2 text-xs font-medium text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none"
            >
              <Pencil className="size-3" />
              Bearbeiten
            </button>
          )}
        </div>
        {(entity.locationLabel || languages.length > 0) && (
          <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
            {entity.locationLabel && (
              <span className="inline-flex items-center gap-1">
                <MapPin className="size-3.5" />
                {entity.locationLabel}
              </span>
            )}
            {languages.length > 0 && (
              <span className="inline-flex items-center gap-1">
                <Languages className="size-3.5" />
                {languages.join(", ")}
              </span>
            )}
          </div>
        )}
      </div>
      {showContact && (
        <>
          <Button
            className="w-full sm:w-auto"
            variant={alreadyContacted ? "outline" : "default"}
            disabled={!contactable}
            title={
              contactable
                ? undefined
                : !isAuthenticated
                  ? "Anmeldung nötig"
                  : "Profil nicht beansprucht — Kontakt nicht möglich"
            }
            onClick={() => setContactOpen(true)}
          >
            {!isAuthenticated && <Lock className="size-3.5" />}
            {alreadyContacted && <Check className="size-4" />}
            {alreadyContacted ? "Erneut kontaktieren" : "Kontakt aufnehmen"}
          </Button>
          {contactable && (
            <ContactModal
              toEntityId={entity.id}
              toName={entity.displayName}
              open={contactOpen}
              onOpenChange={setContactOpen}
            />
          )}
        </>
      )}
    </header>
  )
}
