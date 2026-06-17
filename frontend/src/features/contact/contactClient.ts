import { apiRequest } from "@/lib/apiClient"
import type { ContactRequest } from "@/types/api"

export const contactClient = {
  send(request: ContactRequest): Promise<void> {
    return apiRequest<void>("/api/v1/contact", {
      method: "POST",
      body: request,
    })
  },
}
