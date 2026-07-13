import { useEffect, useState } from 'react'
import { projectsApi } from '../api/projects'

/**
 * Liefert den Namen des Projekts mit der angegebenen ID für die Kopfzeilen der board-gebundenen
 * Seiten (die nur über `boardId` geroutet sind). `null`, solange nicht geladen oder nicht gefunden.
 * Quelle ist `projectsApi.list()` — dieselbe Liste, aus der die Seiten auch die Projektrolle
 * auflösen.
 */
export function useProjectName(projectId: number | null): string | null {
  const [name, setName] = useState<string | null>(null)
  useEffect(() => {
    if (projectId == null) {
      setName(null)
      return
    }
    let active = true
    void projectsApi.list().then((ps) => {
      if (active) {
        setName(ps.find((p) => p.id === projectId)?.name ?? null)
      }
    })
    return () => {
      active = false
    }
  }, [projectId])
  return name
}
