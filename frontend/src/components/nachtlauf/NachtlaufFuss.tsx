import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'

/** Eine Angabe der Fußzeile: ihre Benennung, ihr Wert und ob der Wert ein Vorbehalt ist. */
export interface Fussangabe {
  label: string
  wert: string
  /**
   * `true` gibt dem Wert die Warnfarbe des Entwurfs (`dd.warn`). Sie gilt einem Vorbehalt, nicht
   * einer fehlenden Angabe: „nicht angegeben" ist keine Warnung, sondern eine Auskunft.
   */
  vorbehalt?: boolean
}

/**
 * Die Fußzeile eines Laufs (#918) — die Angaben unter den Vorgängen, als Beschreibungsliste in der
 * Gestalt des Entwurfs.
 *
 * <p>Beide Lauf-Arten führen andere Angaben: die Kette ihre Zeitvorgaben, ihr Kostenbudget und die
 * höchsten Kosten eines Vorgangs, der Umsetzungs-Lauf das Ergebnis der Nacht, seinen teuersten
 * Vorgang und die Herkunft seines Stands. Welche es sind, entscheidet die Seite.
 *
 * <p><b>Als `<dl>`</b> und nicht als Reihe von Kästen: Jede Angabe ist ein Paar aus Benennung und
 * Wert, und das ist genau die Bedeutung einer Beschreibungsliste — ein Vorlesewerkzeug liest sie
 * dann als Paare statt als lose Textfolge.
 */
export function NachtlaufFuss({
  angaben,
  testId,
}: Readonly<{ angaben: readonly Fussangabe[]; testId: string }>) {
  return (
    <Box
      component="dl"
      data-testid={testId}
      sx={{
        marginTop: '34px',
        paddingTop: '18px',
        borderTop: `1px solid ${NACHTLAUF_FARBEN.line}`,
        display: 'flex',
        flexWrap: 'wrap',
        gap: '10px 26px',
        marginBottom: 0,
      }}
    >
      {angaben.map((angabe) => (
        <Box key={angabe.label}>
          <Box
            component="dt"
            sx={{
              fontFamily: NACHTLAUF_SCHRIFTEN.body,
              fontSize: 11,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: NACHTLAUF_FARBEN.ink3,
            }}
          >
            {angabe.label}
          </Box>
          <Typography
            component="dd"
            sx={{
              margin: '2px 0 0',
              fontFamily: NACHTLAUF_SCHRIFTEN.mono,
              fontSize: 13,
              fontVariantNumeric: 'tabular-nums',
              color: angabe.vorbehalt === true ? NACHTLAUF_FARBEN.budget : NACHTLAUF_FARBEN.ink2,
            }}
          >
            {angabe.wert}
          </Typography>
        </Box>
      ))}
    </Box>
  )
}
