/** Feld → Meldung aus der `fieldErrors`-Extension einer RFC-9457-Fehlerantwort. */
export type FieldErrors = Readonly<Record<string, string>>

/** Fehler einer API-Antwort mit HTTP-Status; message stammt aus `detail`/`title` (RFC 9457). */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly fieldErrors?: FieldErrors,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * Liest `detail`/`title` und `fieldErrors` aus einem RFC-9457-Problem-Body
 * (`application/problem+json`). Tolerant gegenüber Nicht-JSON- und Fremdformat-Bodies
 * (z. B. 401 aus der Security-Filterkette): dann bleibt das Ergebnis leer und der
 * Aufrufer fällt auf den Roh-Body zurück.
 */
function parseProblem(body: string): { message?: string; fieldErrors?: FieldErrors } {
  let data: unknown
  try {
    data = JSON.parse(body)
  } catch {
    return {}
  }
  if (typeof data !== 'object' || data === null) return {}
  const problem = data as Record<string, unknown>
  return {
    message: firstNonEmptyString(problem.detail, problem.title),
    fieldErrors: toFieldErrors(problem.fieldErrors),
  }
}

function firstNonEmptyString(...values: unknown[]): string | undefined {
  return values.find((value): value is string => typeof value === 'string' && value !== '')
}

function toFieldErrors(value: unknown): FieldErrors | undefined {
  if (typeof value !== 'object' || value === null) return undefined
  const entries = Object.entries(value as Record<string, unknown>).filter(
    (entry): entry is [string, string] => typeof entry[1] === 'string',
  )
  return entries.length > 0 ? Object.fromEntries(entries) : undefined
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
    const problem = parseProblem(body)
    throw new ApiError(
      response.status,
      problem.message ?? (body || response.statusText),
      problem.fieldErrors,
    )
  }

  const text = await response.text()
  // Extern stammende Daten sind `unknown`, bis validiert. Sicherheitsrelevante Endpoints
  // übergeben einen `parse`-Type-Guard (z. B. authApi.me/login). Ohne `parse` bleibt der
  // Wrapper generisch: der Cast auf T ist die bewusst dokumentierte Systemgrenze eines
  // typisierten fetch-Wrappers — kein Cast zur Umgehung eines Modellfehlers.
  const data: unknown = text ? JSON.parse(text) : undefined
  return parse ? parse(data) : (data as T)
}
