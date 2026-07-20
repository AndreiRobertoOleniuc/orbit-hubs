/**
 * Canonical, one-sentence definitions for Orbit's coined terms — so every inline
 * gloss (via <InfoHint term="…" />) reads identically across the app. Lifecycle
 * status wording lives in features/requests/requestUtils.ts (STATUS_HINT) and is
 * not duplicated here.
 */
export const GLOSSARY = {
  hub: "Ein Hub ist eine Zugehörigkeit — eine Institution, ein Inkubator oder eine Szene (z. B. FHNW, Technopark). Kein geografischer Umkreis, sondern wo du dazugehörst. Er steuert, in welchem Kreis dein Profil zuerst gefunden wird.",
  region:
    "Eine Region fasst mehrere Hubs geografisch zusammen (z. B. Aargau). Findest du in deinem Hub zu wenig, kannst du auf die Region und die ganze Schweiz erweitern.",
  postfach:
    "Dein Postfach sammelt Kontaktanfragen an dich und von dir sowie Anfragen, die zu deinem Profil passen. Der erste Kontakt läuft hier, alles Weitere per E-Mail.",
  anfrage:
    "Eine Anfrage ist eine öffentliche Ausschreibung: Du beschreibst, wen oder was du suchst — passende Profile werden benachrichtigt und können sich bei dir melden.",
  pensum:
    "Pensum = Arbeitspensum in Prozent (100 % = Vollzeit). Beeinflusst die Reihenfolge der Treffer, filtert aber nicht hart.",
  nachweise:
    "Nachweise sind die zitierten Belege hinter deinem Profil — die konkreten Quellen, aus denen Orbit deine Kompetenzen ableitet.",
  kompetenzen:
    "Kompetenzen fassen zusammen, worin du stark bist — als Kurzprofil und Tags, automatisch aus deinen Quellen abgeleitet und manuell ergänzbar.",
  verfuegbarkeit:
    "«Offen für Anfragen» zeigt anderen, dass du für Zusammenarbeit offen bist — mit optionalem Zeitraum, Pensum und Art der Zusammenarbeit. Erscheint als Badge in Suchergebnissen.",
} as const

export type GlossaryTerm = keyof typeof GLOSSARY
