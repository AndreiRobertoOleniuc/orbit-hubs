/**
 * Canonical, one-sentence definitions for Orbit's coined terms — so every inline
 * gloss (via <InfoHint term="…" />) reads identically across the app. Lifecycle
 * status wording lives in features/requests/requestUtils.ts (STATUS_HINT) and is
 * not duplicated here.
 */
export const GLOSSARY = {
  hub: "Ein Hub ist eine Zugehörigkeit: eine Institution, ein Inkubator oder eine Szene, zum Beispiel FHNW oder Technopark. Es geht nicht um Distanz, sondern darum, wo du dazugehörst. Dort wird dein Profil zuerst gesucht.",
  region:
    "Eine Region fasst mehrere Hubs zusammen, zum Beispiel Aargau. Wenn dein Hub zu eng ist, kannst du auf die Region oder die ganze Schweiz erweitern.",
  postfach:
    "Dein Postfach sammelt Kontaktanfragen an dich und von dir sowie Anfragen, die zu deinem Profil passen. Der erste Kontakt läuft hier, alles Weitere per E-Mail.",
  anfrage:
    "Eine Anfrage ist eine öffentliche Ausschreibung. Du beschreibst, wen oder was du suchst. Passende Profile bekommen eine Meldung und können sich bei dir melden.",
  pensum:
    "Pensum = Arbeitspensum in Prozent (100 % = Vollzeit). Beeinflusst die Reihenfolge der Treffer, filtert aber nicht hart.",
  nachweise:
    "Nachweise sind die zitierten Belege hinter deinem Profil: die Quellen, aus denen Orbit deine Kompetenzen ableitet.",
  kompetenzen:
    "Kompetenzen zeigen, worin du stark bist: als Kurzprofil und Tags. Orbit leitet sie aus deinen Quellen ab; du kannst sie danach bearbeiten.",
  verfuegbarkeit:
    "«Offen für Anfragen» zeigt anderen, dass du für Zusammenarbeit offen bist. Zeitraum, Pensum und Art der Zusammenarbeit kannst du ergänzen.",
} as const

export type GlossaryTerm = keyof typeof GLOSSARY
