import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Today as an ISO date string (YYYY-MM-DD) — the floor for date pickers. */
export function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

/** Order-insensitive equality for two lists — used for dirty-state checks. */
export function sameSet<T>(a: readonly T[], b: readonly T[]) {
  if (a.length !== b.length) return false
  const sa = [...a].sort()
  const sb = [...b].sort()
  return sa.every((v, i) => v === sb[i])
}
