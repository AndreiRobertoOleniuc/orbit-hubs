import { useParams } from "react-router"

import { RequestDetailView } from "@/features/requests/RequestDetailView"

/** Public request detail at /board/:requestId (token-free route). */
export function RequestDetailPage() {
  const { requestId } = useParams<{ requestId: string }>()
  if (!requestId) return null
  return <RequestDetailView requestId={requestId} />
}
