import { Navigate } from "react-router"
import { Loader2 } from "lucide-react"

import { HubAdminView } from "@/features/admin/HubAdminView"
import { useGetMeQuery } from "@/lib/api"

/**
 * Admin-only hub management at /admin/hubs. The route is auth-guarded; this page
 * additionally checks the {@code isAdmin} flag from /me and redirects non-admins.
 */
export function AdminHubsPage() {
  const { data: me, isLoading } = useGetMeQuery()

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!me?.isAdmin) {
    return <Navigate to="/" replace />
  }

  return (
    <div className="mx-auto max-w-2xl py-4">
      <HubAdminView />
    </div>
  )
}
