import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { NACHTLAUF_FARBEN, NACHTLAUF_MASSE, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'

/**
 * Der Kopf einer Nacht in der Gestaltung des Entwurfs (#915, AK 3): Vorzeile, Überschrift,
 * Metazeile. Die Kernaussage steht zuoberst, und die Überschrift ist die größte Schrift der Seite.
 *
 * <p><b>Er stellt dar und rechnet nicht.</b> Die Angaben der Metazeile kommen fertig von der
 * Seite, die sie schon vor diesem Paket gebildet hat. So wandert keine Rechenstelle aus der Seite
 * in eine Komponente — und die Werte bleiben nachweislich dieselben (Nicht-Ziel 3).
 *
 * <p><b>Die Metazeile ist lang</b>, und das ist Absicht: Der Entwurf zeigt dort vier Angaben, die
 * heutige Kopfzeile eines Laufs trägt aber mehr — Lauf-Art, Dauer, bearbeitete und übergangene
 * Karten, den Einlieferungsstand. AK 10 verlangt, sie zu erhalten und einzupassen, statt sie
 * fallen zu lassen.
 *
 * <p><b>Ohne Überschriftenebene:</b> Der Kopf steht im `AccordionSummary` des Laufs, also in einem
 * `<button>`; ein `<h1>` wäre dort ungültiges HTML. Die Bedeutung trägt die Gestaltung.
 */
export function NachtlaufKopf({
  startedAt,
  mode,
  angaben,
}: Readonly<{
  startedAt: string
  mode: 'CHAIN' | 'IMPLEMENTATION'
  /**
   * Was über den Lauf zu sagen ist: Modell, Label und Abschluss aus dem Stand, die Herkunft des
   * Stands (AK 9, Fall 3) und die Angaben, die der Entwurf nicht vorsieht und die nach AK 10
   * trotzdem erhalten bleiben. Die Seite stellt sie zusammen, leere Einträge lässt sie weg.
   */
  angaben: readonly string[]
}>) {
  const start = new Date(startedAt)
  const tag = start.toLocaleDateString('de-DE', { day: 'numeric', month: 'long' })
  const metazeile = [laufkennung(start), ...angaben.filter((eintrag) => eintrag !== '')].join(' · ')

  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography
        component="div"
        data-testid="nachtlauf-vorzeile"
        sx={{
          fontFamily: NACHTLAUF_SCHRIFTEN.mono,
          fontSize: NACHTLAUF_MASSE.vorzeile,
          letterSpacing: '0.12em',
          textTransform: 'uppercase',
          color: NACHTLAUF_FARBEN.ink3,
          mb: '10px',
        }}
      >
        {`Nachtlauf · ${ART_TEXT[mode]}`}
      </Typography>
      <Typography
        component="div"
        data-testid="nachtlauf-ueberschrift"
        sx={{
          fontFamily: NACHTLAUF_SCHRIFTEN.display,
          fontWeight: 800,
          fontSize: NACHTLAUF_MASSE.ueberschrift,
          lineHeight: 1.1,
          letterSpacing: '-0.02em',
          textWrap: 'balance',
          color: NACHTLAUF_FARBEN.ink,
          mb: '6px',
        }}
      >
        {`Nacht vom ${tag}`}
      </Typography>
      <Typography
        component="div"
        data-testid="nachtlauf-meta"
        sx={{
          fontFamily: NACHTLAUF_SCHRIFTEN.mono,
          fontSize: NACHTLAUF_MASSE.metazeile,
          color: NACHTLAUF_FARBEN.ink2,
        }}
      >
        {metazeile}
      </Typography>
    </Box>
  )
}

const ART_TEXT: Record<'CHAIN' | 'IMPLEMENTATION', string> = {
  CHAIN: 'Kette',
  IMPLEMENTATION: 'Umsetzung',
}

/**
 * Die Kennung des Laufs in der Form seines Dateinamens (`2026-09-14-131200`) — daran erkennt man
 * den Lauf in `night-run-<kennung>.json` wieder. Gebildet aus der **Ortszeit**, weil der Runner
 * den Dateinamen ebenso bildet.
 */
function laufkennung(start: Date): string {
  const zwei = (wert: number) => String(wert).padStart(2, '0')
  const datum = [start.getFullYear(), zwei(start.getMonth() + 1), zwei(start.getDate())].join('-')
  const zeit = [start.getHours(), start.getMinutes(), start.getSeconds()].map(zwei).join('')
  return `${datum}-${zeit}`
}
