import { apiRequest } from "@/lib/apiClient"
import type {
  EntityId,
  OnboardingOrganisationDto,
  OnboardingPersonDto,
  OnboardingStatusDto,
  SubscriptionRequest,
} from "@/types/api"

export const onboardingClient = {
  onboardPerson(request: OnboardingPersonDto): Promise<EntityId> {
    return apiRequest<EntityId>("/api/v1/onboarding/person", {
      method: "POST",
      body: request,
    })
  },

  onboardOrganisation(
    request: OnboardingOrganisationDto
  ): Promise<EntityId> {
    return apiRequest<EntityId>("/api/v1/onboarding/organisation", {
      method: "POST",
      body: request,
    })
  },

  getStatus(entityId: EntityId): Promise<OnboardingStatusDto> {
    return apiRequest<OnboardingStatusDto>(
      `/api/v1/onboarding/${entityId}/status`
    )
  },
}

export const subscriptionClient = {
  subscribe(request: SubscriptionRequest): Promise<void> {
    return apiRequest<void>("/api/v1/subscriptions", {
      method: "POST",
      body: request,
    })
  },

  unsubscribe(tagSlug: string): Promise<void> {
    return apiRequest<void>(`/api/v1/subscriptions/${tagSlug}`, {
      method: "DELETE",
    })
  },
}
