# Orbit — Spezifikation v2

> **Version 2** — konsolidiert die ursprüngliche Spec (`Orbit_Spec.md`), das Meeting-Feedback von
> Norbert (22.06.2026) und die daraus abgeleiteten HighFi-Design-Anpassungen zu **einem** Dokument.
> v2 beschreibt den Stand, der im UI tatsächlich umgesetzt wurde: Demand/Supply-Einstiegsweiche,
> drei statt einer Profil-/Dashboard-Seiten, strukturierte Verfügbarkeit (Datumsbereich + Pensum %),
> Dringlichkeit auf Anfragen, transparente Verteilungsstrategie, Person↔Organisation-Verknüpfung,
> ein kontextreicher Erstkontakt und das neue **Hub-/Region-Modell** (§14, ersetzt das alte «Scope»-
> /Umkreis-Modell). Das Leitprinzip bleibt unverändert: **Die Plattform ist asymmetrisch.
> Nachfrage handelt, Angebot wartet. Es gibt kein «Bieten».**

---

## 1. Was die Plattform lösen will

Im regionalen Innovationsökosystem rund um Baden, Brugg und die FHNW läuft die Zusammenarbeit zwischen Startups, Forschenden und Unternehmen fast nur über informelle Kontaktketten — Vitamin B. Wer eine Kompetenz oder einen Partner sucht, ist auf das eigene Netzwerk angewiesen. Das ist langsam, hängt vom Zufall ab und schliesst alle aus, die noch keinen Zugang zu den bestehenden Kreisen haben.

Das Kernproblem in einem Satz: «Ich brauche jemanden in meiner Nähe, von dem ich nicht weiss, dass es ihn gibt.»

Orbit ersetzt das informelle Netzwerk nicht. Es ergänzt es um einen strukturierten Weg, der für alle zugänglich ist und Bedarf und Kompetenz zusammenbringt. Die Plattform ist bewusst regional verankert und liegt irgendwo zwischen LinkedIn, HR-Tool und Jobbörse, ohne eines davon zu sein.

---

## 2. Akteure, Rollen und der Demand/Supply-Split

Es gibt zwei Akteurstypen, die sich schon im Onboarding gabeln: **Person** und **Organisation**. Beide sind gleichberechtigt und können dasselbe tun. Der Unterschied liegt nur in den Quellen, aus denen ihr Profil gebaut wird, und in der Verifikation.

Entscheidend ist: Jeder Akteur ist gleichzeitig **potenzielle Nachfrage** und **stehendes Angebot**. Eine Anfrage zeigt sichtbar, ob sie von einer Privatperson oder einer Firma stammt, und ein Profil ist je nach Typ ein Personen- oder Organisationsprofil. Die Asymmetrie verläuft also nicht zwischen Personen und Organisationen, sondern zwischen den beiden **Modi**, die jeder Akteur einnehmen kann: **handeln (Anfrage)** und **gefunden werden (Profil)**.

### 2.1 Ein Datenmodell, zwei Zielpfade

Jeder Akteur hat **ein Grundprofil** und **eine Anmeldung**. Was sich gegenüber v1 ändert, ist die *Einstiegsfrage*: Orbit fragt beim Einstieg, ob jemand gerade **sucht** (Demand) oder **gefunden werden** will (Supply), und führt ihn entlang des dazu passenden Screensets. Beide Pfade schreiben am Ende in dasselbe Profil bzw. dieselbe Anfrage — es entstehen keine getrennten Konten und kein «Angebots»-Objekt.

**Einstiegsweiche** (neu, ersetzt die frühere Suche-/Board-Startseite als Default-Landing):

> «Was möchtest du tun?»
> - **Ich suche jemanden** → Demand-Pfad (Freitext-Suche → ggf. Anfrage ausschreiben).
> - **Ich möchte gefunden werden** → Supply-Pfad (Profil aufbauen → Verfügbarkeit setzen).

Die Weiche ist nur ein Routing. Ein eingeloggter Nutzer kann jederzeit über den Modus-Schalter oben rechts («Ich suche | Ich biete») zwischen beiden Seiten wechseln. Niemand ist auf eine Seite festgelegt — die Asymmetrie liegt zwischen den *Modi*, nicht zwischen *Nutzern*.

**Warum kein echter Zwei-App-Split:** Ein «Angebot ausschreiben» würde das Board mit stehenden Kompetenzen fluten und die Nachfrage-Klarheit zerstören. Supply bleibt der Profil-Zustand. Der Split bedient den Wunsch nach Orientierung («wo bin ich, was soll ich tun»), ohne das Kernargument der Arbeit aufzugeben.

---

## 3. Szenarien

### 3.1 Startup ↔ Experte (primär, Demo-Fokus)

Ein Gründer braucht für zwei Monate Unterstützung bei Embedded-Firmware. Heute heisst das: Mitgründer fragen, Slack-Gruppen durchgehen, LinkedIn-Spam verschicken. Das zieht sich zwei bis drei Wochen hin, und oft gibt er vorher auf. Mit Orbit steht die Anfrage in unter zwei Minuten, die Engine liefert passende Profile samt Begründung (GitHub-Repos, Firmenseiten), und der Kontakt läuft direkt. Schon das Inserat allein schlägt Vitamin B, bevor das Matching perfekt ist.

### 3.2 Forschende → Industriepartner

Eine Forscherin hat ein Paper und braucht ein KMU, um es zu validieren und einen Innosuisse-Antrag mitzuschreiben. Heute läuft das ad hoc über LinkedIn, Kollegen und Konferenzen, und outbound funktioniert kaum. Mit Orbit beschreibt sie die Forschungsmöglichkeit in einer Anfrage, und passende KMU werden benachrichtigt. Umgekehrt taucht ihr auto-generiertes Profil auf, sobald ein Unternehmen einen passenden Bedarf postet.

### 3.3 Industrie → Experte unter Zeitdruck

Ein Projektleiter braucht für zwei Monate einen Spezialisten, etwa für multimodale Mobilität. Heute heisst das: generische Google-Suche, Mails an info@-Adressen, am Ende eine teure Beratung. Mit Orbit stellt er eine Freitext-Anfrage, die semantische Suche durchsucht Forschung und Freelancer, zuerst an der FHNW, dann CH-weit, und jeder Treffer kommt mit einer Begründung. Mit einer **Dringlichkeit** «Dringend» eskaliert die Verteilung automatisch, wenn binnen 48 h niemand reagiert.

### 3.4 Praktikum / Werkstudium (Match-Paar)

Ein Student hat über den Sommer Zeit und setzt im Profil «offen für Praktikum/Mandat, 60 % Aug–Okt». Ein KMU schreibt eine Anfrage mit Art der Zusammenarbeit «Praktikum / Werkstudium» und Zeitraum Aug–Okt aus. Über Verfügbarkeit (§6) + Umfang (§7) + Art-der-Zusammenarbeit-Abgleich (§13) finden sich beide als zeitlich und inhaltlich passendes Paar.

Jedes Szenario wurde für eine **gefüllte** und eine **leere** Plattform durchgespielt. Der Anfrage-Lebenszyklus liefert in beiden Zuständen einen Mehrwert.

---

## 4. Konzept-Lösung: asymmetrisch

Die Plattform ist asymmetrisch: **Nachfrage handelt, Angebot wartet.**

### 4.1 Nachfrage

Nachfrage ist aktiv und eskaliert in zwei Schritten. Die ausgeschriebene Anfrage durchläuft danach einen klaren Lebenszyklus.

1. **Suche** — sofortiges semantisches Matching gegen bestehende Profile, ohne Konto. Die Hub-/Region-Auswahl (Alle, ein Hub wie FHNW, eine Region wie Aargau) wirkt als harter Filter: Treffer ausserhalb der gewählten Ebene werden nicht angezeigt, mit ausdrücklicher Aufweitung Hub → Region → CH; «Alle» filtert nicht (Details in §14). Zusätzlich lässt sich optional ein **Zeitfilter** setzen (siehe §7).
2. **Anfrage** — reicht die Suche nicht, wird der Bedarf zu einem persistenten, öffentlichen Objekt auf dem Anfrage-Board. Erst hier ist eine Anmeldung nötig, die Sucheingabe bleibt dabei erhalten. Eine Anfrage trägt Titel, Beschreibung, Präsenzmodus (Vor Ort, Hybrid oder Remote), Hub/Region, Tags, **Umfang/Zeitraum** (§7) und **Dringlichkeit** (§8).

Die ausgeschriebene Anfrage läuft durch vier Stufen: **Offen → Verteilt → Kontaktiert → Geschlossen.**

- **Offen** — live und öffentlich auf dem Board lesbar.
- **Verteilt** — passende Profile und Tag-Abonnenten werden benachrichtigt, etwa «4 Profile mit passenden Tags wurden benachrichtigt». Die Empfängerseite sieht, warum verteilt wurde: «Verteilt, weil dein Profil die Tags … trägt». Wie verteilt wird, hängt von Dringlichkeit und Hub/Region ab (siehe §9).
- **Kontaktiert** — mindestens eine Kontaktanfrage wurde vermittelt.
- **Geschlossen** — vom Ersteller als gelöst markiert und danach nicht mehr auf dem Board sichtbar.

Eine konzeptuelle Stufe **Empfehlung** bleibt vorerst nur Konzept: Unbeantwortete Anfragen wandern über Empfehlungsketten weiter und bringen so neue Akteure auf die Plattform. Die regelbasierte Hub-/Region-Aufweitung bei «Dringend» (§9) ist die konkrete, sichtbare Vorstufe davon.

Nachfrage läuft nie ins Leere.

### 4.2 Angebot

Angebot ist passiv und **nie ein Post**.

Das Profil ist das stehende Angebot und rund um die Uhr findbar. Es wird automatisch aus verlinkten Quellen aufgebaut — bei Personen aus GitHub, ORCID, einem normalen CV-PDF, einem LinkedIn-PDF, Website und Sprachen, bei Organisationen aus Handelsregister/Zefix und Firmenwebsite. Tags und Beschreibung werden aus den Quellen abgeleitet und lassen sich manuell editieren. Jede Aussage ist mit zitierten Nachweisen belegt.

Organisationen werden über das Handelsregister abgeglichen und tragen ein **HR-verifiziert**-Badge.

Die zentrale aktive Geste ist der **Verfügbarkeits-Toggle** («Offen für Anfragen»). Er erhöht das Ranking und zeigt ein Badge. Aufgeklappt trägt er strukturierte Angaben: Datumsbereich, Pensum % und bevorzugte Zusammenarbeit (Details in §6).

Anbieter reagieren ausserdem auf Nachfrage. Abonnierte Themen (Tags) lösen Benachrichtigungen aus, passende offene Anfragen erscheinen im **Postfach** unter «Offene passende Anfragen», und mit «Antworten» lassen sie sich aufgreifen.

### 4.3 Warum asymmetrisch

Ein Bedarf ist akut und temporär, also ein Objekt mit Lebenszyklus. Eine Kompetenz ändert sich vielleicht einmal im Jahr, also ist sie ein Profil-Zustand und kein Post. Im Prototyp hat es die Nutzer verwirrt, beides symmetrisch zu modellieren. Deshalb gibt es **kein «Bieten»** mehr.

---

## 5. Seitenstruktur — das Profil entlasten

> Löst den Kernpunkt aus dem Feedback: «Profil ist zu überladen, Nutzer weiss nicht, was er tun soll.»

Das frühere Profil machte zu viel auf einmal: Identität (Skills, Quellen) **und** Verfügbarkeits-Einstellung **und** eigene ausgeschriebene Anfragen **und** eingehende/passende Anfragen. Diese vier Jobs sind auf **drei Seiten** aufgeteilt, erreichbar über die Top-Navigation:

| Seite | Inhalt | Job |
|---|---|---|
| **Profil** | Wer du bist: Name, Tags, Beschreibung, Skills/Kompetenzen, Quellen/Nachweise. Plus Verfügbarkeits-**Badge** (Status, read-only). Der Verfügbarkeits-**Editor** erscheint hier nur im Supply-Modus. | Identität |
| **Meine Anfragen** | Demand: die von dir **ausgeschriebenen Anfragen** und ihre Verwaltung — Lebenszyklus Offen → Verteilt → Kontaktiert → Geschlossen, mit Stati und Aktionen. | Demand |
| **Postfach** | Supply-Inbox: **eingehende Kontaktanfragen** zu deinem Profil **und** **offene passende Anfragen**, die du beantworten kannst. Reiner Posteingang. | Supply |

### 5.1 Profil ist modus-abhängig

Der **eigene Profil-Screen** sieht je nach Modus völlig anders aus:

- **«Ich suche» (Demand):** Identität plus Inhalt — **Kopf** (Name, Untertitel/Rolle, Zugehörigkeit-Chip Firma/Selbständig/Privatperson, Standort- und Sprach-Tags), **Kompetenzen**, **Deine Quellen** und **Nachweise** (Punkte 1, 4, 5, 6 der Supply-Liste). **Ausgeblendet** sind nur der **Vervollständigen-Banner** und der **Verfügbarkeits-Block** (inkl. «Abonnierte Themen») — Punkte 2 und 3. Dazu eine dezente Ein-Zeilen-Hinweis: «Du bist im Such-Modus. Um gefunden zu werden, wechsle zu ‹Ich biete› und setze deine Verfügbarkeit.» Wer nur sucht, wird also nicht mit Verfügbarkeits-Feldern und dem Vollständigkeits-Druck belastet, kann seine Identität und Quellen aber pflegen.
- **«Ich biete» (Supply):** das **volle Profil**, von oben nach unten:
  1. **Kopf** — Name, Untertitel, Zugehörigkeit-Chip, Standort-/Sprach-Tags.
  2. **Vervollständigen-Banner** — nur sichtbar, solange das Profil unvollständig ist (z. B. < 2 verlinkte Quellen oder keine Verfügbarkeit gesetzt): Titel «Mach dein Profil vollständig», ein Satz, ein Fortschrittsbalken («Profil zu 40 % · 2 von 5») und Button «Profil vervollständigen». Erscheint nur im Supply-Modus.
  3. **Verfügbarkeits-Block** (hell-violett): voller Editor «Offen für Anfragen» (§6). Direkt darunter der Abschnitt **«Abonnierte Themen»** mit Tag-Chips + «Bearbeiten».
  4. **Kompetenzen** — Beschreibung + editierbare Skill-Tags + «Erstellt aus: …»-Zeile mit ↻-Button.
  5. **Deine Quellen** (bearbeitbar): GitHub / ORCID / LinkedIn (PDF) / Website, jede Zeile mit «+ hinzufügen» bzw. grünem «✓ verknüpft». Neue Quelle → Profil wird neu abgeleitet. Darunter ausgegraut «Bald verfügbar» (Google Scholar, Referenzen).
  6. **Nachweise** — Liste der verlinkten Belege (read-only).

**Öffentliche Ansicht** (was andere sehen): Verfügbarkeits-**Badge** (read-only) + Beschreibung + Skill-Tags + Quellen-Belege + Button «Kontakt aufnehmen». Keine Editoren, keine Anfrage-Listen.

### 5.2 Verfügbarkeit bleibt am Profil, ist aber modus-abhängig sichtbar

Verfügbarkeit ist konzeptuell Teil *wer du als Angebot bist* — sie gehört neben Skills und Quellen, nicht in einen Posteingang.

- **Öffentliches Profil:** zeigt den Verfügbarkeits-**Badge** (z. B. «Offen für Anfragen · 60 %, Aug–Okt») — read-only.
- **Eigenes Profil, Supply-Modus:** zeigt zusätzlich den **Editor**.
- **Eigenes Profil, Demand-Modus:** der Editor ist **ausgeblendet**.

Es entsteht weiterhin **kein** «Angebot ausschreiben»-Objekt — Supply bleibt der Profil-Zustand, das Postfach zeigt nur, was daraus an Inbound entsteht.

---

## 6. Strukturierte Verfügbarkeit — Datumsbereich + Pensum %

Der Verfügbarkeits-Toggle («Offen für Anfragen») trägt aufgeklappt **strukturierte Zeit- und Umfangsangaben** statt freiem Text:

- **Verfügbar von – bis** (Datumsbereich; «bis» optional = offen).
- **Pensum** als **Prozentwert** (z. B. 60 %), optional als Slider, mit optionaler Freitext-Notiz für Nuancen («ca. 4 h/Woche, flexibel»).
- **Bevorzugte Zusammenarbeit** als auswählbare Chips: Bachelor-/Masterarbeit, Innosuisse-Projekt, bezahltes Mandat, Forschungskollaboration, **Praktikum / Werkstudium**.

**Anzeige in Suchergebnissen und auf dem Profil:** statt «Verfügbar ab 15. Juni» künftig «**60 %, 1. Aug – 31. Okt**» bzw. «**verfügbar ab 1. Aug, 60 %**» wenn kein Enddatum gesetzt ist. Die Prozent-Angabe erscheint als kleines Pill neben dem Namen, damit der Suchende den Umfang sofort einschätzt.

**Platzierung:** Der Editor erscheint im Onboarding (immer, dort gibt es keinen Modus) und auf dem eigenen Profil nur im Supply-Modus. Ist noch nie eine Verfügbarkeit gesetzt worden und der Toggle aus, zeigt die Box einen Einrichtungs-Aufruf «Sag, wofür und wann du offen bist» + Button «Verfügbarkeit einrichten».

**Re-Ranking:** Verfügbarkeit, deren Datumsbereich das gewünschte Fenster überlappt, wird höher gewichtet. Profile ohne gesetztes Fenster gelten als «unbestimmt verfügbar» und ranken neutral, nicht negativ.

---

## 7. Such-Zeitfilter und Anfrage-Umfang («von wann bis wann»)

**Suche:** Unter dem Freitextfeld und neben der Hub-/Region-Auswahl kommt ein optionaler **Zeitfilter**: «Ich brauche es von [Datum] bis [Datum]» plus optional «Umfang ca. [%]». Leer gelassen = keine Zeit-Einschränkung. Gesetzt = fliesst als **Re-Ranking-Gewicht** ein (Überlappung mit Profil-Verfügbarkeit), **nicht** als harter Filter. Helptext: «Leer lassen = keine Zeit-Einschränkung. Beeinflusst die Reihenfolge, filtert nicht hart.»

**Anfrage:** Das Anfrage-Formular erhält ein Feld **Umfang / Zeitraum**: «von – bis» und optional Pensum %. Diese Angabe erscheint auf der Anfrage-Karte («**Aug – Okt · ~60 %**») und steuert, welche Profile als zeitlich passend benachrichtigt werden.

---

## 8. Dringlichkeit auf offenen Anfragen

Eine Anfrage trägt eine **Dringlichkeit**, gesetzt beim Erstellen (Segmented-Control, Style wie Präsenzmodus):

- **Flexibel** (kein Zeitdruck) — Default, zeigt kein Badge.
- **Bald** (innert ~2 Wochen) — gelbes Pill.
- **Dringend** (sofort) — rotes Pill.
- Optional ein **Deadline-Datum** («Antworten bis …»).

**Anzeige auf dem Board und in der Detailansicht:**

- Dringlichkeits-Badge auf der Karte («🔴 Dringend», «🟡 Bald», neutral bei «Flexibel»).
- Bei gesetzter Deadline ein Countdown-Hinweis («noch 3 Tage»).
- Eine abgelaufene Deadline schaltet die Karte sichtbar auf «**Frist abgelaufen**» und entfernt sie aus der Standard-Sortierung (bleibt für den Ersteller sichtbar, bis er sie schliesst).

Dringlichkeit ist auch ein **Sortier- und Verteilungs-Signal** (siehe §9).

---

## 9. Verteilungsstrategie — dringend vs. nicht dringend vs. ohne Deadline

Wie eine Anfrage verteilt wird, hängt von Dringlichkeit und Hub/Region ab. Die Strategie wird dem Ersteller **transparent angezeigt** («So verteilen wir deine Anfrage»).

| Dringlichkeit | Verteilungsstrategie | Was der Ersteller sieht |
|---|---|---|
| **Dringend** | Sofortige Benachrichtigung aller passenden Profile **und** Tag-Abonnenten im engsten Hub; nach 48 h ohne Reaktion automatische Aufweitung Hub → Region → CH. | «Sofort an X passende Profile. Keine Reaktion in 48 h → Hub wird aufgeweitet.» |
| **Bald** | Benachrichtigung passender Profile im gewählten Hub/Region; eine Erinnerung nach 5 Tagen an noch nicht reagierende Top-Treffer. | «An X Profile verteilt. Erinnerung nach 5 Tagen.» |
| **Flexibel** | Benachrichtigung passender Profile + Tag-Abonnenten; keine Eskalation, Anfrage bleibt ruhend auf dem Board sichtbar. | «An X Profile verteilt. Bleibt offen auf dem Board.» |
| **Mit Deadline** | Wie oben je nach Dringlichkeit, zusätzlich eine letzte Benachrichtigungswelle ~24 h vor Ablauf. | «Letzte Verteilung 24 h vor Frist.» |
| **Ohne Deadline** | Keine zeitgesteuerte Eskalation; nur initiale Verteilung + laufende Sichtbarkeit auf dem Board. | «Keine Frist — Anfrage bleibt offen sichtbar.» |

Die automatische Hub-/Region-Aufweitung bei «Dringend» bleibt **regelbasiert** (kein intelligentes Routing — das bleibt Konzept-only).

---

## 10. Onboarding — geteilt nach Modus

Nach Login oder Registrierung führt Orbit Nutzer ohne aktives Profil einmalig direkt ins Onboarding; Nutzer mit aktivem Profil kehren zum ursprünglich aufgerufenen Ziel zurück. Das Onboarding ist kein globaler Lock: öffentliche Seiten bleiben erreichbar. Es gabelt sich nach der ersten Einstiegsfrage «Ich suche» / «Gefunden werden», baut aber dasselbe Grundprofil:

| Schritt | Demand-Onboarding | Supply-Onboarding |
|---|---|---|
| Wer bist du | Person / Organisation | Person / Organisation |
| Basics | Name, E-Mail, Sprachen | Name, E-Mail, Sprachen |
| Quellen | Person: **CV-PDF / LinkedIn-PDF / manuelle Kurzbeschreibung**; Organisation: **Zefix / Firmenwebsite / Kurzbeschreibung** | **vollständig** (GitHub/ORCID/CV bzw. Zefix/Website) |
| Nachweise & Kompetenzen | automatische Ableitung im Hintergrund, kein Pflicht-Prüfschritt | Auswahl und Prüfung als Pflichtschritte |
| Verfügbarkeit | übersprungen | **Kern-Schritt** (Toggle + Datumsbereich + Pensum %) |
| Abschluss | «Du kannst suchen / eine Anfrage ausschreiben» (zwei Buttons «Suche starten» / «Anfrage ausschreiben») | «Dein Profil ist live» |

**Demand** braucht ein Grundprofil (Name + verifizierte E-Mail + zugehörige Organisation/Rolle) und eine geeignete Identitätsgrundlage. Für Personen ist das ein normales textbasiertes CV-PDF, ein LinkedIn-PDF oder eine manuelle Kurzbeschreibung (2–3 Sätze); eine persönliche Website ist freiwillig, ersetzt diese Grundlage aber nicht. Für Organisationen genügt Zefix, Firmenwebsite oder Kurzbeschreibung. Der Grund: Wer eine Anfrage stellt oder Kontakt aufnimmt, übergibt dem Empfänger Kontext über sich selbst. Der Empfänger muss einschätzen können, *wer* da anfragt — z. B. «Software Engineer bei der ZKB möchte sich zu deinem Cybersecurity-Wissen austauschen». Summary und Kompetenz-Tags werden weiterhin automatisch aus der Quelle abgeleitet, müssen im kurzen Demand-Flow aber nicht manuell geprüft werden und blockieren bei einem LLM-Ausfall nicht den Abschluss. GitHub/ORCID-Nachweisauswahl und Verfügbarkeit entfallen; der Abschluss bietet «Suche starten» und «Anfrage ausschreiben».

**Supply** durchläuft den vollen Quellen- und Verfügbarkeits-Flow, weil sein Profil das stehende Angebot ist. Beide Pfade landen im selben Profil-Objekt; ein Demand-Nutzer kann seine Quellen später ergänzen und wird damit auch findbar.

---

## 11. Erstkontakt

### 11.1 Voraussetzung — wie viel Profil braucht es, um zu kontaktieren

Kontaktaufnahme ist niedrigschwellig, aber nicht anonym. **Mindestanforderung, um eine Kontaktanfrage zu senden:**

1. Angemeldetes Grundprofil (Name + **verifizierte E-Mail**).
2. Zugeordnete **Organisation oder Rolle** (Firma, «Selbständig» oder «Privatperson») — damit der Empfänger weiss, wer anfragt.
3. **Mindestens eine geeignete Identitätsquelle** (CV / LinkedIn / manuelle Kurzbeschreibung; bei Organisationen Zefix / Firmenwebsite / Kurzbeschreibung), aus der eine knappe Absender-Beschreibung gebaut wird.
4. Eine **persönliche Nachricht** im Kontaktdialog (nicht leer).

**Nicht** erforderlich: vollständiger Quellen-Flow, aktive Verfügbarkeit, ausgefülltes Kompetenzprofil. Ein Suchender soll kontaktieren können, ohne selbst «offen für Anfragen» sein zu müssen — aber er ist nie ein leeres, kontextloses Konto.

Reicht das Grundprofil nicht, zeigt der Kontaktdialog inline, was fehlt («Bitte ergänze deinen Namen, bestätige deine E-Mail und verlinke eine Quelle oder beschreibe dich kurz, bevor du Kontakt aufnimmst») mit Link «Profil vervollständigen», statt den Button einfach zu sperren.

### 11.2 Kontakt-Übergabe zeigt den Anfragenden (Kontext)

Beim «Kontakt aufnehmen» («Stell dich einmal vor») wird **nicht nur die Nachricht** übergeben, sondern ein kompakter **Absender-Kontext**. Im Dialog steht oben eine Absender-Zeile «Du kontaktierst als **M. Keller · NovaTech GmbH**» mit Hinweis «Der Empfänger sieht deinen Namen, deine Firma und wonach du gesucht hast.»

Die Kontakt-Benachrichtigung (und die E-Mail) enthält:

- **Wer fragt:** Name + Rolle/Position.
- **Kurzbeschreibung des Anfragenden:** ein bis zwei Sätze, aus seiner Quelle abgeleitet, z. B. «Software Engineer bei der ZKB, Schwerpunkt Banking-Systeme».
- **Von wo:** zugeordnete Organisation («M. Keller · NovaTech GmbH») — die Person↔Firma-Verknüpfung (§12).
- **Was gesucht wurde:** der ursprüngliche Suchtext bzw. die Anfrage-Kurzbeschreibung («Gesucht: ‹Embedded System Engineer für 2 Monate›»).
- **Die persönliche Nachricht.**
- **Die Kontaktdaten** (E-Mail) zum direkten Weitermachen off-platform.

### 11.3 Übergabepunkt, keine Inbox

Die Rolle der Plattform endet beim Erstkontakt. Alles Weitere läuft direkt zwischen den Beteiligten und ausserhalb der Plattform, per E-Mail, LinkedIn oder Telefon. Es gibt keinen laufenden Chat und keinen On-Platform-Messaging-Thread. Eingehende Kontaktanfragen erscheinen als Karte im **Postfach**, sind aber ein einmaliger Übergabepunkt zur E-Mail und keine Inbox.

---

## 12. Profil-Verknüpfung Person ↔ Organisation

Eine Person kann mit einer **Organisation verknüpft** sein («arbeitet bei», «selbständig», «Mitgründer bei»). Diese Verknüpfung:

- erscheint auf dem **Personenprofil** als klickbarer Organisations-Chip («🏢 **NovaTech GmbH** · Industrielle Sensorik») direkt unter Name/Untertitel und verlinkt aufs Organisationsprofil;
- erscheint auf dem **Organisationsprofil** in einem Abschnitt «Personen» als zugehörige Personen-Chips (sofern öffentlich);
- wird in **Suchergebnissen, Anfrage-Karten und der Kontakt-Übergabe** mitgezeigt, damit immer klar ist, *wer von wo* aus handelt.

Die Verknüpfung ist bei Organisationen über das Handelsregister/Zefix gestützt (HR-verifiziert), bei Privatpersonen selbstdeklariert. Eine Person ohne Firma erscheint als «Privatperson» oder «Selbständig» — nie ohne Herkunftsangabe.

---

## 13. Zwei Oberflächen, eine Datenschicht

### 13.1 Anfrage-Board

Das **Anfrage-Board** ist ein öffentlicher Feed offener Anfragen, ohne Login lesbar.

- **Erklär-Header oben:** eine dezente Info-Box «Offene Anfragen aus der Region» mit Satz «Antworte direkt oder abonniere Themen per Tag. Angebote werden hier **nicht** ausgeschrieben — dein Profil wird gefunden.»
- **Sichtbare Sortierung** als Dropdown: Neueste, Relevanteste, **Dringlichkeit**, **Frist zuerst**.
- **Filter:** Tag, Hub/Region, **Akteurstyp** (Person/Firma), **Dringlichkeit**, **Präsenzmodus**, **Zeitfenster**.
- **Tag-Abonnement** je Tag.
- **Karten** zeigen Typ-Badge (Person/Firma), Dringlichkeits-Badge, Präsenzmodus, Hub/Region, Zeitraum/Umfang («Aug – Okt · ~60 %») und bei Frist «noch 3 Tage».

**Benennung:** «Anfrage-Board» bleibt Funktionsname, im UI mit Untertitel «Offene Anfragen». Alternativnamen zum Testen in Usability-Interviews: «Gesuche», «Offene Anfragen», «Bedarf-Board». Entscheidung nach Interview-Feedback.

### 13.2 Discovery Engine

Freitext-Suche und semantisches Matching. Jeder Treffer kommt mit einer **Textbegründung und zitierten Belegen** statt Prozentwerten, dazu eine Entfernungsangabe wie «12 Min. von FHNW Brugg» und das strukturierte Verfügbarkeits-Pill («60 % · 1. Aug – 31. Okt»).

### 13.3 Matching-Pipeline

```
Anfrage / Query
  → harter Hub-/Region-Filter (Treffer ausserhalb der gewählten Ebene ausgeschlossen, Aufweitung Hub → Region → CH)
  → Embedding-Retrieval über die verbleibenden Kandidaten (multilingual-e5-large via Ollama)
  → Hybrid-Suche (BM25 + pgvector) + Reciprocal Rank Fusion
  → gewichtetes Re-Ranking nach Verfügbarkeit (Fenster-Überlappung, §6/§7),
    Präsenzmodus, geografischer Nähe und Hub-Überlappung
  → LLM-generierte Begründung mit zitierten Quellen
```

---

## 14. Hubs und Regionen — geografische und Zugehörigkeits-Verankerung

Das frühere «Scope»-Modell (FHNW → Aargau → CH als gestufter Umkreis) hat zwei grundverschiedene Dinge in **einen** Regler gepresst und damit eine saubere Verschachtelung vorgetäuscht, die es nicht gibt: FHNW-Brugg liegt geografisch in Aargau, FHNW-Olten aber in Solothurn — «FHNW» ist also keine Teilmenge von «Aargau». Wir lösen das auf, indem **Zugehörigkeit** (ein *Hub*) und **geografische Nähe** als getrennte Konzepte modelliert werden.

### 14.1 Begriffe

Ein **Hub** ist eine benannte Entität im Innovationsökosystem: eine **Institution** (z. B. FHNW), ein **Inkubator- oder Förderprogramm** (z. B. ein Accelerator), oder eine **Szene/Cluster** (z. B. die Startup-Szene einer Stadt). Ein Hub ist eine *Zugehörigkeit*, kein Radius. Er trägt einen Standort und gehört zu einer Region.

Eine **Region** ist die übergeordnete Ebene (z. B. Aargau, Region Zürich). Hubs rollen in Regionen hoch, Regionen in die landesweite Ebene (**CH**). Die Hierarchie ist also **Hub → Region → CH**.

Das löst die bisher widersprüchlichen Fragen sauber auf:

- «Ist FHNW Teil des Aargau-Hubs?» → FHNW ist ein **Hub**, Aargau eine **Region**; FHNW gehört zur Region Aargau. Kein Hub steckt im anderen.
- «Ist Baden +15 km Teil von FHNW?» → Nein. Geografische Nähe ist ein **separates Re-Ranking-Signal**, keine Hub-Mitgliedschaft.

### 14.2 Zwei getrennte Achsen — Zugehörigkeit, Nähe, plus Präsenzmodus

Drei Konzepte, die früher vermischt waren, sind jetzt sauber getrennt:

- **Hub / Region (Zugehörigkeit)** = *wo jemand verankert ist* (FHNW, ein Inkubator, die Region Aargau). Auf der Suchseite wählt der Suchende einen **Hub oder eine Region**. In der **Suche** wirkt die Auswahl als harter Filter mit ausdrücklicher Aufweitung **Hub → Region → CH** (analog zur bisherigen Stufung, aber benannt statt als Radius); in der **Anfrage-Verteilung** wirkt sie als Re-Ranking- und Eskalations-Gewicht (§9).
- **Geografische Nähe** = *räumliche Distanz* (z. B. «12 Min. von FHNW Brugg»). Ein eigenständiges Re-Ranking-Gewicht, unabhängig vom Hub.
- **Präsenzmodus** (Vor Ort / Hybrid / Remote) = *wie* gearbeitet wird. «Remote» heisst geografie-unabhängig — dann treten Hub-Nähe und Distanz in den Hintergrund. «Vor Ort» heisst, Nähe zählt stark.

«CH» ist die oberste Region/Aufweitungsstufe, **nie** ein Präsenzmodus. Bei Präsenzmodus «Remote» werden Hub- und Distanz-Gewicht automatisch entschärft (Hinweis «Remote — Hub/Nähe weniger relevant»). Jede Hub-/Region-Beschriftung erhält einen Ein-Satz-Tooltip, der erklärt, wen sie einschliesst (z. B. «FHNW: Profile mit FHNW-Zugehörigkeit»).

### 14.3 Mehrfach-Zugehörigkeit

Ein Profil kann **mehreren Hubs** angehören — z. B. FHNW-affiliiert **und** in einem Inkubator **und** in einer Stadt-Szene. Das Re-Ranking gewichtet jeden überlappenden Hub. Es gibt keinen erzwungenen «Primär-Hub».

### 14.4 Verwaltung der Hubs

Hubs und Regionen sind **admin-verwaltet**. Die Plattform startet mit einem **Seed-Set** (FHNW, Region Aargau, CH und die ersten Baden-/Brugg-relevanten Hubs); ein Admin/Operator kann Hubs und Regionen **anlegen und bearbeiten** (Name, Typ, Standort, Region-Zuordnung). Es gibt keinen offenen Self-Service, mit dem Nutzer beliebige Hubs erzeugen — das hält die Hub-Liste kuratiert und vertrauenswürdig.

### 14.5 Zuordnung beim Onboarding — abgeleitet, dann bestätigt

Ein Nutzer wird nicht gezwungen, seine Hubs manuell zu suchen. Stattdessen **leitet Orbit die Hub-Zugehörigkeit aus den Profil-Quellen ab** (z. B. FHNW-E-Mail-Domain oder ORCID-Affiliation → FHNW-Hub; Arbeitgeber = Portfolio-Firma eines Inkubators → dessen Hub; Standort → Region). Die abgeleiteten Hubs werden im Onboarding **vorgeschlagen**, und der Nutzer **bestätigt** sie oder **wechselt** zu einem anderen. So bleibt die Zuordnung niederschwellig, aber nicht rein selbstdeklariert.

> **Offene Punkte (für Usability-Interviews / spätere Iteration):** die genauen Ableitungsregeln je Hub-Typ (welche Quelle mappt auf welchen Hub), der Admin-Workflow zum Pflegen von Hubs, und ob Regionen rein geografisch oder ebenfalls kuratiert definiert werden. Das Hub-Konzept ist im MVP mit Seed-Set + Ableitung + Bestätigung umgesetzt; die volle Hub-Governance ist bewusst leichtgewichtig gehalten.

---

## 15. MVP-Abgrenzung

### 15.1 Umgesetzt

- Einstiegsweiche Demand/Supply als Default-Landing; Modus-Schalter «Ich suche | Ich biete».
- Geteiltes Onboarding (verkürzte Demand-Variante / volle Supply-Variante), beide ins selbe Profil.
- Anfrage erstellen (Freitext, Titel, Beschreibung, Präsenzmodus, Hub/Region, Tags, Umfang/Zeitraum, Dringlichkeit, Art der Zusammenarbeit).
- Direktes semantisches Matching mit Begründungen und zitierten Quellen.
- Such-Zeitfilter (Re-Ranking, kein harter Filter).
- Anfrage-Board (öffentlich, Erklär-Header, erweiterte Filter, sichtbare Sortierung, Tag-Abonnement, Dringlichkeits-Badges, Zeitraum/Frist-Anzeige).
- Vierstufiger Anfrage-Lebenszyklus (Offen → Verteilt → Kontaktiert → Geschlossen) auf der Seite «Meine Anfragen».
- Transparente, dringlichkeits-abhängige Verteilungsstrategie inkl. regelbasierter Hub-/Region-Aufweitung bei «Dringend».
- Tag-basierte Verteilung und Benachrichtigung passender Profile und Abonnenten, samt sichtbarer Verteilungs-Begründung.
- Drei-Seiten-Struktur: Profil (modus-abhängig) · Meine Anfragen · Postfach.
- Profile (Person und Organisation, auto-generiert, mit Nachweisen, manuell editierbar).
- Person↔Organisation-Verknüpfung auf Profilen, in Suchergebnissen, Anfrage-Karten und Kontakt-Übergabe.
- Zefix-/Handelsregister-Abgleich sowie HR-Verifikation für Organisationen.
- Strukturierte Verfügbarkeit (Datumsbereich + Pensum % + Notiz + bevorzugte Zusammenarbeit inkl. Praktikum).
- Postfach mit «Eingehenden Kontaktanfragen» (mit Absender-Kontext) und «Offenen passenden Anfragen».
- Kontakt-Gating (Mindest-Grundprofil) mit inline-Hinweis statt gesperrtem Button.
- Kontakt-Übergabe per E-Mail mit Absender-Kontext (einmaliger Erstkontakt, danach off-platform).
- Hub-/Region-Modell (Hub → Region → CH, admin-verwaltet, abgeleitete + bestätigte Zugehörigkeit, Mehrfach-Hubs) mit getrennten Achsen Präsenzmodus / Zugehörigkeit / Nähe, Remote-Entschärfung und Hub-Tooltips.

### 15.2 Konzept-only

- Empfehlungsketten für unbeantwortete Anfragen (über die regelbasierte Hub-/Region-Aufweitung hinaus).
- «Verbundene Kreise» als Hub-Typ (persönliches Netzwerk als Hub; auswählbar, aber noch ohne Wirkung).
- Vollständige Hub-Governance: feinkörnige Ableitungsregeln je Hub-Typ und ausgebauter Admin-Workflow (MVP: Seed-Set + Ableitung + Bestätigung, §14).
- Intelligentes Routing unbeantworteter Anfragen über Organisationsprofile als Eskalationsstufe.
- Automatischer Import von LinkedIn-Unternehmensseite und Referenzen/Case-Studies (Organisation).
- Google-Scholar-Abgleich (Person).

### 15.3 Bewusste Nicht-Funktion

Aktive Angebots-Promotion («Bieten»). Das hält das Board nachfrageseitig und spam-frei. Es entsteht **kein** «Angebot ausschreiben»-Objekt.
