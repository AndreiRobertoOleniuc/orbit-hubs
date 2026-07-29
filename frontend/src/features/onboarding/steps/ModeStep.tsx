import { Search, Sparkles } from "lucide-react"

import type { OnboardingMode } from "@/features/onboarding/types"

export function ModeStep({
  onSelect,
}: {
  onSelect: (mode: OnboardingMode) => void
}) {
  return (
    <div>
      <div className="text-center">
        <h1 className="text-3xl font-bold tracking-tight">
          Was möchtest du mit Orbit tun?
        </h1>
        <p className="mx-auto mt-3 max-w-lg text-sm text-muted-foreground">
          Fürs Suchen reicht ein kurzes Profil. Wenn du gefunden werden willst,
          fragen wir gleich etwas mehr ab. Den Modus kannst du später wechseln.
        </p>
      </div>
      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        <ModeChoice
          icon={<Search className="size-8 text-primary" />}
          title="Ich suche"
          description="Du brauchst nur ein kurzes Absenderprofil und kannst danach suchen, anfragen und Kontakt aufnehmen."
          onClick={() => onSelect("demand")}
        />
        <ModeChoice
          icon={<Sparkles className="size-8 text-primary" />}
          title="Gefunden werden"
          description="Baue ein Profil aus deinen Quellen auf und zeig, wann du für Anfragen offen bist."
          onClick={() => onSelect("supply")}
        />
      </div>
    </div>
  )
}

function ModeChoice({
  icon,
  title,
  description,
  onClick,
}: {
  icon: React.ReactNode
  title: string
  description: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      className="flex flex-col items-start gap-3 rounded-xl border bg-card p-6 text-left transition-colors hover:border-primary/50 hover:bg-accent"
      onClick={onClick}
    >
      {icon}
      <span className="text-lg font-semibold">{title}</span>
      <span className="text-sm leading-relaxed text-muted-foreground">
        {description}
      </span>
    </button>
  )
}
