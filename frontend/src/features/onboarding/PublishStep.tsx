import { CheckCircle2, Loader2, Pencil, Rocket } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { AvailabilityFields } from "@/features/profile/AvailabilityFields"
import { AvailabilityPreview } from "@/features/profile/AvailabilityPreview"
import {
  toAvailabilityBody,
  toAvailabilityDraft,
  type AvailabilityDraft,
} from "@/features/profile/availabilityUtils"
import {
  usePublishEntityMutation,
  useUpdateAvailabilityMutation,
} from "@/lib/api"
import { cn } from "@/lib/utils"
import type { EntityDto } from "@/types/api"

type AvailabilityChoice = "open" | "closed" | null

export function PublishStep({
  entity,
  onEdit,
  onPublished,
}: {
  entity: EntityDto
  onEdit: (step: number) => void
  onPublished: (entity: EntityDto) => void
}) {
  const supply = entity.profileMode === "supply"
  const availability = entity.availability
  const [choice, setChoice] = useState<AvailabilityChoice>(
    availability?.openToRequests ? "open" : supply ? null : "closed"
  )
  const [draft, setDraft] = useState<AvailabilityDraft>(() =>
    toAvailabilityDraft(availability)
  )
  const [error, setError] = useState<string | null>(null)
  const [updateAvailability, { isLoading: availabilitySaving }] =
    useUpdateAvailabilityMutation()
  const [publish, { isLoading: publishing }] = usePublishEntityMutation()

  const patch = (p: Partial<AvailabilityDraft>) =>
    setDraft((d) => ({ ...d, ...p }))

  const body = toAvailabilityBody(choice === "open", draft)

  const submit = async () => {
    if (supply && choice == null) return
    setError(null)
    try {
      if (supply) {
        await updateAvailability({ entityId: entity.id, body }).unwrap()
      }
      onPublished(await publish(entity.id).unwrap())
    } catch {
      setError(
        "Das Profil konnte noch nicht veröffentlicht werden. Prüfe deine Angaben und versuch es erneut."
      )
    }
  }

  const activeEvidence = entity.sources.filter((source) => source.active).length

  return (
    <div>
      <div className="text-center">
        <Rocket className="mx-auto size-9 text-primary" />
        <h1 className="mt-3 text-2xl font-bold tracking-tight">
          {supply ? "Fast geschafft" : "Dein Absenderprofil ist bereit"}
        </h1>
        <p className="mx-auto mt-2 max-w-lg text-sm text-muted-foreground">
          {supply
            ? "Prüfe kurz die wichtigsten Angaben. Mit dem letzten Klick wird dein Profil öffentlich."
            : "Prüfe deine Basisdaten. Beschreibung und Kompetenzen entstehen automatisch aus deiner Grundlage."}
        </p>
      </div>

      {supply && (
        <section className="mt-6 rounded-xl border bg-card p-5">
          <h2 className="font-semibold">Bist du offen für Anfragen?</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Deine Verfügbarkeit hilft beim Sortieren der Treffer. Sie schliesst
            dich nicht aus. Du kannst sie später im Profil ändern.
          </p>
          <div className="mt-4 grid gap-2 sm:grid-cols-2">
            <ChoiceButton
              active={choice === "open"}
              title="Ja, ich bin offen"
              description="Meine Verfügbarkeit darf sichtbar sein."
              onClick={() => setChoice("open")}
            />
            <ChoiceButton
              active={choice === "closed"}
              title="Momentan nicht"
              description="Mein Profil bleibt auffindbar, ohne Verfügbarkeits-Badge."
              onClick={() => setChoice("closed")}
            />
          </div>

          {choice === "open" && (
            <div className="mt-5 space-y-5 border-t pt-5">
              <AvailabilityFields
                value={draft}
                onChange={patch}
                idPrefix="onboarding-avail"
              />
              <AvailabilityPreview availability={body} />
            </div>
          )}
        </section>
      )}

      <section className="mt-4 rounded-xl border bg-card p-5">
        <h2 className="font-semibold">Dein Profil im Überblick</h2>
        <div className="mt-4 divide-y">
          <ReviewRow
            label="Profildaten"
            value={`${entity.displayName}${entity.headline ? ` · ${entity.headline}` : ""}`}
            onEdit={() => onEdit(3)}
          />
          <ReviewRow
            label={supply ? "Nachweise" : "Grundlage"}
            value={`${activeEvidence} aktiv${entity.hubs.length > 0 ? ` · ${entity.hubs.length} Hub${entity.hubs.length > 1 ? "s" : ""}` : ""}`}
            onEdit={supply ? () => onEdit(2) : undefined}
          />
          {supply && (
            <ReviewRow
              label="Kompetenzen"
              value={`${entity.tags.length} Tags · Beschreibung geprüft`}
              onEdit={() => onEdit(4)}
            />
          )}
        </div>
      </section>

      {supply && choice == null && (
        <p className="mt-3 text-sm text-muted-foreground">
          Wähle noch aus, ob du aktuell offen für Anfragen bist.
        </p>
      )}
      {error && (
        <p className="mt-3 text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
      <Button
        className="mt-6 w-full"
        disabled={
          (supply && choice == null) || availabilitySaving || publishing
        }
        onClick={() => void submit()}
      >
        {(availabilitySaving || publishing) && (
          <Loader2 className="size-4 animate-spin" />
        )}
        {supply ? "Profil veröffentlichen" : "Onboarding abschliessen"}
      </Button>
    </div>
  )
}

function ChoiceButton({
  active,
  title,
  description,
  onClick,
}: {
  active: boolean
  title: string
  description: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      className={cn(
        "flex gap-3 rounded-lg border p-3 text-left transition-colors",
        active ? "border-primary bg-primary/5" : "hover:bg-muted/50"
      )}
      onClick={onClick}
    >
      <CheckCircle2
        className={cn(
          "mt-0.5 size-4 shrink-0",
          active ? "text-primary" : "text-muted-foreground/40"
        )}
      />
      <span>
        <span className="block text-sm font-semibold">{title}</span>
        <span className="mt-0.5 block text-xs leading-relaxed text-muted-foreground">
          {description}
        </span>
      </span>
    </button>
  )
}

function ReviewRow({
  label,
  value,
  onEdit,
}: {
  label: string
  value: string
  onEdit?: () => void
}) {
  return (
    <div className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
      <div className="min-w-0 flex-1">
        <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          {label}
        </p>
        <p className="mt-0.5 truncate text-sm">{value}</p>
      </div>
      {onEdit && (
        <Button variant="ghost" size="sm" onClick={onEdit}>
          <Pencil className="size-3.5" /> Ändern
        </Button>
      )}
    </div>
  )
}
