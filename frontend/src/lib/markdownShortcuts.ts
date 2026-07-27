// Tipp-Hilfen für Markdown-Beschreibungen.
//
// Auf der deutschen Mac-Tastatur brauchen eckige Klammern Option-5 und Option-6 — beim Schreiben
// von Task-Listen ist das umständlich (Issue #420). Die Kurzschreibweise ersetzt deshalb `/` bzw.
// `/x` am Zeilenanfang durch ein fertiges GFM-Task-List-Item.
//
// Ausgelöst wird bewusst vom **folgenden Leerzeichen**, nicht vom Slash selbst: Nur so bleibt eine
// Zeile, die mit einem Pfad beginnt (`/api/cards`), unangetastet. Und die Ersetzung passiert beim
// Tippen, nicht beim Rendern — gespeichert wird normales Markdown. Das hält `markdownTasks.ts`
// unberührt, insbesondere den Index-Abgleich zwischen gerenderten Checkboxen und `toggleTaskAt`.

/** Am Zeilenanfang: optionale Einrückung, Slash, optionales x/X, genau ein Leerzeichen, Zeilenende. */
const TRIGGER = /^(\s*)\/([xX]?) $/

/**
 * Prüft, ob direkt vor dem Cursor eine Checkbox-Kurzschreibweise steht, und liefert den ersetzten
 * Text, die neue Cursor-Position und die ursprüngliche (`from`, für das Zurücknehmen per Backspace).
 * `null`, wenn nichts zutrifft — dann bleibt die Eingabe, wie sie getippt wurde.
 *
 * `caret` nimmt bewusst auch `null` an: `selectionStart` eines Textfelds ist laut DOM-Typ nullable,
 * und die Normalisierung gehört in diese testbare Funktion, nicht in den Hook.
 */
export function expandCheckboxShortcut(
  value: string,
  caret: number | null,
): { value: string; caret: number; from: number } | null {
  if (caret === null || caret <= 0) {
    return null
  }
  const before = value.slice(0, caret)
  const lineStart = before.lastIndexOf('\n') + 1
  const match = TRIGGER.exec(before.slice(lineStart))
  if (match === null) {
    return null
  }
  const replacement = `${match[1]}- [${match[2] === '' ? ' ' : 'x'}] `
  return {
    value: value.slice(0, lineStart) + replacement + value.slice(caret),
    caret: lineStart + replacement.length,
    from: caret,
  }
}
