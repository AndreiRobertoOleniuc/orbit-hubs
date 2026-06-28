# Orbit — Spec-Erweiterung (Feedback Norbert, 22.06.2026)

Dieses Dokument enthält die überarbeiteten und neuen Spec-Abschnitte, die das Meeting-Feedback
vom 22.06. einarbeiten. Es ist als Ergänzung zu `Orbit_Spec.md` formuliert — jeder Abschnitt sagt,
ob er einen bestehenden Abschnitt **ersetzt** oder **ergänzt**.

Leitprinzip bleibt unangetastet: Die Plattform ist asymmetrisch. Nachfrage handelt (Objekt mit
Lebenszyklus), Angebot wartet (Profil-Zustand). Keine «Bieten»-Posts. Der von Norbert gewünschte
Demand/Supply-Split wird deshalb als **Navigations- und Framing-Ebene** umgesetzt, nicht als zweites
Datenmodell.

---

## 1. Demand/Supply-Split — ein Datenmodell, zwei Zielpfade

> **Ersetzt** den Abschnitt «Akteure und Rollen» (Ergänzung am Ende) und präzisiert «Zwei Oberflächen».

Jeder Akteur hat **ein Grundprofil** und **eine Anmeldung**. Das ändert sich nicht. Was sich ändert,
ist die *Einstiegsfrage*: Orbit fragt beim Einstieg, ob jemand gerade **sucht** (Demand) oder
**gefunden werden** will (Supply), und führt ihn entlang des dazu passenden Screensets. Beide Pfade
schreiben am Ende in dasselbe Profil bzw. dieselbe Anfrage — es entstehen keine getrennten Konten und
kein «Angebots»-Objekt.

**Einstiegsweiche (neu, ersetzt die heutige Suche-Startseite als Default-Landing):**

> «Was möchtest du tun?»
> - **Ich suche jemanden** → Demand-Pfad (Freitext-Suche → ggf. Anfrage ausschreiben).
> - **Ich möchte gefunden werden** → Supply-Pfad (Profil aufbauen → Verfügbarkeit setzen).

Die Weiche ist nur ein Routing. Ein eingeloggter Nutzer kann jederzeit zwischen beiden Seiten
wechseln (Navigation: «Suche» ↔ «Mein Profil»). Niemand ist auf eine Seite festgelegt — die
Asymmetrie liegt zwischen den *Modi*, nicht zwischen *Nutzern*.

**Warum kein echter Zwei-App-Split:** Ein «Angebot ausschreiben» würde das Board mit stehenden
Kompetenzen fluten und die Nachfrage-Klarheit zerstören. Supply bleibt der Profil-Zustand. Der Split
bedient Norberts Wunsch nach Orientierung («wo bin ich, was soll ich tun»), ohne das Kernargument
der Arbeit aufzugeben.

### 1.1 Geteiltes Onboarding pro Seite

> **Ergänzt** «Angebot» und «Erstkontakt».

Das Onboarding gabelt sich nach der Einstiegsfrage, baut aber dasselbe Grundprofil:

| Schritt | Demand-Onboarding | Supply-Onboarding |
|---|---|---|
| Wer bist du | Person / Organisation | Person / Organisation |
| Basics | Name, E-Mail, Sprachen | Name, E-Mail, Sprachen |
| Quellen | **mindestens eine** (LinkedIn/Website/Manuelle Beschreibung) | **vollständig** (GitHub/ORCID/CV bzw. Zefix/Website) |
| Verfügbarkeit | übersprungen | **Kern-Schritt** (Toggle + Datumsbereich + Pensum %) |
| Abschluss | «Du kannst suchen / eine Anfrage ausschreiben» | «Dein Profil ist live» |

Demand braucht ein **Grundprofil** (Name + verifizierte E-Mail + zugehörige Organisation) **und
mindestens eine Quelle** — LinkedIn, Website oder eine manuelle Kurzbeschreibung. Der Grund: Wer eine
Anfrage stellt oder Kontakt aufnimmt, übergibt damit dem Empfänger Kontext über sich selbst. Der
Empfänger muss einschätzen können, *wer* da anfragt — z. B. «Software Engineer bei der ZKB möchte sich
zu deinem Cybersecurity-Wissen austauschen». Dafür braucht es schon auf der Demand-Seite genug
Material, um eine knappe Beschreibung zu bauen. Eine einzige Quelle (oder zwei, drei Sätze manuell)
genügt; der volle Quellen-Flow ist nicht nötig.

Supply durchläuft den vollen Quellen- und Verfügbarkeits-Flow, weil sein Profil das stehende Angebot
ist. Beide landen im selben Profil-Objekt; ein Demand-Nutzer kann seine Quellen später ergänzen und
wird damit auch findbar.

### 1.2 Seitenstruktur — das Profil entlasten

> **Neu**, löst Norberts Punkt «Profil ist zu überladen, Nutzer weiss nicht, was er tun soll».

Das heutige Profil macht zu viel auf einmal: Identität (Skills, Quellen) **und** Verfügbarkeits-
Einstellung **und** eigene ausgeschriebene Anfragen **und** eingehende/passende Anfragen. Das ist die
Überladung. Wir teilen diese vier Jobs auf **drei Seiten**, erreichbar über die Top-Navigation:

| Seite | Inhalt | Seite |
|---|---|---|
| **Profil** | Wer du bist: Name, Tags, Beschreibung, Skills/Kompetenzen, Quellen/Nachweise. Plus Verfügbarkeits-**Badge** (Status, read-only). **Der Verfügbarkeits-Editor erscheint hier nur im Supply-Modus** (siehe unten). | Identität |
| **Meine Anfragen** | Demand: die von dir **ausgeschriebenen Anfragen** und ihre Verwaltung — Lebenszyklus Offen → Verteilt → Kontaktiert → Geschlossen, mit Stati und Aktionen. | Demand |
| **Postfach** | Supply-Inbox: **eingehende Kontaktanfragen** zu deinem Profil **und** **offene passende Anfragen**, die du beantworten kannst. Reiner Posteingang. | Supply |

**Verfügbarkeit bleibt am Profil, ist aber modus-abhängig sichtbar.** Verfügbarkeit («Offen für
Anfragen» + Datumsbereich + Pensum % + bevorzugte Zusammenarbeit) ist konzeptuell Teil *wer du als
Angebot bist* — sie gehört neben Skills und Quellen, nicht in einen Posteingang. Deshalb:

- **Öffentliches Profil:** zeigt den Verfügbarkeits-**Badge** (z. B. «Offen für Anfragen · 60 %,
  Aug–Okt») — read-only, damit Suchende ihn sehen.
- **Eigenes Profil im Supply-Modus:** zeigt zusätzlich den **Editor** (Toggle, Daten, Pensum,
  bevorzugte Zusammenarbeit, abonnierte Themen).
- **Eigenes Profil im Demand-Modus:** der Editor ist **ausgeblendet** — wer nur sucht, wird nicht mit
  Verfügbarkeits-Feldern belastet.

So bleibt das Profil schlank und beantwortet eine Frage («wer ist das»), die beiden Dashboard-Seiten
beantworten je eine andere («was habe ich ausgeschrieben» / «wer will etwas von mir»). Es entsteht
weiterhin **kein** «Angebot ausschreiben»-Objekt — Supply bleibt der Profil-Zustand, das Postfach
zeigt nur, was daraus an Inbound entsteht.

**Navigation:** Top-Nav wird zu `Suche · Anfrage-Board · Meine Anfragen · Postfach · Profil`. «Meine
Anfragen» und «Postfach» sind nur eingeloggt sichtbar.

---

## 2. Strukturierte Verfügbarkeit — Datumsbereich + Pensum %

> **Ersetzt** den Verfügbarkeits-Teil in «Angebot».

Der Verfügbarkeits-Toggle («Offen für Anfragen») trägt aufgeklappt jetzt **strukturierte Zeit- und
Umfangsangaben** statt freiem Text:

- **Verfügbar von – bis** (Datumsbereich; «bis» optional = offen).
- **Pensum** als **Prozentwert** (z. B. 60 %) mit optionaler Freitext-Notiz für Nuancen
  («ca. 4 h/Woche, flexibel»).
- **Bevorzugte Zusammenarbeit** (unverändert: Bachelor-/Masterarbeit, Innosuisse, bezahltes Mandat,
  Forschungskollaboration).

**Anzeige in Suchergebnissen und auf dem Profil:** statt «Verfügbar ab 15. Juni» künftig
«**60 %, 1. Aug – 31. Okt**» bzw. «**verfügbar ab 1. Aug, 60 %**» wenn kein Enddatum gesetzt ist. Die
Prozent-Angabe erscheint als kleines Badge neben dem Namen, damit der Suchende den Umfang sofort
einschätzt.

**Re-Ranking:** Verfügbarkeit, deren Datumsbereich das in der Suche/Anfrage gewünschte Fenster
überlappt, wird höher gewichtet. Profile ohne gesetztes Fenster gelten als «unbestimmt verfügbar»
und ranken neutral, nicht negativ.

---

## 3. Such-Zeitfilter und Anfrage-Umfang («von wann bis wann»)

> **Ergänzt** «Nachfrage → Suche» und «Anfrage».

**Suche:** Unter dem Freitextfeld und neben dem Umkreis kommt ein optionaler **Zeitfilter**:
«**Ich brauche es** von [Datum] bis [Datum]» plus optional «**Umfang** ca. [%]». Leer gelassen =
keine Zeit-Einschränkung (heutiges Verhalten). Gesetzt = fliesst als Re-Ranking-Gewicht ein
(Überlappung mit Profil-Verfügbarkeit), **nicht** als harter Filter — analog zum Umkreis.

**Anfrage:** Das Anfrage-Formular erhält ein Feld **Umfang / Zeitraum**: «von – bis» und optional
Pensum %. Diese Angabe erscheint auf der Anfrage-Karte («**Aug – Okt · ~60 %**») und steuert, welche
Profile als zeitlich passend benachrichtigt werden.

---

## 4. Dringlichkeit auf offenen Anfragen

> **Neu**, ergänzt den Anfrage-Lebenszyklus.

Eine Anfrage trägt eine **Dringlichkeit**, gesetzt beim Erstellen:

- **Flexibel** (kein Zeitdruck) — Default.
- **Bald** (innert ~2 Wochen).
- **Dringend** (sofort) — sichtbares Badge.
- Optional ein **Deadline-Datum** («Antworten bis …»).

**Anzeige auf dem Board und in der Detailansicht:**

- Dringlichkeits-Badge auf der Karte («🔴 Dringend», «🟡 Bald», neutral bei «Flexibel»).
- Bei gesetzter Deadline ein Countdown-Hinweis («noch 3 Tage»).
- Eine abgelaufene Deadline schaltet die Karte sichtbar auf «**Frist abgelaufen**» und entfernt sie
  aus der Standard-Sortierung (bleibt für den Ersteller sichtbar, bis er sie schliesst).

Dringlichkeit ist auch ein **Sortier- und Verteilungs-Signal** (siehe §5).

---

## 5. Verteilungsstrategie — dringend vs. nicht dringend vs. ohne Deadline

> **Neu**, präzisiert die Stufe «Verteilt» im Lebenszyklus.

Wie eine Anfrage verteilt wird, hängt von Dringlichkeit und Geltungsbereich ab. Die Strategie wird
dem Ersteller **transparent angezeigt** («So verteilen wir deine Anfrage»), damit klar ist, was
passiert.

| Dringlichkeit | Verteilungsstrategie | Was der Ersteller sieht |
|---|---|---|
| **Dringend** | Sofortige Benachrichtigung aller passenden Profile **und** Tag-Abonnenten im engsten Scope; nach 48 h ohne Reaktion automatische Scope-Erweiterung (FHNW → Baden +20 km → CH). | «Sofort an X passende Profile. Keine Reaktion in 48 h → Umkreis wird erweitert.» |
| **Bald** | Benachrichtigung passender Profile im gewählten Scope; eine Erinnerung nach 5 Tagen an noch nicht reagierende Top-Treffer. | «An X Profile verteilt. Erinnerung nach 5 Tagen.» |
| **Flexibel** | Benachrichtigung passender Profile + Tag-Abonnenten; keine Eskalation, Anfrage bleibt ruhend auf dem Board sichtbar. | «An X Profile verteilt. Bleibt offen auf dem Board.» |
| **Mit Deadline** | Wie oben je nach Dringlichkeit, zusätzlich eine letzte Benachrichtigungswelle ~24 h vor Ablauf. | «Letzte Verteilung 24 h vor Frist.» |
| **Ohne Deadline** | Keine zeitgesteuerte Eskalation; nur die initiale Verteilung + laufende Sichtbarkeit auf dem Board. | «Keine Frist — Anfrage bleibt offen sichtbar.» |

Die automatische Scope-Erweiterung bei «Dringend» ist die konkrete, sichtbare Ausprägung der
Empfehlungs-/Eskalations-Idee — sie bleibt aber regelbasiert (kein intelligentes Routing, das bleibt
Konzept-only).

---

## 6. Kontakt-Voraussetzung («wie viel Profil braucht es, um zu kontaktieren»)

> **Neu**, ergänzt «Erstkontakt».

Kontaktaufnahme ist niedrigschwellig, aber nicht anonym. **Mindestanforderung, um eine
Kontaktanfrage zu senden:**

1. Angemeldetes Grundprofil (Name + **verifizierte E-Mail**).
2. Zugeordnete **Organisation oder Rolle** (Firma, «Selbständig» oder «Privatperson») — damit der
   Empfänger weiss, wer anfragt.
3. **Mindestens eine Quelle** (LinkedIn / Website / manuelle Kurzbeschreibung), aus der eine knappe
   Absender-Beschreibung gebaut wird — damit der Empfänger einordnen kann, *wer* mit welchem
   Hintergrund anfragt.
4. Eine **persönliche Nachricht** im Kontaktdialog (nicht leer).

**Nicht** erforderlich: vollständiger Quellen-Flow, aktive Verfügbarkeit, ausgefülltes
Kompetenzprofil. Ein Suchender soll kontaktieren können, ohne selbst aktiv findbar/«offen für
Anfragen» sein zu müssen — aber er ist nie ein leeres, kontextloses Konto.

Reicht das Grundprofil nicht, zeigt der Kontaktdialog inline, was fehlt («Bitte ergänze deinen Namen,
bestätige deine E-Mail und verlinke eine Quelle oder beschreibe dich kurz, bevor du Kontakt
aufnimmst»), statt den Button einfach zu sperren.

---

## 7. Kontakt-Übergabe zeigt den Anfragenden (Kontext)

> **Ersetzt** den Dialog-Teil von «Erstkontakt».

Beim «Kontakt aufnehmen» wird **nicht nur die Nachricht** übergeben, sondern ein kompakter
**Absender-Kontext**, damit der Empfänger die Anfrage einordnen kann:

Die Kontakt-Benachrichtigung (und die E-Mail) enthält:

- **Wer fragt:** Name + Rolle/Position.
- **Kurzbeschreibung des Anfragenden:** ein bis zwei Sätze, aus seiner Quelle abgeleitet (siehe §6),
  z. B. «Software Engineer bei der ZKB, Schwerpunkt Banking-Systeme».
- **Von wo:** zugeordnete Organisation («M. Keller · NovaTech GmbH») — die Person↔Firma-Verknüpfung
  (siehe §8).
- **Was gesucht wurde:** der ursprüngliche Suchtext bzw. die Anfrage-Kurzbeschreibung
  («Gesucht: ‹Embedded System Engineer für 2 Monate›»).
- **Die persönliche Nachricht.**
- **Die Kontaktdaten** (E-Mail) zum direkten Weitermachen off-platform.

So weiss der Empfänger sofort: *Wer* will *was*, *von welcher Firma* — bevor er antwortet.

---

## 8. Profil-Verknüpfung Person ↔ Organisation

> **Neu**, ergänzt «Angebot» und «Profile».

Eine Person kann mit einer **Organisation verknüpft** sein («arbeitet bei», «selbständig», «Mitgründer
bei»). Diese Verknüpfung:

- erscheint auf dem **Personenprofil** («NovaTech GmbH · Industrielle Sensorik») und verlinkt aufs
  Organisationsprofil;
- erscheint auf dem **Organisationsprofil** als zugehörige Personen (sofern öffentlich);
- wird in **Suchergebnissen, Anfrage-Karten und der Kontakt-Übergabe** mitgezeigt, damit immer klar
  ist, *wer von wo* aus handelt.

Die Verknüpfung ist bei Organisationen über das Handelsregister/Zefix gestützt (HR-verifiziert), bei
Privatpersonen selbstdeklariert. Eine Person ohne Firma erscheint als «Privatperson» oder
«Selbständig» — nie ohne Herkunftsangabe.

---

## 9. Anfrage-Board — Klarheit, Sortierung, Filter, Benennung

> **Ergänzt** «Zwei Oberflächen → Anfrage-Board».

- **Erklär-Header oben:** Eine kurze Info-Zeile erklärt, was das Board ist — «Offene Anfragen aus der
  Region. Du kannst antworten oder per Tag abonnieren. Angebote werden hier **nicht** ausgeschrieben.»
- **Sichtbare Sortierung:** Sortier-Auswahl sichtbar machen (nicht nur «Neueste/Relevanteste»),
  ergänzt um **Dringlichkeit** und **Deadline (Frist zuerst)**.
- **Bessere Filter:** zusätzlich zu Tag + Umkreis: Filter nach **Akteurstyp** (Person/Firma),
  **Dringlichkeit**, **Präsenzmodus** und **Zeitfenster**.
- **Benennung:** «Anfrage-Board» beibehalten als Funktionsname, aber im UI mit klarem Untertitel
  führen («Offene Anfragen»). Alternativ-Namen zum Testen in den Usability-Interviews:
  «Gesuche», «Offene Anfragen», «Bedarf-Board». Entscheidung nach Interview-Feedback.

---

## 10. Scopes schärfen — «ganze Schweiz» ist kein Präsenzmodus

> **Ergänzt** «Zwei Oberflächen» und das Anfrage-Formular.

Norbert hat zu Recht moniert, dass «ganze Schweiz» kein sinnvoller *Präsenzmodus* ist. **Präsenzmodus**
(Vor Ort / Hybrid / Remote) und **Geltungsbereich/Umkreis** (FHNW / Baden +20 km / CH / Verbundene
Kreise) sind zwei getrennte Achsen und dürfen nicht vermischt werden:

- **Präsenzmodus** = *wie* gearbeitet wird. «Remote» heisst geografie-unabhängig — dann tritt der
  Umkreis als Re-Ranking-Gewicht in den Hintergrund. «Vor Ort» heisst, Nähe zählt stark.
- **Umkreis** = *wo* gesucht wird, als Gewicht, nicht als harter Filter (FHNW → Aargau → CH).

**Konkretisierung:** Bei «Remote» wird der Umkreis automatisch entschärft (Hinweis im UI: «Remote —
Umkreis weniger relevant»). «CH» bleibt eine Umkreis-Stufe, ist aber nie ein Präsenzmodus. Die
Scope-Beschriftungen erhalten je einen Ein-Satz-Tooltip, der erklärt, *wen* sie einschliessen.

---

## 11. Praktika-Anfragen über das Anfrage-Board

> **Ergänzt** «Anfrage».

Ein Praktikums-/Werkstudenten-Bedarf ist ein regulärer Anfrage-Typ, kein Sonderobjekt. Damit er
sauber matcht, bekommt die Anfrage ein optionales Feld **Art der Zusammenarbeit**, das gegen die
«Bevorzugte Zusammenarbeit» der Profile (Bachelor-/Masterarbeit, Innosuisse, bezahltes Mandat,
Forschungskollaboration) abgeglichen wird — **erweitert um «Praktikum / Werkstudium»**. So findet
z. B. der «Student mit Zeit über den Sommer» (Profil: offen für Praktikum/Mandat, 60 % Aug–Okt)
genau die KMU-Anfrage, die Praktikumsunterstützung sucht. Beide von Norbert genannten Leitfälle
(Student bietet Sommer-Skills an / Startup braucht Skills kurzfristig) sind damit als ein Match-Paar
über §2 (Verfügbarkeit) + §3 (Umfang) + dieses Feld abgedeckt.

---

## 12. Default-Landing & Navigation

> **Ergänzt** §1.

Norbert: «Anfrage-Board Startseite → auf Supply-Seite». Aufgelöst über die Einstiegsweiche (§1): Die
**erste** Landing für neue/ausgeloggte Besucher ist die Weiche «Suchen vs. gefunden werden», nicht das
Board. Eingeloggt landet man auf der zuletzt genutzten Seite. Das Anfrage-Board bleibt jederzeit über
die Top-Navigation erreichbar, ist aber nicht mehr die Default-Startseite — das verhindert, dass das
Board fälschlich als «das ist die App» gelesen wird.

Die vollständige Top-Navigation (eingeloggt) lautet damit:
`Suche · Anfrage-Board · Meine Anfragen · Postfach · Profil` (siehe §1.2 für die Seiten-Aufteilung).

---

## Zusammenfassung der Datenmodell-Änderungen

Für die Umsetzung (Spring Boot Modulith) ergeben sich diese neuen/erweiterten Felder:

- **Availability**: `availableFrom: date`, `availableTo: date?`, `pensumPercent: int?`,
  `note: string?` (ersetzt reines Freitext-Pensum).
- **Request**: `urgency: enum{FLEXIBLE, SOON, URGENT}`, `deadline: date?`,
  `scopeStart: date?`, `scopeEnd: date?`, `scopePensumPercent: int?`.
- **Search-Query**: `neededFrom: date?`, `neededTo: date?`, `neededPensumPercent: int?` (Re-Ranking-
  Inputs, kein harter Filter).
- **Person**: `organizationRef: id?`, `relationType: enum{EMPLOYEE, FOUNDER, SELF_EMPLOYED, PRIVATE}`.
- **ContactRequest**: trägt zusätzlich `senderName`, `senderRole`, `senderOrg`, `senderDescription`
  (aus mind. einer Quelle abgeleitet), `originalQueryText`.
- **Profile/Demand**: mind. eine Quelle Pflicht (`linkedinPdf` | `website` | `manualDescription`),
  bevor eine Anfrage gestellt oder Kontakt aufgenommen werden kann.
- **Request** (Ergänzung): `collaborationType: enum{…, INTERNSHIP}` (Praktikum/Werkstudium).
- **Availability** (Ergänzung): `preferredCollaboration` enthält zusätzlich `INTERNSHIP`.
- **Scope/PresenceMode**: bleiben **getrennte** Felder; «CH» ist ausschliesslich ein Scope-Wert, nie
  ein PresenceMode.
- **Seiten/Routing** (siehe §1.2): drei Routen `/profil`, `/meine-anfragen` (Demand), `/postfach`
  (Supply). Der Verfügbarkeits-Editor ist eine modus-abhängige Komponente am Profil (sichtbar nur im
  Supply-Modus); öffentlich erscheint nur der Verfügbarkeits-Badge. Es gibt **kein** Angebots-Objekt.

Diese Felder sind additiv; bestehende MVP-Flows brechen nicht.
