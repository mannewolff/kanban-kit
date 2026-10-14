import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import IconButton from '@mui/material/IconButton'
import type { TooltipProps } from '@mui/material/Tooltip'
import type { ReactNode } from 'react'
import { useT } from '../i18n/SpracheContext'
import { useTabletQuer } from '../lib/tabletQuer'
import { TEXT_SCHWACH } from '../theme'
import { TabletErklaerung } from './Erklaerung'

/**
 * Info-Symbol neben einem Element mit eigener Aktion, etwa einem Eintrag in Auswahl oder Filter
 * (Plan #1707, E7). Auf dem Tablet quer öffnet Antippen des Symbols die Erklärung über denselben
 * Offen-Zustand wie jede andere Erklärung; die Aktion des Nachbarn bleibt dem Antippen des Elements
 * selbst. Am Desktop rendert es nichts — dort erscheint die Erklärung beim Darüberfahren (AK 7).
 *
 * `name` ist der Name des Elements und trägt die Beschriftung des Symbols.
 */
export function ErklaerungInfo({
  name,
  title,
  placement,
}: Readonly<{ name: string; title: ReactNode; placement?: TooltipProps['placement'] }>) {
  const t = useT()
  const tabletQuer = useTabletQuer()
  if (!tabletQuer) return null
  return (
    <TabletErklaerung title={title} placement={placement} describeChild ausklappbar>
      <IconButton size="small" aria-label={t.ERKLAERUNG_INFO.ZEIGEN(name)} sx={{ p: '2px', color: TEXT_SCHWACH }}>
        <InfoOutlinedIcon fontSize="inherit" />
      </IconButton>
    </TabletErklaerung>
  )
}
