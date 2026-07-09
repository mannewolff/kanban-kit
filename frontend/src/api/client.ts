/** Fehler einer API-Antwort mit HTTP-Status. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * Dünner Fetch-Wrapper. Sendet Cookies mit (Session-Auth), setzt JSON-Header und
 * wirft {@link ApiError} bei nicht-2xx-Antworten. Leere Antworten -> undefined.
 */
export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(options.headers ?? {}) },
    ...options,
  })

  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new ApiError(response.status, body || response.statusText)
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
