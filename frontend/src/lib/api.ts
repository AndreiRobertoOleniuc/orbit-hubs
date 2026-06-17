import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react"

import type {
  BoardListParams,
  BoardResponse,
  ContactRequest,
  CreateRequestDto,
  DistributionDto,
  EntityId,
  OnboardingOrganisationDto,
  OnboardingPersonDto,
  OnboardingStatusDto,
  PatchRequestStatusDto,
  ProfileDto,
  RationaleResponse,
  RequestDto,
  RequestId,
  SearchRequest,
  SearchResponse,
  SubscriptionRequest,
} from "@/types/api"

interface StateWithAuth {
  auth: { token: string | null }
}

const baseUrl =
  `${import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"}/api/v1`

const baseQuery = fetchBaseQuery({
  baseUrl,
  prepareHeaders: (headers, { getState }) => {
    const token = (getState() as StateWithAuth).auth.token
    if (token) {
      headers.set("Authorization", `Bearer ${token}`)
    }
    return headers
  },
})

export const apiSlice = createApi({
  reducerPath: "api",
  baseQuery,
  tagTypes: [
    "Search",
    "Board",
    "Request",
    "Entity",
    "Profile",
    "Availability",
    "Subscription",
    "Onboarding",
  ],
  endpoints: (builder) => ({
    search: builder.query<SearchResponse, SearchRequest>({
      query: (body) => ({ url: "/search", method: "POST", body }),
      providesTags: ["Search"],
    }),

    getSearchRationale: builder.query<
      RationaleResponse,
      { entityId: EntityId; query: string }
    >({
      query: ({ entityId, query }) =>
        `/search/rationale/${entityId}?query=${encodeURIComponent(query)}`,
      providesTags: ["Search"],
    }),

    getBoard: builder.query<BoardResponse, BoardListParams | void>({
      query: (params = {}) => {
        const qs = new URLSearchParams()
        if (params.tag) qs.set("tag", params.tag)
        if (params.scope) qs.set("scope", params.scope)
        if (params.sort) qs.set("sort", params.sort)
        if (params.page !== undefined) qs.set("page", String(params.page))
        const query = qs.toString()
        return `/board${query ? `?${query}` : ""}`
      },
      providesTags: ["Board"],
    }),

    createRequest: builder.mutation<RequestDto, CreateRequestDto>({
      query: (body) => ({ url: "/requests", method: "POST", body }),
      invalidatesTags: ["Board", "Request"],
    }),

    getRequest: builder.query<RequestDto, RequestId>({
      query: (id) => `/requests/${id}`,
      providesTags: (_result, _error, id) => [{ type: "Request", id }],
    }),

    patchRequestStatus: builder.mutation<
      RequestDto,
      { id: RequestId; body: PatchRequestStatusDto }
    >({
      query: ({ id, body }) => ({
        url: `/requests/${id}/status`,
        method: "PATCH",
        body,
      }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: "Request", id },
        "Board",
      ],
    }),

    getRequestDistributions: builder.query<DistributionDto[], RequestId>({
      query: (id) => `/requests/${id}/distributions`,
      providesTags: (_result, _error, id) => [{ type: "Request", id }],
    }),

    getEntity: builder.query<ProfileDto, EntityId>({
      query: (id) => `/entities/${id}`,
      providesTags: (_result, _error, id) => [
        { type: "Entity", id },
        { type: "Profile", id },
      ],
    }),

    updateEntity: builder.mutation<
      ProfileDto,
      { id: EntityId; body: Partial<ProfileDto> }
    >({
      query: ({ id, body }) => ({
        url: `/entities/${id}`,
        method: "PATCH",
        body,
      }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: "Entity", id },
        { type: "Profile", id },
      ],
    }),

    onboardPerson: builder.mutation<EntityId, OnboardingPersonDto>({
      query: (body) => ({ url: "/onboarding/person", method: "POST", body }),
      invalidatesTags: ["Onboarding", "Profile"],
    }),

    onboardOrganisation: builder.mutation<
      EntityId,
      OnboardingOrganisationDto
    >({
      query: (body) => ({
        url: "/onboarding/organisation",
        method: "POST",
        body,
      }),
      invalidatesTags: ["Onboarding", "Profile", "Entity"],
    }),

    getOnboardingStatus: builder.query<OnboardingStatusDto, EntityId>({
      query: (entityId) => `/onboarding/${entityId}/status`,
      providesTags: (_result, _error, entityId) => [
        { type: "Onboarding", id: entityId },
      ],
    }),

    sendContact: builder.mutation<void, ContactRequest>({
      query: (body) => ({ url: "/contact", method: "POST", body }),
      invalidatesTags: ["Request"],
    }),

    subscribeTag: builder.mutation<void, SubscriptionRequest>({
      query: (body) => ({ url: "/subscriptions", method: "POST", body }),
      invalidatesTags: ["Subscription"],
    }),

    unsubscribeTag: builder.mutation<void, string>({
      query: (tagSlug) => ({
        url: `/subscriptions/${tagSlug}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Subscription"],
    }),
  }),
})

export const {
  useSearchQuery,
  useGetSearchRationaleQuery,
  useGetBoardQuery,
  useCreateRequestMutation,
  useGetRequestQuery,
  usePatchRequestStatusMutation,
  useGetRequestDistributionsQuery,
  useGetEntityQuery,
  useUpdateEntityMutation,
  useOnboardPersonMutation,
  useOnboardOrganisationMutation,
  useGetOnboardingStatusQuery,
  useSendContactMutation,
  useSubscribeTagMutation,
  useUnsubscribeTagMutation,
} = apiSlice
