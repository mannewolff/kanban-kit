import { useEffect, useState } from 'react'
import { fremdBoardApi, type FremdBoardStand } from '../api/fremdboard'
import { projectsApi } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { canEditCards, canModerateComments, darfStoerungenSehen, isPlatformAdmin } from './roles'

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
 *
 * Liegt das Board des Projekts in einem fremden System (Issue #2170, Plan #2152 A9, A10), gilt
 * `canEdit=false` für jede Rolle, auch für Plattform-Admins: Der Server lehnt dort jedes Schreiben
 * ab, und keine Fläche soll einen Knopf zeigen, der daran scheitert. Die Auskunft
 * (`fremdBoardApi.stand`, für jedes Mitglied) kommt als `fremdBoard` mit; bis sie da ist oder wenn
 * sie scheitert, ist sie `null` und das Recht bleibt das der Rolle — die Sperre hält der Server.
 */
export function useProjectRole(projectId: number | null): {
  effectiveRole: string
  canEdit: boolean
  canModerate: boolean
  darfStoerungenSehen: boolean
  fremdBoard: FremdBoardStand | null
} {
  const { user } = useAuth()
  const [fetched, setFetched] = useState<{ projectId: number; role: string } | null>(null)
  const [geladenerStand, setGeladenerStand] = useState<{
    projectId: number
    stand: FremdBoardStand | null
  } | null>(null)
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
          // Stryker disable next-line OptionalChaining: gleichwertig — ohne `?.` wirft `.role` bei fehlendem Projekt, und das `catch` setzt dieselbe Rolle VIEWER (#2244)
          setFetched({ projectId, role: projects.find((p) => p.id === projectId)?.role ?? 'VIEWER' })
        }
      })
      .catch(() => {
        if (active) {
          // Stryker disable next-line ObjectLiteral: gleichwertig — ein leeres Objekt trifft keine Projektnummer und ergibt ebenfalls VIEWER (#2244)
          setFetched({ projectId, role: 'VIEWER' })
        }
      })
    return () => {
      active = false
    }
  }, [projectId, membershipRole])

  useEffect(() => {
    if (projectId == null) {
      return
    }
    let active = true
    fremdBoardApi
      .stand(projectId)
      .then((stand) => {
        if (active) {
          setGeladenerStand({ projectId, stand })
        }
      })
      .catch(() => {
        if (active) {
          setGeladenerStand({ projectId, stand: null })
        }
      })
    return () => {
      active = false
    }
  }, [projectId])

  const fetchedRole = fetched?.projectId === projectId ? fetched.role : null
  const effectiveRole = membershipRole ?? fetchedRole ?? 'VIEWER'
  const platformAdmin = isPlatformAdmin(user)
  const fremdBoard = geladenerStand?.projectId === projectId ? geladenerStand.stand : null
  return {
    effectiveRole,
    canEdit: canEditCards(effectiveRole, platformAdmin) && fremdBoard?.imFremdsystem !== true,
    canModerate: canModerateComments(effectiveRole, platformAdmin),
    darfStoerungenSehen: darfStoerungenSehen(effectiveRole, platformAdmin),
    fremdBoard,
  }
}
