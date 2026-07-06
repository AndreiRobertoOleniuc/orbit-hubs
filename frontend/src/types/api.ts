/**
 * API DTOs matching currently implemented backend endpoints only.
 * Types for future endpoints stay out until the backend contract is real.
 * IDs are human-readable string slugs; they are typed as string, never UUID.
 */

export interface MeDto {
  userId: string
  email: string
}

// ─── Ingestion ───

export interface IngestionRequestDto {
  sourceType: string
  context: Record<string, string>
}

export type IngestionResultStatus = "INGESTED" | "SKIPPED" | "ERROR"

export interface IngestionResultDto {
  entityId: number | null
  sourceId: number | null
  chunkCount: number
  status: IngestionResultStatus
}

export interface IngestionResponseDto {
  sourceType: string
  sources: number
  ingested: number
  skipped: number
  errors: number
  totalChunks: number
  results: IngestionResultDto[]
  error?: string
}

// ─── Entities (profile read models) ───

export type EntityType = "person" | "organization"

export interface EntitySourceDto {
  id: number
  sourceType: string
  label: string | null
  sourceUrl: string | null
  fetchStatus: string | null
  fetchedAt: string | null
}

export type TagOrigin = "derived" | "manual"

export interface EntityTagDto {
  id: number
  slug: string
  label: string
  origin: TagOrigin
}

export interface ResyncResponseDto {
  entity: EntityDto
  ingested: number
  skipped: number
  errors: number
}

export interface EntityDto {
  id: number
  entityType: EntityType
  displayName: string
  headline: string | null
  orgName: string | null
  summary: string | null
  status: string
  metadata: Record<string, unknown> | null
  createdAt: string | null
  sources: EntitySourceDto[]
  tags: EntityTagDto[]
}

// ─── Zefix (Handelsregister autocomplete) ───

export interface ZefixCompanyDto {
  name: string
  uid: string
  legalSeat: string
  canton: string
  legalForm: string
  status: string
}
