// Auflösung einer Markdown-Spezifikation in Ideen-Rohdaten (Issue #493).
//
// Die Datei verlässt den Browser nicht: gelesen und aufgelöst wird hier, an den Server gehen nur
// die fertigen Karten (Batch-Endpoint aus #492). Deshalb liegt diese Logik im Frontend — das
// Backend hat keinerlei Markdown-Infrastruktur, und ohne Upload gibt es weder einen Dateityp-Check
// noch eine Frage nach dem Löschen der Quelldatei.
//
// Warum zeilenweise statt über die remark-Kette: `react-markdown`/`remark-gfm` sind
// Renderer-Bausteine, kein aufrufbarer Parser; ein eigenständiger (`unified`/`remark-parse`) liegt
// nur als transitive Abhängigkeit im Baum und wäre ohne Eintrag in `package.json` eine stille
// Kopplung an ein fremdes Implementierungsdetail. Für die einzige hier nötige Frage — „ist diese
// Zeile eine ATX-Überschrift der Ebene N?" — genügt die im Projekt etablierte zeilenweise
// Behandlung mit Code-Fence-Schutz (siehe `markdownTasks.ts`).

/** Titelgrenze des Backends (`BatchIdeaItem.title`, `@Size(max = 300)`). */
export const MAX_TITLE_LENGTH = 300
/** Beschreibungsgrenze des Batch-Endpoints (`ProjectIdeaController.MAX_DESCRIPTION_LENGTH`). */
export const MAX_DESCRIPTION_LENGTH = 10_000
/** Elementgrenze eines Stapel-Aufrufs (`ProjectIdeaController.MAX_IDEAS_PER_BATCH`). */
export const MAX_IDEAS_PER_IMPORT = 200

/** Trennende Überschriftenebene: H1 oder H2, vom Nutzer beim Import gewählt. */
export type HeadingLevel = 1 | 2

/** Eine aus der Spezifikation aufgelöste Idee, bereits auf die Feldgrenzen des Servers gekürzt. */
export interface SpecSection {
  title: string
  description: string
  /** Titel war länger als {@link MAX_TITLE_LENGTH} und wurde gekürzt. */
  titleTruncated: boolean
  /** Beschreibung war länger als {@link MAX_DESCRIPTION_LENGTH} und wurde gekürzt. */
  descriptionTruncated: boolean
}

/**
 * Code-Fence (CommonMark: bis zu drei Leerzeichen Einrückung, mindestens drei ` oder ~). Der
 * Info-String dahinter wird nicht mitgematcht, sondern hinter dem Treffer abgeschnitten: ein
 * abschließendes `(.*)$` konkurrierte mit dem `{3,}` um dieselben Zeichen und machte die Laufzeit
 * bei einem Fehlschlag super-linear (Sonar S8786).
 */
const FENCE = /^ {0,3}(`{3,}|~{3,})/
/**
 * ATX-Überschrift: bis zu drei Leerzeichen, ein bis sechs #, danach Zeilenende oder Whitespace. Der
 * Titeltext dahinter wird — wie beim Fence — nicht mitgematcht, sondern hinter dem Treffer
 * abgeschnitten: ein abschließendes `(.*)$` konkurrierte mit dem vorangehenden `[ \t]+` um dieselben
 * Zeichen und machte die Laufzeit bei einem Fehlschlag super-linear (Sonar S8786). Weil das
 * trennende Leerzeichen im Treffer bleibt, beginnt der Rest genau dort, wo zuvor die Gruppe begann.
 */
const HEADING = /^ {0,3}(#{1,6})(?:[ \t]+|$)/
/** Optionale abschließende Rautenfolge einer ATX-Überschrift (`## Titel ##`). */
const CLOSING_HASHES = /[ \t]+#+[ \t]*$/

/** Abschnitt im Aufbau: Titel steht fest, die Beschreibung sammelt noch Zeilen. */
interface OpenSection {
  title: string
  titleTruncated: boolean
  body: string[]
}

/**
 * Fortschreibung des Fence-Zustands: `null` = außerhalb, sonst die öffnende Markierung. Ein Fence
 * schließt nur mit demselben Zeichen, mindestens derselben Länge und ohne Info-String — sonst
 * bliebe ein ``` innerhalb eines ~~~-Blocks fälschlich als Ende stehen.
 */
function nextFence(line: string, open: string | null): string | null {
  const match = FENCE.exec(line)
  if (match === null) {
    return open
  }
  const marker = match[1]
  if (open === null) {
    return marker
  }
  const info = line.slice(match[0].length)
  const closes = marker[0] === open[0] && marker.length >= open.length && info.trim() === ''
  return closes ? null : open
}

function truncate(text: string, max: number): { text: string; truncated: boolean } {
  return text.length > max ? { text: text.slice(0, max), truncated: true } : { text, truncated: false }
}

function finish(section: OpenSection): SpecSection {
  const description = truncate(section.body.join('\n').trim(), MAX_DESCRIPTION_LENGTH)
  return {
    title: section.title,
    description: description.text,
    titleTruncated: section.titleTruncated,
    descriptionTruncated: description.truncated,
  }
}

/**
 * Löst eine Markdown-Spezifikation in Ideen auf: Jede Überschrift der gewählten Ebene wird eine
 * Karte — der Überschriftentext ist der Titel, alles bis zur nächsten gleichrangigen Überschrift
 * die Beschreibung. Tiefere Überschriften bleiben Teil der Beschreibung; was vor der ersten
 * passenden Überschrift steht (Vorspann, Inhaltsverzeichnis), gehört zu keiner Karte und entfällt.
 *
 * Innerhalb von Code-Fences wird nichts als Überschrift gedeutet — eine `#`-Zeile in einem
 * Beispielblock ist Inhalt, keine Struktur. Eine Überschrift ohne Text startet keine Karte (der
 * Server nähme den leeren Titel nicht an) und bleibt Teil der laufenden Beschreibung.
 *
 * Titel und Beschreibung werden hier schon auf die Feldgrenzen des Servers gekürzt und die Kürzung
 * gemeldet, damit die Vorschau sie zeigt, statt den Nutzer in einen 400er laufen zu lassen.
 */
export function splitSpecIntoSections(markdown: string, level: HeadingLevel): SpecSection[] {
  const sections: SpecSection[] = []
  let current: OpenSection | null = null
  let fence: string | null = null

  for (const raw of markdown.split('\n')) {
    // Exporte aus Confluence & Co. kommen mit CRLF; der Wagenrücklauf gehört in keinen Titel.
    const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw
    const after = nextFence(line, fence)
    if (after !== fence || fence !== null) {
      // Fence-Markierung selbst oder Zeile innerhalb eines Blocks: nie Struktur, immer Inhalt.
      fence = after
      current?.body.push(line)
      continue
    }

    const heading = HEADING.exec(line)
    const title =
      heading?.[1].length === level
        ? line.slice(heading[0].length).replace(CLOSING_HASHES, '').trim()
        : ''
    if (title !== '') {
      if (current !== null) {
        sections.push(finish(current))
      }
      const short = truncate(title, MAX_TITLE_LENGTH)
      current = { title: short.text, titleTruncated: short.truncated, body: [] }
      continue
    }

    current?.body.push(line)
  }

  if (current !== null) {
    sections.push(finish(current))
  }
  return sections
}
