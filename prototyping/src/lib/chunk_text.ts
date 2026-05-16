export interface ChunkTextOptions {
  targetWords?: number;
  overlapWords?: number;
}

export interface TextChunk {
  content: string;
  position: number;
}

const DEFAULT_TARGET = 350;
const DEFAULT_OVERLAP = 50;

function tokenize(text: string): string[] {
  return text.split(/\s+/).filter(Boolean);
}

export function chunkText(
  text: string,
  opts: ChunkTextOptions = {},
): TextChunk[] {
  const target = opts.targetWords ?? DEFAULT_TARGET;
  const overlap = Math.min(opts.overlapWords ?? DEFAULT_OVERLAP, target - 1);

  const cleaned = text.replace(/\r\n/g, "\n").trim();
  if (!cleaned) return [];

  // Pre-split on paragraph boundaries so we can prefer them as cut points.
  const paragraphs = cleaned
    .split(/\n{2,}/)
    .map((p) => p.trim())
    .filter(Boolean);

  // Build a flat list of [word, paragraphIndex] so we can detect paragraph
  // boundaries when sliding.
  const flat: { word: string; pEnd: boolean }[] = [];
  paragraphs.forEach((p, pi) => {
    const words = tokenize(p);
    words.forEach((w, wi) => {
      flat.push({ word: w, pEnd: wi === words.length - 1 });
    });
  });

  if (flat.length === 0) return [];

  const chunks: TextChunk[] = [];
  let start = 0;
  let position = 0;
  while (start < flat.length) {
    let end = Math.min(start + target, flat.length);

    // If we're not at the very end of the document, try to back up to a
    // paragraph boundary within the last ~25% of the window.
    if (end < flat.length) {
      const minEnd = start + Math.floor(target * 0.75);
      for (let i = end - 1; i >= minEnd; i--) {
        if (flat[i]!.pEnd) {
          end = i + 1;
          break;
        }
      }
    }

    const slice = flat.slice(start, end).map((f) => f.word).join(" ");
    if (slice.trim().length > 0) {
      chunks.push({ content: slice, position });
      position++;
    }

    if (end >= flat.length) break;
    start = Math.max(end - overlap, start + 1);
  }

  return chunks;
}
