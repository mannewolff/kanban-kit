import type { NightRunErrorClass, NightRunMode, NightRunState } from '../lib/nightRunLog'
import { apiFetch } from './client'

/**
 * Anbindung der drei Nachtlauf-Endpunkte (Issue #723).
 *
 * Fehlerklasse, Zustand und Betriebsart kommen aus `lib/nightRunLog.ts` und werden hier
 * **nicht** neu deklariert: Verbindlich ist der Parser (Plan #718, A13), und ein eigener
 * Union-Typ waere eine dritte Kopie, die der Abgleichtest nicht sieht.
 */

/** Ein Arbeitspaket, wie es an den Server geht — der Ausschnitt des Parser-Ergebnisses, den der Server kennt. */
export interface NightRunItemSubmission {
  cardNumber: number
  title: string
  state: NightRunState
  errorClass?: NightRunErrorClass
  durationMs?: number
  commitHash?: string
  excerpt?: string
}

/** Ein einzuliefernder Lauf. */
export interface NightRunSubmission {
  startedAt: string
  mode: NightRunMode
  durationMs: number
  processedCount: number
  skippedCount: number
  unparsedCount: number
  unparsedSample?: string
  items: NightRunItemSubmission[]
}

/** Ergebnis je eingeliefertem Lauf, in Anfragereihenfolge. */
export interface NightRunResult {
  startedAt: string
  created: boolean
}

/**
 * Ein aufbewahrtes Arbeitspaket.
 *
 * Die Felder ohne Wert kommen als `null` und nicht als fehlender Schlüssel: Das Backend hält sie
 * `@Nullable`, und weder `@JsonInclude(NON_NULL)` noch `default-property-inclusion` sind gesetzt.
 * Sie deshalb als `?:` zu deklarieren beschriebe eine Antwort, die es nicht gibt — und weil die
 * Antwort ungeprüft als dieser Typ gilt, bemerkt das niemand, bis eine Seite `.split()` darauf ruft
 * (Issue #734).
 */
export interface NightRunItemView {
  id: number
  cardNumber: number
  title: string
  state: NightRunState
  errorClass: NightRunErrorClass | null
  durationMs: number | null
  commitHash: string | null
  excerpt: string | null
}

/** Ein aufbewahrter Lauf samt seiner Arbeitspakete. */
export interface NightRunView {
  id: number
  startedAt: string
  mode: NightRunMode
  durationMs: number
  processedCount: number
  skippedCount: number
  unparsedCount: number
  /** Auszug der ungedeuteten Zeilen; `null`, wenn es keine gab — siehe {@link NightRunItemView}. */
  unparsedSample: string | null
  createdAt: string
  items: NightRunItemView[]
}

/** Je Fehlerklasse die Zahl der aufbewahrten Laeufe, in denen sie vorkam; fehlende Klassen kamen nie vor. */
export type NightRunErrorClassCounts = Partial<Record<NightRunErrorClass, number>>

export const nightRunsApi = {
  submit: (projectId: number, runs: NightRunSubmission[]) =>
    apiFetch<NightRunResult[]>(`/api/projects/${projectId}/night-runs`, {
      method: 'POST',
      body: JSON.stringify({ runs }),
    }),
  list: (projectId: number) => apiFetch<NightRunView[]>(`/api/projects/${projectId}/night-runs`),
  errorClassCounts: (projectId: number) =>
    apiFetch<NightRunErrorClassCounts>(`/api/projects/${projectId}/night-runs/error-class-counts`),
}

export type NightRunsApi = typeof nightRunsApi
