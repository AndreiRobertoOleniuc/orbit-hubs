import { Navigate, Outlet } from "react-router"

import { useAuth } from "@/features/auth/useAuth"

export function AuthGuard() {
  const { isAuthenticated, status } = useAuth()

  // On a hard reload Redux starts at "idle" for one render before Clerk has
  // restored the session. Redirecting during that window loses the current
  // route and sends an authenticated user through the landing page.
  if (status === "idle" || status === "loading") {
    return (
      <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
        Lädt …
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
