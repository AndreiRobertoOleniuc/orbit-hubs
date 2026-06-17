import { useCallback } from "react"

import { type AuthUser, clearSession } from "@/features/auth/authSlice"
import { useAppDispatch, useAppSelector } from "@/lib/hooks"

export interface AuthContextValue {
  token: string | null
  getToken: () => Promise<string | null>
  isAuthenticated: boolean
  user: AuthUser | null
  status: "idle" | "loading" | "authenticated" | "unauthenticated"
  signOut: () => void
}

/**
 * Token-provider abstraction backed by the Redux auth slice.
 *
 * This hook intentionally knows nothing about Clerk or Spring Security.
 * The concrete provider (Clerk today, possibly something else tomorrow)
 * syncs its token into the `auth` slice; the rest of the app reads from here.
 */
export function useAuth(): AuthContextValue {
  const dispatch = useAppDispatch()
  const token = useAppSelector((state) => state.auth.token)
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated)
  const user = useAppSelector((state) => state.auth.user)
  const status = useAppSelector((state) => state.auth.status)

  const getToken = useCallback(async () => token, [token])
  const signOut = useCallback(() => {
    dispatch(clearSession())
  }, [dispatch])

  return { token, getToken, isAuthenticated, user, status, signOut }
}
