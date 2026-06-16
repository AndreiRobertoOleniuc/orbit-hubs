import { useAuth } from "@clerk/react"

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"

export function useApi() {
  const { getToken } = useAuth()

  async function request(path: string, init: RequestInit = {}) {
    const token = await getToken()
    const headers = new Headers(init.headers)
    if (token) headers.set("Authorization", `Bearer ${token}`)
    headers.set("Content-Type", "application/json")

    const res = await fetch(`${API_BASE}${path}`, { ...init, headers })
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
    return res.status === 204 ? null : res.json()
  }

  return { request }
}
