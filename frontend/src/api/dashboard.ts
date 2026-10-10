import { apiFetch } from './client'

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
}

export type DashboardApi = typeof dashboardApi
