import { pool, toVectorLiteral } from "./db.js";
import { embedText } from "./embed.js";

export type EntityType = "company" | "person" | "publication";
export type ChunkSource =
  | "lindas"
  | "wikidata"
  | "github"
  | "linkedin"
  | "website"
  | "orcid"
  | "openalex"
  | "irf_fhnw";

export interface ChunkInput {
  kind: string;
  label: string;
  content: string;
  sourceUrl?: string | null;
  metadata?: Record<string, unknown>;
}

const EMBED_CONCURRENCY = 6;

async function embedAll(texts: string[]): Promise<number[][]> {
  const out: number[][] = new Array(texts.length);
  let cursor = 0;
  const workers: Promise<void>[] = [];
  const next = async (): Promise<void> => {
    while (true) {
      const i = cursor++;
      if (i >= texts.length) return;
      out[i] = await embedText(texts[i]!, "passage");
    }
  };
  for (let w = 0; w < Math.min(EMBED_CONCURRENCY, texts.length); w++) {
    workers.push(next());
  }
  await Promise.all(workers);
  return out;
}

export async function upsertChunks(
  entityType: EntityType,
  entityId: string,
  source: ChunkSource,
  chunks: ChunkInput[],
): Promise<number> {
  if (chunks.length === 0) {
    const client = await pool.connect();
    try {
      await client.query(
        `DELETE FROM chunks
          WHERE entity_type = $1 AND entity_id = $2 AND source = $3`,
        [entityType, entityId, source],
      );
    } finally {
      client.release();
    }
    return 0;
  }

  const embeddings = await embedAll(chunks.map((c) => c.content));

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query(
      `DELETE FROM chunks
        WHERE entity_type = $1 AND entity_id = $2 AND source = $3`,
      [entityType, entityId, source],
    );

    const cols = [
      "entity_type",
      "entity_id",
      "source",
      "chunk_kind",
      "chunk_label",
      "content",
      "embedding",
      "source_url",
      "metadata",
    ];
    const placeholders: string[] = [];
    const values: any[] = [];
    chunks.forEach((c, i) => {
      const base = i * cols.length;
      placeholders.push(`(${cols.map((_, j) => `$${base + j + 1}`).join(",")})`);
      values.push(
        entityType,
        entityId,
        source,
        c.kind,
        c.label,
        c.content,
        toVectorLiteral(embeddings[i]!),
        c.sourceUrl ?? null,
        JSON.stringify(c.metadata ?? {}),
      );
    });
    await client.query(
      `INSERT INTO chunks (${cols.join(",")}) VALUES ${placeholders.join(",")}`,
      values,
    );
    await client.query("COMMIT");
    return chunks.length;
  } catch (err) {
    await client.query("ROLLBACK").catch(() => {});
    throw err;
  } finally {
    client.release();
  }
}
