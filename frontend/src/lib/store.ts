import { configureStore } from "@reduxjs/toolkit"

import authReducer from "@/features/auth/authSlice"
import searchReducer, {
  SEARCH_HISTORY_STORAGE_KEY,
} from "@/features/search/searchSlice"
import { apiSlice } from "@/lib/api"

export const store = configureStore({
  reducer: {
    auth: authReducer,
    search: searchReducer,
    [apiSlice.reducerPath]: apiSlice.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(apiSlice.middleware),
})

let persistedSearchHistory = ""
store.subscribe(() => {
  if (typeof window === "undefined") return
  const next = JSON.stringify(store.getState().search.history)
  if (next === persistedSearchHistory) return
  persistedSearchHistory = next
  try {
    window.localStorage.setItem(SEARCH_HISTORY_STORAGE_KEY, next)
  } catch {
    // Storage may be unavailable or full; in-session Redux history still works.
  }
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
