import { createSlice, type PayloadAction } from "@reduxjs/toolkit"

export interface AuthUser {
  id: string
  email: string
  displayName?: string
  entityId?: string
}

export interface AuthState {
  token: string | null
  isAuthenticated: boolean
  status: "idle" | "loading" | "authenticated" | "unauthenticated"
  user: AuthUser | null
}

const initialState: AuthState = {
  token: null,
  isAuthenticated: false,
  status: "idle",
  user: null,
}

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setToken(state, action: PayloadAction<string | null>) {
      state.token = action.payload
      state.isAuthenticated = !!action.payload
      state.status = action.payload ? "authenticated" : "unauthenticated"
    },
    setAuthUser(state, action: PayloadAction<AuthUser | null>) {
      state.user = action.payload
    },
    setAuthStatus(state, action: PayloadAction<AuthState["status"]>) {
      state.status = action.payload
    },
    clearSession(state) {
      state.token = null
      state.isAuthenticated = false
      state.status = "unauthenticated"
      state.user = null
    },
  },
})

export const { setToken, setAuthUser, setAuthStatus, clearSession } =
  authSlice.actions
export default authSlice.reducer
