// 02_seed.js — Embeds each person via Ollama and inserts into persons table
import pkg from "pg";
const { Client } = pkg;

const client = new Client({
    host: "localhost",
    port: 5432,
    database: "badenconnect",
    user: "root",
    password: "123456789",
});

const people = [
    {
        name: "Luca Nana",
        github: null,
        website: null,
        description:
            "Luca Nana ist ein erfahrener Systemingenieur mit einer fundierten Ausbildung in Informatik und Systemtechnik, ergänzt durch praktische Erfahrung in der Industrie und der Schweizer Armee. Seine technischen Stärken liegen in der Systemarchitektur, der Datenverwaltung (SQL Server) sowie der modernen Softwareentwicklung mit Tools wie Docker und Gitlab. Durch sein aktuelles Studium an der FHNW kombiniert er diese praktischen Fähigkeiten mit theoretischem Wissen im Bereich der Informatik. Diese Kombination aus industrieller Erfahrung und akademischer Tiefe macht ihn zu einem wertvollen Partner für Innovationen im digitalen Bereich. Eine sinnvolle Zusammenarbeit könnte insbesondere mit Forschungsinstituten im Bereich der vernetzten Systeme oder Technologie-Start-ups im Raum Baden oder der Deutschschweiz entstehen.",
    },
    {
        name: "Andrei Oleniuc",
        github: "https://github.com/AndreiRobertoOleniuc",
        website: "https://andreioleniuc.com",
        description:
            "Andrei Oleniuc ist ein erfahrener Full Stack Engineer mit einem starken Hintergrund in Softwarearchitektur und Produktmanagement, der sich auf die Entwicklung skalierbarer digitaler Lösungen spezialisiert hat. Seine Expertise liegt in der Gestaltung und Umsetzung komplexer Anwendungen, unterstützt durch tiefgehende Kenntnisse in Java, React, Python sowie modernen DevOps-Praktiken wie GCP/GKE und CI/CD. Er hat nachweislich die Fähigkeit, technische Herausforderungen in produktive Lösungen umzuwandeln, was durch seine Arbeit an Forschungsplattformen und SaaS-Produkten unterstrichen wird. Diese Kombination aus technischer Tiefe und Produktverständnis macht ihn zu einem wertvollen Partner für Innovationsprojekte im Schweizer Raum. Eine sinnvolle Zusammenarbeit wäre besonders mit Forschungsinstituten wie der PHZH oder spezialisierten Technologieunternehmen im Baden-Ecosystem denkbar.",
    },
    {
        name: "Nicolas Yates",
        github: "https://github.com/nicolasstevenyates",
        website: "https://www.nicolasyates.com/",
        description:
            "Nicolas Yates ist ein erfahrener Software Engineer mit Schwerpunkt auf Full-Stack-Entwicklung und Netzwerkautomatisierung, erworben in einem grossen Telekommunikationsumfeld wie Swisscom. Seine technischen Stärken liegen in der Beherrschung moderner Sprachen wie JavaScript, Java und Angular sowie in DevOps-Praktiken mittels Docker und GitLab CI. Er verfügt über fundierte Erfahrung in der Entwicklung von Software, die kritische Infrastrukturen verwaltet und Sicherheitsanalysen durchführt. Seine Expertise erstreckt sich von der Automatisierung komplexer Netzwerkverbindungen bis hin zur Anomalieerkennung und Dashboard-Entwicklung mit Tools wie Splunk. Für eine sinnvolle Zusammenarbeit eignen sich Forschungsgruppen im Bereich KI-gestützte Netzwerksicherheit oder Organisationen, die sich mit der Skalierung von Cloud-nativen Infrastrukturen in der Schweizer Innovationslandschaft beschäftigen.",
    },
    {
        name: "Marvin von Rappard",
        github: "https://github.com/marvinvr",
        website: "https://marvinvr.ch",
        description:
            "Marvin von Rappard ist ein Full-Stack-Entwickler und Data Scientist mit einer tiefen Expertise in der Entwicklung von Machine-Learning-Plattformen und der Anwendung von Large Language Models (LLMs) im industriellen Kontext. Seine technischen Stärken liegen in der Kombination von Python, Go und TypeScript, ergänzt durch fundierte Kenntnisse in MLOps, Containerisierung (Docker/Kubernetes) und der Implementierung von RAG-Systemen. Er verfügt über Erfahrung in der Entwicklung von Enterprise-Software sowie in der akademischen Forschung im Bereich KI, was eine Brücke zwischen theoretischer Forschung und praktischer industrieller Anwendung schlägt. Für eine Zusammenarbeit wären Forschungsgruppen oder Organisationen im Bereich angewandter KI, Data Science oder die akademischen Einrichtungen wie die FHNW besonders sinnvoll. Er könnte ideal mit Forschungsinstituten oder Start-ups in der Region Baden zusammenarbeiten, die an der Operationalisierung von KI-Lösungen in der Industrie interessiert sind.",
    },
];

// e5 models need "passage: " prefix for documents being stored
async function getEmbedding(text) {
    const res = await fetch("http://localhost:11434/api/embeddings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            model: "jeffh/intfloat-multilingual-e5-large:f16",
            prompt: `passage: ${text}`,
        }),
    });

    if (!res.ok) {
        throw new Error(`Ollama embedding failed: ${res.status} ${res.statusText}`);
    }

    const data = await res.json();
    return data.embedding; // Ollama returns { embedding: float[] }
}

async function main() {
    await client.connect();
    console.log("Connected to PostgreSQL\n");

    for (const person of people) {
        console.log(`Embedding: ${person.name}...`);

        const vector = await getEmbedding(person.description);
        console.log(`  Vector dimensions: ${vector.length}`);

        await client.query(
            `INSERT INTO persons (name, github, website, description, embedding)
       VALUES ($1, $2, $3, $4, $5::vector)`,
            [
                person.name,
                person.github,
                person.website,
                person.description,
                JSON.stringify(vector),
            ]
        );

        console.log(`  ✓ Inserted\n`);
    }

    console.log("All 4 people seeded. Run 03_search.js to test matching.");
    await client.end();
}

main().catch(console.error);