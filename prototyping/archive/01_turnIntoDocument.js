import fs from "node:fs";
import path from "node:path";

const INPUT = path.resolve("raw_profiles_300.json");
const OUTPUT = path.resolve("profile_documents.jsonl");
const MODEL = "batiai/gemma4-e2b:q4";
const OLLAMA_URL = "http://localhost:11434/api/generate";

const SYSTEM_INSTRUCTION =
  "Du erstellst ein semantisches Matchmaking-Profil für eine Innovationsplattform in der Schweiz (Baden-Ecosystem). " +
  "Erstelle einen Fliesstext (max. 5 Sätze) der beschreibt: " +
  "1. Was diese Person tut und welche Expertise sie hat " +
  "2. Welche technischen/fachlichen Stärken erkennbar sind " +
  "3. Mit welchen Forschenden oder Organisationen eine Zusammenarbeit sinnvoll wäre. " +
  "Nur Fliesstext. Keine Aufzählungen. Deutsch.";

function formatGithub(repos) {
  if (!repos || repos.length === 0) return "keine";
  return repos
    .map((r) => {
      const parts = [r.name];
      if (r.language) parts.push(r.language);
      if (r.description) parts.push(r.description);
      if (r.topics && r.topics.length) parts.push(`Topics: ${r.topics.join(", ")}`);
      return parts.join(" — ");
    })
    .join(" | ");
}

function buildPrompt(profile) {
  const github = formatGithub(profile.github);
  const website = profile.website ? profile.website : "keine";
  const linkedin = profile.linkedin ? profile.linkedin : "keine";

  return (
    SYSTEM_INSTRUCTION +
    `\n\nName: ${profile.name}` +
    `\n\nGitHub: ${github}` +
    `\n\nWebsite: ${website}` +
    `\n\nLinkedIn: ${linkedin}`
  );
}

async function generate(prompt) {
  const res = await fetch(OLLAMA_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model: MODEL,
      stream: false,
      keep_alive: 100,
      prompt,
    }),
  });
  if (!res.ok) {
    throw new Error(`Ollama HTTP ${res.status}: ${await res.text()}`);
  }
  const data = await res.json();
  return data.response;
}

function loadDone() {
  if (!fs.existsSync(OUTPUT)) return new Set();
  const done = new Set();
  const lines = fs.readFileSync(OUTPUT, "utf8").split("\n").filter(Boolean);
  for (const line of lines) {
    try {
      const obj = JSON.parse(line);
      if (obj.name) done.add(obj.name);
    } catch {}
  }
  return done;
}

async function main() {
  const profiles = JSON.parse(fs.readFileSync(INPUT, "utf8"));
  const done = loadDone();
  const stream = fs.createWriteStream(OUTPUT, { flags: "a" });

  console.log(`Loaded ${profiles.length} profiles. Already done: ${done.size}.`);

  for (let i = 0; i < profiles.length; i++) {
    const profile = profiles[i];
    if (done.has(profile.name)) {
      console.log(`[${i + 1}/${profiles.length}] skip ${profile.name}`);
      continue;
    }
    const prompt = buildPrompt(profile);
    const start = Date.now();
    try {
      const document = await generate(prompt);
      const record = { name: profile.name, document: document.trim() };
      stream.write(JSON.stringify(record) + "\n");
      const dt = ((Date.now() - start) / 1000).toFixed(1);
      console.log(`[${i + 1}/${profiles.length}] ok ${profile.name} (${dt}s)`);
    } catch (err) {
      console.error(`[${i + 1}/${profiles.length}] FAIL ${profile.name}:`, err.message);
    }
  }

  stream.end();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
