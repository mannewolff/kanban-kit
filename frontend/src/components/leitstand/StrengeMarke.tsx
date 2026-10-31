import Box from '@mui/material/Box'
import Tooltip from '@mui/material/Tooltip'
import type { Strenge } from '../../api/projects'
import { useT } from '../../i18n/SpracheContext'
import { KlassenMarke } from './LeitstandBausteine'

/**
 * Die Strenge-Stufe, die das Projekt im Kit gewählt hat, als kleine Marke (Issue #2014) — im
 * Plattform-Leitstand am Kopf jeder Projektgruppe, im Leitstand eines Boards im Seitenkopf.
 *
 * <p>Nur der Name der Stufe und der Hinweis beim Darüberfahren, keine Erklärung der Stufen. Hat das
 * Projekt nie eine Stufe gemeldet, steht nichts da — eine Marke „unbekannt" wäre eine Aussage, die
 * das Kit nicht gemacht hat.
 *
 * <p>In Stahl wie die übrigen Auskunfts-Marken (`GleichzeitigkeitMarken`): Die Stufe ist eine
 * Auskunft, keine Störung; Zinnober und Bernstein tragen im Leitstand Fehlschlag und Warten.
 */
export function StrengeMarke({ stufe }: Readonly<{ stufe: Strenge | null | undefined }>) {
  const t = useT().STRENGE
  if (stufe == null) {
    return null
  }
  const name = t[stufe]
  return (
    <Tooltip title={t.HINWEIS}>
      <Box
        component="span"
        data-testid="strenge-marke"
        aria-label={`${t.HINWEIS}: ${name}`}
        sx={{ flex: 'none', display: 'inline-flex' }}
      >
        <KlassenMarke melder="stahl">{name}</KlassenMarke>
      </Box>
    </Tooltip>
  )
}
