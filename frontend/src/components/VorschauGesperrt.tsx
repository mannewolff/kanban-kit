import Tooltip from '@mui/material/Tooltip'
import { cloneElement, type ReactElement } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useT } from '../i18n/SpracheContext'

/** Was ein umschlossenes Bedienelement annehmen muss, damit die Vorschau es sperren kann. */
interface Sperrbar {
  disabled?: boolean
  'aria-label'?: string
}

interface VorschauGesperrtProps {
  /** Genau ein Bedienelement zum Ändern, etwa ein Knopf zum Anlegen oder Löschen. */
  children: ReactElement<Sperrbar>
}

/**
 * Sperrt ein Bedienelement zum Ändern in der nur lesenden „Ansicht als XY“ (Plan #1914, E15).
 *
 * Außerhalb der Vorschau reicht er das Element unverändert durch. In der Vorschau setzt er es auf
 * `disabled` und gibt ihm den Hinweis „Vorschau“ als Tooltip und als zugänglichen Namen. Sichtbar
 * bleibt das Element trotzdem: Ob XY es hätte, entscheidet weiter die Rolle aus den Antworten.
 */
export function VorschauGesperrt({ children }: Readonly<VorschauGesperrtProps>) {
  const { vorschau } = useAuth()
  const t = useT()
  if (!vorschau) {
    return children
  }
  // Der Umschlag mit `span` gibt dem Tooltip am gesperrten Element einen Ereignisempfänger.
  return (
    <Tooltip title={t.VORSCHAU.GESPERRT}>
      <span>{cloneElement(children, { disabled: true, 'aria-label': t.VORSCHAU.GESPERRT })}</span>
    </Tooltip>
  )
}
