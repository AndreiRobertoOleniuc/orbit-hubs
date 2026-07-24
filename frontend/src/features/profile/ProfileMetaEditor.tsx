import { useState } from "react"
import { X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { LocationInput } from "@/features/profile/LocationInput"
import { ZefixOrgInput } from "@/features/profile/ZefixOrgInput"
import { useUpdateProfileMetaMutation } from "@/lib/api"
import { cn, sameSet } from "@/lib/utils"
import type { AffiliationKind, EntityDto } from "@/types/api"

const COMMON_LANGUAGES = ["DE", "EN", "FR", "IT"]

/**
 * Edits the profile Kopf metadata: title/role, employer, languages, and
 * location — via the existing PUT /profile-meta. Shown only in the own private
 * view; opened from the header pencil.
 */
export function ProfileMetaEditor({
  entity,
  onClose,
}: {
  entity: EntityDto
  onClose: () => void
}) {
  const [displayName, setDisplayName] = useState(entity.displayName)
  const [headline, setHeadline] = useState(entity.headline ?? "")
  const [orgName, setOrgName] = useState(entity.orgName ?? "")
  const [affiliationKind, setAffiliationKind] =
    useState<AffiliationKind | null>(entity.affiliationKind)
  const [languages, setLanguages] = useState<string[]>(entity.languages ?? [])
  const [location, setLocation] = useState(entity.locationLabel ?? "")
  const [newLang, setNewLang] = useState("")
  const [updateProfileMeta, { isLoading }] = useUpdateProfileMetaMutation()

  const toggleLang = (lang: string) =>
    setLanguages((prev) =>
      prev.includes(lang) ? prev.filter((l) => l !== lang) : [...prev, lang]
    )
  const addLang = () => {
    const v = newLang.trim().toUpperCase()
    if (v && !languages.includes(v)) setLanguages([...languages, v])
    setNewLang("")
  }

  const dirty =
    displayName !== entity.displayName ||
    headline !== (entity.headline ?? "") ||
    orgName !== (entity.orgName ?? "") ||
    affiliationKind !== entity.affiliationKind ||
    location !== (entity.locationLabel ?? "") ||
    !sameSet(languages, entity.languages ?? [])
  const valid =
    displayName.trim().length > 0 &&
    headline.trim().length > 0 &&
    (entity.entityType === "organization" ||
      (affiliationKind != null &&
        (affiliationKind !== "company" || orgName.trim().length > 0)))

  const save = async () => {
    await updateProfileMeta({
      entityId: entity.id,
      body: {
        displayName: displayName.trim(),
        headline: headline.trim() || null,
        orgName:
          entity.entityType === "person" && affiliationKind !== "company"
            ? null
            : orgName.trim() || null,
        affiliationKind,
        languages,
        locationLabel: location.trim() || null,
      },
    }).unwrap()
    onClose()
  }

  return (
    <div className="space-y-5 rounded-lg border bg-muted/30 p-3 sm:p-4">
      <div className="space-y-1.5">
        <Label htmlFor="meta-display-name" required>
          {entity.entityType === "organization" ? "Organisationsname" : "Name"}
        </Label>
        <Input
          id="meta-display-name"
          value={displayName}
          autoComplete="name"
          placeholder={
            entity.entityType === "organization"
              ? "Name der Organisation"
              : "Vor- und Nachname"
          }
          onChange={(event) => setDisplayName(event.target.value)}
        />
      </div>

      {/* Titel / Rolle */}
      <div className="space-y-1.5">
        <Label htmlFor="meta-headline" required>
          Titel / Rolle
        </Label>
        <Input
          id="meta-headline"
          placeholder="z.B. Professor für Embedded Systems"
          value={headline}
          onChange={(e) => setHeadline(e.target.value)}
        />
      </div>

      {entity.entityType === "person" && (
        <div className="space-y-2">
          <Label>Berufliche Zugehörigkeit</Label>
          <div className="flex flex-wrap gap-2">
            {(
              [
                ["company", "Angestellt"],
                ["self_employed", "Selbständig"],
                ["private", "Privatperson"],
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                aria-pressed={affiliationKind === value}
                className={cn(
                  "rounded-full border px-3 py-1 text-sm transition-colors",
                  affiliationKind === value
                    ? "border-primary bg-primary/10 text-primary"
                    : "text-muted-foreground hover:bg-muted"
                )}
                onClick={() => setAffiliationKind(value)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      )}

      {(entity.entityType === "organization" ||
        affiliationKind === "company") && (
        <div className="space-y-1.5">
          <Label htmlFor="meta-org">Arbeitgeber / Organisation</Label>
          <ZefixOrgInput id="meta-org" value={orgName} onChange={setOrgName} />
        </div>
      )}

      {/* Sprachen */}
      <div className="space-y-1.5">
        <Label>Sprachen</Label>
        <div className="flex flex-wrap items-center gap-2">
          {COMMON_LANGUAGES.map((lang) => (
            <button
              key={lang}
              type="button"
              onClick={() => toggleLang(lang)}
              className={cn(
                "rounded-full border px-3 py-1 text-sm transition-colors",
                languages.includes(lang)
                  ? "border-primary bg-primary/10 text-primary"
                  : "border-input text-muted-foreground hover:bg-muted"
              )}
            >
              {lang}
            </button>
          ))}
          {languages
            .filter((l) => !COMMON_LANGUAGES.includes(l))
            .map((lang) => (
              <span
                key={lang}
                className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
              >
                {lang}
                <button
                  onClick={() => toggleLang(lang)}
                  className="text-primary/60 hover:text-primary"
                  aria-label={`Entfernen: ${lang}`}
                >
                  <X className="size-3" />
                </button>
              </span>
            ))}
          <Input
            placeholder="+ Sprache"
            value={newLang}
            onChange={(e) => setNewLang(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault()
                addLang()
              }
            }}
            className="h-8 w-28 text-sm"
          />
        </div>
      </div>

      {/* Standort */}
      <div className="space-y-1.5">
        <Label htmlFor="meta-location">Standort suchen</Label>
        <LocationInput
          id="meta-location"
          value={location}
          onChange={setLocation}
        />
        <p className="text-xs text-muted-foreground">
          Suche nach echten Schweizer Orten, Postleitzahlen und Adressen.
        </p>
      </div>

      <div className="grid gap-2 sm:flex sm:justify-end">
        <Button
          className="w-full sm:w-auto"
          size="sm"
          variant="ghost"
          onClick={onClose}
        >
          Abbrechen
        </Button>
        <Button
          className="w-full sm:w-auto"
          size="sm"
          onClick={() => void save()}
          disabled={!dirty || !valid || isLoading}
        >
          Speichern
        </Button>
      </div>
    </div>
  )
}
