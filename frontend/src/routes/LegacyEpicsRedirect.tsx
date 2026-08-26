import { Navigate, useLocation, useParams } from 'react-router-dom'

/**
 * Weiterleitung des alten Pfads `/boards/:boardId/epics` auf `/boards/:boardId/vorhaben`.
 *
 * <p>Gesetzte Lesezeichen und Verweise aus der Dokumentation dürfen nach der Umbenennung nicht ins
 * Leere laufen. Query und Fragment werden mitgenommen — eine Weiterleitung, die Parameter
 * verschluckt, ist schlechter als keine.
 *
 * <p>Liegt bewusst hier statt in `App.tsx`: Diese Datei trägt Logik und muss gemessen werden,
 * `App.tsx` ist in `vite.config.ts` von der Coverage ausgenommen („reines Routen-Wiring").
 */
export function LegacyEpicsRedirect() {
  const { boardId } = useParams()
  const { search, hash } = useLocation()

  return <Navigate to={`/boards/${boardId}/vorhaben${search}${hash}`} replace />
}
