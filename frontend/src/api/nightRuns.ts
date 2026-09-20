import type { NightRunErrorClass, NightRunMode, NightRunState } from '../lib/nightRunLog'
import { apiFetch } from './client'

/**
 * Anbindung der drei Nachtlauf-Endpunkte (Issue #723).
 *
 * Fehlerklasse, Zustand und Betriebsart kommen aus `lib/nightRunLog.ts` und werden hier
 * **nicht** neu deklariert: Verbindlich ist der Parser (Plan #718, A13), und ein eigener
 * Union-Typ waere eine dritte Kopie, die der Abgleichtest nicht sieht.
 */

/**
 * Die Modi, die der Server kennt — `NIGHTPLAN` bleibt bewusst browser-only (Plan #803,
 * Architektonische Entscheidung 8): Backend-Enum und `ck_night_run_mode` kennen
 * `IMPLEMENTATION`, `REVIEW` und seit Issue #853 `CHAIN`; `NIGHTPLAN` ist der einzige Ausschluss.
 * Diese Verengung erzwingt den Compiler-Schutz in `NightRunPage.tsx` (`istEinlieferbar`) — ein Cast
 * an `mode` hebelte ihn aus. Der Ausschluss ist der einzige Ort, der gepflegt wird: Ein neuer
 * einlieferbarer Modus weitet diesen Typ von selbst.
 *
 * `INTERACTIVE` (Issue #1010, Plan #1007 E23) kommt **hinzu** statt aus `NightRunMode`: Eine
 * interaktive Sitzung hat kein Nachtlauf-Protokoll, der Browser-Parser kann sie nie erzeugen, und
 * eingeliefert wird sie allein vom Hook des Kits. Sie im Parser-Typ zu führen, hieße eine Zeile zu
 * versprechen, die dort nie steht. Wer den Typ erweitert, muss die `Record`-Tabellen darüber
 * pflegen — `LAUF_ART_TEXT` (`KartenAnlaeufe`) und `MODUS` (`lib/leitstand`) brechen sonst `tsc`.
 */
export type NightRunServerMode = Exclude<NightRunMode, 'NIGHTPLAN'> | 'INTERACTIVE'

/**
 * Die Gattung eines Eintrags: **was** er ist — ein Lauf des Runners oder eine Sitzung am Rechner
 * eines Menschen (Issue #1010, Plan #1007). Nicht zu verwechseln mit {@link NightRunServerMode},
 * der **Art** des Laufs, und nicht mit der Herkunft (`origin`), dem **Weg** ans Board.
 */
export type NightRunKind = 'NIGHT' | 'INTERACTIVE'

/**
 * Der gemeldete Verbrauch eines Laufs oder eines Arbeitspakets (Issue #948).
 *
 * Alle Felder sind optional, weil der Browser eine fehlende Angabe **weglaesst** statt `null` zu
 * senden — dieselbe Regel wie bei den uebrigen Submission-Feldern. Der Server liest ein fehlendes
 * Feld als „nicht gemessen", und das ist etwas anderes als eine gemessene Null.
 *
 * Vom Upload-Weg gefuellt wird allein `costUsd`: Die drei Mengen entstehen erst mit der Messung im
 * Runner und kommen ueber den Token-Weg herein.
 */
export interface NightRunUsage {
  costUsd?: number
  inputTokens?: number
  outputTokens?: number
  cachedInputTokens?: number
}

/** Ein Arbeitspaket, wie es an den Server geht — der Ausschnitt des Parser-Ergebnisses, den der Server kennt. */
export interface NightRunItemSubmission {
  cardNumber: number
  title: string
  state: NightRunState
  errorClass?: NightRunErrorClass
  durationMs?: number
  commitHash?: string
  excerpt?: string
  usage?: NightRunUsage
}

/** Ein einzuliefernder Lauf. */
export interface NightRunSubmission {
  startedAt: string
  mode: NightRunServerMode
  durationMs: number
  processedCount: number
  skippedCount: number
  unparsedCount: number
  unparsedSample?: string
  usage?: NightRunUsage
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
  usage: NightRunUsageView | null
}

/**
 * Der aufbewahrte Verbrauch, wie der Server ihn schickt (Issue #949).
 *
 * Ein **zweiter** Typ neben {@link NightRunUsage}, und zwar mit `| null` statt `?:` — aus demselben
 * Grund wie bei {@link NightRunItemView}: Der Server sendet ein fehlendes Feld als `null`, nicht
 * als fehlenden Schluessel. Ein `?:` beschriebe hier eine Antwort, die es nicht gibt, und niemand
 * bemerkte es, bis eine Rechnung auf `null` laeuft.
 *
 * `usage` selbst ist `null`, wenn ueberhaupt nichts gemessen wurde.
 */
export interface NightRunUsageView {
  costUsd: number | null
  inputTokens: number | null
  outputTokens: number | null
  cachedInputTokens: number | null
}

/**
 * Der Befund eines Laufs, wie der Server ihn seit Issue #1078 mitschickt.
 *
 * Der Massstab „nicht vollstaendig gelungen" lebt dort (Plan #1072 E2) — der Browser liest ihn und
 * rechnet ihn nicht nach. Er kommt als **Daten**: kein fertiger Satz, sondern das massgebliche
 * Paket und der Grund eines Laufs ohne Arbeit. Den Text bildet weiterhin `nightRunZustandsText`,
 * damit die Stoerzeile und die Nachtlauf-Auswertung denselben tragen (AK 6 der fachlichen Quelle).
 */
export interface NightRunOutcomeView {
  verdict: 'SUCCEEDED' | 'FAILED' | 'WAITING' | 'RUNNING'
  /** Das Paket, das den Ausgang bestimmt; `null` bei Erfolg, bei laufendem Lauf und ohne Arbeit. */
  decisiveItem: {
    cardNumber: number
    state: NightRunState
    errorClass: NightRunErrorClass | null
  } | null
  /** Grund, warum der Lauf nichts abgearbeitet hat; `null`, wenn er gearbeitet hat. */
  noWorkReason: string | null
}

/** Ein aufbewahrter Lauf samt seiner Arbeitspakete. */
export interface NightRunView {
  id: number
  startedAt: string
  mode: NightRunServerMode
  durationMs: number
  processedCount: number
  skippedCount: number
  unparsedCount: number
  /** Auszug der ungedeuteten Zeilen; `null`, wenn es keine gab — siehe {@link NightRunItemView}. */
  unparsedSample: string | null
  createdAt: string
  /** Wie der Lauf hereinkam: `UPLOAD` ueber den Browser, `TOKEN` gemeldet von der Kette selbst. */
  origin: 'UPLOAD' | 'TOKEN'
  /** Der Name des meldenden Tokens; `null` bei einem hochgeladenen Lauf. */
  tokenName: string | null
  /** `false`, solange die Kette den Lauf noch nicht abgeschlossen gemeldet hat. */
  complete: boolean
  /** Zeitpunkt der letzten Meldung; `null`, wenn der Lauf seit dem Anlegen nicht gemeldet wurde. */
  updatedAt: string | null
  usage: NightRunUsageView | null
  /**
   * Grund, warum der Lauf nichts abgearbeitet hat (Issue #1068). `null` heisst „der Lauf hat
   * gearbeitet" oder „eingeliefert vor der Umstellung" — beides ist kein Befund. Felder ohne Wert
   * kommen als `null` und nicht als fehlender Schluessel (Issue #734).
   */
  noWorkReason: string | null
  /**
   * Der Befund des Laufs (Issue #1078). **Pflichtfeld**, kein `?:` — optional verschoebe es die eine
   * Wahrheit wieder in den Browser, weil jede Lesestelle einen Rueckfallweg braeuchte.
   */
  outcome: NightRunOutcomeView
  items: NightRunItemView[]
}

/**
 * Ein Anlauf an einer Karte — ein Arbeitspaket aus irgendeinem Lauf, auch einem verdrängten
 * (Issue #967). Felder ohne Wert kommen als `null` und nicht als fehlender Schlüssel — derselbe
 * Grund wie bei {@link NightRunItemView} (Issue #734).
 */
export interface NightRunAnlauf {
  startedAt: string
  mode: NightRunServerMode
  /**
   * Die Gattung des Anlaufs (Issue #1015). `null` steht für eine Antwort, die das Feld noch nicht
   * führt — ein Server vor der Migration `V34`: Der heutige Endpunkt schickt die Gattung immer und
   * liest einen Anlauf ohne eigene Gattung selbst schon als `NIGHT`. Der Typ lässt das Fehlen
   * trotzdem zu, damit die Anzeige den Fall behandeln **muss**, statt ihn ungeprüft als Sitzung
   * oder als leere Beschriftung durchzureichen.
   */
  kind: NightRunKind | null
  state: NightRunState
  errorClass: NightRunErrorClass | null
  durationMs: number | null
  commitHash: string | null
  usage: NightRunUsageView | null
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
  /** Die Anläufe einer Karte über alle Läufe, jüngster zuerst (Issue #967). */
  anlaeufeDerKarte: (projectId: number, cardNumber: number) =>
    apiFetch<NightRunAnlauf[]>(
      `/api/projects/${projectId}/night-runs/items?cardNumber=${encodeURIComponent(cardNumber)}`,
    ),
}

export type NightRunsApi = typeof nightRunsApi
