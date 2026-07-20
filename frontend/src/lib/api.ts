import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react"
import type { BaseQueryApi, FetchArgs } from "@reduxjs/toolkit/query/react"

import { getFreshToken } from "@/lib/clerkToken"
import type {
  AffiliationKind,
  ContactGatingDto,
  ContactRequestDto,
  CreateContactRequestBody,
  CreateRequestBody,
  RespondContactBody,
  EntityDto,
  HubDto,
  InboxDto,
  IngestionRequestDto,
  IngestionResponseDto,
  MeDto,
  ProfileMode,
  RegionDto,
  RequestDto,
  RequestStatus,
  RequestSummaryDto,
  ResyncResponseDto,
  SearchRequestBody,
  SearchResponseDto,
  SubscriptionDto,
  TagRefDto,
  ZefixCompanyDto,
} from "@/types/api"

/** Body for PUT /entities/{id}/availability. */
export interface UpdateAvailabilityBody {
  openToRequests: boolean
  availableFrom: string | null
  availableTo: string | null
  pensumPct: number | null
  note: string | null
  preferredCollaboration: string[]
}

/** Body for PUT /entities/{id}/profile-meta. */
export interface UpdateProfileMetaBody {
  displayName: string
  headline: string | null
  orgName: string | null
  affiliationKind: AffiliationKind | null
  languages: string[]
  locationLabel: string | null
}

/** Body for admin hub create/edit. */
export interface HubMutationBody {
  name: string
  slug?: string
  hubType?: string | null
  regionId: number
}
const baseUrl = `${import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"}/api/v1`

const rawBaseQuery = fetchBaseQuery({
  baseUrl,
  prepareHeaders: async (headers) => {
    // Fetch a fresh token per request; Clerk refreshes it transparently when
    // near expiry, so an idle tab no longer sends an expired snapshot.
    const token = await getFreshToken()
    if (token) {
      headers.set("Authorization", `Bearer ${token}`)
    }
    return headers
  },
})

/**
 * Wraps the base query so a 401 (token expired in-flight or at the expiry
 * boundary) forces Clerk to mint a new token and retries the request once,
 * instead of surfacing the error to the UI.
 */
const baseQuery = async (
  args: string | FetchArgs,
  api: BaseQueryApi,
  extraOptions: object
) => {
  let result = await rawBaseQuery(args, api, extraOptions)
  if (result.error?.status === 401) {
    await getFreshToken({ skipCache: true })
    result = await rawBaseQuery(args, api, extraOptions)
  }
  return result
}

export const apiSlice = createApi({
  reducerPath: "api",
  baseQuery,
  refetchOnMountOrArgChange: true,
  tagTypes: [
    "MyEntities",
    "Entity",
    "Hubs",
    "Subscriptions",
    "Board",
    "MyRequests",
    "Request",
    "Inbox",
    "ContactedEntities",
  ],
  endpoints: (builder) => ({
    getMe: builder.query<MeDto, void>({
      query: () => "/me",
    }),

    // ─── Entities ───

    getEntity: builder.query<EntityDto, number | string>({
      query: (id) => `/entities/${id}`,
      providesTags: (_result, _error, id) => [{ type: "Entity", id }],
    }),

    getMyEntities: builder.query<EntityDto[], void>({
      query: () => "/me/entities",
      providesTags: ["MyEntities"],
    }),

    deleteEntity: builder.mutation<void, number>({
      query: (entityId) => ({
        url: `/entities/${entityId}`,
        method: "DELETE",
      }),
      invalidatesTags: (_result, _error, entityId) => [
        { type: "Entity", id: entityId },
        "MyEntities",
        "Inbox",
        "ContactedEntities",
      ],
    }),

    // ─── Discovery search ───

    search: builder.mutation<SearchResponseDto, SearchRequestBody>({
      query: (body) => ({ url: "/search", method: "POST", body }),
    }),

    // ─── Zefix (Handelsregister autocomplete) ───

    searchZefix: builder.query<ZefixCompanyDto[], string>({
      query: (name) => `/zefix/search?name=${encodeURIComponent(name)}`,
    }),

    // ─── Ingestion ───

    ingestSource: builder.mutation<IngestionResponseDto, IngestionRequestDto>({
      query: (body) => ({
        url: "/ingestion",
        method: "POST",
        body,
      }),
      invalidatesTags: ["MyEntities"],
    }),

    uploadLinkedInPdf: builder.mutation<IngestionResponseDto, File>({
      query: (file) => {
        const formData = new FormData()
        formData.append("file", file)
        return {
          url: "/ingestion/linkedin-pdf",
          method: "POST",
          body: formData,
        }
      },
      invalidatesTags: ["MyEntities"],
    }),

    // ─── Entity tag & summary editing ───

    updateEntityTags: builder.mutation<
      EntityDto,
      { entityId: number; tags: string[] }
    >({
      query: ({ entityId, tags }) => ({
        url: `/entities/${entityId}/tags`,
        method: "PUT",
        body: { tags },
      }),
      invalidatesTags: (_result, _error, { entityId }) => [
        { type: "Entity", id: entityId },
        "MyEntities",
      ],
    }),

    updateEntitySummary: builder.mutation<
      EntityDto,
      { entityId: number; summary: string }
    >({
      query: ({ entityId, summary }) => ({
        url: `/entities/${entityId}/summary`,
        method: "PUT",
        body: { summary },
      }),
      invalidatesTags: (_result, _error, { entityId }) => [
        { type: "Entity", id: entityId },
        "MyEntities",
      ],
    }),

    // ─── Entity resync (re-ingest all sources + re-enrich) ───

    resyncEntity: builder.mutation<ResyncResponseDto, number>({
      query: (entityId) => ({
        url: `/entities/${entityId}/resync`,
        method: "POST",
      }),
      invalidatesTags: (_result, _error, entityId) => [
        { type: "Entity", id: entityId },
        "MyEntities",
      ],
    }),

    // ─── Supply-side profile editing ───

    updateAvailability: builder.mutation<
      EntityDto,
      { entityId: number; body: UpdateAvailabilityBody }
    >({
      query: ({ entityId, body }) => ({
        url: `/entities/${entityId}/availability`,
        method: "PUT",
        body,
      }),
      invalidatesTags: (_result, _error, { entityId }) => [
        { type: "Entity", id: entityId },
        "MyEntities",
      ],
    }),

    updateProfileMeta: builder.mutation<
      EntityDto,
      { entityId: number; body: UpdateProfileMetaBody }
    >({
      query: ({ entityId, body }) => ({
        url: `/entities/${entityId}/profile-meta`,
        method: "PUT",
        body,
      }),
      invalidatesTags: (_result, _error, { entityId }) => [
        { type: "Entity", id: entityId },
        "MyEntities",
      ],
    }),

    updateEntityMode: builder.mutation<
      EntityDto,
      { entityId: number; mode: ProfileMode }
    >({
      query: ({ entityId, mode }) => ({
        url: `/entities/${entityId}/mode`,
        method: "PUT",
        body: { mode },
      }),
      invalidatesTags: (_result, _error, { entityId }) => [
        { type: "Entity", id: entityId },
        "MyEntities",
      ],
    }),

    // ─── Source curation (deselect repos / works) ───
    setSourceActive: builder.mutation<
      EntityDto,
      { entityId: number; sourceId: number; active: boolean }
    >({
      query: ({ entityId, sourceId, active }) => ({
        url: `/entities/${entityId}/sources/${sourceId}`,
        method: "PATCH",
        body: { active },
      }),
      invalidatesTags: (_result, _error, { entityId }) => [
        { type: "Entity", id: entityId },
        "MyEntities",
      ],
    }),

    updateEntityHubs: builder.mutation<
      EntityDto,
      { entityId: number; hubIds: number[] }
    >({
      query: ({ entityId, hubIds }) => ({
        url: `/entities/${entityId}/hubs`,
        method: "PUT",
        body: { hubIds },
      }),
      invalidatesTags: (_result, _error, { entityId }) => [
        { type: "Entity", id: entityId },
        "MyEntities",
      ],
    }),

    // ─── Tag subscriptions ("Abonnierte Themen") ───

    getSubscriptions: builder.query<SubscriptionDto[], number>({
      query: (entityId) => `/entities/${entityId}/subscriptions`,
      providesTags: (_result, _error, entityId) => [
        { type: "Subscriptions", id: entityId },
      ],
    }),

    updateSubscriptions: builder.mutation<
      SubscriptionDto[],
      { entityId: number; tags: string[] }
    >({
      query: ({ entityId, tags }) => ({
        url: `/entities/${entityId}/subscriptions`,
        method: "PUT",
        body: { tags },
      }),
      invalidatesTags: (_result, _error, { entityId }) => [
        { type: "Subscriptions", id: entityId },
      ],
    }),

    // ─── Requests (Anfrage lifecycle, reference §8) ───

    getBoard: builder.query<RequestSummaryDto[], void>({
      query: () => "/board",
      providesTags: ["Board"],
    }),

    getMyRequests: builder.query<RequestSummaryDto[], void>({
      query: () => "/requests",
      providesTags: ["MyRequests"],
    }),

    getRequest: builder.query<RequestDto, number | string>({
      query: (id) => `/requests/${id}`,
      providesTags: (_result, _error, id) => [{ type: "Request", id }],
    }),

    createRequest: builder.mutation<RequestDto, CreateRequestBody>({
      query: (body) => ({ url: "/requests", method: "POST", body }),
      invalidatesTags: ["Board", "MyRequests"],
    }),

    updateRequestStatus: builder.mutation<
      RequestDto,
      { id: number; status: RequestStatus }
    >({
      query: ({ id, status }) => ({
        url: `/requests/${id}/status`,
        method: "PUT",
        body: { status },
      }),
      invalidatesTags: (_result, _error, { id }) => [
        "Board",
        "MyRequests",
        { type: "Request", id },
      ],
    }),

    // ─── Contact handoff + Postfach (reference §8.2, §11) ───

    getContactGating: builder.query<ContactGatingDto, void>({
      query: () => "/contacts/gating",
    }),

    createContactRequest: builder.mutation<
      ContactRequestDto,
      CreateContactRequestBody
    >({
      query: (body) => ({ url: "/contacts", method: "POST", body }),
      // First contact flips the linked request to 'contacted' → refresh it.
      invalidatesTags: (_result, _error, { requestId }) => [
        "Board",
        "MyRequests",
        "Inbox",
        "ContactedEntities",
        ...(requestId != null
          ? [{ type: "Request" as const, id: requestId }]
          : []),
      ],
    }),

    respondContact: builder.mutation<
      ContactRequestDto,
      { id: number; body: RespondContactBody }
    >({
      query: ({ id, body }) => ({
        url: `/contacts/${id}/respond`,
        method: "POST",
        body,
      }),
      invalidatesTags: ["Inbox"],
    }),

    getInbox: builder.query<InboxDto, void>({
      query: () => "/inbox",
      providesTags: ["Inbox"],
    }),

    getContactedEntityIds: builder.query<number[], void>({
      query: () => "/contacts/contacted-entity-ids",
      providesTags: ["ContactedEntities"],
    }),

    // ─── Hubs (public list + admin management) ───

    getHubs: builder.query<HubDto[], void>({
      query: () => "/hubs",
      providesTags: ["Hubs"],
    }),

    // ─── Regions (public tree — scope selector + widen ladder) ───

    getRegions: builder.query<RegionDto[], void>({
      query: () => "/regions",
    }),

    // ─── Tag vocabulary autocomplete ───

    searchTags: builder.query<TagRefDto[], string>({
      query: (q) => `/tags?q=${encodeURIComponent(q)}`,
    }),

    getAdminRegions: builder.query<RegionDto[], void>({
      query: () => "/admin/regions",
    }),

    createHub: builder.mutation<HubDto, HubMutationBody>({
      query: (body) => ({ url: "/admin/hubs", method: "POST", body }),
      invalidatesTags: ["Hubs"],
    }),

    updateHub: builder.mutation<HubDto, { id: number; body: HubMutationBody }>({
      query: ({ id, body }) => ({
        url: `/admin/hubs/${id}`,
        method: "PUT",
        body,
      }),
      invalidatesTags: ["Hubs"],
    }),

    deleteHub: builder.mutation<void, number>({
      query: (id) => ({ url: `/admin/hubs/${id}`, method: "DELETE" }),
      invalidatesTags: ["Hubs"],
    }),
  }),
})

export const {
  useGetMeQuery,
  useLazyGetMeQuery,
  useGetEntityQuery,
  useGetMyEntitiesQuery,
  useDeleteEntityMutation,
  useSearchMutation,
  useGetRegionsQuery,
  useLazySearchZefixQuery,
  useIngestSourceMutation,
  useUploadLinkedInPdfMutation,
  useUpdateEntityTagsMutation,
  useUpdateEntitySummaryMutation,
  useResyncEntityMutation,
  useUpdateAvailabilityMutation,
  useUpdateProfileMetaMutation,
  useUpdateEntityModeMutation,
  useSetSourceActiveMutation,
  useUpdateEntityHubsMutation,
  useGetSubscriptionsQuery,
  useUpdateSubscriptionsMutation,
  useGetBoardQuery,
  useGetMyRequestsQuery,
  useGetRequestQuery,
  useCreateRequestMutation,
  useUpdateRequestStatusMutation,
  useGetContactGatingQuery,
  useCreateContactRequestMutation,
  useRespondContactMutation,
  useGetInboxQuery,
  useGetContactedEntityIdsQuery,
  useGetHubsQuery,
  useLazySearchTagsQuery,
  useGetAdminRegionsQuery,
  useCreateHubMutation,
  useUpdateHubMutation,
  useDeleteHubMutation,
} = apiSlice
