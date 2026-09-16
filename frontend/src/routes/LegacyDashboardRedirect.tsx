import { Navigate, useLocation, useParams } from 'react-router-dom'

/**
 * Weiterleitung des alten Pfads `/boards/:boardId/dashboard` auf den Leitstand
 * `/boards/:boardId/leitstand` (#979). Lesezeichen auf die frühere Kennzahlen-Ansicht laufen so nicht
 * ins Leere; Query und Fragment werden mitgenommen — wie bei {@link LegacyEpicsRedirect}.
 */
export function LegacyDashboardRedirect() {
  const { boardId } = useParams()
  const { search, hash } = useLocation()

  return <Navigate to={`/boards/${boardId}/leitstand${search}${hash}`} replace />
}
