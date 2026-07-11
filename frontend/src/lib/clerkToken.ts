/**
 * Bridge between the React auth provider and the (non-React) RTK Query
 * base query. Clerk session tokens are short-lived (~60s); the SDK refreshes
 * them on demand, but only if you actually call `getToken()` at request time.
 *
 * `AuthProvider` registers the live getter here; the base query awaits a fresh
 * token per request instead of reusing a snapshot from the Redux slice (which
 * goes stale after idle and causes 401s). Passing `{ skipCache: true }` forces
 * Clerk to mint a new token, used to retry once after a 401.
 */
export interface TokenOptions {
  skipCache?: boolean
}

type TokenGetter = (options?: TokenOptions) => Promise<string | null>

let tokenGetter: TokenGetter | null = null

/** Called by AuthProvider whenever the active session (or dev bypass) changes. */
export function setTokenGetter(getter: TokenGetter | null): void {
  tokenGetter = getter
}

/** Resolve a currently-valid bearer token, or null when signed out. */
export function getFreshToken(options?: TokenOptions): Promise<string | null> {
  return tokenGetter ? tokenGetter(options) : Promise.resolve(null)
}
