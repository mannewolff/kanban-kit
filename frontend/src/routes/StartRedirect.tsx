import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/**
 * Die Weiche auf `/` (Issue #1082, Plan #1072 E8).
 *
 * Ein Plattform-Admin landet auf dem Plattform-Leitstand — AK 1 der fachlichen Quelle #1064 will,
 * dass er beim Anmelden sieht, ob in der Nacht etwas schiefging, ohne zu suchen. Alle anderen
 * gehen auf die Projektliste, die dafür den eigenen Pfad `/projects` bekommen hat; ohne ihn käme
 * ein Plattform-Admin gar nicht mehr an sie heran.
 *
 * **Der Name endet auf `Redirect`, und das ist keine Kosmetik:** `designQuelle.test.ts` nimmt
 * Weiterleitungen allein an diesem Suffix von der Tabelle „Ansicht → Regel" in `CLAUDE-design.md`
 * aus. Eine Weiche stellt nichts dar; eine Tabellenzeile dafür wäre eine Regel ohne Gegenstand.
 *
 * **`location.state` wird durchgereicht** (Plan E21): `ProjectsPage` routet bei genau einem Projekt
 * durch, wenn `location.key === 'default'` **oder** `state.autoRoute` gesetzt ist. Hinter einer
 * Weiterleitung ist der Key nicht mehr `default` — ohne den State verlöre ein Nutzer mit genau
 * einem Projekt das automatische Durchrouten nach dem Anmelden.
 */
export function StartRedirect() {
  const { user } = useAuth()
  const { state } = useLocation()

  return user?.platformRole === 'ADMIN' ? (
    <Navigate to="/plattform-leitstand" replace />
  ) : (
    <Navigate to="/projects" replace state={state} />
  )
}
