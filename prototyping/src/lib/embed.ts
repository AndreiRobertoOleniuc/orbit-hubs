const OLLAMA_URL =
  process.env.OLLAMA_URL ?? "http://localhost:11434/api/embeddings";
const MODEL = "jeffh/intfloat-multilingual-e5-large:f16";

const PASSAGE_FALLBACK_CHARS = [1500, 1000, 600];

async function callOllama(prompt: string): Promise<number[]> {
  const res = await fetch(OLLAMA_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model: MODEL, prompt }),
  });
  if (!res.ok) {
    const text = await res.text();
    const err = new Error(`Ollama ${res.status}: ${text}`);
    (err as any).status = res.status;
    (err as any).body = text;
    throw err;
  }
  const data = (await res.json()) as { embedding: number[] };
  return data.embedding;
}

function looksLikeTokenOverflow(err: any): boolean {
  const body = String(err?.body ?? err?.message ?? "");
  return /token|length|context|too long|exceed/i.test(body);
}

export async function embedText(
  text: string,
  role: "passage" | "query",
): Promise<number[]> {
  const clean = text.replace(/\s+/g, " ").trim();
  const prompt = (raw: string) => `${role}: ${raw}`;

  if (role === "query") {
    return callOllama(prompt(clean));
  }

  // passage: progressive truncation on token-overflow style failures
  let lastErr: any = null;
  for (const limit of PASSAGE_FALLBACK_CHARS) {
    const slice = clean.length > limit ? clean.slice(0, limit) : clean;
    try {
      return await callOllama(prompt(slice));
    } catch (err) {
      lastErr = err;
      if (!looksLikeTokenOverflow(err)) throw err;
    }
  }
  throw lastErr;
}
