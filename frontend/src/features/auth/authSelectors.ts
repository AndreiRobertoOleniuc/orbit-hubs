import type { RootState } from "@/lib/store"

export const selectToken = (state: RootState) => state.auth.token
export const selectIsAuthenticated = (state: RootState) =>
  state.auth.isAuthenticated
export const selectAuthUser = (state: RootState) => state.auth.user
export const selectAuthStatus = (state: RootState) => state.auth.status
