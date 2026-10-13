import { formatierer } from '../i18n/format'
import type { Sprache } from '../i18n/woerterbuch'

/**
 * Formatiert eine Dauer in Sekunden menschenlesbar in der Sprache. `null` (keine Datenbasis) wird zu
 * „n. v." bzw. „n/a". Grobkörnig: ab einem Tag „T + Std", ab einer Stunde „Std + Min", darunter
 * „Min" bzw. „s" — die Einheiten stehen im Wörterbuch.
 */
export function formatDuration(seconds: number | null, sprache: Sprache = 'de'): string {
  return formatierer(sprache).dauer(seconds)
}
