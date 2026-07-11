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
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
  parse?: (data: unknown) => T,
): Promise<T> {
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
  // Extern stammende Daten sind `unknown`, bis validiert. Sicherheitsrelevante Endpoints
  // übergeben einen `parse`-Type-Guard (z. B. authApi.me/login). Ohne `parse` bleibt der
  // Wrapper generisch: der Cast auf T ist die bewusst dokumentierte Systemgrenze eines
  // typisierten fetch-Wrappers — kein Cast zur Umgehung eines Modellfehlers.
  const data: unknown = text ? JSON.parse(text) : undefined
  return parse ? parse(data) : (data as T)
}
