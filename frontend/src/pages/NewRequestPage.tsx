import { CreateRequestView } from "@/features/requests/CreateRequestView"

/** Auth-guarded: post a new Anfrage (optionally prefilled from a search). */
export function NewRequestPage() {
  return <CreateRequestView />
}
