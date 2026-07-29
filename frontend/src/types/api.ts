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
  onboardingDraft?: boolean
  profileMode?: ProfileMode
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

export interface ApiErrorDto {
  message: string
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
  /** Source curation: when false, this repo/work is excluded from search. */
  active: boolean
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

/** A resolved Person→Organisation link (§10) — present only when linked. */
export interface OrgLinkDto {
  entityId: number
  name: string
  hrVerified: boolean
}

/** Which mode the profile is presented in — set by the onboarding fork. */
export type ProfileMode = "supply" | "demand"
export type EntityStatus = "draft" | "active" | "inactive"

export interface EntityDto {
  id: number
  entityType: EntityType
  displayName: string
  headline: string | null
  orgName: string | null
  summary: string | null
  status: EntityStatus
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
  /** The linked organization (§10) — null unless a Person↔Org link exists. */
  orgLink: OrgLinkDto | null
  /** Whether a user owns this profile — unclaimed profiles can't be contacted. */
  claimed: boolean
}

// ─── Search (discovery) ───

/** Scope selection — the Hub/Region hard filter (reference §5). */
export type ScopeKind = "all" | "hub" | "region"

export interface ScopeSelection {
  kind: ScopeKind
  hubId?: number | null
  regionId?: number | null
}

/** POST /search body. Only `query` is required; the rest are the UI filters. */
export interface SearchRequestBody {
  query: string
  matchCount?: number
  scope?: ScopeSelection
  wantFrom?: string | null
  wantTo?: string | null
  wantPensumPct?: number | null
}

/** A validated citation chip on a result card (reference §6.5). */
export interface SearchCitationDto {
  sourceId: number
  label: string | null
  sourceType: string | null
  sourceUrl: string | null
}

/** One search result — the full entity plus the search-specific signals. */
export interface SearchResultDto {
  entity: EntityDto
  score: number
  rationale: string | null
  citations: SearchCitationDto[]
  distance: string | null
}

export interface SearchResponseDto {
  query: string
  sufficient: boolean
  results: SearchResultDto[]
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

// ─── Requests (Anfrage lifecycle, reference §8) ───

/** Lifecycle: open → distributed → contacted → closed. */
export type RequestStatus = "open" | "distributed" | "contacted" | "closed"

/** Drives the distribution/escalation strategy (reference §8.3). */
export type RequestUrgency = "flexible" | "soon" | "urgent"

/** Compact request summary — board lists and "Meine Anfragen". */
export interface RequestSummaryDto {
  id: number
  title: string | null
  description: string | null
  queryText: string
  scopeKind: ScopeKind
  scopeHubName: string | null
  scopeRegionName: string | null
  urgency: RequestUrgency
  deadline: string | null
  status: RequestStatus
  wantFrom: string | null
  wantTo: string | null
  wantPensumPct: number | null
  tags: TagRefDto[]
  authorEntityId: number | null
  authorName: string | null
  authorOrgName: string | null
  authorType: EntityType | null
  createdAt: string | null
}

/** Why a request was distributed to a profile (§8.2). */
export type DistributionReason = "profile_match" | "subscription"

/** One profile a request was distributed to — owner-only (§8.2). */
export interface DistributedProfileDto {
  entityId: number
  name: string | null
  orgName: string | null
  entityType: EntityType | null
  reason: DistributionReason
  matchedTags: string[] | null
  notifiedAt: string | null
}

/** Full request — detail view. `mine` gates the owner lifecycle controls. */
export interface RequestDto {
  id: number
  title: string | null
  description: string | null
  queryText: string
  scopeKind: ScopeKind
  scopeHubId: number | null
  scopeHubName: string | null
  scopeRegionId: number | null
  scopeRegionName: string | null
  urgency: RequestUrgency
  deadline: string | null
  status: RequestStatus
  wantFrom: string | null
  wantTo: string | null
  wantPensumPct: number | null
  tags: TagRefDto[]
  authorEntityId: number | null
  authorName: string | null
  authorOrgName: string | null
  authorType: EntityType | null
  mine: boolean
  /** Owner-only: "X Profile benachrichtigt" — null on foreign requests. */
  distributionCount: number | null
  /** Owner-only: effective escalation scope kind — null on foreign requests. */
  wideningLevel: ScopeKind | null
  /** Owner-only: the profiles this request was distributed to — null on foreign requests. */
  distributions: DistributedProfileDto[] | null
  createdAt: string | null
}

/** POST /requests body. Only `queryText` is required. */
export interface CreateRequestBody {
  title?: string | null
  description?: string | null
  queryText: string
  scope?: ScopeSelection
  urgency?: RequestUrgency
  deadline?: string | null
  wantFrom?: string | null
  wantTo?: string | null
  wantPensumPct?: number | null
  tags?: string[]
}

// ─── Contact handoff + Postfach (reference §8.2, §11) ───

/** Gating check id that can block a contact send (§11.1). */
export type ContactGatingCheck = "entity" | "email" | "source"

/** GET /contacts/gating — evaluated before the dialog sends. */
export interface ContactGatingDto {
  allowed: boolean
  missing: ContactGatingCheck[]
}

/** POST /contacts body. */
export interface CreateContactRequestBody {
  toEntityId: number
  requestId?: number | null
  message: string
  searchedText?: string | null
}

/** POST /contacts response — the brokered handoff. */
export interface ContactRequestDto {
  id: number
  fromEntityId: number
  toEntityId: number
  requestId: number | null
  message: string
  createdAt: string | null
}

/** The recipient's one-time handshake outcome (§11). */
export type ContactStatus = "pending" | "accepted" | "declined"

/** POST /contacts/{id}/respond body — accept (with message) or decline. */
export interface RespondContactBody {
  accept: boolean
  message?: string
}

/** Frozen sender snapshot carried by a contact request (§11.2). */
export interface SenderContextDto {
  name: string | null
  role: string | null
  org: string | null
  description: string | null
  searchedText: string | null
}

/** One incoming contact request in the Postfach "Erhalten" section.
 *  `fromEmail` is revealed only once `status === "accepted"` (mutual opt-in). */
export interface InboxContactDto {
  id: number
  fromEntityId: number
  fromName: string | null
  fromOrgName: string | null
  fromEmail: string | null
  senderContext: SenderContextDto | null
  message: string
  requestId: number | null
  requestTitle: string | null
  status: ContactStatus
  replyMessage: string | null
  respondedAt: string | null
  createdAt: string | null
}

/** One outgoing contact request in the Postfach "Gesendet" section.
 *  `toEmail` and `replyMessage` appear only once `status === "accepted"`. */
export interface InboxSentContactDto {
  id: number
  toEntityId: number
  toName: string | null
  toOrgName: string | null
  toEmail: string | null
  message: string
  status: ContactStatus
  replyMessage: string | null
  requestId: number | null
  requestTitle: string | null
  respondedAt: string | null
  createdAt: string | null
}

/** One distributed request in the Postfach — "Verteilt, weil …". */
export interface InboxDistributionDto {
  requestId: number
  title: string | null
  queryText: string
  authorEntityId: number | null
  authorName: string | null
  authorOrgName: string | null
  reason: DistributionReason
  matchedTags: string[] | null
  wideningLevel: ScopeKind
  requestStatus: RequestStatus
  notifiedAt: string | null
}

/** GET /inbox — the Postfach payload (received + sent + distributions). */
export interface InboxDto {
  received: InboxContactDto[]
  sent: InboxSentContactDto[]
  distributions: InboxDistributionDto[]
}
