import { useEffect, useState } from 'react'
import { projectsApi, type Strenge } from '../api/projects'

/**
 * Liefert die zuletzt vom Kit gemeldete Strenge-Stufe des Projekts (Issue #2014) für den Kopf des
 * Leitstands eines Boards. `null`, solange nicht geladen, nicht gefunden oder nie gemeldet.
 *
 * Quelle ist `projectsApi.list()` wie bei {@link useProjectName} — die Projektliste enthält auch
 * die Projekte, die ein Plattform-Admin ohne eigene Mitgliedschaft sieht. Ein Fehler beim Laden
 * lässt die Marke weg; sie ist eine Auskunft und hält die Seite nicht an.
 */
export function useProjectStrictness(projectId: number | null): Strenge | null {
  const [stufe, setStufe] = useState<Strenge | null>(null)
  useEffect(() => {
    setStufe(null)
    if (projectId == null) {
      return
    }
    let active = true
    projectsApi
      .list()
      .then((ps) => {
        if (active) {
          setStufe(ps.find((p) => p.id === projectId)?.strictness ?? null)
        }
      })
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [projectId])
  return stufe
}
