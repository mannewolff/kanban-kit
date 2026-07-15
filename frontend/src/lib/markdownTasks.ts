// Task-Checkbox-Behandlung für Karten-Beschreibungen.
//
// GitHub-Flavored-Markdown erzeugt Checkboxen nur aus echten Task-List-Items mit **genau einem**
// Zeichen zwischen den Klammern (`- [ ]`, `- [x]`). Nutzer schreiben aber oft `[  ]` (zwei
// Leerzeichen), `[]` (leer) oder `[ x ]` — GFM rendert diese dann als rohen Text statt als
// Checkbox. Damit `[ ]`/`[x]` in allen Varianten einheitlich als Checkbox erscheinen, kanonisieren
// wir die Marker vor dem Rendern zu `[ ]`/`[x]` — am Zeilenanfang und außerhalb von Code-Fences.
// Nackte Marker ohne Listenmarker bekommen zusätzlich einen `- `, damit GFM sie als Liste erkennt.

const FENCE = /^\s*(```|~~~)/
/** Marker-Kern: eckige Klammern mit optionalem x/X und beliebigem Whitespace. */
const MARKER = /\[\s*[xX]?\s*\]/
/** Zeile mit Listenmarker (`-`/`*`/`+`/`1.`) direkt vor dem Task-Marker. */
const LISTED = /^(\s*(?:[-*+]|\d+[.)])\s+)(\[\s*[xX]?\s*\])(.*)$/
/** Nackter Task-Marker am Zeilenanfang (ohne Listenmarker), gefolgt von Whitespace. */
const NAKED = /^(\s*)(\[\s*[xX]?\s*\])(\s.*)$/

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
  return md
    .split('\n')
    .map((line) => {
      if (FENCE.test(line)) {
        inFence = !inFence
        return line
      }
      if (inFence) {
        return line
      }
      const listed = line.match(LISTED)
      if (listed) {
        const [, prefix, marker, rest] = listed
        const body = rest.replace(/^\s*/, '')
        return `${prefix}${canonical(marker)}${body ? ` ${body}` : ''}`
      }
      const naked = line.match(NAKED)
      if (naked) {
        const [, indent, marker, rest] = naked
        const body = rest.replace(/^\s*/, '')
        return `${indent}- ${canonical(marker)}${body ? ` ${body}` : ''}`
      }
      return line
    })
    .join('\n')
}

/**
 * Schaltet die `targetIndex`-te Checkbox (0-basiert, in Dokumentreihenfolge) zwischen `[ ]` und
 * `[x]` um und schreibt sie kanonisch. Zählweise identisch zu {@link normalizeTaskLists}/GFM
 * (Code-Fences zählen nicht; Marker-Varianten wie `[  ]`/`[]`/`[ x ]` zählen mit), damit der Index
 * dem gerenderten Checkbox-Index entspricht. Kein Treffer → unveränderter Text.
 */
export function toggleTaskAt(md: string, targetIndex: number): string {
  const lines = md.split('\n')
  let inFence = false
  let i = 0
  for (let l = 0; l < lines.length; l++) {
    const line = lines[l]
    if (FENCE.test(line)) {
      inFence = !inFence
      continue
    }
    if (inFence || (!LISTED.test(line) && !NAKED.test(line))) {
      continue
    }
    if (i === targetIndex) {
      // Nur den führenden Marker flippen (nicht Klammern im Task-Text) und kanonisch schreiben.
      lines[l] = line.replace(MARKER, (mk) => (/[xX]/.test(mk) ? '[ ]' : '[x]'))
      return lines.join('\n')
    }
    i++
  }
  return md
}
