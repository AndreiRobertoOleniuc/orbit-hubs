import { useState } from "react"
import { X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ZefixOrgInput } from "@/features/profile/ZefixOrgInput"
import { useUpdateProfileMetaMutation } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { EntityDto } from "@/types/api"

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
  const [headline, setHeadline] = useState(entity.headline ?? "")
  const [orgName, setOrgName] = useState(entity.orgName ?? "")
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

  const save = async () => {
    await updateProfileMeta({
      entityId: entity.id,
      body: {
        headline: headline.trim() || null,
        orgName: orgName.trim() || null,
        // Zugehörigkeit was dropped from the UI; preserve any existing value.
        affiliationKind: entity.affiliationKind,
        languages,
        locationLabel: location.trim() || null,
      },
    }).unwrap()
    onClose()
  }

  return (
    <div className="space-y-5 rounded-lg border bg-muted/30 p-4">
      {/* Titel / Rolle */}
      <div className="space-y-1.5">
        <Label htmlFor="meta-headline">Titel / Rolle</Label>
        <Input
          id="meta-headline"
          placeholder="z.B. Professor für Embedded Systems"
          value={headline}
          onChange={(e) => setHeadline(e.target.value)}
        />
      </div>

      {/* Arbeitgeber / Organisation — with Handelsregister (Zefix) search */}
      <div className="space-y-1.5">
        <Label htmlFor="meta-org">Arbeitgeber / Organisation</Label>
        <ZefixOrgInput id="meta-org" value={orgName} onChange={setOrgName} />
      </div>

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
        <Label htmlFor="meta-location">Standort</Label>
        <Input
          id="meta-location"
          placeholder="z.B. Baden, AG"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
        />
      </div>

      <div className="flex justify-end gap-2">
        <Button size="sm" variant="ghost" onClick={onClose}>
          Abbrechen
        </Button>
        <Button size="sm" onClick={() => void save()} disabled={isLoading}>
          Speichern
        </Button>
      </div>
    </div>
  )
}
