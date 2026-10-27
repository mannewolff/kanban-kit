import { de } from '../i18n/de'

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
    /** Problem-Typ aus dem Antwortkörper (`type`), etwa `vorschau-verborgen` (Plan #1914, E16). */
    public readonly type?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * Anzeigbarer Text zu einem Fehler: die Server-Meldung aus {@link ApiError.detail}, sonst der
 * übergebene Fallback, ohne Angabe der deutsche Text aus dem Wörterbuch (Plan #1638, E6). Eine
 * Seite in Englisch übergibt ihren Fallback aus `useT()`. Liest ausschließlich `detail` — nie `message`, das bei einem nicht
 * lesbaren Body auf den Roh-Body oder `statusText` zurückfällt (z. B. die HTML-Fehlerseite
 * eines Reverse-Proxys bei 502/504) und deshalb nicht nach außen gehört.
 */
export function apiErrorMessage(error: unknown, fallback: string = de.FEHLER_UNBEKANNT): string {
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
function parseProblem(body: string): { message?: string; fieldErrors?: FieldErrors; type?: string } {
  let data: unknown
  try {
    data = JSON.parse(body)
  }
  // Stryker disable next-line BlockStatement: gleichwertig — ohne `return {}` bleibt `data` undefiniert, und die Typprüfung darunter liefert dasselbe `{}`
  catch {
    return {}
  }
  // Stryker disable next-line ConditionalExpression: gleichwertig — an einem JSON-Primitiv liest der Zugriff darunter nur `undefined`, `null` fängt der zweite Teil
  if (typeof data !== 'object' || data === null) return {}
  const problem = data as Record<string, unknown>
  return {
    message: firstNonEmptyString(problem.detail, problem.title),
    fieldErrors: toFieldErrors(problem.fieldErrors),
    type: firstNonEmptyString(problem.type),
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

let unauthorizedHandler: (() => void) | null = null

/**
 * Registriert den Haken, der bei einer `401`-Antwort feuert; `null` meldet ihn wieder ab.
 *
 * Bewusst eine blosse Funktionsreferenz: Dieses Modul kennt weder Anwendungszustand noch
 * Navigation. Wer den Haken belegt, entscheidet, was eine abgelaufene Sitzung bedeutet.
 */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

/**
 * Meldet den Status einer Fehlerantwort und löst den registrierten Haken genau bei `401` aus.
 *
 * Für Aufrufer, die an {@link apiFetch} vorbei direkt `fetch` nutzen (Multipart-Upload,
 * Blob-Abruf) — sie brauchen andere Body- und Antwortbehandlung als der JSON-Wrapper,
 * sollen aber dieselbe Sitzungsregel erfüllen.
 */
export function notifyUnauthorized(status: number): void {
  if (status === 401) unauthorizedHandler?.()
}

/** Problem-Typ, mit dem der Server eine Anfrage aus einem veralteten Tab abweist (Plan #1914, E8). */
const VORSCHAU_GEWECHSELT = 'vorschau-gewechselt'

/** Problem-Typ, mit dem der Server einen Änderungsaufruf in der Vorschau abweist (Plan #1914, E15). */
const VORSCHAU_NUR_LESEND = 'vorschau-nur-lesend'

let vorschauKennung: string | null = null

/**
 * Setzt die Kennung der Vorschau, die dieser Tab zu sehen glaubt; `null` außerhalb einer Vorschau.
 *
 * Das Register belegt `AuthContext` aus der Selbstauskunft, und zwar im selben Zug wie den Nutzer:
 * Die erste Anfrage nach einem Wechsel trägt so schon die neue Kennung.
 */
export function setVorschauKennung(kennung: string | null): void {
  vorschauKennung = kennung
}

/**
 * Die Kopfzeile `X-Vorschau` mit der Kennung aus {@link setVorschauKennung} oder `keine` (E8).
 *
 * Für die Direktaufrufe an {@link apiFetch} vorbei; weicht sie vom Stand am Server ab, führt der
 * Server nichts aus und antwortet mit `409 vorschau-gewechselt`.
 */
export function vorschauKopf(): Record<string, string> {
  return { 'X-Vorschau': vorschauKennung ?? 'keine' }
}

const aufStartseite = () => window.location.assign('/')

let neuLaden: () => void = aufStartseite

/**
 * Ersetzt das Neuladen bei `409 vorschau-gewechselt`; `null` stellt die Vorgabe wieder her.
 *
 * Vorgabe ist `/`, nicht die alte Seite: `StartRedirect` leitet von dort auf die Startseite des
 * gültigen Stands weiter, die alte Seite gibt es für ihn vielleicht nicht (Plan #1914, E13).
 */
export function setNeuLaden(handler: (() => void) | null): void {
  neuLaden = handler ?? aufStartseite
}

/**
 * Lädt auf `/` neu wie nach `409 vorschau-gewechselt` — für den Wechsel, den die Oberfläche selbst
 * auslöst, etwa „Beenden“ im Balken der Vorschau (Plan #1914, E13).
 */
export function startseiteNeuLaden(): void {
  neuLaden()
}

let nurLesendHandler: (() => void) | null = null

/**
 * Registriert die Meldung „Vorschau – nur lesend“ bei `403 vorschau-nur-lesend`; `null` meldet
 * sie ab. Ein Netz für ein Bedienelement, das in der Vorschau nicht gesperrt wurde (E15).
 */
export function setNurLesendHandler(handler: (() => void) | null): void {
  nurLesendHandler = handler
}

/** Löst die Folgen der Vorschau-Abweisungen aus: Neuladen bei 409, Meldung bei 403. */
function folgeDerVorschau(status: number, type: string | undefined): void {
  if (status === 409 && type === VORSCHAU_GEWECHSELT) neuLaden()
  if (status === 403 && type === VORSCHAU_NUR_LESEND) nurLesendHandler?.()
}

/**
 * Dünner Fetch-Wrapper. Sendet Cookies mit (Session-Auth), setzt JSON-Header und `X-Vorschau` und
 * wirft {@link ApiError} bei nicht-2xx-Antworten. Leere Antworten -> undefined.
 */
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
  parse?: (data: unknown) => T,
): Promise<T> {
  // Die Kopfzeilen stehen nach `options`, damit eigene Kopfzeilen des Aufrufers sie ergänzen und nicht
  // ersetzen; `X-Vorschau` steht zuletzt und lässt sich nicht überschreiben (E8).
  const response = await fetch(path, {
    credentials: 'include',
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers, ...vorschauKopf() },
  })

  if (!response.ok) {
    // Stryker disable next-line ArrowFunction: gleichwertig — ein undefinierter Body scheitert in `parseProblem` am JSON und fällt wie `''` auf `statusText`
    const body = await response.text().catch(() => '')
    const problem = parseProblem(body)
    notifyUnauthorized(response.status)
    folgeDerVorschau(response.status, problem.type)
    // Auch nach dem Neuladen wirft der Aufruf, damit kein Aufrufer mit leeren Daten weitermacht.
    throw new ApiError(
      response.status,
      problem.message ?? (body || response.statusText),
      problem.fieldErrors,
      problem.message,
      problem.type,
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
