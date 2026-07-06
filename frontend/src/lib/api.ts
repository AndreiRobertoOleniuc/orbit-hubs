import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react"

import type {
  EntityDto,
  IngestionRequestDto,
  IngestionResponseDto,
  MeDto,
  ZefixCompanyDto,
} from "@/types/api"

interface StateWithAuth {
  auth: { token: string | null }
}

const baseUrl = `${import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"}/api/v1`

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
  refetchOnMountOrArgChange: true,
  tagTypes: ["MyEntities", "Entity"],
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
  }),
})

export const {
  useGetMeQuery,
  useLazyGetMeQuery,
  useGetEntityQuery,
  useGetMyEntitiesQuery,
  useLazySearchZefixQuery,
  useIngestSourceMutation,
  useUploadLinkedInPdfMutation,
} = apiSlice
