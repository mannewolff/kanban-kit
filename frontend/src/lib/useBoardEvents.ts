import { useEffect, useRef } from 'react'

// Kurzes Debounce gegen Event-Stürme (z. B. eine Bulk-Aktion löst viele BoardChangedEvents aus):
// mehrere Events innerhalb des Fensters führen zu genau einem Refetch.
const DEBOUNCE_MS = 250

/**
 * Abonniert den Live-Board-SSE-Stream (`/api/boards/{boardId}/events`) und ruft `onChange`
 * debounced, sobald der Server eine board-relevante Änderung meldet — so ziehen offene Sessions
 * Moves/Änderungen anderer live nach (Board- und Listen-Ansicht). Ergänzt {@link useRefetchOnFocus}:
 * SSE liefert live, der Fokus-Refetch holt Lücken nach einem Reconnect auf.
 *
 * Der Server sendet die Änderungen als benannte `board-changed`-Events (siehe BoardEventRegistry)
 * und dazwischen Heartbeat-Kommentare, die EventSource ignoriert. EventSource reconnectet bei einem
 * Verbindungsabbruch selbstständig — daher kein eigenes Reconnect-Handling (keine Endlosschleife).
 * Bei Board-Wechsel/Unmount wird die Verbindung geschlossen und ein offener Debounce-Timer verworfen
 * (kein Refetch nach Unmount).
 *
 * Die aktuelle `onChange`-Referenz wird über ein Ref gehalten, damit der Stream nicht bei jeder
 * Neudefinition der Callback-Funktion neu geöffnet werden muss (nur `boardId` steuert das).
 */
export function useBoardEvents(boardId: number, onChange: () => void): void {
  const ref = useRef(onChange)
  useEffect(() => {
    ref.current = onChange
  })

  useEffect(() => {
    if (!Number.isInteger(boardId) || boardId <= 0) {
      return
    }
    const source = new EventSource(`/api/boards/${boardId}/events`)
    let timer: ReturnType<typeof setTimeout> | undefined
    const onChanged = () => {
      clearTimeout(timer)
      timer = setTimeout(() => ref.current(), DEBOUNCE_MS)
    }
    source.addEventListener('board-changed', onChanged)
    return () => {
      clearTimeout(timer)
      source.removeEventListener('board-changed', onChanged)
      source.close()
    }
  }, [boardId])
}
