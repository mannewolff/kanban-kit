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

/** Eine Karte, die ungewöhnlich lange in einer Spalte lag. */
export interface OutlierCard {
  cardId: number
  number: number
  title: string
  columnName: string
  dwellSeconds: number
}

/** Zykluszeit-Kennzahlen eines Boards. Dauern in Sekunden; `null` = keine Datenbasis. */
export interface BoardDashboardKpis {
  columnDwell: ColumnDwell[]
  throughput: WeeklyThroughput[]
  avgLeadTimeSeconds: number | null
  avgCycleTimeSeconds: number | null
  outliers: OutlierCard[]
}

export const dashboardApi = {
  get: (boardId: number) => apiFetch<BoardDashboardKpis>(`/api/boards/${boardId}/dashboard`),
}

export type DashboardApi = typeof dashboardApi
