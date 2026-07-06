import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react"

import type {
  IngestionRequestDto,
  IngestionResponseDto,
  MeDto,
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
  endpoints: (builder) => ({
    getMe: builder.query<MeDto, void>({
      query: () => "/me",
    }),

    // ─── Ingestion ───

    ingestSource: builder.mutation<IngestionResponseDto, IngestionRequestDto>({
      query: (body) => ({
        url: "/ingestion",
        method: "POST",
        body,
      }),
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
    }),
  }),
})

export const {
  useGetMeQuery,
  useLazyGetMeQuery,
  useIngestSourceMutation,
  useUploadLinkedInPdfMutation,
} = apiSlice