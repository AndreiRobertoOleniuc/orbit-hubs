import { apiRequest } from "@/lib/apiClient"
import type {
  EntityId,
  RationaleResponse,
  SearchRequest,
  SearchResponse,
} from "@/types/api"

export const searchClient = {
  search(request: SearchRequest): Promise<SearchResponse> {
    return apiRequest<SearchResponse>("/api/v1/search", {
      method: "POST",
      body: request,
    })
  },

  getRationale(entityId: EntityId, query: string): Promise<RationaleResponse> {
    const qs = new URLSearchParams({ query })
    return apiRequest<RationaleResponse>(
      `/api/v1/search/rationale/${entityId}?${qs.toString()}`
    )
  },
}
