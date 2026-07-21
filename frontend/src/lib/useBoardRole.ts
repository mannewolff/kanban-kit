import { useEffect, useState } from 'react'
import type { Board } from '../api/boards'
import { projectsApi } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { canEditCards, canModerateComments, isPlatformAdmin } from './roles'

/**
 * Löst für eine board-gebundene Seite die effektive Projektrolle auf und leitet die Rechte ab.
 * Bevorzugt die eigene Mitgliedschaft aus dem Auth-Context; ist sie nicht bekannt, wird die Rolle
 * über `projectsApi.list()` nachgeladen (Fallback `VIEWER`). Gemeinsame Basis für die Seiten, die
 * nur über `boardId` geroutet sind (EpicsPage, BoardListPage) — dieselbe Quelle wie `useProjectName`.
 */
export function useBoardRole(board: Board | null): {
  effectiveRole: string
  canEdit: boolean
  canModerate: boolean
} {
  const { user } = useAuth()
  const [fetchedRole, setFetchedRole] = useState<string | null>(null)
  const membershipRole = board
    ? user?.memberships.find((m) => m.projectId === board.projectId)?.role
    : undefined
  useEffect(() => {
    if (!board || membershipRole) {
      setFetchedRole(null)
      return
    }
    void projectsApi
      .list()
      .then((ps) => setFetchedRole(ps.find((p) => p.id === board.projectId)?.role ?? 'VIEWER'))
  }, [board, membershipRole])
  const effectiveRole = membershipRole ?? fetchedRole ?? 'VIEWER'
  const platformAdmin = isPlatformAdmin(user)
  return {
    effectiveRole,
    canEdit: canEditCards(effectiveRole, platformAdmin),
    canModerate: canModerateComments(effectiveRole, platformAdmin),
  }
}
