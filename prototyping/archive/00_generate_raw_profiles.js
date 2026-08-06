// generate_raw_profiles.js
// Generates realistic raw source data (GitHub repos, website text, LinkedIn text)
// Usage: ANTHROPIC_API_KEY=your_key node generate_raw_profiles.js 100

import { writeFileSync } from "fs";
import { faker as fakerBase, allFakers } from "@faker-js/faker";

const COUNT = parseInt(process.argv[2] ?? "20");

const domains = [
    "Embedded Systems und IoT",
    "Machine Learning und Data Science",
    "Cloud Infrastructure und DevOps",
    "Fullstack Webentwicklung",
    "Mobile Entwicklung iOS/Android",
    "Cybersecurity und Netzwerksicherheit",
    "Industrieautomatisierung und Robotik",
    "Fintech und RegTech",
    "Medtech und digitale Gesundheit",
    "Nachhaltige Energie und Cleantech",
    "Datenbanken und Backend-Systeme",
    "KI-gestützte Bildverarbeitung",
    "EdTech und Lernplattformen",
];

const companies = [
    "ABB Schweiz",
    "Swisscom",
    "Zühlke Engineering",
    "Sensirion",
    "u-blox",
    "Endress+Hauser",
    "Georg Fischer",
    "Axpo",
    "SBB CFF FFS",
    "Kantonsspital Baden",
    "Siemens Schweiz",
    "Hitachi Energy",
    "dormakaba",
    "Brugg Group",
    "einer kleinen KMU in der Region Aargau",
    "einem Startup im Baden-Ecosystem",
    "der FHNW als Wissenschaftlicher Assistent",
    "Apiax",
    "Ergon Informatik",
    "Octotronic",
];

const seniority = [
    "sehr erfahren (8+ Jahre)",
    "erfahren (4-7 Jahre)",
    "mid-level (2-4 Jahre)",
    "junior (1-2 Jahre)",
    "FHNW Student mit Teilzeitjob",
];

const githubPresence = [
    "hat einen aktiven GitHub Account mit mehreren Projekten",
    "hat keinen GitHub Account",
    "hat einen kaum genutzten GitHub Account mit 1-2 alten Repos",
];

const websitePresence = [
    "hat eine persönliche Portfolio-Website",
    "hat keine persönliche Website",
];

// Mix locales for realistic Swiss diversity
const locales = [
    "de_CH", "de_CH", "de_CH",
    "fr_CH", "it",
    "en_US", "en_GB",
    "tr", "pt_BR",
];

function pick(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function randomName() {
    const locale = pick(locales);
    // allFakers has pre-built instances per locale
    const f = allFakers[locale] ?? fakerBase;
    return `${f.person.firstName()} ${f.person.lastName()}`;
}

async function generateRawProfile(index) {
    const name = randomName();
    const domain = pick(domains);
    const secondDomain = pick(domains.filter((d) => d !== domain));
    const company = pick(companies);
    const level = pick(seniority);
    const github = pick(githubPresence);
    const website = pick(websitePresence);

    const prompt = `Du generierst realistische Testdaten für eine Schweizer Innovations-Matchmaking-Plattform.

Erstelle realistische Rohdaten für folgende Person:
- Name: ${name} (verwende genau diesen Namen, keine Änderungen)
- Seniority: ${level}
- Hauptdomäne: ${domain}
- Nebendomäne: ${secondDomain}
- Arbeitgeber: ${company}
- GitHub: ${github}
- Website: ${website}

Wichtig für Realismus:
- GitHub repos sollen unterschiedlich vollständig sein — manchmal null descriptions, manchmal keine topics
- Website text soll wie echter gescrapeter HTML-Text aussehen (etwas unstrukturiert, mit Navigation-Elementen)
- LinkedIn soll wie ein PDF-Export aussehen mit deutschen Feldnamen (Berufserfahrung, Ausbildung, Kenntnisse etc.)
- Profile sollen NICHT perfekt sein — echte Lücken und unklare Formulierungen sind erwünscht
- Falls kein GitHub: github Feld auf null setzen
- Falls keine Website: website Feld auf null setzen

Antworte NUR mit diesem JSON (kein Markdown, keine Erklärungen):
{
  "name": "${name}",
  "github": [{ "name": "repo-name", "language": "Go", "description": "short desc or null", "topics": [] }] or null,
  "website": "raw scraped website text as one long string, or null",
  "linkedin": "raw LinkedIn PDF export text as one long string"
}`;

    const res = await fetch("https://api.anthropic.com/v1/messages", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "x-api-key": process.env.ANTHROPIC_API_KEY,
            "anthropic-version": "2023-06-01",
        },
        body: JSON.stringify({
            model: "claude-haiku-4-5-20251001",
            max_tokens: 2000,
            messages: [{ role: "user", content: prompt }],
        }),
    });

    if (!res.ok) {
        const err = await res.text();
        throw new Error(`Claude API error: ${res.status} — ${err}`);
    }

    const data = await res.json();
    const text = data.content[0].text.trim();

    try {
        return JSON.parse(text);
    } catch {
        const clean = text.replace(/```json|```/g, "").trim();
        return JSON.parse(clean);
    }
}

async function main() {
    if (!process.env.ANTHROPIC_API_KEY) {
        console.error("Error: ANTHROPIC_API_KEY not set");
        console.error("Usage: ANTHROPIC_API_KEY=your_key node generate_raw_profiles.js 100");
        process.exit(1);
    }

    console.log(`\nGenerating ${COUNT} raw profiles...\n`);

    const results = [];
    let failed = 0;

    for (let i = 0; i < COUNT; i++) {
        try {
            process.stdout.write(`[${i + 1}/${COUNT}] Generating...`);
            const profile = await generateRawProfile(i);
            results.push(profile);
            console.log(` ✓ ${profile.name}`);
            await new Promise((r) => setTimeout(r, 300));
        } catch (err) {
            console.log(` ✗ ${err.message}`);
            failed++;
        }
    }

    const filename = `raw_profiles_${COUNT}.json`;
    writeFileSync(filename, JSON.stringify(results, null, 2));

    console.log(`\n--- Done ---`);
    console.log(`Generated: ${results.length}`);
    console.log(`Failed:    ${failed}`);
    console.log(`Saved to:  ${filename}`);
    console.log(`\nNext: node synthesize_and_embed.js ${filename}`);
}

main().catch(console.error);