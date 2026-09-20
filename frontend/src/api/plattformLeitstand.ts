import type { NightRunOutcomeView } from './nightRuns'
import { apiFetch } from './client'

/**
 * Anbindung des Plattform-Leitstands (Issue #1083, Server in #1080).
 *
 * Der Pfadstamm ist `/api/admin` und nicht `/api/platform` — das ist eine Sicherheitsentscheidung
 * des Servers (Plan #1072 E24): Nur dort verlangt die Filterkette eine Sitzung und laesst kein
 * Token zu. Der Browser folgt ihr hier bloss.
 */

/**
 * Eine offene Stoerung, wie der Plattform-Leitstand sie zeigt.
 *
 * Der **Grund** kommt als `outcome` und nicht als fertiger Satz: Den Text bildet der Browser aus
 * denselben Tabellen, aus denen die Nachtlauf-Auswertung ihn zeigt (AK 6 der fachlichen Quelle
 * #1064). Ein Satz vom Server waere die zweite Formulierung desselben Sachverhalts.
 */
export interface DisruptionView {
  nightRunId: number
  projectId: number
  projectName: string
  startedAt: string
  outcome: NightRunOutcomeView
}

export const plattformLeitstandApi = {
  /** Die offenen Stoerungen aller teilnehmenden Projekte, juengste zuoberst (AK 4, 13). */
  liste: () => apiFetch<DisruptionView[]>('/api/admin/disruptions'),
  /**
   * Quittiert eine Stoerung — „ich habe es gesehen" (AK 8). Idempotent: Ein zweiter Aufruf ist kein
   * Fehler, weil zwei Admins dieselbe Zeile gleichzeitig wegraeumen koennen.
   */
  quittieren: (laufId: number) =>
    apiFetch<void>(`/api/admin/disruptions/${laufId}`, { method: 'DELETE' }),
}
