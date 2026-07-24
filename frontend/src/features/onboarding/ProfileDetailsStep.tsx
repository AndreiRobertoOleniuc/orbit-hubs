import { Building2, Loader2, MapPin, UserRound, X } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { LocationInput } from "@/features/profile/LocationInput"
import { ZefixOrgInput } from "@/features/profile/ZefixOrgInput"
import {
  useGetHubsQuery,
  useUpdateEntityHubsMutation,
  useUpdateProfileMetaMutation,
} from "@/lib/api"
import { cn } from "@/lib/utils"
import type { AffiliationKind, EntityDto } from "@/types/api"

const COMMON_LANGUAGES = ["DE", "EN", "FR", "IT"]

const AFFILIATIONS: Array<{
  value: AffiliationKind
  label: string
  description: string
}> = [
  {
    value: "company",
    label: "Angestellt",
    description: "Ich arbeite für eine Organisation.",
  },
  {
    value: "self_employed",
    label: "Selbständig",
    description: "Ich arbeite unter eigenem Namen.",
  },
  {
    value: "private",
    label: "Privatperson",
    description: "Mein Profil ist keiner Firma zugeordnet.",
  },
]

export function ProfileDetailsStep({
  entity,
  onContinue,
}: {
  entity: EntityDto
  onContinue: (entity: EntityDto) => void
}) {
  const person = entity.entityType === "person"
  const [displayName, setDisplayName] = useState(entity.displayName)
  const [headline, setHeadline] = useState(entity.headline ?? "")
  const [affiliation, setAffiliation] = useState<AffiliationKind | null>(
    entity.affiliationKind
  )
  const [orgName, setOrgName] = useState(entity.orgName ?? "")
  const [languages, setLanguages] = useState<string[]>(entity.languages ?? [])
  const [newLanguage, setNewLanguage] = useState("")
  const [location, setLocation] = useState(entity.locationLabel ?? "")
  const [selectedHubs, setSelectedHubs] = useState<number[]>(
    entity.hubs.map((hub) => hub.id)
  )
  const [error, setError] = useState<string | null>(null)

  const { data: hubs, isLoading: hubsLoading } = useGetHubsQuery()
  const [updateMeta, { isLoading: metaSaving }] = useUpdateProfileMetaMutation()
  const [updateHubs, { isLoading: hubsSaving }] = useUpdateEntityHubsMutation()

  const companyRequired = person && affiliation === "company"
  const valid =
    displayName.trim().length > 0 &&
    headline.trim().length > 0 &&
    (!person || affiliation != null) &&
    (!companyRequired || orgName.trim().length > 0)

  const toggleLanguage = (language: string) =>
    setLanguages((current) =>
      current.includes(language)
        ? current.filter((value) => value !== language)
        : [...current, language]
    )

  const addLanguage = () => {
    const language = newLanguage.trim().toUpperCase()
    if (language && !languages.includes(language)) {
      setLanguages((current) => [...current, language])
    }
    setNewLanguage("")
  }

  const toggleHub = (hubId: number) =>
    setSelectedHubs((current) =>
      current.includes(hubId)
        ? current.filter((id) => id !== hubId)
        : [...current, hubId]
    )

  const save = async () => {
    if (!valid) return
    setError(null)
    try {
      await updateMeta({
        entityId: entity.id,
        body: {
          displayName: displayName.trim(),
          headline: headline.trim() || null,
          orgName:
            person && affiliation !== "company" ? null : orgName.trim() || null,
          affiliationKind: person ? affiliation : entity.affiliationKind,
          languages,
          locationLabel: location.trim() || null,
        },
      }).unwrap()
      const updated = await updateHubs({
        entityId: entity.id,
        hubIds: selectedHubs,
      }).unwrap()
      onContinue(updated)
    } catch {
      setError(
        "Die Profildaten konnten nicht vollständig gespeichert werden. Bitte versuch es erneut."
      )
    }
  }

  return (
    <div>
      <div className="text-center">
        {person ? (
          <UserRound className="mx-auto size-9 text-primary" />
        ) : (
          <Building2 className="mx-auto size-9 text-primary" />
        )}
        <h1 className="mt-3 text-2xl font-bold tracking-tight">
          Vervollständige deine Profildaten
        </h1>
        <p className="mx-auto mt-2 max-w-lg text-sm text-muted-foreground">
          Diese Angaben helfen anderen, dich einzuordnen. Hubs, Sprachen und
          Standort kannst du auch später ergänzen.
        </p>
      </div>

      <div className="mt-6 space-y-5 rounded-xl border bg-card p-5">
        <div className="space-y-1.5">
          <Label htmlFor="onboarding-name" required>
            {person ? "Name" : "Organisationsname"}
          </Label>
          <Input
            id="onboarding-name"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="onboarding-role" required>
            {person ? "Rolle / Funktion" : "Kurzbezeichnung"}
          </Label>
          <Input
            id="onboarding-role"
            placeholder={
              person
                ? "z.B. Professorin für Embedded Systems"
                : "z.B. Innovationsberatung für KMU"
            }
            value={headline}
            onChange={(event) => setHeadline(event.target.value)}
          />
        </div>

        {person && (
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">
              Berufliche Zugehörigkeit{" "}
              <span className="text-destructive">*</span>
            </legend>
            <div className="grid gap-2 sm:grid-cols-3">
              {AFFILIATIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  aria-pressed={affiliation === option.value}
                  className={cn(
                    "rounded-lg border p-3 text-left transition-colors",
                    affiliation === option.value
                      ? "border-primary bg-primary/5"
                      : "hover:bg-muted/50"
                  )}
                  onClick={() => setAffiliation(option.value)}
                >
                  <span className="block text-sm font-semibold">
                    {option.label}
                  </span>
                  <span className="mt-1 block text-xs leading-relaxed text-muted-foreground">
                    {option.description}
                  </span>
                </button>
              ))}
            </div>
          </fieldset>
        )}

        {companyRequired && (
          <div className="space-y-1.5">
            <Label htmlFor="onboarding-company" required>
              Arbeitgeber / Organisation
            </Label>
            <ZefixOrgInput
              id="onboarding-company"
              value={orgName}
              onChange={setOrgName}
            />
          </div>
        )}

        <div className="space-y-2">
          <Label>Sprachen</Label>
          <div className="flex flex-wrap items-center gap-2">
            {COMMON_LANGUAGES.map((language) => (
              <button
                key={language}
                type="button"
                aria-pressed={languages.includes(language)}
                className={cn(
                  "rounded-full border px-3 py-1 text-sm transition-colors",
                  languages.includes(language)
                    ? "border-primary bg-primary/10 text-primary"
                    : "text-muted-foreground hover:bg-muted"
                )}
                onClick={() => toggleLanguage(language)}
              >
                {language}
              </button>
            ))}
            {languages
              .filter((language) => !COMMON_LANGUAGES.includes(language))
              .map((language) => (
                <span
                  key={language}
                  className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
                >
                  {language}
                  <button
                    type="button"
                    aria-label={`${language} entfernen`}
                    onClick={() => toggleLanguage(language)}
                  >
                    <X className="size-3" />
                  </button>
                </span>
              ))}
            <Input
              value={newLanguage}
              placeholder="+ Sprache"
              className="h-8 w-28"
              onChange={(event) => setNewLanguage(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault()
                  addLanguage()
                }
              }}
            />
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="onboarding-location">Standort</Label>
          <LocationInput
            id="onboarding-location"
            value={location}
            onChange={setLocation}
          />
        </div>

        <div className="border-t pt-5">
          <div className="flex items-start gap-2">
            <MapPin className="mt-0.5 size-4 shrink-0 text-primary" />
            <div>
              <p className="text-sm font-medium">Hubs</p>
              <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                Wähle freiwillig die Netzwerke, denen du tatsächlich angehörst.
              </p>
            </div>
          </div>
          {hubsLoading ? (
            <Loader2 className="mx-auto mt-4 size-5 animate-spin text-muted-foreground" />
          ) : hubs && hubs.length > 0 ? (
            <div className="mt-3 flex flex-wrap gap-2">
              {hubs.map((hub) => (
                <button
                  key={hub.id}
                  type="button"
                  aria-pressed={selectedHubs.includes(hub.id)}
                  className={cn(
                    "rounded-full border px-3 py-1 text-sm transition-colors",
                    selectedHubs.includes(hub.id)
                      ? "border-primary bg-primary/10 text-primary"
                      : "text-muted-foreground hover:bg-muted"
                  )}
                  onClick={() => toggleHub(hub.id)}
                >
                  {hub.name}
                  {hub.regionName ? ` · ${hub.regionName}` : ""}
                </button>
              ))}
            </div>
          ) : (
            <p className="mt-3 text-sm text-muted-foreground">
              Aktuell sind keine Hubs verfügbar.
            </p>
          )}
        </div>
      </div>

      {!valid && (
        <p className="mt-3 text-sm text-muted-foreground">
          Fülle alle mit * markierten Angaben aus.
        </p>
      )}
      {error && (
        <p className="mt-3 text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
      <Button
        className="mt-6 w-full"
        disabled={!valid || metaSaving || hubsSaving}
        onClick={() => void save()}
      >
        {(metaSaving || hubsSaving) && (
          <Loader2 className="size-4 animate-spin" />
        )}
        Profildaten speichern und weiter
      </Button>
    </div>
  )
}
