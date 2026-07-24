import { CheckCircle2, Loader2, Pencil, Rocket } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { COLLABORATION_TYPES } from "@/features/profile/availabilityUtils"
import {
  usePublishEntityMutation,
  useUpdateAvailabilityMutation,
} from "@/lib/api"
import { cn, todayIso } from "@/lib/utils"
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
  const [from, setFrom] = useState(availability?.availableFrom ?? "")
  const [to, setTo] = useState(availability?.availableTo ?? "")
  const [pensum, setPensum] = useState(availability?.pensumPct ?? 100)
  const [note, setNote] = useState(availability?.note ?? "")
  const [collaboration, setCollaboration] = useState<string[]>(
    availability?.preferredCollaboration ?? []
  )
  const [error, setError] = useState<string | null>(null)
  const [updateAvailability, { isLoading: availabilitySaving }] =
    useUpdateAvailabilityMutation()
  const [publish, { isLoading: publishing }] = usePublishEntityMutation()

  const toggleCollaboration = (value: string) =>
    setCollaboration((current) =>
      current.includes(value)
        ? current.filter((item) => item !== value)
        : [...current, value]
    )

  const submit = async () => {
    if (supply && choice == null) return
    setError(null)
    try {
      if (supply) {
        await updateAvailability({
          entityId: entity.id,
          body: {
            openToRequests: choice === "open",
            availableFrom: choice === "open" && from ? from : null,
            availableTo: choice === "open" && to ? to : null,
            pensumPct: choice === "open" ? pensum : null,
            note: choice === "open" && note.trim() ? note.trim() : null,
            preferredCollaboration: choice === "open" ? collaboration : [],
          },
        }).unwrap()
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
          Fast geschafft
        </h1>
        <p className="mx-auto mt-2 max-w-lg text-sm text-muted-foreground">
          Prüfe die wichtigsten Angaben. Erst mit dem letzten Klick wird dein
          Profil öffentlich und in Orbit auffindbar.
        </p>
      </div>

      {supply && (
        <section className="mt-6 rounded-xl border bg-card p-5">
          <h2 className="font-semibold">Bist du offen für Anfragen?</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Diese Auswahl kannst du später jederzeit im Profil ändern.
          </p>
          <div className="mt-4 grid gap-2 sm:grid-cols-2">
            <ChoiceButton
              active={choice === "open"}
              title="Ja, ich bin offen"
              description="Orbit darf meine Verfügbarkeit hervorheben."
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
            <div className="mt-5 space-y-4 border-t pt-5">
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="onboarding-available-from">
                    Verfügbar ab
                  </Label>
                  <Input
                    id="onboarding-available-from"
                    type="date"
                    min={todayIso()}
                    max={to || undefined}
                    value={from}
                    onChange={(event) => setFrom(event.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="onboarding-available-to">Verfügbar bis</Label>
                  <Input
                    id="onboarding-available-to"
                    type="date"
                    min={from || todayIso()}
                    value={to}
                    onChange={(event) => setTo(event.target.value)}
                  />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="onboarding-pensum">Pensum: {pensum} %</Label>
                <input
                  id="onboarding-pensum"
                  type="range"
                  min={0}
                  max={100}
                  step={10}
                  value={pensum}
                  className="w-full accent-primary"
                  onChange={(event) => setPensum(Number(event.target.value))}
                />
              </div>
              <div className="space-y-2">
                <Label>Art der Zusammenarbeit</Label>
                <div className="flex flex-wrap gap-2">
                  {COLLABORATION_TYPES.map((type) => (
                    <button
                      key={type}
                      type="button"
                      aria-pressed={collaboration.includes(type)}
                      className={cn(
                        "rounded-full border px-3 py-1 text-sm transition-colors",
                        collaboration.includes(type)
                          ? "border-primary bg-primary/10 text-primary"
                          : "text-muted-foreground hover:bg-muted"
                      )}
                      onClick={() => toggleCollaboration(type)}
                    >
                      {type}
                    </button>
                  ))}
                </div>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="onboarding-availability-note">
                  Notiz (optional)
                </Label>
                <Input
                  id="onboarding-availability-note"
                  placeholder="z.B. remote bevorzugt"
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                />
              </div>
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
            label="Nachweise"
            value={`${activeEvidence} aktiv${entity.hubs.length > 0 ? ` · ${entity.hubs.length} Hub${entity.hubs.length > 1 ? "s" : ""}` : ""}`}
            onEdit={() => onEdit(2)}
          />
          <ReviewRow
            label="Kompetenzen"
            value={`${entity.tags.length} Tags · Beschreibung geprüft`}
            onEdit={() => onEdit(4)}
          />
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
        Profil veröffentlichen
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
  onEdit: () => void
}) {
  return (
    <div className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
      <div className="min-w-0 flex-1">
        <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          {label}
        </p>
        <p className="mt-0.5 truncate text-sm">{value}</p>
      </div>
      <Button variant="ghost" size="sm" onClick={onEdit}>
        <Pencil className="size-3.5" /> Ändern
      </Button>
    </div>
  )
}
