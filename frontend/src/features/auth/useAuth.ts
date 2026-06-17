import { useCallback } from "react"

import {
  selectAuthStatus,
  selectAuthUser,
  selectIsAuthenticated,
  selectToken,
} from "@/features/auth/authSelectors"
import { clearSession } from "@/features/auth/authSlice"
import { useAppDispatch } from "@/hooks/useAppDispatch"
import { useAppSelector } from "@/hooks/useAppSelector"

export interface AuthContextValue {
  token: string | null
  getToken: () => Promise<string | null>
  isAuthenticated: boolean
  user: import("@/features/auth/authSlice").AuthUser | null
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
  const token = useAppSelector(selectToken)
  const isAuthenticated = useAppSelector(selectIsAuthenticated)
  const user = useAppSelector(selectAuthUser)
  const status = useAppSelector(selectAuthStatus)

  const getToken = useCallback(async () => token, [token])
  const signOut = useCallback(() => {
    dispatch(clearSession())
  }, [dispatch])

  return { token, getToken, isAuthenticated, user, status, signOut }
}
