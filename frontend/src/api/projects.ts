import { apiFetch } from './client'

export interface Project {
  id: number
  name: string
  role: string
  createdAt: string
  /** Ob das Projekt am Plattform-Leitstand teilnimmt (Issue #1076). */
  dashboardParticipation?: boolean
  /**
   * Ob **dieser** Aufrufer die Teilnahme schalten darf — echte Mitgliedschaft als OWNER oder ADMIN
   * (Issue #1076, Plan #1072 E7).
   *
   * Sagt der Server, weil der Browser es nicht ausrechnen kann: `ProjectService#list` setzt fuer
   * einen Plattform-Admin ohne Mitgliedschaft die synthetische Rolle `OWNER`, aus `role` ist die
   * echte Mitgliedschaft also nicht ablesbar.
   */
  participationEditable?: boolean
}

const json = (data: unknown, method: string): RequestInit => ({ method, body: JSON.stringify(data) })

export const projectsApi = {
  list: () => apiFetch<Project[]>('/api/projects'),
  create: (name: string, ownerEmail: string) =>
    apiFetch<Project>('/api/projects', json({ name, ownerEmail }, 'POST')),
  rename: (id: number, name: string) => apiFetch<Project>(`/api/projects/${id}`, json({ name }, 'PATCH')),
  nextCardNumber: (id: number) =>
    apiFetch<{ nextCardNumber: number }>(`/api/projects/${id}/next-card-number`),
  setNextCardNumber: (id: number, nextCardNumber: number) =>
    apiFetch<{ nextCardNumber: number }>(
      `/api/projects/${id}/next-card-number`,
      json({ nextCardNumber }, 'PUT'),
    ),
  /**
   * Schaltet die Teilnahme am Plattform-Leitstand (Issue #1084, Server in #1077). Eigener Endpunkt
   * statt eines Feldes am PATCH: Das Umbenennen laesst einen Plattform-Admin passieren, dieser Weg
   * ausdruecklich nicht (AK 16).
   */
  setDashboardParticipation: (id: number, participating: boolean) =>
    apiFetch<Project>(
      `/api/projects/${id}/dashboard-participation`,
      json({ participating }, 'PUT'),
    ),
  /**
   * Legt das Monatsbudget des Projekts fest (Issue #1968, Server in #1965). Der Betrag geht als
   * Zeichenkette mit Punkt als Dezimaltrenner (Plan #1962 E4) — kein Gleitkomma fuer Geld; das
   * Komma wandelt das Eingabefeld, nicht diese Funktion.
   */
  setMonthlyBudget: (id: number, amount: string) =>
    apiFetch<void>(`/api/projects/${id}/monthly-budget`, json({ amountUsd: amount }, 'PUT')),
  /** Entfernt das Monatsbudget des Projekts (Issue #1968). */
  removeMonthlyBudget: (id: number) =>
    apiFetch<void>(`/api/projects/${id}/monthly-budget`, { method: 'DELETE' }),
  remove: (id: number) => apiFetch<void>(`/api/projects/${id}`, { method: 'DELETE' }),
  transferOwner: (id: number, newOwnerUserId: number) =>
    apiFetch<void>(`/api/projects/${id}/owner`, json({ newOwnerUserId }, 'POST')),
}
