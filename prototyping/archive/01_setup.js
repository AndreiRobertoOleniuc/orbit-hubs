// 01_setup.js — Run this first to create the table
import pkg from "pg";
const { Client } = pkg;

const client = new Client({
    host: "localhost",
    port: 5432,
    database: "badenconnect",
    user: "root",
    password: process.env.DB_PASSWORD,
});

async function main() {
    await client.connect();
    console.log("Connected to PostgreSQL\n");

    await client.query(`CREATE EXTENSION IF NOT EXISTS vector`);
    console.log("✓ pgvector extension ready");

    await client.query(`DROP TABLE IF EXISTS persons`);

    await client.query(`
    CREATE TABLE persons (
      id        SERIAL PRIMARY KEY,
      name      TEXT NOT NULL,
      github    TEXT,
      website   TEXT,
      description TEXT NOT NULL,
      embedding vector(1024),
      created_at TIMESTAMP DEFAULT now()
    )
  `);
    console.log("✓ persons table created");

    await client.query(`
    CREATE INDEX persons_embedding_idx
      ON persons USING hnsw (embedding vector_cosine_ops)
  `);
    console.log("✓ HNSW index created");

    console.log("\nSetup complete. Run 02_seed.js next.");
    await client.end();
}

main().catch(console.error);