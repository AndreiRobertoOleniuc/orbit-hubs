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
          Deine Auswahl bestimmt, wie viel wir jetzt für dein Profil brauchen.
          Du kannst den Modus später jederzeit wechseln.
        </p>
      </div>
      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        <ModeChoice
          icon={<Search className="size-8 text-primary" />}
          title="Ich suche"
          description="Ein kurzes Absenderprofil genügt, damit du suchen, anfragen und Kontakt aufnehmen kannst."
          onClick={() => onSelect("demand")}
        />
        <ModeChoice
          icon={<Sparkles className="size-8 text-primary" />}
          title="Gefunden werden"
          description="Erstelle ein vollständiges Kompetenzprofil und lege deine Verfügbarkeit fest."
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
