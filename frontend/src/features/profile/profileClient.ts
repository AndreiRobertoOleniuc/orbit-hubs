import { apiRequest } from "@/lib/apiClient"
import type {
  Availability,
  EntityDto,
  EntityId,
  PatchEntityDto,
  ProfileDto,
  RequestDto,
} from "@/types/api"

export const entityClient = {
  get(id: EntityId): Promise<ProfileDto> {
    return apiRequest<ProfileDto>(`/api/v1/entities/${id}`)
  },

  patch(id: EntityId, patch: PatchEntityDto): Promise<EntityDto> {
    return apiRequest<EntityDto>(`/api/v1/entities/${id}`, {
      method: "PATCH",
      body: patch,
    })
  },

  putAvailability(
    id: EntityId,
    availability: Availability
  ): Promise<Availability> {
    return apiRequest<Availability>(`/api/v1/entities/${id}/availability`, {
      method: "PUT",
      body: availability,
    })
  },

  getMatchingRequests(id: EntityId): Promise<RequestDto[]> {
    return apiRequest<RequestDto[]>(`/api/v1/entities/${id}/matching-requests`)
  },
}
