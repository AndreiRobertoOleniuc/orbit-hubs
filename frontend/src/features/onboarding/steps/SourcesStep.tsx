import { Check } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { SourceGlyph } from "@/components/SourceGlyph"
import { PlannedSourceCard } from "@/features/ingestion/PlannedSourceCard"
import { draftHref } from "@/features/ingestion/requests"
import { SourceCard } from "@/features/ingestion/SourceCard"
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
  // An organization's profile is built from Zefix + company website in BOTH
  // modes, so org sources don't depend on supply/demand — only persons do.
  const sourceDefs = isOrg
    ? ORG_SOURCES
    : supply
      ? PERSON_SOURCES
      : DEMAND_SOURCES
  const plannedDefs = isOrg ? ORG_PLANNED : supply ? PERSON_PLANNED : []
  const copy = descriptionCopy(supply, isOrg)
  const hasDemandPersonBasis =
    sources.some(
      (source) => source.kind === "cv" || source.kind === "linkedin"
    ) || description.trim().length > 0
  const canFinish = supply
    ? sources.length > 0 ||
      description.trim().length > 0 ||
      (isOrg && firm != null)
    : isOrg
      ? firm != null ||
        sources.some((source) => source.kind === "website") ||
        description.trim().length > 0
      : hasDemandPersonBasis

  return (
    <>
      {supply ? (
        <h1 className="mb-2 text-2xl font-bold tracking-tight">
          {isOrg ? "Verlinkt eure Quellen" : "Verlinke deine Quellen"}
        </h1>
      ) : (
        <>
          <h1 className="mb-2 text-2xl font-bold tracking-tight">
            {isOrg ? "Kurz zu eurer Firma" : "Kurz zu dir"}
          </h1>
          <p className="mb-4 text-sm leading-relaxed text-muted-foreground">
            {isOrg
              ? "Eine kurze Beschreibung oder Website reicht, damit andere wissen, wer anfragt."
              : "Eine kurze Beschreibung, ein Lebenslauf oder LinkedIn reicht, damit andere wissen, wer anfragt."}
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
                {firm.name} · aus dem letzten Schritt übernommen
              </span>
            </span>
            <Badge variant="secondary">
              <Check className="size-3" /> verknüpft
            </Badge>
          </div>
        )}

        {sourceDefs.map((def) => {
          const draft = sources.find((s) => s.kind === def.kind)
          return (
            <SourceCard
              key={def.title}
              def={def}
              added={draft}
              href={draft && draftHref(draft)}
              onAdd={(value, file) => onAddSource(def.kind, value, file)}
              onRemove={() => onRemoveSource(def.kind)}
            />
          )
        })}

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
              ? isOrg
                ? "Wenn auf der Website etwas fehlt, ergänzt es hier kurz."
                : "Wenn deine Quellen etwas nicht zeigen, ergänze es hier kurz."
              : isOrg
                ? "Ein bis zwei Sätze reichen: wer ihr seid und was ihr sucht."
                : "Ein bis zwei Sätze reichen: wer du bist und was du suchst."}
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
        Quellen einlesen
      </Button>
      {!canFinish && (
        <p className="mt-3 text-center text-sm text-muted-foreground">
          {supply
            ? "Verlinke mindestens eine Quelle oder beschreibe dich kurz."
            : isOrg
              ? "Firmenwebsite oder Kurzbeschreibung, eines davon genügt."
              : "Lebenslauf, LinkedIn-PDF oder Kurzbeschreibung, eines davon genügt. Eine Website ist freiwillig."}
        </p>
      )}
      {!supply && (
        <p className="mt-6 text-center text-sm text-muted-foreground">
          {isOrg
            ? "Wollt ihr später auch gefunden werden? Das Profil könnt ihr danach ausbauen."
            : "Willst du später auch gefunden werden? Dein Profil kannst du danach ausbauen."}
        </p>
      )}
    </>
  )
}
