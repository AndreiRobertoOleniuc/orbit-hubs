import { apiRequest } from "@/lib/apiClient"
import type { BoardListParams, BoardResponse } from "@/types/api"

export const boardClient = {
  list(params: BoardListParams = {}): Promise<BoardResponse> {
    const qs = new URLSearchParams()
    if (params.tag) qs.set("tag", params.tag)
    if (params.scope) qs.set("scope", params.scope)
    if (params.sort) qs.set("sort", params.sort)
    if (params.page !== undefined) qs.set("page", String(params.page))

    const query = qs.toString()
    return apiRequest<BoardResponse>(`/api/v1/board${query ? `?${query}` : ""}`)
  },
}
