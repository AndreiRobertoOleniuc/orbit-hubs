# Orbit — Design-Prompts zur HighFi-Anpassung (Feedback Norbert)

Diese Prompts sind so geschrieben, dass du sie **direkt in dein Design-Tool (Claude Design) einfügen**
kannst, um die bestehenden HighFi-Screens an die neue Spec anzupassen. Reihenfolge = sinnvolle
Bearbeitungsreihenfolge. Jeder Prompt nennt den betroffenen Screen (mit PDF-Seite zur Orientierung),
hält den bestehenden visuellen Stil bei und beschreibt nur die Änderung.

**Globaler Kontext-Prompt — einmal voranstellen:**

> Du passt die HighFi-Mockups der Plattform «Orbit» an (Innovations-Matchmaking, Region Baden/FHNW).
> Visueller Stil bleibt exakt wie bisher: helles Sand/Off-White-Background, violetter Primär-Akzent
> (#5B4FE0-artig), abgerundete Karten, Inter-Schrift, dezente graue Tags, viel Weissraum, DE als
> Default-Sprache mit DE/EN-Toggle oben rechts. Ändere nur, was im jeweiligen Prompt steht, und halte
> Abstände, Tag-Stil und Button-Stil konsistent zu den bestehenden Screens.

---

## Prompt 1 — Neue Einstiegsweiche (Demand/Supply)

> Erstelle einen neuen Start-Screen «Was möchtest du tun?» als erste Landing nach dem Logo-Header.
> Zwei grosse, gleich grosse Auswahl-Karten nebeneinander, im Stil der bestehenden «Wer bist du?»-Karten
> (Person/Organisation, PDF S. 8): links **«Ich suche jemanden»** mit Lupen-Icon und Untertitel
> «Beschreibe, was du brauchst — wir finden passende Profile.»; rechts **«Ich möchte gefunden werden»**
> mit Profil-/Stern-Icon und Untertitel «Bau dein Profil auf und sag, wofür du offen bist.». Darunter
> klein und grau: «Du kannst später jederzeit zwischen beiden wechseln.» Keine weiteren Elemente.

---

## Prompt 2 — Suche-Screen: Zeitfilter ergänzen

> Screen «Wen suchst du?» (PDF S. 32 / 35). Unter dem Freitextfeld, in derselben Zeile wie das
> bestehende Umkreis-Dropdown, ergänze einen optionalen Zeitfilter: zwei kompakte Datumsfelder
> «von» und «bis» mit Label «Zeitraum (optional)» und ein schmales Feld «Umfang ca. %». Halte sie
> visuell sekundär (kleiner, gleiche Höhe wie das Umkreis-Dropdown), damit das grosse Freitextfeld der
> Hauptfokus bleibt. Helptext darunter: «Leer lassen = keine Zeit-Einschränkung. Beeinflusst die
> Reihenfolge, filtert nicht hart.»

---

## Prompt 3 — Suchergebnisse: Verfügbarkeit als Pensum-% + Datumsbereich

> Treffer-Screen (PDF S. 33). Ersetze das grüne Verfügbarkeits-Badge oben rechts auf jeder Treffer-
> Karte. Statt «Verfügbar ab 15. Juni» zeige jetzt zweizeilig kompakt: erste Zeile **«60 %»** als
> Prozent-Pill, zweite Zeile klein «1. Aug – 31. Okt». Wenn kein Enddatum: «ab 1. Aug · 60 %». Behalte
> die grüne Akzentfarbe und die abgerundete Pill-Form bei. Lass den restlichen Karten-Aufbau (Name,
> Rolle, Begründungstext, Belege-Chips, Entfernung, «Kontakt aufnehmen») unverändert.

---

## Prompt 4 — Verfügbarkeits-Setting: Datumsbereich + Pensum-Slider

> **Kontext:** Es gibt einen Verfügbarkeits-Block namens «Offen für Anfragen». Er erscheint an zwei
> Stellen mit identischem Aufbau: (1) im Onboarding auf dem Schritt «Bist du offen für Anfragen?» und
> (2) auf dem eigenen Profil. Beide Stellen gleich ändern.
>
> **So sieht der Block heute aus:** eine Karte mit hell-violettem Hintergrund. Titelzeile «Offen für
> Anfragen» mit einem Toggle rechts (aktiv = violett, daneben grünes Label «● Aktiv»). Aufgeklappt
> enthält er: ein Feld «Ab wann» (einzelnes Datum), ein Freitextfeld «Pensum / Notiz» (z. B. «4h die
> Woche»), und darunter die Überschrift «Bevorzugte Zusammenarbeit» mit auswählbaren Chips
> (Bachelor-/Masterarbeit, Innosuisse-Projekt, Bezahltes Mandat, Forschungskollaboration).
>
> **Ändere den aufgeklappten Inhalt so:**
> - Ersetze das einzelne «Ab wann»-Datumsfeld durch **zwei Datumsfelder nebeneinander**: «Verfügbar
>   von» (Datum) und «bis (optional)» (Datum).
> - Ersetze das Freitextfeld «Pensum / Notiz» durch eine **Pensum-Eingabe in Prozent**: ein Zahlenfeld
>   mit «%»-Suffix (z. B. «60 %»), optional als Slider. **Daneben/darunter** ein kleineres, optionales
>   Freitextfeld «Notiz (optional)» für Nuancen (z. B. «ca. 4h/Woche, flexibel»).
> - Die «Bevorzugte Zusammenarbeit»-Chips bleiben **unverändert** darunter.
> - Hell-violetter Karten-Hintergrund, violetter aktiver Toggle und grünes «● Aktiv»-Label bleiben.
>
> **Platzierung / Sichtbarkeit (wichtig — Details in Prompt C):** Dieser Editor sitzt auf dem **eigenen
> Profil** und erscheint **nur im Supply-Modus («Ich biete»)**.
> - **Eigenes Profil, Supply-Modus:** voller Editor (oben beschrieben).
> - **Eigenes Profil, Demand-Modus («Ich suche»):** Das Profil zeigt nur eine minimale Identitäts-Karte
>   (Prompt C1) — der Verfügbarkeits-Editor erscheint hier **gar nicht**.
> - **Öffentliches Profil (was andere sehen):** nur der read-only Verfügbarkeits-**Badge**, kein Editor.
>
> Im **Onboarding** wird immer der volle Editor gezeigt (dort gibt es keinen Modus).

---

## Prompt 5 — Anfrage erstellen: Umfang/Zeitraum + Dringlichkeit

> Screen «Anfrage erstellen» (PDF S. 37–38). Füge zwei neue Blöcke zwischen «Beschreibung» und
> «Präsenzmodus» ein:
> (a) **Umfang / Zeitraum** — zwei Datumsfelder «von»/«bis (optional)» und ein Feld «Pensum ca. %».
> (b) **Dringlichkeit** — eine Segmented-Control mit drei Optionen «Flexibel | Bald | Dringend»
> (Default «Flexibel»), darunter ein optionales Datumsfeld «Antworten bis (optional)». Style identisch
> zur bestehenden Präsenzmodus-Segmented-Control. Der Rest des Formulars (Geltungsbereich-Map, Tags,
> «Anfrage veröffentlichen») bleibt unverändert.

---

## Prompt 6 — Anfrage-Board: Erklär-Header, Sortierung, Filter, Dringlichkeit-Badges

> Anfrage-Board (PDF S. 1–3). Vier Änderungen, Stil beibehalten:
> 1. **Erklär-Header** ganz oben über der Filterzeile: eine dezente Info-Box (heller violetter
>    Hintergrund wie die bestehenden Hinweis-Boxen) mit Titel «Offene Anfragen aus der Region» und
>    einem Satz: «Antworte direkt oder abonniere Themen per Tag. Angebote werden hier nicht
>    ausgeschrieben — dein Profil wird gefunden.»
> 2. **Sortierung sichtbar als Dropdown** statt nur «Neueste/Relevanteste»-Tabs: Optionen «Neueste,
>    Relevanteste, Dringlichkeit, Frist zuerst».
> 3. **Zusätzliche Filter** neben Umkreis: kleine Dropdowns «Typ (Person/Firma)», «Dringlichkeit»,
>    «Präsenzmodus».
> 4. **Dringlichkeit-Badge** auf den Karten: bei dringenden Anfragen ein rotes «Dringend»-Pill neben
>    dem Firma/Privatperson-Badge; bei «Bald» ein gelbes Pill; «Flexibel» zeigt nichts. Bei gesetzter
>    Frist zusätzlich klein grau «noch 3 Tage» bei der Zeitangabe.

---

## Prompt 7 — Anfrage-Detail: Dringlichkeit + Umfang anzeigen

> Anfrage-Detailansicht (PDF S. 4 und 6). In der Badge-Zeile oben (heute: Typ, Präsenzmodus, Scope,
> Status) ergänze ein **Dringlichkeit-Badge** (rot «Dringend» / gelb «Bald», nichts bei flexibel) und
> direkt unter der Beschreibung eine kleine Meta-Zeile «**Zeitraum:** 1. Aug – 31. Okt · ~60 %» sowie
> bei Frist «**Antworten bis:** 15. Juli». Stil wie die bestehenden grauen Tags. «Kontakt aufnehmen»-
> Button unverändert.

---

## Prompt 8 — Kontakt-Dialog: Absender-Kontext + Gating-Hinweis

> Kontakt-Dialog «Stell dich einmal vor» (PDF S. 5 / 34). Zwei Änderungen:
> 1. **Absender-Kontext oben im Dialog**, über dem grauen Zitat-Feld: eine kompakte Absender-Zeile mit
>    Avatar, «Du kontaktierst als **M. Keller · NovaTech GmbH**» und darunter klein «Der Empfänger
>    sieht deinen Namen, deine Firma und wonach du gesucht hast.»
> 2. **Gating-Hinweis (Variante anlegen):** ein zweiter Zustand des Dialogs, in dem das Grundprofil
>    unvollständig ist — statt des aktiven «Kontaktanfrage senden»-Buttons ein deaktivierter Button mit
>    Inline-Hinweis darüber: «Bitte ergänze deinen Namen, bestätige deine E-Mail und verlinke eine
>    Quelle (LinkedIn/Website) oder beschreibe dich kurz, bevor du Kontakt aufnimmst.» plus Link
>    «Profil vervollständigen». Hintergrund: Der Empfänger sieht eine Kurzbeschreibung des Anfragenden,
>    dafür braucht es mindestens eine Quelle.

---

> **Ab hier: vier grössere, in sich geschlossene Aufträge (A–D).** Sie ersetzen die früheren kurzen
> Einzel-Prompts 9–18, die sich teils überschnitten und zu wenig Kontext hatten. Jeder Auftrag A–D
> beschreibt Ausgangslage **und** Ziel vollständig und kommt ohne Verweise auf einen PDF oder andere
> Prompts aus — du kannst ihn einzeln an Claude Design geben.

---

## Prompt A — Person ↔ Organisation auf Profilen verknüpfen

> **Ausgangslage:** Es gibt zwei ähnlich aufgebaute Profiltypen. Ein **Personenprofil**: Kopf mit Name,
> darunter ein Untertitel (z. B. «Embedded · C/C++»), darunter Tags für Standort/Sprache, dann ein
> Verfügbarkeits-Block, dann ein Abschnitt «Kompetenzen» (Beschreibung + Skill-Tags), dann ein Abschnitt
> «Nachweise» (verlinkte Quellen). Ein **Organisationsprofil**: gleicher Aufbau, mit Firmenname,
> Branchen-Untertitel und einem grünen «HR-verifiziert»-Badge.
>
> **Ziel — mach die Zugehörigkeit *Person → Firma* überall sichtbar:**
> - **Personenprofil:** direkt unter Name/Untertitel ein **klickbarer Organisations-Chip** mit kleinem
>   Firmen-Icon, z. B. «🏢 **NovaTech GmbH** · Industrielle Sensorik». Klick führt aufs
>   Organisationsprofil. Hat die Person keine Firma, stattdessen der neutrale Chip «Privatperson» oder
>   «Selbständig» (nie ganz ohne Herkunftsangabe).
> - **Organisationsprofil:** ein neuer kleiner Abschnitt **«Personen»** mit den verknüpften Personen als
>   klickbare Chips (nur sofern öffentlich).
> - **Stil:** wie die bestehenden grauen Tag-Chips; der Organisations-Chip auf dem Personenprofil darf
>   leicht hervorgehoben sein (Icon + Firmenname fett).
>
> Diese Verknüpfung taucht später auch in Suchergebnissen, Anfrage-Karten und der Kontakt-Übergabe auf —
> hier geht es nur um die beiden Profilseiten.

---

## Prompt B — Onboarding-Formulare schärfen (Demand-Variante · Scopes · Praktikum)

> Drei zusammenhängende Änderungen an den Einstiegs- und Erstellen-Formularen. Sie betreffen dieselben
> Flows, daher in einem Auftrag.
>
> ### B1 — Verkürztes Demand-Onboarding
> **Ausgangslage:** Das volle Person-Onboarding hat die Schritte «Wer bist du» (Person/Organisation) →
> «Basics» (Name, E-Mail, Sprachen) → «Verlinke deine Quellen» (GitHub, ORCID, LinkedIn-PDF, Website +
> Freitext, mit Profil-Vorschau) → «Bist du offen für Anfragen?» (Verfügbarkeit) → Abschluss «Dein
> Profil ist live».
>
> **Ziel:** Lege daneben eine **kürzere Demand-Variante** an für Leute, die nur suchen wollen:
> «Wer bist du» → «Basics» → **eine reduzierte Quellen-Karte** → Abschluss. Die reduzierte Quellen-Karte
> verlangt **mindestens eine** von drei Angaben: LinkedIn-PDF, Website-URL **oder** ein manuelles
> Freitextfeld «Kurzbeschreibung (2–3 Sätze)». Erklärtext darüber: «Damit der Empfänger weiss, wer
> anfragt, brauchen wir eine kurze Beschreibung von dir.» Der grosse GitHub/ORCID-Block **und** der
> Verfügbarkeits-Schritt **entfallen** in dieser Variante; stattdessen am Ende ein dezenter Hinweis:
> «Willst du auch gefunden werden? Du kannst dein Profil später vervollständigen.» Abschluss-Screen:
> Überschrift «Du kannst jetzt suchen oder eine Anfrage ausschreiben» mit zwei Buttons «Suche starten»
> und «Anfrage ausschreiben». Das volle Onboarding bleibt die Supply-Variante; beide enden im selben
> Profil.
>
> ### B2 — Präsenzmodus und Umkreis klar trennen
> **Ausgangslage** (im «Anfrage erstellen»-Formular und im Such-Screen): ein **Präsenzmodus**-Umschalter
> «Vor Ort / Hybrid / Remote» und eine **Umkreis/Geltungsbereich**-Auswahl «FHNW / Baden +20 km / CH /
> Verbundene Kreise», teils auf einer Karten-Map dargestellt.
>
> **Ziel:** Mach deutlich, dass das **zwei getrennte Achsen** sind. Ergänze pro Umkreis-Option einen
> kurzen Tooltip-/Hilfesatz, der erklärt, wen er einschliesst (z. B. «FHNW: nur Profile mit FHNW-Bezug»).
> Wird als Präsenzmodus **«Remote»** gewählt, blende unter der Umkreis-Map einen dezenten Hinweis ein
> «Remote — Umkreis weniger relevant» und dimme die Map leicht. «CH» darf **nie** als Präsenzmodus-
> Option erscheinen (es ist nur ein Umkreis-Wert).
>
> ### B3 — Praktikum/Werkstudium als Zusammenarbeits-Art
> **Ausgangslage:** Im Verfügbarkeits-Block gibt es auswählbare Chips «Bevorzugte Zusammenarbeit»
> (Bachelor-/Masterarbeit, Innosuisse-Projekt, Bezahltes Mandat, Forschungskollaboration).
>
> **Ziel:** (a) Ergänze dort einen weiteren Chip **«Praktikum / Werkstudium»**. (b) Ergänze im
> «Anfrage erstellen»-Formular ein optionales Chip-Feld **«Art der Zusammenarbeit»** mit denselben
> Optionen inkl. «Praktikum / Werkstudium». Stil = bestehende auswählbare Chips (violett wenn aktiv).

---

## Prompt C — Grosse Seiten-Umstrukturierung: Profil nach Modus + zwei neue Seiten

> Das ist die zentrale Strukturänderung — bitte genau lesen, hier ist im Prototyp am meisten
> schiefgegangen. Das **eigene Profil** trägt aktuell viel zu viel und zeigt in **beiden** Modi
> dasselbe (Vervollständigen-Banner, Verfügbarkeit, abonnierte Themen, eigene Anfragen, passende
> Anfragen, Kontaktanfrage, Kompetenzen, Quellen, Nachweise — alles gestapelt). Das ist genau die
> Überladung, die wir vermeiden wollen. Die Lösung hat drei Säulen: **(1)** das Profil ist
> **modus-abhängig** — in «Ich suche» (Demand) nur eine minimale Identitäts-Karte, in «Ich biete»
> (Supply) das volle Profil; **(2)** eigene Anfragen und eingehende/passende Anfragen wandern auf
> **zwei eigene Seiten**; **(3)** alle Sektions-Boxen bekommen **einheitliches Spacing**.
>
> ### Grundprinzip: Profil ist modus-abhängig
> Die App hat oben rechts einen Modus-Schalter «Ich suche | Ich biete». Der **eigene Profil-Screen**
> (Privat-Ansicht) sieht je nach Modus völlig anders aus:
> - **«Ich suche» (Demand):** **nur eine minimale Identitäts-Karte** (siehe C1). KEINE Verfügbarkeit,
>   KEINE Quellen, KEINE Kompetenzen-Bearbeitung, KEIN Vervollständigen-Banner, KEINE Anfrage-Listen.
>   Wer sucht, braucht sein eigenes Angebots-Profil gerade nicht.
> - **«Ich biete» (Supply):** das **volle Profil** (siehe C2) — Identität + Verfügbarkeit +
>   Vervollständigen-Banner + Kompetenzen + Quellen + Nachweise.
>
> ### C1 — Demand-Modus: minimale Identitäts-Karte
> Im Modus «Ich suche» zeigt der Profil-Screen nur:
> - Name, Untertitel (Rolle), **Zugehörigkeit-Chip** (Firma/Selbständig/Privatperson, bearbeitbar,
>   aus Prompt A/A2), Standort- und Sprach-Tags.
> - Darunter **ein** dezenter Hinweis (eine Zeile, kein grosser Banner): «Du bist im Such-Modus. Um
>   gefunden zu werden, wechsle zu **‹Ich biete›** und baue dein Profil auf.» — «Ich biete» als Link.
> - Sonst **nichts**. Kein Verfügbarkeits-Block, keine Quellen, keine Anfrage-Listen.
>
> ### C2 — Supply-Modus: volles Profil (entschlackt + vervollständigbar)
> Im Modus «Ich biete» zeigt der Profil-Screen, von oben nach unten, genau diese Sektionen — und
> **nur** diese (keine Anfrage-Listen mehr, die sind auf eigenen Seiten):
> 1. **Kopf:** Name, Untertitel, Zugehörigkeit-Chip, Standort-/Sprach-Tags.
> 2. **Vervollständigen-Banner** — nur sichtbar, solange das Profil unvollständig ist (z. B. < 2
>    verlinkte Quellen oder keine Verfügbarkeit gesetzt): Titel «Mach dein Profil vollständig», ein
>    Satz «Verlinke weitere Quellen, damit du besser gefunden wirst — GitHub, ORCID, Lebenslauf oder
>    Website.», ein Fortschrittsbalken («Profil zu 40 % · 2 von 5») und Button «Profil vervollständigen»
>    (scrollt/führt zum Quellen-Abschnitt, Punkt 5). Wird das Profil vollständig, verschwindet der
>    Banner. **Dieser Banner erscheint nur im Supply-Modus, nie im Demand-Modus.**
> 3. **Verfügbarkeits-Block** (hell-violett): voller Editor «Offen für Anfragen» + Toggle, «Verfügbar
>    von / bis», Pensum %, Notiz, «Bevorzugte Zusammenarbeit»-Chips (Detail in Prompt 4). Ist noch nie
>    eine Verfügbarkeit gesetzt worden und der Toggle aus, zeige innerhalb derselben Box einen
>    Einrichtungs-Aufruf «Sag, wofür und wann du offen bist» + Button «Verfügbarkeit einrichten», der
>    den Editor aufklappt. Direkt darunter (in derselben Box oder als nächste Box, einheitlich) der
>    Abschnitt **«Abonnierte Themen»** mit Tag-Chips + «Bearbeiten».
> 4. **«Kompetenzen»:** Beschreibung + editierbare Skill-Tags + «Erstellt aus: …»-Zeile mit ↻-Button.
> 5. **«Deine Quellen»** (bearbeitbar): Liste GitHub / ORCID / LinkedIn (PDF) / Website, jede Zeile mit
>    «+ hinzufügen» bzw. grünem «✓ verknüpft» + «×». Bereits gesetzte Quellen erscheinen verknüpft. Neue
>    Quelle hinzufügen → Profil wird neu abgeleitet (Kompetenzen/Nachweise aktualisieren sich), kurzer
>    Hinweis «Profil aktualisiert». Darunter ausgegraut «Bald verfügbar» (Google Scholar, Referenzen).
> 6. **«Nachweise»:** Liste der verlinkten Belege (read-only).
>
> **Öffentliche Ansicht** (Reiter «Öffentlich», was andere sehen) bleibt wie bisher: Verfügbarkeits-
> **Badge** (read-only) + Beschreibung + Skill-Tags + Quellen-Belege + Button «Kontakt aufnehmen».
> Keine Editoren, keine Anfrage-Listen.
>
> ### C3 — Neue Seite «Meine Anfragen» (Demand)
> Eigene Seite (Top-Nav, Prompt D) mit Titel «Meine Anfragen». Hierher wandert der frühere Profil-Block
> «Meine Anfragen»: pro selbst ausgeschriebener Anfrage eine Karte mit Titel, Tags (Präsenzmodus,
> Umkreis, Dringlichkeit), **Status-Badge** (Offen / Verteilt / Kontaktiert / Geschlossen) und
> Kurzstatus-Zeile («6 Profile benachrichtigt · noch keine Kontaktanfrage»). Klick öffnet die bestehende
> 4-Stufen-Lebenszyklus-Detailansicht. Oben optional Filter nach Status.
>
> ### C4 — Neue Seite «Postfach» (Supply-Inbox)
> Eigene Seite (Top-Nav, Prompt D) mit Titel «Postfach» und Unterzeile «Anfragen, die zu dir passen,
> und Kontaktanfragen zu deinem Profil». Zwei Bereiche untereinander:
> - **Oben «Eingehende Kontaktanfragen»** — Karte je Kontaktanfrage, **mit Absender-Kontext**: oben
>   **Name + Rolle + Firma** (z. B. «A. Hürlimann · Selbständig, Baden»), darunter eine kleine graue
>   **Kurzbeschreibung** (1–2 Sätze, aus der Quelle des Absenders, z. B. «Software Engineer bei der ZKB,
>   Schwerpunkt Banking-Systeme»), dann «**Gesucht:** ‹…›» mit dem ursprünglichen Suchtext, dann die
>   persönliche Nachricht im Zitat-Stil, dann «Antworten».
> - **Darunter «Offene passende Anfragen»** — Karten fremder Anfragen mit Verteilungs-Begründung
>   («Verteilt, weil dein Profil die Tags … trägt») + «Antworten».
>
> ### C5 — Einheitliches Spacing für alle Sektions-Boxen (wichtig!)
> Im Prototyp haben die hell-violetten Boxen viel enger gepolstert als die weissen — das sieht
> uneinheitlich aus. Lege **eine** Spezifikation fest und wende sie auf **alle** Sektions-Boxen an
> (violett wie weiss, auf Profil, «Meine Anfragen» und «Postfach»):
> - **Innen-Padding:** überall identisch, **24 px** auf allen vier Seiten.
> - **Eck-Radius:** überall identisch, **16 px**.
> - **Vertikaler Abstand zwischen Boxen:** überall identisch, **16 px**.
> - **Box-Breite:** alle Sektionen gleich breit, gleiche linke/rechte Kante (eine gemeinsame
>   Inhalts-Spalte, zentriert).
> - Überschriften innerhalb der Boxen einheitlich (gleiche Grösse/gleicher Abstand zum ersten Element).
>
> Ergebnis: ein ruhiges, gleichmässiges Raster — egal ob violette Verfügbarkeits-Box oder weisse
> Karten-Box.

---

## Prompt D — Navigation und Default-Landing

> Zwei Routing-Änderungen, die zur neuen Struktur gehören.
>
> ### D1 — Top-Navigation erweitern
> **Ausgangslage:** Top-Nav mit «Suche · Anfrage-Board · Profil» (links neben Logo), rechts DE/EN-Toggle,
> E-Mail und «Abmelden». **Ziel:** Ergänze zwei eingeloggt-sichtbare Einträge, so dass die Nav lautet:
> **Suche · Anfrage-Board · Meine Anfragen · Postfach · Profil**. «Meine Anfragen» und «Postfach» sind
> nur im eingeloggten Zustand sichtbar. Aktiver Tab wie heute fett/markiert. Optional ein kleiner
> Zähler-Badge am «Postfach» bei neuen eingehenden Anfragen.
>
> ### D2 — Default-Landing auf die Einstiegsweiche
> **Ausgangslage:** Ausgeloggte Besucher landen heute faktisch auf der Suche / dem Anfrage-Board.
> **Ziel:** Erste Landing für neue/ausgeloggte Besucher ist die **Einstiegsweiche «Was möchtest du
> tun?»** (Suchen vs. gefunden werden, Prompt 1). Das Anfrage-Board bleibt über die Nav erreichbar, ist
> aber nicht mehr der Default-Screen. Eingeloggte Nutzer landen auf der zuletzt genutzten Seite.

---

## Nicht zu ändern (bewusst)

- Das Grundlayout, der Farbakzent und die Belege-/Nachweise-Logik bleiben.
- Das Profil zeigt keine persönlichen Anfrage-Listen mehr — nur Identität (+ Verfügbarkeit im Supply-
  Modus). Outgoing-Anfragen leben auf «Meine Anfragen», Inbound auf «Postfach».
- Es entsteht **kein** «Angebot ausschreiben»-Formular — Supply bleibt der Profil-Zustand.
- Die Konzept-only-Features (Empfehlungsketten, Verbundene Kreise mit Wirkung, intelligentes Routing)
  werden **nicht** als aktive Screens gebaut; höchstens als statischer «bald verfügbar»-Hinweis.
