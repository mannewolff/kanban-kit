import { useEffect, useState } from 'react'
import { projectsApi } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { canEditCards, canModerateComments, isPlatformAdmin } from './roles'

/**
 * Löst die effektive Projektrolle auf und leitet daraus die Rechte ab — die einzige Quelle dafür
 * im Frontend. Bevorzugt die eigene Mitgliedschaft aus dem Auth-Context; ist das Projekt dort noch
 * nicht bekannt (z. B. frisch in dieser Session angelegt), wird die Rolle über `projectsApi.list()`
 * nachgeladen (Fallback `VIEWER`, auch bei einem Ladefehler).
 *
 * `projectId` ist bewusst nullable: Aufrufer, deren Projekt erst asynchron feststeht oder gar nicht
 * offen ist, müssen den Hook trotzdem unbedingt aufrufen (Hook-Regeln). Ohne Projekt gilt `VIEWER`
 * ohne Netzwerkzugriff.
 *
 * Der nachgeladene Wert ist an die ID gebunden, für die er geholt wurde: Ein Projektwechsel fällt
 * damit sofort auf `VIEWER` zurück, statt die alte Rolle bis zur neuen Antwort weiterzuzeigen
 * (Least Privilege). Das Cancellation-Flag verhindert zusätzlich, dass eine verspätet eintreffende
 * Antwort des vorigen Projekts die aktuelle Rolle überschreibt.
 */
export function useProjectRole(projectId: number | null): {
  effectiveRole: string
  canEdit: boolean
  canModerate: boolean
} {
  const { user } = useAuth()
  const [fetched, setFetched] = useState<{ projectId: number; role: string } | null>(null)
  const membershipRole =
    projectId == null ? undefined : user?.memberships.find((m) => m.projectId === projectId)?.role

  useEffect(() => {
    if (projectId == null || membershipRole) {
      return
    }
    let active = true
    void projectsApi
      .list()
      .then((projects) => {
        if (active) {
          setFetched({ projectId, role: projects.find((p) => p.id === projectId)?.role ?? 'VIEWER' })
        }
      })
      .catch(() => active && setFetched({ projectId, role: 'VIEWER' }))
    return () => {
      active = false
    }
  }, [projectId, membershipRole])

  const fetchedRole = fetched?.projectId === projectId ? fetched.role : null
  const effectiveRole = membershipRole ?? fetchedRole ?? 'VIEWER'
  const platformAdmin = isPlatformAdmin(user)
  return {
    effectiveRole,
    canEdit: canEditCards(effectiveRole, platformAdmin),
    canModerate: canModerateComments(effectiveRole, platformAdmin),
  }
}
