import { useState } from "react"
import { Link } from "react-router"
import {
  BadgeCheck,
  Check,
  ExternalLink,
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
import { collaborationLabel } from "@/features/profile/availabilityUtils"
import { useIsOwnEntity } from "@/features/profile/useProfileMode"
import {
  initials,
  isHrVerified,
  externalUrl,
  zefixUid,
} from "@/features/profile/profileUtils"
import { useGetContactedEntityIdsQuery } from "@/lib/api"
import { cn } from "@/lib/utils"
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
  const websiteSource = entity.sources.find(
    (source) =>
      (source.sourceType === "personal_site" ||
        source.sourceType === "company_site") &&
      externalUrl(source) != null
  )
  const websiteUrl = websiteSource ? externalUrl(websiteSource) : null
  // In der Vorschau ("So sehen dich andere") zeigt der Kopf denselben Button
  // wie für alle anderen — er darf hier nur nicht funktionieren, sonst endet
  // ein Klick im 400 des Backends ("Cannot contact your own profile").
  const isOwnProfile = useIsOwnEntity(entity.id)
  const contactable = isAuthenticated && entity.claimed && !isOwnProfile
  const { data: contactedIds } = useGetContactedEntityIdsQuery(undefined, {
    skip: !isAuthenticated,
  })
  const alreadyContacted = contactedIds?.includes(entity.id) ?? false

  return (
    <header className="min-w-0">
      {/* Identitätszeile: das Bildelement steht nur neben Name, Rolle und
          Arbeitgeber. Alles Weitere beginnt darunter wieder am linken Rand —
          eingerückt unter dem Namen zerfiele der Kopf in zwei Spalten. */}
      <div className="flex min-w-0 flex-col items-start gap-4 sm:flex-row sm:gap-5">
        <div
          aria-hidden="true"
          className={cn(
            "flex size-16 shrink-0 items-center justify-center border bg-muted text-xl font-bold text-muted-foreground",
            isOrgEntity ? "rounded-xl" : "rounded-full"
          )}
        >
          {initials(entity.displayName)}
        </div>

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
                  : isOwnProfile
                    ? "Das ist dein eigenes Profil"
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
      </div>

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
            Profilinformationen bearbeiten
          </button>
        )}
      </div>
      <AvailabilityDetail entity={entity} />
      {(entity.locationLabel || languages.length > 0 || websiteUrl) && (
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
          {websiteUrl && (
            <a
              href={websiteUrl}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`Website von ${entity.displayName} öffnen`}
              className="inline-flex items-center gap-1 hover:text-foreground hover:underline"
            >
              <ExternalLink className="size-3.5" />
              Website
            </a>
          )}
        </div>
      )}
    </header>
  )
}

/**
 * The prose half of an availability statement: which kinds of collaboration are
 * on offer, plus the owner's own note. The badge next to it carries only the
 * window and Pensum, so without this line the note had nowhere to surface at
 * all — people wrote one in the editor and never saw it again.
 */
function AvailabilityDetail({ entity }: { entity: EntityDto }) {
  const availability = entity.availability
  if (!availability?.openToRequests) return null

  const collab = availability.preferredCollaboration ?? []
  const detail = [collab.map(collaborationLabel).join(", "), availability.note]
    .filter(Boolean)
    .join(" · ")
  if (!detail) return null

  return <p className="mt-2 text-sm text-muted-foreground">{detail}</p>
}
