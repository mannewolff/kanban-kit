import type { Board } from '../api/boards'
import { useProjectRole } from './useProjectRole'

/**
 * Löst für eine board-gebundene Seite die effektive Projektrolle auf und leitet die Rechte ab.
 * Dünne Hülle um {@link useProjectRole} für die Seiten, die nur über `boardId` geroutet sind
 * (EpicsPage, BoardListPage) und das Projekt erst aus dem geladenen Board kennen.
 */
export function useBoardRole(board: Board | null): {
  effectiveRole: string
  canEdit: boolean
  canModerate: boolean
} {
  return useProjectRole(board?.projectId ?? null)
}
