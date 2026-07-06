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