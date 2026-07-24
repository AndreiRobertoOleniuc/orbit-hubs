import { BadgeCheck, Mail, PenLine, Search, Sparkles } from "lucide-react"

import { StartCard } from "@/components/StartCard"

const PATHS = [
  {
    title: "Expertise suchen",
    body: "Finde passende Personen oder Organisationen.",
    steps: [
      {
        icon: PenLine,
        title: "Anliegen beschreiben",
        body: "Formuliere frei, welche Kompetenz du brauchst.",
      },
      {
        icon: BadgeCheck,
        title: "Belegte Treffer prüfen",
        body: "Vergleiche Begründungen und öffentliche Quellen.",
      },
      {
        icon: Mail,
        title: "Kontakt anfragen",
        body: "Nach der Annahme schreibt ihr per E-Mail weiter.",
      },
    ],
  },
  {
    title: "Expertise anbieten",
    body: "Baue ein belegtes Profil auf und werde gefunden.",
    steps: [
      {
        icon: Sparkles,
        title: "Quellen verbinden",
        body: "Verbinde die Quellen, aus denen dein Profil entsteht.",
      },
      {
        icon: BadgeCheck,
        title: "Profil prüfen",
        body: "Kontrolliere Inhalte, Quellen und Verfügbarkeit.",
      },
      {
        icon: Search,
        title: "Gefunden werden",
        body: "Passende Suchende und Anfragen erreichen dein Profil.",
      },
    ],
  },
]

/**
 * Public entry: the landing / "Was möchtest du tun?" start screen. Leads with a
 * value proposition + how-it-works so a first-time visitor understands what
 * Orbit is before choosing a side. It remains available after sign-in so the
 * logo always has a predictable global destination.
 */
export function StartPage() {
  return (
    <div className="mx-auto max-w-3xl py-12 sm:py-16">
      {/* Hero */}
      <div className="animate-in text-center duration-500 fade-in slide-in-from-bottom-3 motion-reduce:animate-none">
        <p className="text-xs font-semibold tracking-[0.18em] text-muted-foreground uppercase">
          Regionales Innovations-Matchmaking
        </p>
        <h1 className="mx-auto mt-4 max-w-2xl text-4xl font-bold tracking-tight text-balance sm:text-5xl">
          Finde die passende Kompetenz in deiner Region.
        </h1>
        <p className="mx-auto mt-5 max-w-xl text-[15px] leading-relaxed text-muted-foreground sm:text-base">
          Beschreibe dein Anliegen. Orbit findet passende Personen und
          Organisationen rund um Baden, Brugg und die FHNW und zeigt dir, welche
          Quellen den Treffer belegen.
        </p>
      </div>

      {/* Fork */}
      <div
        className="mt-10 animate-in duration-500 fade-in slide-in-from-bottom-3 motion-reduce:animate-none"
        style={{ animationDelay: "120ms" }}
      >
        <h2 className="text-center text-lg font-semibold">
          Wie möchtest du starten?
        </h2>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <StartCard
            mode="demand"
            to="/search"
            gated={false}
            icon={<Search className="size-8 text-primary" />}
            title="Ich suche jemanden"
            subtitle="Suche direkt nach passenden Personen oder Organisationen. Dafür brauchst du noch kein Konto."
          />
          <StartCard
            mode="supply"
            icon={<Sparkles className="size-8 text-primary" />}
            title="Ich möchte gefunden werden"
            subtitle="Erstelle ein Profil und werde für passende Anfragen gefunden."
          />
        </div>
        <p className="mt-6 text-center text-sm text-muted-foreground">
          Du kannst später jederzeit zwischen beiden wechseln.
        </p>
      </div>

      {/* How it works */}
      <section className="mt-14 border-t pt-10" aria-labelledby="how-heading">
        <h2 id="how-heading" className="text-center text-lg font-semibold">
          So funktioniert Orbit
        </h2>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          {PATHS.map((path) => (
            <div key={path.title} className="rounded-xl border bg-card p-5">
              <h3 className="font-semibold">{path.title}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{path.body}</p>
              <ol className="mt-5 space-y-4">
                {path.steps.map((step, i) => (
                  <li key={step.title} className="flex gap-3">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                      <step.icon className="size-4" />
                    </span>
                    <div className="min-w-0">
                      <p className="text-sm font-semibold">
                        <span className="mr-1.5 text-muted-foreground tabular-nums">
                          {i + 1}.
                        </span>
                        {step.title}
                      </p>
                      <p className="mt-0.5 text-sm leading-relaxed text-muted-foreground">
                        {step.body}
                      </p>
                    </div>
                  </li>
                ))}
              </ol>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
