import type { BoardColumn } from '../api/boards'
import { canonicalColumnKey } from './columnMeta'

/**
 * Das Präfix einer Idee, wie das Kit es schreibt (`board.mjs issue create --art idea`) und der
 * Server es als Kartenart erkennt (Plan #1989, E2). Englisch auf beiden Wegen, Siri wie `/mobil`.
 */
const IDEE_PRAEFIX = '[Idea] '

/** Höchstlänge eines Kartentitels beim Server. */
const TITEL_GRENZE = 300

/**
 * So viele Zeichen Text passen hinter das Präfix, ohne dass der Titel die 300 Zeichen des Servers
 * überschreitet (E2). Die mobile Oberfläche kürzt nicht, sie sperrt den Knopf darüber.
 */
export const IDEE_TEXT_GRENZE = TITEL_GRENZE - IDEE_PRAEFIX.length

/** Der Kartentitel einer Idee: Präfix vor dem Text, Leerraum am Rand abgeschnitten. */
export const ideeTitel = (text: string): string => IDEE_PRAEFIX + text.trim()

/** Ob der Text einer Idee über der Grenze aus {@link IDEE_TEXT_GRENZE} liegt. */
export const ideeZuLang = (text: string): boolean => text.trim().length > IDEE_TEXT_GRENZE

/**
 * Die Spalte, in die eine Idee fällt (E6): die, deren Name nach `columnMeta` Backlog ist, sonst die
 * erste nach Position — dieselbe Regel und derselbe Rückfall wie beim Ingest. Ohne Spalten nichts.
 */
export function ideenSpalte(spalten: readonly BoardColumn[]): BoardColumn | undefined {
  const sortiert = [...spalten].sort((a, b) => a.position - b.position)
  return sortiert.find((s) => canonicalColumnKey(s.name) === 'BACKLOG') ?? sortiert[0]
}
