import { apiFetch } from './client'
import { leserZone } from './nightRunUsage'

/** Durchschnittliche Verweildauer in einer Spalte (nur abgeschlossene Aufenthalte). */
export interface ColumnDwell {
  columnId: number
  columnName: string
  avgDwellSeconds: number | null
  sampleCount: number
}

/** Abgeschlossene Karten in einem Wochenfenster (Beginn des 7-Tage-Fensters, ISO-8601). */
export interface WeeklyThroughput {
  weekStart: string
  doneCount: number
}

/**
 * Mittelwert der Implementierungszeit in einem Wochenfenster — dieselben Fenster wie beim
 * Durchsatz. Ohne gemessene Karte ist der Mittelwert `null` und `sampleCount` 0, nie 0 Sekunden.
 */
export interface WeeklyImplementation {
  weekStart: string
  avgImplementationSeconds: number | null
  sampleCount: number
}

/** Eine Karte, die ungewöhnlich lange in einer Spalte lag. */
export interface OutlierCard {
  cardId: number
  number: number
  title: string
  columnName: string
  dwellSeconds: number
}

/** Kennzahlen eines Boards. Dauern in Sekunden; `null` = keine Datenbasis. */
export interface BoardDashboardKpis {
  columnDwell: ColumnDwell[]
  throughput: WeeklyThroughput[]
  avgLeadTimeSeconds: number | null
  /**
   * Anzahl der abgeschlossenen Karten hinter `avgLeadTimeSeconds` — nicht die Summe des Durchsatzes,
   * die nur zwölf Wochen umfasst.
   */
  leadTimeSampleCount: number
  /** Summe der abgeschlossenen In-Progress-Aufenthalte je erledigter Karte, gemittelt. */
  avgImplementationSeconds: number | null
  /**
   * Anzahl der Karten hinter `avgImplementationSeconds` — kleiner als `leadTimeSampleCount`, sobald
   * eine fertige Karte nie in einer „In Progress“-artigen Spalte lag. `0` heißt „keine Datenbasis“
   * und ist damit die eine Quelle für die Leerwert-Optik der Kennzahl.
   */
  implementationSampleCount: number
  /** Implementierungszeit je Woche über dieselben zwölf Fenster wie `throughput` (Issue #1540). */
  implementationWeekly: WeeklyImplementation[]
  outliers: OutlierCard[]
}

/**
 * Implementierungszeit der Karten eines Boards, die in einem Zeitraum fertig wurden (Issue #1540).
 * `implementationSampleCount` 0 heißt „keine Datenbasis".
 */
export interface ImplementationTime {
  avgImplementationSeconds: number | null
  implementationSampleCount: number
}

/**
 * Ein Kalendertag der Platte „Fluss“ in der Zone des Lesers (Issue #1773, Plan #1765 E9). Ist sein
 * Stand unbekannt (`known = false`), sind alle fünf Zahlen `null` — eine Lücke, keine Null.
 */
export interface FlowDay {
  /** Kalendertag, `YYYY-MM-DD`. */
  date: string
  known: boolean
  backlog: number | null
  ready: number | null
  inProgress: number | null
  inReview: number | null
  /** Seit Beginn des Zeitraums fertig gewordene Arbeitspakete. */
  done: number | null
}

/** Die Platte „Fluss“: die 84 Tage bis heute, der älteste zuerst (Issue #1773). */
export interface BoardFlow {
  days: FlowDay[]
  /** Der erste Tag mit sicherem Stand, wenn er nach dem ersten Tag des Zeitraums liegt; sonst `null`. */
  dataSince: string | null
}

/** Eine Karte der Platte „Alter in Arbeit“ mit ihrem Alter seit dem letzten Eintritt. */
export interface AgingCard {
  id: number
  number: number
  title: string
  ageSeconds: number
  /** Eintritt unbekannt: `ageSeconds` ist nur der Mindestwert ab dem frühesten bekannten Zeitpunkt. */
  ageIsMinimum: boolean
  overThreshold: boolean
}

/** Ein Zustand der Platte „Alter in Arbeit“: Vergleichswerte, Schwelle und Karten, die älteste zuerst. */
export interface AgingState {
  comparisonCount: number
  /** Die 85-%-Schwelle in Sekunden; `null` unter fünf Vergleichswerten. */
  thresholdSeconds: number | null
  cards: AgingCard[]
}

/** Die Platte „Alter in Arbeit“ je Zustand Ready, In Progress und In Review (Issue #1773). */
export interface AgingWip {
  ready: AgingState
  inProgress: AgingState
  inReview: AgingState
}

/**
 * Die Lage eines Vorhabens in der Rangfolge (Plan #1819, AK 11): ohne Pakete, ohne offenes Paket,
 * zu wenig Durchsatz oder mit Daten der Prognose.
 */
export type EpicForecastState = 'NO_PACKAGES' | 'ALL_DONE' | 'INSUFFICIENT_THROUGHPUT' | 'FORECAST'

/** Ein Dokument, aus dem noch Pakete oder ein Plan folgen. */
export interface EpicForecastPendingDocument {
  number: number
  title: string
}

/** Ein Vorhaben der Platte „Vorhaben“ mit Lage, Zählung und den Daten der Prognose. */
export interface EpicForecastEntry {
  id: number
  number: number
  title: string
  state: EpicForecastState
  /** Arbeitspakete des Vorhabens ohne Archiv. */
  total: number
  /** Davon in In Review oder Done. */
  finished: number
  open: number
  /** Tag `YYYY-MM-DD`, benannt nach seinem Ende um 12 Uhr; `null` außer bei `FORECAST`. */
  p50: string | null
  /** Wie `p50`, mit 85 % Wahrscheinlichkeit. */
  p85: string | null
  /** Die Daten gelten nur „frühestens“, weil aus `pendingDocuments` noch etwas folgt. */
  earliest: boolean
  /** Pläne ohne Arbeitspaket und Fachpläne ohne Plan, aufsteigend nach Nummer. */
  pendingDocuments: EpicForecastPendingDocument[]
  /** Andere angezeigte Vorhaben mit mindestens einem offenen Paket. */
  otherEpicsSharing: number
}

/** Die Prognose der Platte „Vorhaben“ (Issue #1828, Plan #1819). */
export interface EpicForecast {
  /** Tage der letzten 30 abgeschlossenen Tage mit mindestens einem fertigen Arbeitspaket. */
  throughputDays: number
  epics: EpicForecastEntry[]
}

export const dashboardApi = {
  get: (boardId: number) => apiFetch<BoardDashboardKpis>(`/api/boards/${boardId}/dashboard`),
  /**
   * Ohne Grenzen über alle gemessenen Karten des Boards; mit Grenzen über die Karten, die von
   * `from` (einschließlich) bis `to` (ausschließlich) fertig wurden.
   */
  implementationTime: (boardId: number, grenzen?: { from: string; to: string }) => {
    const abfrage = grenzen ? '?' + new URLSearchParams(grenzen).toString() : ''
    return apiFetch<ImplementationTime>(`/api/boards/${boardId}/dashboard/implementation-time${abfrage}`)
  },
  /** Die Tage der Platte „Fluss“ in der Zone des Lesers (Plan #1765, E9). */
  flow: (boardId: number, zone: string = leserZone()) =>
    apiFetch<BoardFlow>(`/api/boards/${boardId}/dashboard/flow?${new URLSearchParams({ zone })}`),
  /** Die Prognose der Vorhaben in der Zone des Lesers (Plan #1819). */
  epicForecast: (boardId: number, zone: string = leserZone()) =>
    apiFetch<EpicForecast>(`/api/boards/${boardId}/dashboard/epic-forecast?${new URLSearchParams({ zone })}`),
  /** Die Platte „Alter in Arbeit“. */
  aging: (boardId: number) => apiFetch<AgingWip>(`/api/boards/${boardId}/dashboard/aging`),
}

export type DashboardApi = typeof dashboardApi
