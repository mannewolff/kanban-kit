/** Feld → Meldung aus der `fieldErrors`-Extension einer RFC-9457-Fehlerantwort. */
export type FieldErrors = Readonly<Record<string, string>>

/**
 * Fehler einer API-Antwort mit HTTP-Status; message stammt aus `detail`/`title` (RFC 9457).
 *
 * `detail` ist nur gesetzt, wenn der Response-Body erfolgreich als RFC-9457-Problem gelesen
 * wurde und dort `detail` oder `title` stand — also nur bei einer Meldung, die das Backend
 * bewusst für den Nutzer formuliert hat. `message` behält daneben den bisherigen Fallback
 * auf den Roh-Body bzw. `statusText` und ist damit nicht ohne Prüfung anzeigbar.
 * Anzuzeigende Texte kommen aus {@link apiErrorMessage}.
 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly fieldErrors?: FieldErrors,
    public readonly detail?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * Anzeigbarer Text zu einem Fehler: die Server-Meldung aus {@link ApiError.detail}, sonst der
 * übergebene Fallback. Liest ausschließlich `detail` — nie `message`, das bei einem nicht
 * lesbaren Body auf den Roh-Body oder `statusText` zurückfällt (z. B. die HTML-Fehlerseite
 * eines Reverse-Proxys bei 502/504) und deshalb nicht nach außen gehört.
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) return fallback
  return error.detail !== undefined && error.detail !== '' ? error.detail : fallback
}

/**
 * Liest `detail`/`title` und `fieldErrors` aus einem RFC-9457-Problem-Body
 * (`application/problem+json`). Tolerant gegenüber Nicht-JSON- und Fremdformat-Bodies
 * (z. B. 401 aus der Security-Filterkette): dann bleibt das Ergebnis leer und der
 * Aufrufer fällt auf den Roh-Body zurück.
 *
 * Nur ein hier gefundenes `message` wird zu {@link ApiError.detail} — der Fallback auf
 * den Roh-Body bleibt allein in `message` und wird nie als Server-Meldung ausgegeben.
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
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })

  if (!response.ok) {
    const body = await response.text().catch(() => '')
    const problem = parseProblem(body)
    throw new ApiError(
      response.status,
      problem.message ?? (body || response.statusText),
      problem.fieldErrors,
      problem.message,
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
