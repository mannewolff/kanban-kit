import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext'

/**
 * Ein Eintrag des Board-Verlaufs. Die `projectId` trägt er, seit die Schiene auf einer Projektseite
 * das zuletzt besuchte Board *desselben* Projekts wiederfinden muss (#990) — der Projektname taugt
 * dafür nicht, weil Namen nicht eindeutig sind.
 */
export interface BoardHistoryEntry {
  id: number
  name: string
  projectId: number
  projectName: string
}

export interface BoardHistory {
  history: readonly BoardHistoryEntry[]
  recordVisit: (entry: BoardHistoryEntry) => void
  remove: (boardId: number) => void
}

const STORAGE_PREFIX = 'manban.boardHistory.v1.'
const MAX_ENTRIES = 8

interface HistoryState {
  /** Nutzer, zu dem `entries` gehoert; `null` heisst „kein Verlauf, kein Storage-Zugriff". */
  userId: number | null
  entries: BoardHistoryEntry[]
}

function storageKey(userId: number): string {
  return `${STORAGE_PREFIX}${userId}`
}

/** Eine positive Ganzzahl — die Form, die Board- und Projekt-IDs haben. */
function isId(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) > 0
}

/**
 * Verengung eines Storage-Werts (externer Input, daher `unknown`) auf einen gueltigen Eintrag.
 * Eintraege ohne `projectId` stammen aus der Zeit vor #990; sie werden beim Lesen verworfen statt
 * migriert — der Verlauf fuellt sich beim naechsten Besuch von selbst neu.
 */
function isEntry(value: unknown): value is BoardHistoryEntry {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return (
    isId(candidate.id) &&
    isId(candidate.projectId) &&
    typeof candidate.name === 'string' &&
    typeof candidate.projectName === 'string'
  )
}

/**
 * Von vorne nach hinten: ungueltige Eintraege verwerfen, bei mehrfacher ID das erste — damit
 * neueste — Vorkommen behalten, auf {@link MAX_ENTRIES} begrenzen und auf die vier erlaubten
 * Felder projizieren. Ein Nicht-Array ergibt einen leeren Verlauf.
 */
function normalize(value: unknown): BoardHistoryEntry[] {
  if (!Array.isArray(value)) {
    return []
  }
  const seen = new Set<number>()
  const entries: BoardHistoryEntry[] = []
  for (const item of value) {
    if (isEntry(item) && !seen.has(item.id)) {
      seen.add(item.id)
      entries.push({
        id: item.id,
        name: item.name,
        projectId: item.projectId,
        projectName: item.projectName,
      })
      if (entries.length === MAX_ENTRIES) {
        break
      }
    }
  }
  return entries
}

/**
 * Das zuletzt besuchte Board eines Projekts, oder `undefined`, wenn der Verlauf keines kennt. Der
 * Verlauf steht absteigend nach letzter Benutzung — der erste Treffer ist damit der juengste.
 */
export function lastBoardOfProject(
  history: readonly BoardHistoryEntry[],
  projectId: number,
): BoardHistoryEntry | undefined {
  return history.find((entry) => entry.projectId === projectId)
}

function read(userId: number | null): BoardHistoryEntry[] {
  if (userId === null) {
    return []
  }
  try {
    const raw = localStorage.getItem(storageKey(userId))
    return raw === null ? [] : normalize(JSON.parse(raw))
  } catch {
    // Gesperrter Storage oder kaputtes JSON — der Verlauf startet leer.
    return []
  }
}

function write(userId: number, entries: readonly BoardHistoryEntry[]): void {
  try {
    localStorage.setItem(storageKey(userId), JSON.stringify(entries))
  } catch {
    // localStorage nicht verfuegbar — der Verlauf bleibt sitzungslokal, nichts bricht.
  }
}

/**
 * Verlauf der zuletzt besuchten Boards, nutzerspezifisch unter `manban.boardHistory.v1.<userId>`
 * abgelegt. Der Schluessel traegt die Nutzer-ID, weil `localStorage` fuer die Origin gilt, nicht
 * fuer das Konto — sonst saehe der naechste Nutzer im selben Browser fremde Board- und
 * Projektnamen. Solange der Auth-Zustand laedt oder niemand angemeldet ist, bleibt der Verlauf
 * leer und es findet kein Storage-Zugriff statt.
 *
 * Der Nutzerwechsel wird waehrend des Renderns aufgeloest (nicht in einem Effect): Ein Render mit
 * neuer Nutzer-ID gibt nie noch den Verlauf der vorherigen zurueck.
 */
export function useBoardHistory(): BoardHistory {
  const { user, loading } = useAuth()
  const userId = loading || user === null ? null : user.userId
  const [state, setState] = useState<HistoryState>(() => ({ userId, entries: read(userId) }))

  let current = state
  if (current.userId !== userId) {
    // Nutzerwechsel waehrend des Renders aufloesen: dieser Render gibt bereits den Verlauf der
    // neuen Nutzer-ID zurueck, nie mehr den der vorherigen.
    current = { userId, entries: read(userId) }
    setState(current)
  }

  // Spiegel des aktuellen Stands fuer die Callbacks: haelt mehrere Aufrufe innerhalb eines
  // Batches konsistent (ohne zwischenzeitlichen Render) und laesst ihre Identitaet stabil.
  const latest = useRef<HistoryState>(current)
  useEffect(() => {
    latest.current = current
  }, [current])

  const update = useCallback(
    (change: (entries: readonly BoardHistoryEntry[]) => BoardHistoryEntry[]) => {
      const activeUserId = latest.current.userId
      if (activeUserId === null) {
        return
      }
      const entries = change(latest.current.entries)
      latest.current = { userId: activeUserId, entries }
      write(activeUserId, entries)
      setState(latest.current)
    },
    [],
  )

  const recordVisit = useCallback(
    (entry: BoardHistoryEntry) =>
      update((entries) =>
        [
          { id: entry.id, name: entry.name, projectId: entry.projectId, projectName: entry.projectName },
          ...entries.filter((e) => e.id !== entry.id),
        ].slice(0, MAX_ENTRIES),
      ),
    [update],
  )

  const remove = useCallback(
    (boardId: number) => update((entries) => entries.filter((e) => e.id !== boardId)),
    [update],
  )

  return useMemo(
    () => ({ history: current.entries, recordVisit, remove }),
    [current.entries, recordVisit, remove],
  )
}
