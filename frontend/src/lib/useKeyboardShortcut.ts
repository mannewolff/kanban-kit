import { useEffect, useRef } from 'react'

/**
 * Ob das Ziel eines Tastendrucks eine Texteingabe ist. Ein Kürzel auf einem Schriftzeichen darf
 * dort nie greifen — sonst legt das Tippen von „C++“ in einer Beschreibung zwei Karten an.
 *
 * Editierbarer Inhalt wird über `closest` statt über `isContentEditable` erkannt: Der Cursor steht
 * meist in einem Kindelement des editierbaren Containers, und jsdom kennt `isContentEditable` nicht.
 */
function isTextEntry(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  return (
    ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) ||
    target.closest('[contenteditable]:not([contenteditable="false"])') !== null
  )
}

/**
 * Globales Tastenkürzel auf einer einzelnen Taste (Issue #429). Erstes seiner Art im Frontend,
 * deshalb als benannter Hook statt als Inline-Effekt.
 *
 * Drei harte Abgrenzungen, ohne die ein Kürzel auf einem Schriftzeichen unbrauchbar wäre:
 *
 * 1. **Keine Texteingabe.** Steht der Fokus in einem Eingabefeld oder editierbarem Inhalt, gewinnt
 *    das Tippen — siehe {@link isTextEntry}.
 * 2. **Kein offener Dialog.** Ein modaler Dialog fängt die Bedienung; die Abfrage geht über das DOM
 *    (`[role="dialog"]`), weil auch Dialoge außerhalb der aufrufenden Komponente zählen.
 * 3. **Keine Modifikatoren.** Strg/Alt/Meta gehören dem Browser bzw. dem Betriebssystem. Umschalt
 *    bleibt erlaubt — auf manchen Belegungen ist das Zeichen nur damit erreichbar.
 *
 * `enabled` schaltet das Kürzel scharf; bei `false` hängt gar kein Listener am Dokument.
 */
export function useKeyboardShortcut(key: string, enabled: boolean, onTrigger: () => void): void {
  // Der Handler liegt in einer Ref, damit ein neu erzeugtes Callback je Render den Listener nicht
  // ab- und wieder anmeldet — der Aufruf greift trotzdem stets auf den aktuellen Zustand zu.
  const handler = useRef(onTrigger)
  useEffect(() => {
    handler.current = onTrigger
  })

  useEffect(() => {
    if (!enabled) {
      return
    }
    const listener = (event: KeyboardEvent) => {
      const blocked =
        event.key !== key ||
        event.ctrlKey ||
        event.altKey ||
        event.metaKey ||
        isTextEntry(event.target) ||
        document.querySelector('[role="dialog"]') !== null
      if (!blocked) {
        handler.current()
      }
    }
    document.addEventListener('keydown', listener)
    return () => document.removeEventListener('keydown', listener)
  }, [key, enabled])
}
