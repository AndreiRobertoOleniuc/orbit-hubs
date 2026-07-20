import { Link } from "react-router-dom"
import { BadgeCheck, Mail, PenLine, Search, Sparkles } from "lucide-react"

import { Button } from "@/components/ui/button"
import { GLOSSARY } from "@/lib/glossary"

const STEPS = [
  {
    icon: PenLine,
    title: "1 · Beschreibe dein Anliegen",
    body: "Beschreibe in eigenen Worten, welche Kompetenz du brauchst.",
  },
  {
    icon: BadgeCheck,
    title: "2 · Wir finden belegte Profile",
    body: "Orbit gleicht dein Anliegen mit belegten Kompetenzen ab und erklärt, warum ein Profil passt.",
  },
  {
    icon: Mail,
    title: "3 · Kontaktiere direkt",
    body: "Sende eine kurze Kontaktanfrage. Nach der Zusage tauscht ihr euch direkt aus.",
  },
]

const GLOSSARY_ITEMS: { term: string; text: string }[] = [
  { term: "Anfrage", text: GLOSSARY.anfrage },
  { term: "Postfach", text: GLOSSARY.postfach },
  { term: "Hub", text: GLOSSARY.hub },
  { term: "Nachweise", text: GLOSSARY.nachweise },
]

/** Persistent "So funktioniert Orbit" explainer, reachable any time from the footer. */
export function HowItWorksPage() {
  return (
    <div className="mx-auto max-w-2xl py-10">
      <h1 className="text-3xl font-bold tracking-tight">
        So funktioniert Orbit
      </h1>
      <p className="mt-3 text-[15px] leading-relaxed text-muted-foreground">
        Mit Orbit suchst du nach passenden Personen und Organisationen oder
        lässt dein eigenes Profil für passende Anfragen finden. Du kannst
        jederzeit zwischen beiden Ansichten wechseln.
      </p>

      <div className="mt-8 space-y-3">
        {STEPS.map((step) => (
          <div
            key={step.title}
            className="flex items-start gap-3 rounded-xl border bg-card p-5"
          >
            <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <step.icon className="size-4" />
            </span>
            <div>
              <h3 className="text-sm font-semibold">{step.title}</h3>
              <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                {step.body}
              </p>
            </div>
          </div>
        ))}
      </div>

      <details className="mt-10 rounded-xl border bg-card p-5">
        <summary className="cursor-pointer text-sm font-semibold">
          Wichtige Begriffe erklären
        </summary>
        <dl className="mt-5 space-y-4">
          {GLOSSARY_ITEMS.map((item) => (
            <div key={item.term} className="border-l-2 border-primary/30 pl-4">
              <dt className="text-sm font-semibold">{item.term}</dt>
              <dd className="mt-0.5 text-sm leading-relaxed text-muted-foreground">
                {item.text}
              </dd>
            </div>
          ))}
        </dl>
      </details>

      <div className="mt-10 flex flex-wrap gap-3">
        <Button asChild>
          <Link to="/search">
            <Search className="size-4" /> Jemanden suchen
          </Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/onboarding?mode=supply">
            <Sparkles className="size-4" /> Profil erstellen
          </Link>
        </Button>
      </div>
    </div>
  )
}
