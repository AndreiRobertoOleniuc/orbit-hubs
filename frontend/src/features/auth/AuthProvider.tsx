import { useEffect, useRef } from "react"
import { useAuth as useClerkAuth, useUser } from "@clerk/react"

import { setAuthStatus, setAuthUser, setToken } from "@/features/auth/authSlice"
import { setTokenGetter } from "@/lib/clerkToken"
import { useAppDispatch } from "@/lib/hooks"

const DEV_JWT: string | undefined = import.meta.env.VITE_DEV_JWT

/**
 * Syncs the active auth provider into the Redux auth slice.
 *
 * Today this is Clerk React. If the project later swaps the provider,
 * only this component changes; the rest of the app keeps calling `useAuth()`
 * and reading from the slice.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const dispatch = useAppDispatch()
  const clerkAuth = useClerkAuth()
  const { user: clerkUser, isLoaded: userLoaded } = useUser()
  const syncingRef = useRef(false)

  // Keep latest Clerk references without re-triggering the effect.
  const clerkAuthRef = useRef(clerkAuth)
  const clerkUserRef = useRef(clerkUser)

  useEffect(() => {
    clerkAuthRef.current = clerkAuth
    clerkUserRef.current = clerkUser
  })

  useEffect(() => {
    if (DEV_JWT) {
      setTokenGetter(() => Promise.resolve(DEV_JWT))
      dispatch(setToken(DEV_JWT))
      dispatch(setAuthStatus("authenticated"))
      return
    }

    if (!clerkAuth.isLoaded || !userLoaded) {
      dispatch(setAuthStatus("loading"))
      return
    }

    if (!clerkAuth.isSignedIn) {
      setTokenGetter(null)
      dispatch(setToken(null))
      dispatch(setAuthUser(null))
      dispatch(setAuthStatus("unauthenticated"))
      return
    }

    // Resolve a fresh token per request from Clerk (which refreshes it when
    // near expiry) rather than reusing the snapshot below, which goes stale
    // after idle and 401s. The slice token stays only as an auth-state signal.
    setTokenGetter((options) => clerkAuthRef.current.getToken(options))

    if (syncingRef.current) return
    syncingRef.current = true

    async function sync() {
      try {
        const token = await clerkAuthRef.current.getToken()
        dispatch(setToken(token))
        const user = clerkUserRef.current
          ? {
              id: clerkUserRef.current.id,
              email:
                clerkUserRef.current.primaryEmailAddress?.emailAddress ?? "",
              displayName: clerkUserRef.current.fullName ?? undefined,
            }
          : null
        dispatch(setAuthUser(user))
      } catch (error) {
        console.error("Failed to sync auth token", error)
        dispatch(setToken(null))
        dispatch(setAuthUser(null))
        dispatch(setAuthStatus("unauthenticated"))
      } finally {
        syncingRef.current = false
      }
    }

    sync()
  }, [clerkAuth.isLoaded, clerkAuth.isSignedIn, userLoaded, dispatch])

  return children
}
