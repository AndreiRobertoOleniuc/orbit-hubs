import { Pool } from "pg";

const url =
  process.env.DATABASE_URL ??
  "postgres://root:123456789@localhost:5432/badenconnect";

export const pool = new Pool({ connectionString: url, max: 8 });

export function toVectorLiteral(vec: number[]): string {
  return `[${vec.join(",")}]`;
}
