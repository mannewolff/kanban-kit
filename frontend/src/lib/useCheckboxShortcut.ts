import { useEffect, useRef } from 'react'
import type { ChangeEvent, KeyboardEvent } from 'react'
import { expandCheckboxShortcut } from './markdownShortcuts'

type Field = HTMLInputElement | HTMLTextAreaElement

/**
 * Verdrahtet die Checkbox-Kurzschreibweise (`/` bzw. `/x` plus Leerzeichen am Zeilenanfang) an ein
 * kontrolliertes Textfeld (Issue #420). Liefert `onChange` und `onKeyDown` zum Anhängen.
 *
 * Zwei Feinheiten, die den Hook nötig machen statt eines Einzeilers im `onChange`:
 *
 * 1. **Cursor nachführen.** Nach der Ersetzung schreibt React den neuen Wert ins Textarea, und die
 *    Schreibmarke landet am Ende. Die Zielposition wird deshalb gemerkt und nach dem Render gesetzt.
 * 2. **Backspace nimmt zurück.** Programmatisches Ersetzen stört das native Rückgängig des Browsers;
 *    ein unmittelbar folgendes Backspace stellt daher die getippte Kurzform wieder her (Muster aus
 *    Notion und Slack). Jede andere Taste verwirft diese Möglichkeit.
 */
export function useCheckboxShortcut(onValueChange: (next: string) => void): {
  onChange: (event: ChangeEvent<Field>) => void
  // MUI typisiert `onKeyDown` am Wurzelelement des TextField, nicht am Eingabefeld — deshalb die
  // generische React-Signatur. Hier werden ohnehin nur `key` und `preventDefault()` gebraucht.
  onKeyDown: (event: KeyboardEvent) => void
} {
  const pending = useRef<{ el: Field; caret: number } | null>(null)
  const undo = useRef<{ el: Field; value: string; caret: number } | null>(null)

  useEffect(() => {
    const target = pending.current
    if (target === null) {
      return
    }
    pending.current = null
    target.el.setSelectionRange(target.caret, target.caret)
  })

  const onChange = (event: ChangeEvent<Field>) => {
    const el = event.target
    const expanded = expandCheckboxShortcut(el.value, el.selectionStart)
    if (expanded === null) {
      undo.current = null
      onValueChange(el.value)
      return
    }
    undo.current = { el, value: el.value, caret: expanded.from }
    pending.current = { el, caret: expanded.caret }
    onValueChange(expanded.value)
  }

  const onKeyDown = (event: KeyboardEvent) => {
    const restore = undo.current
    undo.current = null
    if (event.key !== 'Backspace' || restore === null) {
      return
    }
    event.preventDefault()
    pending.current = { el: restore.el, caret: restore.caret }
    onValueChange(restore.value)
  }

  return { onChange, onKeyDown }
}
