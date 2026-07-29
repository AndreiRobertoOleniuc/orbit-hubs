import { Building2, User } from "lucide-react"

import type { EntityKind } from "@/features/onboarding/types"

interface TypeStepProps {
  userLabel?: string
  onSelect: (kind: EntityKind) => void
}

/**
 * Wizard step 1: as whom does this account appear on the platform — as a named
 * individual or as the organisation itself?
 *
 * Testers read the bare "Person / Organisation" pair as a question about who is
 * filling in the form, so employees of a firm picked "Organisation". It is
 * actually about who is on the receiving end of an Anfrage, and it decides how
 * the profile is built (own sources vs. Handelsregister) — and `entity_type`
 * has no update path, so a wrong pick means starting over. Hence the explainer
 * and the two "wähle das, wenn …" lines.
 */
export function TypeStep({ userLabel, onSelect }: TypeStepProps) {
  return (
    <>
      <h1 className="mb-3 text-center text-3xl font-bold tracking-tight">
        Als was möchtest du auf Orbit auftreten?
      </h1>
      <p className="mx-auto mb-8 max-w-xl text-center text-muted-foreground">
        Wähle, wer später auf Orbit sichtbar ist: du selbst oder eure
        Organisation. Ob du suchst oder gefunden werden willst, kommt danach.
      </p>

      <div className="grid gap-4 sm:grid-cols-2">
        <TypeCard
          icon={<User className="size-9 text-primary" />}
          title="Als Person"
          summary="Du bist mit deinem eigenen Namen sichtbar, egal ob selbständig, angestellt oder in der Forschung."
          bullets={[
            "Anfragen gehen direkt an dich",
            "Wir nutzen deine Quellen: GitHub, OpenAlex, LinkedIn-PDF, Website",
            "Firma oder Hochschule kannst du danach ergänzen",
          ]}
          onClick={() => onSelect("person")}
        />
        <TypeCard
          icon={<Building2 className="size-9 text-primary" />}
          title="Als Organisation"
          summary="Du trittst im Namen der ganzen Firma oder Institution auf, nicht als Einzelperson."
          bullets={[
            "Anfragen gehen an die Organisation",
            "Stammdaten kommen aus dem Handelsregister (Zefix)",
            "Gut, wenn das Angebot der Firma gehört und nicht dir persönlich",
          ]}
          onClick={() => onSelect("organization")}
        />
      </div>

      <p className="mt-6 text-center text-sm text-muted-foreground">
        Unsicher? Wähle <span className="font-medium">Person</span>. Das passt
        für die meisten Einzelpersonen, auch wenn sie zu einer Firma gehören.
        Diese Wahl bleibt danach fix.
      </p>

      {userLabel && (
        <p className="mt-4 text-center text-sm text-muted-foreground">
          Angemeldet als {userLabel}. Dieses Konto gehört später zum Profil.
        </p>
      )}
    </>
  )
}

interface TypeCardProps {
  icon: React.ReactNode
  title: string
  summary: string
  bullets: string[]
  onClick: () => void
}

/** One of the two choices — icon, one-line summary, then what it entails. */
function TypeCard({ icon, title, summary, bullets, onClick }: TypeCardProps) {
  return (
    <button
      type="button"
      className="flex flex-col items-start gap-3 rounded-xl border bg-card p-6 text-left transition-colors hover:border-primary/50 hover:bg-accent"
      onClick={onClick}
    >
      {icon}
      <span className="text-lg font-semibold">{title}</span>
      <span className="text-sm text-muted-foreground">{summary}</span>
      <ul className="space-y-1 text-sm text-muted-foreground">
        {bullets.map((bullet) => (
          <li key={bullet} className="flex gap-2">
            <span aria-hidden="true" className="text-primary">
              ·
            </span>
            {bullet}
          </li>
        ))}
      </ul>
    </button>
  )
}
