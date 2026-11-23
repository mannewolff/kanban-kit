import type { Comment } from '../api/comments'
import type { Sprache } from '../i18n/woerterbuch'
import { KOEPFE, traegtKopf } from './kitWortformen'

/**
 * Das Gespräch zwischen Kunde und KI an einer Karte (Issue #2219, Kriterium 2 des Fachplans #2202):
 * die Kommentare mit dem Kopf „Gespräch“ bzw. „Conversation“ (V2). Ob ein Beitrag von der KI stammt,
 * sagt allein der Server über `vonKi` (Kriterium 11, Plan #2206 E6) — nie eine Zeile im Text.
 */

/** Ein Beitrag des Gesprächs. */
export interface Beitrag {
  id: number
  autorName: string
  vonKi: boolean
  /** Der Text ohne die Kopfzeile. */
  text: string
  createdAt: string
  /** Die Sprache des Kopfs, mit dem der Beitrag geschrieben wurde. */
  sprache: Sprache
}

const [KOPF_DE, KOPF_EN] = KOEPFE.gespraech

/** Ob der Kommentar ein Beitrag des Gesprächs ist, gleich in welcher Sprache. */
export function istGespraechsbeitrag(body: string): boolean {
  return traegtKopf(KOEPFE.gespraech, body)
}

/** Alles nach der ersten nichtleeren Zeile — dem Kopf —, ohne Leerraum am Rand. */
function ohneKopf(body: string): string {
  const zeilen = body.split('\n')
  const kopf = zeilen.findIndex((z) => z.trim() !== '')
  return zeilen
    .slice(kopf + 1)
    .join('\n')
    .trim()
}

/** Die Sprache des Kopfs: englisch genau beim englischen Kopf, sonst deutsch. */
function kopfSprache(body: string): Sprache {
  return traegtKopf([KOPF_EN, KOPF_EN], body) ? 'en' : 'de'
}

function alsBeitrag(c: Comment): Beitrag {
  return {
    id: c.id,
    autorName: c.authorName,
    vonKi: c.vonKi,
    text: ohneKopf(c.body),
    createdAt: c.createdAt,
    sprache: kopfSprache(c.body),
  }
}

/** Die Beiträge des Gesprächs in Zeitfolge, gleich alte nach ihrer Kennung — gleich, wie die Kommentare geordnet kommen. */
export function gespraechAus(comments: readonly Comment[]): Beitrag[] {
  return comments
    .filter((c) => istGespraechsbeitrag(c.body))
    .map(alsBeitrag)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id)
}

/** „Die KI antwortet gleich“: Es gibt ein Gespräch, und sein jüngster Beitrag ist nicht von der KI. */
export function kiAntwortetGleich(beitraege: readonly Beitrag[]): boolean {
  const juengster = beitraege.at(-1)
  return juengster !== undefined && !juengster.vonKi
}

/**
 * Der Kopf eines neuen Beitrags (Plan #2206 E9): in der Sprache des ersten Beitrags des Gesprächs,
 * ein neues Gespräch in der Sprache der Oberfläche.
 */
export function kopfFuerNeuenBeitrag(beitraege: readonly Beitrag[], oberflaeche: Sprache): string {
  const sprache = beitraege[0]?.sprache ?? oberflaeche
  return sprache === 'en' ? KOPF_EN : KOPF_DE
}

/** Der Text des Kommentars für einen neuen Beitrag: Kopf, Leerzeile, Text. */
export function beitragsText(kopf: string, text: string): string {
  return `${kopf}\n\n${text}`
}
