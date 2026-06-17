import { store } from "@/lib/store"

const API_BASE: string =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"

export class ApiError extends Error {
  status: number
  statusText: string

  constructor(status: number, statusText: string, message: string) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.statusText = statusText
  }
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {}
): Promise<T> {
  const token = store.getState().auth.token

  const headers = new Headers(options.headers)
  if (token) {
    headers.set("Authorization", `Bearer ${token}`)
  }

  const hasBody = options.body !== undefined
  if (hasBody && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
  }

  const url = new URL(path, API_BASE).toString()
  const init: RequestInit = {
    ...options,
    headers,
    body: hasBody ? JSON.stringify(options.body) : undefined,
  }

  const response = await fetch(url, init)

  if (!response.ok) {
    const body = await response.text()
    throw new ApiError(
      response.status,
      response.statusText,
      body || `${response.status} ${response.statusText}`
    )
  }

  if (
    response.status === 204 ||
    response.headers.get("content-length") === "0"
  ) {
    return null as T
  }

  return (await response.json()) as T
}
