# Orbit handover runbook

This folder contains only the runnable Orbit application and its operational configuration.

## What stays here

- `backend/` - Spring Boot API, Flyway migrations and PostgreSQL compose file.
- `frontend/` - React/Vite web application.
- `reranker/` - optional local reranker service; hosted Hugging Face inference is the default.
- `k8s/` - deployment manifest and deployment script.
- `.llm-wiki/` - project knowledge base used during development.

Historical prototypes are in `../Prototyping/`; data collections are in `../Data/`; technical design and operational evidence are in `../TechnicalImplementationDocs/`.

## Prerequisites

- Java 21
- Node.js 22 or newer
- Docker with Docker Compose
- A Clerk application key for the frontend
- `HF_TOKEN` and `GEMINI_API_KEY` for fully featured hosted search; search still returns evidence cards when the grounding model is unavailable.

## Local start

1. Copy `frontend/.env.example` to `frontend/.env` and add the Clerk publishable key. Supply backend credentials (`HF_TOKEN` and `GEMINI_API_KEY`) through the terminal or your IDE environment. Do not commit credentials.
2. Start PostgreSQL:

   ```bash
   docker compose -f backend/compose.yaml up -d
   ```

3. In a first terminal, run the backend:

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

4. In a second terminal, run the frontend:

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

The application is then available at `http://localhost:5173`; the API runs at `http://localhost:8080`.

## Verification before handover

```bash
cd backend && ./mvnw test
cd frontend && npm ci && npm run typecheck && npm run test && npm run build
```

Consult `../TechnicalImplementationDocs/` for the architecture, search regression queries, ingestion notes, UX audit and deployment validation. `AGENTS.md` contains repository conventions and the complete command reference.
