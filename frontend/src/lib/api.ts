import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react"

import type { MeDto } from "@/types/api"

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
  }),
})

export const { useGetMeQuery, useLazyGetMeQuery } = apiSlice
