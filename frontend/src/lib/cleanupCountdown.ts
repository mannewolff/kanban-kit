import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Verbleibender Cleanup-Countdown einer Done-Karte (portiert aus der Toolbox). Reine Funktion,
 * damit sie deterministisch (mit fixem `now`) getestet werden kann.
 *
 * @param movedToDoneAt ISO-Timestamp des Zugs nach Done
 * @param retentionDays Retention in Tagen
 * @param now aktuelle Zeit in ms (Default Date.now())
 * @returns Tage bis zur Archivierung, mindestens 0
 */
export function cleanupDaysRemaining(
  movedToDoneAt: string,
  retentionDays: number,
  now: number = Date.now(),
): number {
  const moved = new Date(movedToDoneAt).getTime()
  const remaining = retentionDays * 86_400_000 - (now - moved)
  return Math.max(0, Math.ceil(remaining / 86_400_000))
}

/** Label-Text für den Done-Archivierungs-Hinweis in der Sprache. */
export function cleanupCountdownLabel(days: number, sprache: Sprache = 'de'): string {
  const t = woerterbuch(sprache).ARCHIVIERUNG
  if (days === 0) return t.HEUTE
  if (days === 1) return t.MORGEN
  return t.IN_TAGEN(days)
}
