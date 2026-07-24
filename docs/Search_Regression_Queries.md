# Orbit Search Regression Queries

Use the exact same queries against the local and deployed backends. For every
query, record:

- whether the response is sufficient;
- the number of returned profiles;
- the first result;
- the complete result ordering;
- unexpected false positives or false negatives.

Broad queries should be repeated three times because the generated condensed
query can vary between requests.

## 1. Original benchmark

| # | Expected first result | Query | Local result | Deployed result |
|---:|---|---|---|---|
| 1 | Philippe F.-X. Corvini | `Wir suchen einen Experten für Umweltbiotechnologie und den biologischen Abbau pharmazeutischer Mikroschadstoffe im Abwasser.` | | |
| 2 | Markus Lenz | `Gesucht wird Expertise für die Rückgewinnung kritischer Metalle aus Industrieabfällen und für angewandte Kreislaufwirtschaft.` | | |
| 3 | Boris A. Kolvenbach | `Wer kennt sich mit mikrobieller Bioremediation und dem Abbau von Pestiziden oder Antibiotika in der Umwelt aus?` | | |
| 4 | Arzu Çöltekin | `Wir suchen eine Forscherin für Human-Computer Interaction, Extended Reality und immersive Datenvisualisierung.` | | |
| 5 | Norbert Seyff | `Gesucht wird ein Experte für Requirements Engineering, Nutzerfeedback und nachhaltige Softwareentwicklung.` | | |
| 6 | Oliver Bendel | `Wer kann ein Unternehmen bei ethischen Fragen zu künstlicher Intelligenz, Chatbots und sozialen Robotern beraten?` | | |
| 7 | Christine Jacob | `Wir suchen Expertise zur Nutzerakzeptanz und erfolgreichen Einführung einer digitalen Gesundheits-App in Kliniken.` | | |
| 8 | Tanja Manser | `Gesucht wird eine Expertin für Patientensicherheit, Teamkommunikation und Führung in medizinischen Hochrisikosituationen.` | | |
| 9 | Franz Barjak | `Wer erforscht Technologietransfer, Open Innovation und die Zusammenarbeit zwischen Hochschulen und Unternehmen?` | | |
| 10 | Prof. Dr. Michael de Wild | `Wir suchen einen Experten für biokompatible Implantatoberflächen, Knochenersatz und additive Fertigung in der Medizintechnik.` | | |

## 2. Unseen paraphrases

These queries were not part of the original threshold calibration. They check
whether matching generalizes to different wording.

| # | Expected first result | Query | Local result | Deployed result |
|---:|---|---|---|---|
| 11 | Philippe F.-X. Corvini | `Wir benötigen Beratung zur Entfernung von Arzneimittelrückständen aus kommunalem Abwasser durch Mikroorganismen.` | | |
| 12 | Markus Lenz | `Gesucht ist Know-how zur Wiedergewinnung seltener Rohstoffe aus Elektronikschrott.` | | |
| 13 | Arzu Çöltekin | `Wer unterstützt uns bei der Gestaltung verständlicher interaktiver 3D-Karten in einer VR-Anwendung?` | | |
| 14 | Norbert Seyff | `Wir möchten Nachhaltigkeitsziele systematisch als Anforderungen in einem agilen Softwareprojekt erfassen.` | | |
| 15 | Christine Jacob | `Unsere Klinik plant eine mobile Anwendung, aber Ärzte und Patienten akzeptieren den neuen Prozess nicht.` | | |
| 16 | Oliver Bendel | `Wir entwickeln einen Pflegeroboter und benötigen eine ethische Beurteilung seines Verhaltens.` | | |

## 3. Broad in-domain queries

These queries may legitimately return multiple profiles. Check that the results
remain relevant and that the backend does not return most or all profiles.

| # | Expected behavior | Query | Local result | Deployed result |
|---:|---|---|---|---|
| 17 | Andrei Oleniuc, Norbert Seyff and possibly Shenia Scherer | `Wir suchen allgemein Unterstützung bei moderner Softwareentwicklung.` | | |
| 18 | Christine Jacob and Tanja Manser; possibly Oliver Bendel | `Wir suchen allgemein Expertise im Gesundheitswesen.` | | |
| 19 | Franz Barjak first | `Wer hat Erfahrung mit Innovation, Forschung und Wissenstransfer?` | | |
| 20 | Corvini, Lenz and/or Kolvenbach expected | `Wir benötigen eine Fachperson für Umweltwissenschaften und nachhaltige Technologien.` | | |
| 21 | Corvini and/or Kolvenbach expected | `Wer verfügt über Expertise in Umweltchemie, biologischem Schadstoffabbau und Wasseraufbereitung?` | | |

The generic software and environmental queries are known sensitivity checks.
Repeat them three times and record changes in the returned profiles or ordering.

## 4. Negative controls

All of these should produce `sufficient = false` and zero results.

| # | Query | Expected | Local result | Deployed result |
|---:|---|---|---|---|
| 22 | `Wir suchen einen erfahrenen Steueranwalt für internationale Mehrwertsteuer und Unternehmensrecht.` | 0 results | | |
| 23 | `Gesucht wird ein Architekt für die Sanierung eines denkmalgeschützten Wohnhauses.` | 0 results | | |
| 24 | `Wer kann unsere Bäckerei bei der Entwicklung glutenfreier Sauerteigrezepte unterstützen?` | 0 results | | |
| 25 | `Wir benötigen Expertise für Quantencomputing, supraleitende Qubits und Quantenfehlerkorrektur.` | 0 results | | |
| 26 | `Gesucht ist eine Fachperson für Agronomie, Pflanzenschutz und den Anbau von Winterweizen.` | 0 results | | |
| 27 | `Wir suchen Beratung für Social-Media-Marketing, Markenstrategie und Influencer-Kampagnen.` | 0 results | | |

## Acceptance checklist

- [ ] All ten original benchmark queries return the expected profile first.
- [ ] All six unseen paraphrases return the expected profile first.
- [ ] Broad queries return only relevant profiles and do not flood the response.
- [ ] Repeated broad queries are reasonably stable.
- [ ] All six negative controls return zero profiles.
- [ ] Local and deployed versions use the same database snapshot and request filters.
- [ ] No query fails because an external inference provider is unavailable.
