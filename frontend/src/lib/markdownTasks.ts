// Task-Checkbox-Behandlung für Karten-Beschreibungen.
//
// GitHub-Flavored-Markdown erzeugt Checkboxen nur aus echten Task-List-Items mit **genau einem**
// Zeichen zwischen den Klammern (`- [ ]`, `- [x]`). Nutzer schreiben aber oft `[  ]` (zwei
// Leerzeichen), `[]` (leer) oder `[ x ]` — GFM rendert diese dann als rohen Text statt als
// Checkbox. Damit `[ ]`/`[x]` in allen Varianten einheitlich als Checkbox erscheinen, kanonisieren
// wir die Marker vor dem Rendern zu `[ ]`/`[x]` — am Zeilenanfang und außerhalb von Code-Fences.
// Nackte Marker ohne Listenmarker bekommen zusätzlich einen `- `, damit GFM sie als Liste erkennt.
// Zeilen mit CRLF-Ende werden ebenso behandelt; das `\r` bleibt erhalten.
// Beim Umschalten zählt `toggleTaskAt` die Checkboxen wie der Renderer: über denselben GFM-Parser.

import type { ListItem, Nodes } from 'mdast'
import remarkGfm from 'remark-gfm'
import remarkParse from 'remark-parse'
import { unified } from 'unified'

const FENCE = /^\s*(```|~~~)/
// Marker-Kern durchgängig `\[\s*(?:[xX]\s*)?\]` statt `\[\s*[xX]?\s*\]`: Letzteres hat zwei
// benachbarte `\s*` um ein optionales Zeichen und erlaubt super-lineares Backtracking bei
// Eingaben wie `[` + vielen Leerzeichen ohne `]` (S8786). Das `[xX]` trennt hier die beiden
// Whitespace-Gruppen als Anker; die Menge der akzeptierten Marker ist identisch
// (`[]`, `[ ]`, `[  ]`, `[x]`, `[ x ]`, `[X]`).
/** Marker-Kern: eckige Klammern mit optionalem x/X und beliebigem Whitespace. */
const MARKER = /\[\s*(?:[xX]\s*)?\]/
/** Zeile mit Listenmarker (`-`/`*`/`+`/`1.`) direkt vor dem Task-Marker. */
const LISTED = /^(\s*(?:[-*+]|\d+[.)])\s+)(\[\s*(?:[xX]\s*)?\])(.*)$/
/** Nackter Task-Marker am Zeilenanfang (ohne Listenmarker), gefolgt von Whitespace. */
const NAKED = /^(\s*)(\[\s*(?:[xX]\s*)?\])(\s.*)$/

/** Kanonische Marker-Form: `[x]` wenn irgendein x/X enthalten ist, sonst `[ ]`. */
function canonical(marker: string): string {
  return /[xX]/.test(marker) ? '[x]' : '[ ]'
}

/**
 * Wandelt Task-Marker am Zeilenanfang in kanonische GFM-Task-List-Items (`- [ ]`/`- [x]`) um, damit
 * sie zuverlässig als Checkbox gerendert werden — unabhängig von Leerzeichen-Zahl (`[  ]`, `[]`,
 * `[ x ]`) oder Groß-/Kleinschreibung (`[X]`). Listenmarker bleiben erhalten; nackte Marker (ohne
 * Listenmarker) bekommen einen `- ` und werden nur mit folgendem Whitespace behandelt, damit
 * Klammern im Fließtext unangetastet bleiben. Code-Fences bleiben unverändert.
 */
export function normalizeTaskLists(md: string): string {
  let inFence = false
  const normalizeLine = (line: string): string => {
    if (FENCE.test(line)) {
      inFence = !inFence
      return line
    }
    if (inFence) {
      return line
    }
    const listed = LISTED.exec(line)
    if (listed) {
      const [, prefix, marker, rest] = listed
      // Stryker disable next-line Regex: `/\s*/` statt `/^\s*/` ist gleichwertig — `\s*` passt immer schon an Stelle 0 (notfalls leer), und `replace` ohne `g` ersetzt nur diesen ersten Treffer.
      const body = rest.replace(/^\s*/, '')
      const suffix = body ? ` ${body}` : ''
      return `${prefix}${canonical(marker)}${suffix}`
    }
    const naked = NAKED.exec(line)
    if (naked) {
      const [, indent, marker, rest] = naked
      // Stryker disable next-line Regex: `/\s*/` statt `/^\s*/` ist gleichwertig — `\s*` passt immer schon an Stelle 0 (notfalls leer), und `replace` ohne `g` ersetzt nur diesen ersten Treffer.
      const body = rest.replace(/^\s*/, '')
      const suffix = body ? ` ${body}` : ''
      return `${indent}- ${canonical(marker)}${suffix}`
    }
    return line
  }
  return md
    .split('\n')
    .map((raw) => {
      // Bei CRLF bleibt das `\r` am Zeilenende stehen; `.` in LISTED/NAKED trifft es nicht.
      // Abtrennen und wieder anhängen hält jedes Zeichen auf seiner Position (für toggleTaskAt).
      const cr = raw.endsWith('\r') ? '\r' : ''
      return normalizeLine(raw.slice(0, raw.length - cr.length)) + cr
    })
    .join('\n')
}

/** Derselbe Parser wie beim Rendern (react-markdown mit remark-gfm), damit beide gleich zählen. */
const parser = unified().use(remarkParse).use(remarkGfm)

/**
 * Sammelt die Task-List-Items in Dokumentreihenfolge — rekursiv, also auch in Zitaten und
 * verschachtelten Listen. Kriterium wie in `mdast-util-to-hast`: Ein `listItem` wird genau dann als
 * Checkbox gerendert, wenn `checked` ein Boolean ist.
 */
function collectTasks(node: Nodes, tasks: ListItem[]): ListItem[] {
  // Stryker disable next-line ConditionalExpression: `node.type === 'listItem'` → `true` ist gleichwertig — in mdast trägt nur ein `listItem` das Feld `checked`.
  if (node.type === 'listItem' && typeof node.checked === 'boolean') {
    tasks.push(node)
  }
  if ('children' in node) {
    for (const child of node.children) {
      collectTasks(child, tasks)
    }
  }
  return tasks
}

/**
 * Schaltet die `targetIndex`-te Checkbox (0-basiert, in Dokumentreihenfolge) zwischen `[ ]` und
 * `[x]` um und schreibt sie kanonisch. Gezählt wird wie der Renderer: Der Text wird wie beim
 * Rendern normalisiert ({@link normalizeTaskLists}) und mit demselben GFM-Parser gelesen — was als
 * Checkbox erscheint, zählt (auch in Zitaten und verschachtelten Listen), eingerückter und
 * umzäunter Code zählt nicht. Weil die Normalisierung Zeilenzahl und Startspalte der Listeneinträge
 * erhält, wird im **Originaltext** der erste Marker ab der Startspalte des getroffenen Eintrags
 * geflippt; der Rest der Zeile bleibt unverändert. Kein Treffer → unveränderter Text.
 */
export function toggleTaskAt(md: string, targetIndex: number): string {
  const normalized = normalizeTaskLists(md)
  const task = collectTasks(parser.parse(normalized), [])[targetIndex]
  if (!task) {
    return md
  }
  // Der Parser setzt an jedem Knoten aus dem Quelltext eine Position.
  const { line, offset } = task.position!.start
  // Stryker disable next-line ArithmeticOperator: `offset! + 1` ist gleichwertig — nach der Normalisierung folgt auf den Listenmarker einer Aufgabe stets `.`, `)` oder Whitespace auf derselben Zeile, nie ein `\n`, also findet `lastIndexOf` denselben Zeilenumbruch.
  const column = offset! - (normalized.lastIndexOf('\n', offset! - 1) + 1)
  const lines = md.split('\n')
  const original = lines[line - 1]
  // Nur den Marker des Eintrags flippen (nicht Klammern im Task-Text) und kanonisch schreiben.
  lines[line - 1] =
    original.slice(0, column) +
    original.slice(column).replace(MARKER, (mk) => (/[xX]/.test(mk) ? '[ ]' : '[x]'))
  return lines.join('\n')
}
