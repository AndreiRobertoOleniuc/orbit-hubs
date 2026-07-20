import { configureStore } from "@reduxjs/toolkit"

import authReducer from "@/features/auth/authSlice"
import searchReducer from "@/features/search/searchSlice"
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

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
