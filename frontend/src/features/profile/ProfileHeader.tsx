import { useState } from "react"
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
import type { AffiliationKind, EntityDto } from "@/types/api"

interface ProfileHeaderProps {
  entity: EntityDto
  /** Public view shows the contact call-to-action (handoff dialog, §11). */
  showContact?: boolean
  /** Own private view: show the Kopf-edit pencil (affiliation/languages/location). */
  editable?: boolean
  onEditMeta?: () => void
}

const AFFILIATION_LABELS: Record<AffiliationKind, string> = {
  company: "Firma",
  self_employed: "Selbständig",
  private: "Privatperson",
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
          {entity.affiliationKind && (
            <Badge variant="outline">
              {AFFILIATION_LABELS[entity.affiliationKind]}
            </Badge>
          )}
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
              aria-label="Zugehörigkeit, Sprachen und Standort bearbeiten"
              title="Kopf bearbeiten"
              className="inline-flex size-6 items-center justify-center rounded-md border text-muted-foreground hover:bg-muted"
            >
              <Pencil className="size-3" />
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
