import { useEffect, useRef } from 'react'

// Kurzes Debounce gegen Event-Stürme (z. B. ein Bulk-Ingest löst viele ProjectIdeasChangedEvents aus):
// mehrere Events innerhalb des Fensters führen zu genau einem Refetch.
const DEBOUNCE_MS = 250

/**
 * Abonniert den Live-Ideen-Pool-SSE-Stream (`/api/projects/{projectId}/ideas/events`) und ruft
 * `onChange` debounced, sobald der Server eine Pool-Änderung meldet — so ziehen offene Ideen-Ansichten
 * per Token-Ingest oder von anderen Nutzern eingelieferte Ideen live nach. Spiegelbild von
 * {@link useBoardEvents}, nur projekt- statt board-scoped. Ergänzt {@link useRefetchOnFocus}:
 * SSE liefert live, der Fokus-Refetch holt Lücken nach einem Reconnect auf.
 *
 * Der Server sendet die Änderungen als benannte `project-ideas-changed`-Events (siehe
 * ProjectIdeaEventRegistry) und dazwischen Heartbeat-Kommentare, die EventSource ignoriert.
 * EventSource reconnectet bei einem Verbindungsabbruch selbstständig — daher kein eigenes
 * Reconnect-Handling (keine Endlosschleife). Bei Projektwechsel/Unmount wird die Verbindung
 * geschlossen und ein offener Debounce-Timer verworfen (kein Refetch nach Unmount).
 *
 * Die aktuelle `onChange`-Referenz wird über ein Ref gehalten, damit der Stream nicht bei jeder
 * Neudefinition der Callback-Funktion neu geöffnet werden muss (nur `projectId` steuert das).
 */
export function useProjectIdeaEvents(projectId: number, onChange: () => void): void {
  const ref = useRef(onChange)
  useEffect(() => {
    ref.current = onChange
  })

  useEffect(() => {
    if (!Number.isInteger(projectId) || projectId <= 0) {
      return
    }
    const source = new EventSource(`/api/projects/${projectId}/ideas/events`)
    let timer: ReturnType<typeof setTimeout> | undefined
    const onChanged = () => {
      clearTimeout(timer)
      timer = setTimeout(() => ref.current(), DEBOUNCE_MS)
    }
    source.addEventListener('project-ideas-changed', onChanged)
    return () => {
      clearTimeout(timer)
      source.removeEventListener('project-ideas-changed', onChanged)
      source.close()
    }
  }, [projectId])
}
