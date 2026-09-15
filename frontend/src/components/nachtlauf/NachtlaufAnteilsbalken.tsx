import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'

/**
 * Der Anteil eines Vorgangs eines Umsetzungs-Laufs an der Laufzeit der Nacht (#917, AK 6).
 *
 * <p>An der Stelle, an der ein Ketten-Vorgang sein Stufenband hat: Ein Umsetzungs-Vorgang kennt
 * keine Arbeitsschritte, sondern eine Strecke — derselbe Balken, dieselbe Höhe, dieselbe Farbe,
 * nur eine statt vier.
 *
 * <p><b>Die Aussage hängt nie an der Farbe</b> (`CLAUDE-react.md`): Der Balken trägt eine Ansage
 * mit Kartennummer, Zustand und Dauer, und Dauer wie Anteil stehen daneben sichtbar in Worten.
 *
 * <p><b>Ohne Bezugsgröße kein Balken</b>: Ein Vorgang ohne gemessene Dauer und ein Lauf mit
 * weniger als zwei gemessenen Vorgängen bekommen keinen — ein Balken über einem Verhältnis, das
 * es nicht gibt, behauptete eines. Darüber entscheidet die Seite; hier steht nur, was daraus wird.
 */
export function NachtlaufAnteilsbalken({
  /** Der Anteil in Prozent, wie die Seite ihn rechnet. */
  anteil,
  /** Dauer und Anteil in Worten, etwa „12 Min · 15 % der Nacht". */
  beschriftung,
  ansage,
  farbe,
  testId,
  /** Eigene Kennung statt einer Ableitung aus {@link testId} — sonst träfe ein Präfix-Muster über
   * die Abschnitte auch die Füllungen und zählte doppelt. */
  fuellungTestId,
}: Readonly<{
  anteil: number
  beschriftung: string
  ansage: string
  farbe: string
  testId: string
  fuellungTestId: string
}>) {
  return (
    <Box
      role="img"
      aria-label={ansage}
      data-testid={testId}
      data-anteil={anteil}
      sx={{
        display: 'grid',
        gridTemplateColumns: '1fr auto',
        alignItems: 'center',
        gap: '12px',
      }}
    >
      <Box
        sx={{
          height: 26,
          borderRadius: '3px',
          overflow: 'hidden',
          backgroundColor: NACHTLAUF_FARBEN.rail,
        }}
      >
        <Box
          data-testid={fuellungTestId}
          sx={{ height: '100%', width: `${anteil}%`, backgroundColor: farbe }}
        />
      </Box>
      <Typography
        component="div"
        sx={{
          fontFamily: NACHTLAUF_SCHRIFTEN.mono,
          fontSize: 12,
          fontVariantNumeric: 'tabular-nums',
          color: NACHTLAUF_FARBEN.ink2,
          whiteSpace: 'nowrap',
        }}
      >
        {beschriftung}
      </Typography>
    </Box>
  )
}
