import { apiFetch } from './client'

/**
 * Stand der Sicherung für den Plattform-Admin (Issue #833; Endpunkt aus Issue #826, Plan #825).
 *
 * Die Zeitpunkte kommen als ISO-8601-Zeichenketten und ohne Wert als `null`, nicht als fehlender
 * Schlüssel — wie in den übrigen API-Modulen. Fehler wirft {@link apiFetch} als
 * {@link import('./client').ApiError}; der anzeigbare Text kommt aus `apiErrorMessage`.
 */

/** Das Gesamturteil über den Stand der Sicherung. `ABGESCHALTET` heißt „läuft nicht", nicht „kaputt". */
export type BackupVerdict = 'OK' | 'VERALTET' | 'FEHLGESCHLAGEN' | 'ABGESCHALTET'

/** Die Arten von Sicherungsläufen, in der Reihenfolge, in der der Server sie liefert. */
export type BackupKind = 'BASIS' | 'WAL' | 'SPIEGEL' | 'OFFSITE'

/** Ausgang eines einzelnen Laufs. */
export type BackupOutcome = 'ERFOLG' | 'FEHLSCHLAG'

/** Der Stand einer Art. `stale` = seit dem letzten Erfolg ist mehr als die Warnfrist vergangen. */
export interface BackupKindStatus {
  kind: BackupKind
  lastStartedAt: string | null
  lastFinishedAt: string | null
  lastOutcome: BackupOutcome | null
  detail: string | null
  bytes: number | null
  lastSuccessAt: string | null
  ageSeconds: number | null
  warnAfterSeconds: number
  stale: boolean
}

/**
 * Der Stand insgesamt.
 *
 * `alertMailEnabled` ist nicht dasselbe wie `enabled`: Auch eine laufende Sicherung alarmiert nur
 * ins Protokoll, wenn der Mailversand aus ist (Plan #825 E11). Dann ist diese Ansicht der einzige
 * verlässliche Weg — und sagt das.
 */
export interface BackupStatus {
  verdict: BackupVerdict
  enabled: boolean
  alertMailEnabled: boolean
  targetLabel: string
  kinds: BackupKindStatus[]
}

export const backupApi = {
  getStatus: () => apiFetch<BackupStatus>('/api/admin/backup/status'),
}

export type BackupApi = typeof backupApi
