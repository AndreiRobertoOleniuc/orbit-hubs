import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Today as an ISO date string (YYYY-MM-DD) — the floor for date pickers. */
export function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

/**
 * An http(s) href, or null. The single guard against dead links — source URLs
 * can also be internal keys (`zefix:CHE…`, `manual:…`) or, in the worst case,
 * a `javascript:` URL, and none of those may end up in an anchor.
 */
export function externalHref(url: string | null | undefined): string | null {
  return url && /^https?:\/\/\S/i.test(url) ? url : null
}

/** Order-insensitive equality for two lists — used for dirty-state checks. */
export function sameSet<T>(a: readonly T[], b: readonly T[]) {
  if (a.length !== b.length) return false
  const sa = [...a].sort()
  const sb = [...b].sort()
  return sa.every((v, i) => v === sb[i])
}
