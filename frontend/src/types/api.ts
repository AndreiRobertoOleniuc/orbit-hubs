/**
 * API DTOs matching currently implemented backend endpoints only.
 * Types for future endpoints stay out until the backend contract is real.
 * IDs are human-readable string slugs; they are typed as string, never UUID.
 */

export interface MeDto {
  userId: string
  email: string
  isAdmin: boolean
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

/** Affiliation origin — Firma / Selbständig / Privatperson. */
export type AffiliationKind = "company" | "self_employed" | "private"

/** Structured availability — drives the availability pill + re-ranking. */
export interface AvailabilityDto {
  openToRequests: boolean
  availableFrom: string | null
  availableTo: string | null
  pensumPct: number | null
  note: string | null
  preferredCollaboration: string[] | null
}

/** A region node in the Hub → Region → CH tree. */
export interface RegionDto {
  id: number
  slug: string
  name: string
  parentRegionId: number | null
}

/** A hub (affiliation) with its owning region name resolved. */
export interface HubDto {
  id: number
  slug: string
  name: string
  hubType: string | null
  regionId: number
  regionName: string | null
}

/** A subscribed tag ("Abonniertes Thema"). */
export interface SubscriptionDto {
  id: number
  slug: string
  label: string
}

/** A tag suggestion from the controlled vocabulary (autocomplete). */
export interface TagRefDto {
  id: number
  slug: string
  label: string
}

/** Which mode the profile is presented in — set by the onboarding fork. */
export type ProfileMode = "supply" | "demand"

export interface EntityDto {
  id: number
  entityType: EntityType
  displayName: string
  headline: string | null
  orgName: string | null
  summary: string | null
  status: string
  profileMode: ProfileMode
  metadata: Record<string, unknown> | null
  createdAt: string | null
  sources: EntitySourceDto[]
  tags: EntityTagDto[]
  availability: AvailabilityDto | null
  affiliationKind: AffiliationKind | null
  languages: string[] | null
  locationLabel: string | null
  hubs: HubDto[]
  homeRegion: RegionDto | null
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
