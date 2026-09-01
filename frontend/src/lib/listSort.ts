/**
 * Sortierung der Listen-Ansicht nach Spalteninhalt (Plan #622) — reine Logik, frei von React und
 * MUI, damit die fünf Vergleichsfälle mit ihren Leerwert- und Gleichstandsregeln einzeln prüfbar
 * sind, ohne eine Seite zu rendern.
 */
import type { BoardColumn } from '../api/boards'
import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'
import { epicOfCard } from './cardEpic'
import { epicShortcode } from './epicMeta'
import { stripMarkdown } from './listExcerpt'

/** Die sortierbaren Spalten der Listen-Ansicht. */
export type ColumnKey = 'number' | 'status' | 'epic' | 'title' | 'excerpt'

export type SortDir = 'asc' | 'desc'

/** Aktive Sortierung, oder `null` für den Grundzustand (Board-Reihenfolge). */
export type SortState = { key: ColumnKey; dir: SortDir } | null

/**
 * Was die Comparators an Umgebung brauchen: die Spalten des Boards für die Status-Ordnung und die
 * Vorhaben für das Kürzel. Bewusst die Vorhaben-Liste statt einer ID-Map — die Zuordnung Karte →
 * Vorhaben ist eine Regel für sich (`epicOfCard`), die hier nicht ein zweites Mal entstehen soll.
 */
export interface SortContext {
  columnById: Map<number, BoardColumn>
  epics: readonly Epic[]
}

/**
 * Drei-Zustands-Zyklus eines Spaltenkopfs: neue Spalte → aufsteigend, dieselbe Spalte → absteigend,
 * noch einmal → keine Sortierung.
 */
export function nextSortState(current: SortState, key: ColumnKey): SortState {
  if (current?.key !== key) return { key, dir: 'asc' }
  if (current.dir === 'asc') return { key, dir: 'desc' }
  return null
}

/**
 * Vergleichswert einer Karte in der sortierten Spalte. `ende` markiert Karten, die in **beiden**
 * Richtungen hinten stehen (archiviert, kein Vorhaben, leere Beschreibung): nach oben zu wandern
 * schöbe eine halbe Bildschirmhöhe leerer Zellen über die eigentliche Antwort. Je Spalte trägt
 * entweder `zahl` oder `text` den Wert, die jeweils andere bleibt neutral.
 */
interface Schluessel {
  ende: boolean
  zahl: number
  text: string
}

/** Position der Board-Spalte; eine nicht mehr existierende Spalte gilt als Position 0. */
function spaltenPosition(card: Card, ctx: SortContext): number {
  return ctx.columnById.get(card.columnId)?.position ?? 0
}

const SCHLUESSEL: Record<ColumnKey, (card: Card, ctx: SortContext) => Schluessel> = {
  number: (card) => ({ ende: false, zahl: card.number, text: '' }),
  // Der Status folgt der fachlichen Kette der Spalten („Backlog, Ready, …"), nicht dem Alphabet.
  // Archivierte Karten haben in dieser Kette keinen Platz.
  status: (card, ctx) =>
    card.archived
      ? { ende: true, zahl: 0, text: '' }
      : { ende: false, zahl: spaltenPosition(card, ctx), text: '' },
  epic: (card, ctx) => {
    // Der Leerwert hängt am fehlenden Vorhaben, nicht am Kürzel: `epicShortcode` fällt notfalls
    // auf „VORH" zurück und ist nie leer.
    const epic = epicOfCard(card, ctx.epics)
    return epic === undefined
      ? { ende: true, zahl: 0, text: '' }
      : { ende: false, zahl: 0, text: epicShortcode(epic.title, epic.shortcode) }
  },
  title: (card) => ({ ende: false, zahl: 0, text: card.title }),
  excerpt: (card) => {
    // Sortiert wird über den angezeigten, gestrippten Text — über den rohen Markdown zu sortieren
    // trüge führende `#`/`*` in die Reihenfolge.
    const text = stripMarkdown(card.description ?? '')
    return { ende: text === '', zahl: 0, text }
  },
}

function vergleicheSchluessel(a: Schluessel, b: Schluessel): number {
  return a.zahl - b.zahl || a.text.localeCompare(b.text, 'de', { numeric: true, sensitivity: 'base' })
}

/** Die Ordnung, die die Liste auch im Grundzustand herstellt: Spaltenposition, dann Position darin. */
function grundordnung(a: Card, b: Card, ctx: SortContext): number {
  return spaltenPosition(a, ctx) - spaltenPosition(b, ctx) || a.positionInColumn - b.positionInColumn
}

/**
 * Sortiert die Karten nach der gewählten Spalte. Ohne Sortierung kommt eine unveränderte **Kopie**
 * zurück — ein einheitlicher Rückgabevertrag erspart dem Aufrufer die Fallunterscheidung; die
 * Eingabe bleibt in jedem Fall unangetastet.
 *
 * Bei Gleichstand greift die Grundordnung, und zwar in **beiden** Richtungen aufsteigend: Sonst
 * ordnete ein Richtungswechsel auch gleichwertige Karten neu, ohne dass in der sortierten Spalte
 * etwas anderes stünde.
 */
export function sortCards(cards: readonly Card[], state: SortState, ctx: SortContext): Card[] {
  const kopie = [...cards]
  if (state === null) return kopie
  const schluessel = SCHLUESSEL[state.key]
  const richtung = state.dir === 'asc' ? 1 : -1
  return kopie.sort((a, b) => {
    const ka = schluessel(a, ctx)
    const kb = schluessel(b, ctx)
    if (ka.ende !== kb.ende) return ka.ende ? 1 : -1
    return richtung * vergleicheSchluessel(ka, kb) || grundordnung(a, b, ctx)
  })
}
