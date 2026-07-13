// Task-Checkbox-Behandlung für Karten-Beschreibungen.
//
// GitHub-Flavored-Markdown erzeugt Checkboxen nur aus echten Task-List-Items (`- [ ]`, `1. [ ]`).
// Ein „nacktes" `[ ]` am Zeilenanfang ohne Listenmarker bleibt Text. Damit `[ ]`/`[x]` überall
// einheitlich als Checkbox erscheinen, normalisieren wir nackte Marker vor dem Rendern zu echten
// Task-Items — aber nur am Zeilenanfang und nicht innerhalb von Code-Fences.

const FENCE = /^\s*(```|~~~)/
/** Nacktes `[ ]`/`[x]` am Zeilenanfang (ohne vorangestellten Listenmarker), gefolgt von Text. */
const NAKED_TASK = /^(\s*)(\[[ xX]\]\s.*)$/
/** Zeile, die als gerenderte Checkbox zählt: optionaler Listenmarker, dann `[ ]`/`[x]` + Whitespace. */
const TASK_LINE = /^\s*(?:[-*+]\s+|\d+[.)]\s+)?\[[ xX]\]\s/

/**
 * Wandelt nackte `[ ]`/`[x]` am Zeilenanfang in echte GFM-Task-List-Items (`- [ ]`) um, damit sie
 * als Checkbox gerendert werden. Zeilen mit bereits vorhandenem Listenmarker und Inhalte von
 * Code-Fences bleiben unangetastet.
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
      return line.replace(NAKED_TASK, '$1- $2')
    })
    .join('\n')
}

/**
 * Schaltet die `targetIndex`-te Checkbox (0-basiert, in Dokumentreihenfolge) zwischen `[ ]` und
 * `[x]` um. Zählweise identisch zu {@link normalizeTaskLists}/GFM (Code-Fences zählen nicht), damit
 * der Index dem gerenderten Checkbox-Index entspricht. Kein Treffer → unveränderter Text.
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
    if (inFence || !TASK_LINE.test(line)) {
      continue
    }
    if (i === targetIndex) {
      // Nur das erste `[...]` (die Checkbox am Zeilenanfang) flippen, nicht Klammern im Task-Text.
      lines[l] = line.replace(/\[[ xX]\]/, (m) => (m === '[ ]' ? '[x]' : '[ ]'))
      return lines.join('\n')
    }
    i++
  }
  return md
}
