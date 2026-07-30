import type { Crumb } from '../components/Breadcrumbs'

/**
 * Wo eine Karte liegt — Projekt, Board und Spalte. Gedacht für Ansichten, die eine Karte ohne ihren
 * Ort zeigen: das Detail-Modal aus der Nummernsuche, aus dem Dashboard oder aus einer Liste.
 *
 * Board und Spalte stecken bewusst in **einem** Feld statt in zwei parallel nullable Feldern: Eine
 * board-lose Pool-Idee hat weder Board noch Spalte, board-gebundene Karten haben stets ein Board.
 * So kann kein widersprüchlicher Zwischenzustand („Spalte ohne Board") entstehen.
 */
export interface CardLocation {
  projectId: number
  /** `null`, solange der Projektname noch nachgeladen wird (`useProjectName`). */
  projectName: string | null
  /** Board der Karte; `null` bei einer board-losen Pool-Idee — dann steht „Ideen" für den Ort. */
  board: {
    id: number
    name: string
    /**
     * Archiviertes Board: Die Karte bleibt auffindbar, das Board selbst ist über die normale API
     * nicht mehr ladbar. Im Pfad deshalb gekennzeichnet und nicht verlinkt.
     */
    archived?: boolean
    /**
     * Spaltenname; leer, wenn die Spalte nicht aufgelöst werden konnte — dann entfällt das
     * Segment. `null` und `undefined` sind gleichbedeutend, damit Aufrufer ihren jeweiligen
     * Leerwert (Backend-Feld bzw. erfolgloses `find`) ohne Umrechnung durchreichen können.
     */
    columnName?: string | null
  } | null
}

/**
 * Ortspfad einer Karte als Breadcrumb-Segmente: `Projekt / Board / Spalte`, für eine Pool-Idee
 * `Projekt / Ideen`. Verlinkt ist, wohin man tatsächlich navigieren kann — die Spalte hat keine
 * eigene Route, ein archiviertes Board keine erreichbare Seite.
 *
 * Ein noch nicht geladener Projektname fällt auf „Projekt" zurück, damit der Pfad nicht mit einem
 * leeren Segment beginnt (dasselbe Muster wie in den Seiten-Breadcrumbs).
 */
export function cardLocationCrumbs(location: CardLocation): Crumb[] {
  const project: Crumb = {
    label: location.projectName ?? 'Projekt',
    to: `/projects/${location.projectId}`,
  }
  const { board } = location
  if (board === null) {
    return [project, { label: 'Ideen', to: `/projects/${location.projectId}/ideas` }]
  }
  const boardCrumb: Crumb = board.archived
    ? { label: `${board.name} (archiviert)` }
    : { label: board.name, to: `/boards/${board.id}` }
  return board.columnName == null
    ? [project, boardCrumb]
    : [project, boardCrumb, { label: board.columnName }]
}

/**
 * Derselbe Ort einzeilig, für Stellen ohne Platz für einen Breadcrumb-Pfad (z. B. die Zweitzeile
 * eines Auswahleintrags). Bewusst aus denselben Segmenten abgeleitet, damit Sonderfälle wie
 * „archiviert" oder die Pool-Idee nicht an zwei Stellen gepflegt werden müssen.
 */
export function cardLocationLabel(location: CardLocation): string {
  return cardLocationCrumbs(location)
    .map((crumb) => crumb.label)
    .join(' / ')
}
