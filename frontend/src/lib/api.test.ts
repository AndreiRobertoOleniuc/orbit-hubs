import { configureStore } from "@reduxjs/toolkit"
import { afterEach, describe, expect, it, vi } from "vitest"

import { apiSlice } from "@/lib/api"
import { setTokenGetter } from "@/lib/clerkToken"

afterEach(() => {
  setTokenGetter(null)
  vi.unstubAllGlobals()
})

describe("RTK Query authentication retry", () => {
  it("gets a fresh Clerk token and retries exactly once after a 401", async () => {
    const getToken = vi
      .fn()
      .mockResolvedValueOnce("stale-token")
      .mockResolvedValueOnce("fresh-token")
    setTokenGetter(getToken)

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: "unauthorized" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ userId: "user_1", email: "ada@orbit.test", isAdmin: false }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
    vi.stubGlobal("fetch", fetchMock)

    const store = configureStore({
      reducer: { [apiSlice.reducerPath]: apiSlice.reducer },
      middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware().concat(apiSlice.middleware),
    })

    const result = await store.dispatch(apiSlice.endpoints.getMe.initiate())

    expect(result.data).toMatchObject({ userId: "user_1" })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(getToken).toHaveBeenNthCalledWith(1, undefined)
    expect(getToken).toHaveBeenNthCalledWith(2, { skipCache: true })
  })
})
