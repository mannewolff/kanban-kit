import { apiFetch, ApiError } from './client'

export interface Membership {
  projectId: number
  role: string
}

/** Gewähltes Erscheinungsbild am Konto (#1911): SYSTEM folgt Browser bzw. Betriebssystem. */
export type ColorScheme = 'SYSTEM' | 'LIGHT' | 'DARK'

// Stryker disable next-line StringLiteral: gleichwertig — fehlt 'SYSTEM' in der Liste, fällt die Wahl in `parseMe` auf 'SYSTEM' zurück
const COLOR_SCHEMES: ReadonlyArray<string> = ['SYSTEM', 'LIGHT', 'DARK'] satisfies ColorScheme[]

/** Die laufende, nur lesende „Ansicht als XY“ dieser Anmeldung (Plan #1914, E14). */
export interface Vorschau {
  /** Kennung, die jede Anfrage dieses Tabs in der Kopfzeile `X-Vorschau` nennt (E8). */
  kennung: string
  /** Anzeigename des angesehenen Kontos. */
  zielName: string
  /** Anzeigename des Admins, der die Vorschau gestartet hat. */
  adminName: string
  /** Zeitpunkt, an dem die Vorschau spätestens endet, ISO-8601. */
  laeuftAbAm: string
}

/** Grund, aus dem eine Vorschau endete (Issue #1921). */
export type VorschauEndgrund =
  | 'BEENDET'
  | 'ABLAUF'
  | 'ABMELDUNG'
  | 'ROLLE_ENTZOGEN'
  | 'ADMIN_GESPERRT'
  | 'SITZUNG_UNGUELTIG'
  | 'ZIEL_IST_ADMIN'
  | 'ZIEL_FEHLT'

/** Ein Eintrag, wann welcher Admin die App als ein Konto angesehen hat (Plan #1914, E12). */
export interface VorschauEintrag {
  adminName: string
  /** Das Konto des Admins ist inzwischen gelöscht; der Name bleibt stehen. */
  adminKontoGeloescht: boolean
  gestartetAm: string
  laeuftAbAm: string
  /** `null`, solange die Vorschau läuft. */
  beendetAm: string | null
  /** `null`, solange die Vorschau läuft. */
  endgrund: VorschauEndgrund | null
}

export interface Me {
  userId: number
  email: string
  displayName: string
  platformRole: string
  memberships: Membership[]
  colorScheme: ColorScheme
  /** In der Vorschau beschreibt `Me` das angesehene Konto XY; sonst `null`. */
  vorschau: Vorschau | null
}

const jsonBody = (data: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(data) })

/** Laufzeit-Verengung der sicherheitsrelevanten Me-Antwort (Autorisierungsquelle). */
function isMe(
  data: unknown,
): data is Omit<Me, 'colorScheme' | 'vorschau'> & { colorScheme?: unknown; vorschau?: unknown } {
  if (typeof data !== 'object' || data === null) return false
  const d = data as Record<string, unknown>
  return (
    typeof d.userId === 'number' &&
    typeof d.email === 'string' &&
    typeof d.displayName === 'string' &&
    typeof d.platformRole === 'string' &&
    Array.isArray(d.memberships)
  )
}

const nichtLeer = (wert: unknown): wert is string => typeof wert === 'string' && wert !== ''

function isVorschau(data: unknown): data is Vorschau {
  // Stryker disable next-line ConditionalExpression,LogicalOperator: gleichwertig — `parseMe` reicht nie `null` oder `undefined` herein, und an einem Text oder einer Zahl scheitert schon `nichtLeer`
  if (typeof data !== 'object' || data === null) return false
  const d = data as Record<string, unknown>
  return nichtLeer(d.kennung) && nichtLeer(d.zielName) && nichtLeer(d.adminName) && nichtLeer(d.laeuftAbAm)
}

function parseMe(data: unknown): Me {
  if (!isMe(data)) {
    throw new ApiError(500, 'Unerwartete Antwort vom Server (Me).')
  }
  // Die Farbwahl ist keine Autorisierungsquelle: Fehlt sie oder ist sie unbekannt, gilt „System".
  const colorScheme = COLOR_SCHEMES.includes(String(data.colorScheme)) ? (data.colorScheme as ColorScheme) : 'SYSTEM'
  // Die Vorschau dagegen schon: Aus ihr kommt die Kopfzeile `X-Vorschau`. Eine unlesbare Vorschau
  // wird nicht still zu „keine“, sondern weist die ganze Antwort ab.
  const vorschau = data.vorschau ?? null
  if (vorschau !== null && !isVorschau(vorschau)) {
    throw new ApiError(500, 'Unerwartete Antwort vom Server (Me.vorschau).')
  }
  return { ...data, colorScheme, vorschau }
}

export const authApi = {
  me: () => apiFetch<Me>('/api/me', {}, parseMe),
  updateProfile: (displayName: string) =>
    apiFetch<Me>('/api/me', { method: 'PATCH', body: JSON.stringify({ displayName }) }, parseMe),
  setColorScheme: (colorScheme: ColorScheme) =>
    apiFetch<Me>('/api/me/color-scheme', { method: 'PUT', body: JSON.stringify({ colorScheme }) }, parseMe),
  // Anmeldung per Einmalcode (Plan #1801): anfordern, abgetippten Code oder Link aus der Mail einlösen.
  requestCode: (email: string) => apiFetch<void>('/api/auth/code/request', jsonBody({ email })),
  verifyCode: (email: string, code: string) =>
    apiFetch<Me>('/api/auth/code/verify', jsonBody({ email, code }), parseMe),
  linkLogin: (token: string) => apiFetch<Me>('/api/auth/code/link', jsonBody({ token }), parseMe),
  logout: () => apiFetch<void>('/api/auth/logout', { method: 'POST' }),
  /** Beendet die Vorschau dieser Anmeldung; danach gilt wieder die eigene Sicht (Plan #1914). */
  vorschauBeenden: () => apiFetch<void>('/api/vorschau', { method: 'DELETE' }),
  /** Wann welcher Admin die App als das eigene Konto angesehen hat (E12). */
  eigenesVorschauProtokoll: () => apiFetch<VorschauEintrag[]>('/api/me/vorschau-protokoll'),
  register: (email: string, displayName: string) =>
    apiFetch<{ id: number; email: string }>('/api/auth/register', jsonBody({ email, displayName })),
}
