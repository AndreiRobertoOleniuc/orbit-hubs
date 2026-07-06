import { Navigate, Outlet } from "react-router-dom"

import { useAuth } from "@/features/auth/useAuth"

export function AuthGuard() {
  const { isAuthenticated, status } = useAuth()

  if (status === "loading") {
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
