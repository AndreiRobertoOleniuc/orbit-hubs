import { Check } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
  PlannedSourceCard,
  SourceCard,
  SourceGlyph,
} from "@/features/ingestion/SourceCard"
import {
  DEMAND_SOURCES,
  ORG_PLANNED,
  ORG_SOURCES,
  PERSON_PLANNED,
  PERSON_SOURCES,
} from "@/features/ingestion/sourceDefs"
import type { SourceDraft, SourceKind } from "@/features/ingestion/types"
import type { FirmDraft } from "@/features/onboarding/types"

interface SourcesStepProps {
  supply: boolean
  isOrg: boolean
  firm: FirmDraft | null
  sources: SourceDraft[]
  description: string
  onAddSource: (kind: SourceKind, value: string, file?: File) => void
  onRemoveSource: (kind: SourceKind) => void
  onDescriptionChange: (value: string) => void
  onFinish: () => void
}

function descriptionCopy(supply: boolean, isOrg: boolean) {
  if (isOrg) {
    return {
      label: "Kurzbeschreibung (2–3 Sätze)",
      placeholder:
        "z.B. Wir entwickeln und fertigen industrielle Sensorik für die Prozessüberwachung…",
    }
  }
  if (supply) {
    return {
      label: "In eigenen Worten (optional)",
      placeholder:
        "z.B. Ich entwickle Embedded-Firmware für industrielle Sensorik und leite kleine Hardware-Projekte…",
    }
  }
  return {
    label: "Kurzbeschreibung (2–3 Sätze)",
    placeholder:
      "z.B. Ich leite die Produktion bei einem KMU in Baden und suche Unterstützung bei der Automatisierung…",
  }
}

/** Wizard step: link sources (+ free-text description) — the ingestion inputs. */
export function SourcesStep({
  supply,
  isOrg,
  firm,
  sources,
  description,
  onAddSource,
  onRemoveSource,
  onDescriptionChange,
  onFinish,
}: SourcesStepProps) {
  const sourceDefs = !supply
    ? DEMAND_SOURCES
    : isOrg
      ? ORG_SOURCES
      : PERSON_SOURCES
  const plannedDefs = !supply ? [] : isOrg ? ORG_PLANNED : PERSON_PLANNED
  const copy = descriptionCopy(supply, isOrg)
  const canFinish =
    sources.length > 0 ||
    description.trim().length > 0 ||
    (isOrg && firm != null)

  return (
    <>
      {supply ? (
        <h1 className="mb-2 text-2xl font-bold tracking-tight">
          {isOrg
            ? "Verlinke deine Quellen — wir bauen euer Profil für euch."
            : "Verlinke deine Quellen — wir bauen dein Profil für dich."}
        </h1>
      ) : (
        <>
          <h1 className="mb-2 text-2xl font-bold tracking-tight">
            Kurz zu dir
          </h1>
          <p className="mb-4 text-sm leading-relaxed text-muted-foreground">
            Damit der Empfänger weiss, wer anfragt, brauchen wir eine kurze
            Beschreibung von dir. Gib mindestens eine Angabe an.
          </p>
        </>
      )}

      <div className="mt-4 space-y-2.5">
        {isOrg && firm && (
          <div className="flex items-center gap-3 rounded-lg border bg-muted/40 px-4 py-3">
            <SourceGlyph glyph="HR" />
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-semibold">
                Handelsregister
              </span>
              <span className="block truncate text-xs text-muted-foreground">
                {firm.name} · aus dem vorherigen Schritt übernommen
              </span>
            </span>
            <Badge variant="secondary">
              <Check className="size-3" /> verknüpft
            </Badge>
          </div>
        )}

        {sourceDefs.map((def) => (
          <SourceCard
            key={def.title}
            def={def}
            helper={def.helper}
            added={sources.find((s) => s.kind === def.kind)}
            onAdd={(value, file) => onAddSource(def.kind, value, file)}
            onRemove={() => onRemoveSource(def.kind)}
          />
        ))}

        <div className="space-y-2 rounded-lg border bg-card px-4 py-3.5">
          <Label htmlFor="onb-description">{copy.label}</Label>
          <Textarea
            id="onb-description"
            rows={3}
            placeholder={copy.placeholder}
            value={description}
            onChange={(e) => onDescriptionChange(e.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            {supply
              ? "Falls die Quellen nicht alles abdecken — beschreib kurz, woran du arbeitest."
              : "Reicht völlig — ein, zwei Sätze, wer du bist und was du suchst."}
          </p>
        </div>

        {plannedDefs.length > 0 && (
          <>
            <p className="pt-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
              Bald verfügbar
            </p>
            {plannedDefs.map((def) => (
              <PlannedSourceCard key={def.title} def={def} />
            ))}
          </>
        )}
      </div>

      <Button className="mt-6 w-full" disabled={!canFinish} onClick={onFinish}>
        {supply ? "Profil erstellen" : "Weiter"}
      </Button>
      {!canFinish && (
        <p className="mt-3 text-center text-sm text-muted-foreground">
          {supply
            ? "Mindestens eine Quelle verlinken — oder kurz in eigenen Worten beschreiben."
            : "LinkedIn-PDF, Website-URL oder eine kurze Beschreibung — eines genügt."}
        </p>
      )}
      {!supply && (
        <p className="mt-6 text-center text-sm text-muted-foreground">
          Willst du auch gefunden werden? Du kannst dein Profil später
          vervollständigen.
        </p>
      )}
    </>
  )
}
