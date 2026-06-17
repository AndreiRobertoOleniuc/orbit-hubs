import { apiRequest } from "@/lib/apiClient"
import type {
  CreateRequestDto,
  DistributionDto,
  PatchRequestStatusDto,
  RequestDto,
  RequestId,
} from "@/types/api"

export const requestClient = {
  create(request: CreateRequestDto): Promise<RequestDto> {
    return apiRequest<RequestDto>("/api/v1/requests", {
      method: "POST",
      body: request,
    })
  },

  get(id: RequestId): Promise<RequestDto> {
    return apiRequest<RequestDto>(`/api/v1/requests/${id}`)
  },

  patchStatus(
    id: RequestId,
    status: PatchRequestStatusDto
  ): Promise<RequestDto> {
    return apiRequest<RequestDto>(`/api/v1/requests/${id}/status`, {
      method: "PATCH",
      body: status,
    })
  },

  getDistributions(id: RequestId): Promise<DistributionDto[]> {
    return apiRequest<DistributionDto[]>(
      `/api/v1/requests/${id}/distributions`
    )
  },
}
