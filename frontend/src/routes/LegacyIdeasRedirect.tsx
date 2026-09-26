import { Navigate, useLocation, useParams } from 'react-router-dom'

/**
 * Weiterleitung des alten Pfads `/projects/:projectId/ideas` auf die Projektseite (Issue #1201).
 *
 * <p>Die Ideen-Seite ist entfallen — der Spezifikations-Import sitzt jetzt in der Werkzeugleiste der
 * Board-Ansicht. Gesetzte Lesezeichen und Verweise aus der Dokumentation dürfen deshalb nicht ins
 * Leere laufen. Query und Fragment werden mitgenommen — eine Weiterleitung, die Parameter
 * verschluckt, ist schlechter als keine.
 *
 * <p>Liegt bewusst hier statt in `App.tsx`: Diese Datei trägt Logik und muss gemessen werden,
 * `App.tsx` ist in `vite.config.ts` von der Coverage ausgenommen („reines Routen-Wiring").
 */
export function LegacyIdeasRedirect() {
  const { projectId } = useParams()
  const { search, hash } = useLocation()

  return <Navigate to={`/projects/${projectId}${search}${hash}`} replace />
}
