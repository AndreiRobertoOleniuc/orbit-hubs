/**
 * API DTOs mirroring the §4 REST contract in docs/02_data_model_and_api.md.
 * IDs are human-readable string slugs; they are typed as string, never UUID.
 */

export type EntityId = string
export type RequestId = string
export type DocumentId = string
export type UserId = string

export type Scope = "all" | "fhnw" | "aargau" | "connected"
export type PresenceMode = "onsite" | "hybrid" | "remote"
export type EntityType = "person" | "company"
export type OriginatorType = "person" | "company"
export type RequestStatus = "OPEN" | "DISTRIBUTED" | "CONTACTED" | "CLOSED"
export type SourceType =
  | "github_repo"
  | "website_page"
  | "linkedin_cv"
  | "publication"
  | "github_bio"
  | "orcid_work"
export type CollaborationMode =
  | "bachelor_thesis"
  | "master_thesis"
  | "innosuisse"
  | "paid_mandate"
  | "research_collab"

export interface Citation {
  documentId: DocumentId
  label: string
  url: string
}

export interface SearchRequest {
  query: string
  scope: Scope
  presenceMode?: PresenceMode
}

export interface SearchResult {
  entityId: EntityId
  type: EntityType
  name: string
  headline: string
  scope: Scope
  distanceLabel: string
  verified: boolean
  isOpen: boolean
  rationaleAvailable: boolean
  citations: Citation[]
}

export interface SearchResponse {
  results: SearchResult[]
}

export interface RationaleResponse {
  rationale: string
  citations: Citation[]
}

export interface RequestDto {
  id: RequestId
  title: string
  description: string
  ownerEntityId: EntityId
  originatorType: OriginatorType
  scope: Scope
  presenceMode: PresenceMode
  status: RequestStatus
  validUntil?: string
  createdAt: string
  updatedAt: string
}

export interface CreateRequestDto {
  title: string
  description: string
  scope: Scope
  presenceMode: PresenceMode
  tags: string[]
  originatorType: OriginatorType
}

export interface PatchRequestStatusDto {
  to: Exclude<RequestStatus, "OPEN">
}

export interface DistributionDto {
  id: number
  requestId: RequestId
  entityId: EntityId
  reason: string
  matchedTags: string[]
  createdAt: string
}

export interface DocumentDto {
  id: DocumentId
  entityId: EntityId
  sourceType: SourceType
  sourceUrl?: string
  sourceLabel?: string
  meta?: Record<string, unknown>
  fetchedAt?: string
}

export interface Availability {
  isOpen: boolean
  availableFrom?: string
  capacityNote?: string
  preferredModes: CollaborationMode[]
}

export interface EntityDto {
  id: EntityId
  type: EntityType
  name: string
  headline?: string
  summary?: string
  location?: string
  locationLat?: number
  locationLng?: number
  scope: Scope
  presenceMode: PresenceMode
  verified: boolean
  handelsregisterUid?: string
  ownerUserId?: UserId
  createdAt: string
  updatedAt: string
}

export interface ProfileDto extends EntityDto {
  documents: DocumentDto[]
  tags: string[]
  availability?: Availability
}

export interface PatchEntityDto {
  tags?: string[]
  summary?: string
}

export interface BoardListParams {
  tag?: string
  scope?: Scope
  sort?: "newest" | "relevant"
  page?: number
}

export interface BoardResponse {
  items: RequestDto[]
  page: number
  totalPages: number
  totalItems: number
}

export interface ContactRequest {
  toEntityId: EntityId
  requestId?: RequestId
  message: string
}

export interface SubscriptionRequest {
  tagSlug: string
}

export interface OnboardingPersonDto {
  sources: Array<{
    type: SourceType
    ref: string
  }>
}

export interface OnboardingOrganisationDto {
  handelsregisterUid?: string
  name?: string
}

export interface OnboardingStatusDto {
  entityId: EntityId
  total: number
  synced: number
  status: "idle" | "running" | "done" | "error"
}

export interface ApiErrorBody {
  message?: string
}
