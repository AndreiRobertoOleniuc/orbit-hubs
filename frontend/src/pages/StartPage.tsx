import { Navigate } from "react-router-dom"
import {
  BadgeCheck,
  Loader2,
  Mail,
  PenLine,
  Search,
  Sparkles,
} from "lucide-react"

import { StartCard } from "@/components/StartCard"
import { useAuth } from "@/features/auth/useAuth"
import { useGetMyEntitiesQuery } from "@/lib/api"

const STEPS = [
  {
    icon: PenLine,
    title: "Beschreibe dein Anliegen",
    body: "Beschreibe in eigenen Worten, welche Kompetenz du brauchst.",
  },
  {
    icon: BadgeCheck,
    title: "Wir finden belegte Profile",
    body: "Orbit zeigt passende Profile und nennt die Belege dafür.",
  },
  {
    icon: Mail,
    title: "Kontaktiere direkt",
    body: "Sende eine Kontaktanfrage. Nach der Zusage tauscht ihr euch direkt aus.",
  },
]

/**
 * Public entry: the landing / "Was möchtest du tun?" start screen. Leads with a
 * value proposition + how-it-works so a first-time visitor understands what
 * Orbit is before choosing a side. Signed-in users who already onboarded are
 * routed to their profile — onboarding is a one-time flow.
 */
export function StartPage() {
  const { isAuthenticated, status } = useAuth()
  const { data: myEntities, isLoading } = useGetMyEntitiesQuery(undefined, {
    skip: !isAuthenticated,
  })

  if (status === "loading" || (isAuthenticated && isLoading)) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (isAuthenticated && myEntities && myEntities.length > 0) {
    return <Navigate to="/profile" replace />
  }

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
        <div className="mt-5 grid gap-4 sm:grid-cols-3">
          {STEPS.map((step, i) => (
            <div key={step.title} className="rounded-xl border bg-card p-5">
              <div className="flex items-center gap-2.5">
                <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <step.icon className="size-4" />
                </span>
                <span className="text-xs font-semibold text-muted-foreground tabular-nums">
                  Schritt {i + 1}
                </span>
              </div>
              <h3 className="mt-3 text-sm font-semibold">{step.title}</h3>
              <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                {step.body}
              </p>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
