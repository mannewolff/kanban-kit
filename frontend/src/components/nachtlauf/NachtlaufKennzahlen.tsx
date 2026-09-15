import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { NACHTLAUF_FARBEN, NACHTLAUF_MASSE, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'

/** Eine Kennzahl der Nacht: der Wert, seine Benennung und ein etwaiger Vorbehalt. */
export interface NachtlaufKennzahl {
  wert: string
  label: string
  /** Einschränkung des Werts darüber, etwa fehlende Kostenmeldungen (AK 9, Fall 1). */
  hinweis: string | null
}

/**
 * Die Kennzahlenreihe der Nacht in der Gestaltung des Entwurfs (#915, AK 3): die größte
 * Zahlendarstellung der Seite, zwischen zwei Haarlinien.
 *
 * <p><b>Sie stellt dar und rechnet nicht.</b> Welche Kennzahlen eine Lauf-Art führt und wie ihre
 * Werte entstehen, entscheidet die Seite — dieselben Funktionen wie vor diesem Paket, damit die
 * Werte nachweislich unverändert bleiben (Nicht-Ziel 3).
 *
 * <p><b>Der Vorbehalt steht zusätzlich zur Benennung</b>, nicht an ihrer Stelle. Der Entwurf setzt
 * ihn anstelle des Labels (`kz-label kz-note`); dann verschwände aber „Kosten der Nacht", und die
 * Zahl stünde ohne Aussage darüber, was sie zählt.
 */
export function NachtlaufKennzahlen({
  kennzahlen,
  /**
   * Der Grund, aus dem der Lauf keine Kosten und Züge führt (Plan #864, E8). Wo er steht, hat die
   * Seite die betroffenen Kennzahlen gar nicht erst übergeben.
   */
  hinweis,
}: Readonly<{ kennzahlen: readonly NachtlaufKennzahl[]; hinweis?: string }>) {
  return (
    <Box data-testid="nachtlauf-kennzahlen">
      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '28px',
          mt: '26px',
          padding: '18px 0 20px',
          borderTop: '1px solid',
          borderBottom: '1px solid',
          borderColor: NACHTLAUF_FARBEN.line,
        }}
      >
        {kennzahlen.map((kennzahl) => (
          <Box key={kennzahl.label} data-testid={`nachtlauf-kennzahl-${kennzahl.label}`}>
            <Typography
              component="div"
              sx={{
                fontFamily: NACHTLAUF_SCHRIFTEN.display,
                fontWeight: 600,
                fontSize: NACHTLAUF_MASSE.kennzahlWert,
                fontVariantNumeric: 'tabular-nums',
                lineHeight: 1.1,
                color: NACHTLAUF_FARBEN.ink,
              }}
            >
              {kennzahl.wert}
            </Typography>
            <Typography component="div" sx={BESCHRIFTUNG}>
              {kennzahl.label}
            </Typography>
            {kennzahl.hinweis !== null && (
              <Typography component="div" sx={{ ...BESCHRIFTUNG, color: NACHTLAUF_FARBEN.budget }}>
                {kennzahl.hinweis}
              </Typography>
            )}
          </Box>
        ))}
      </Box>
      {hinweis !== undefined && (
        <Typography
          component="div"
          data-testid="nachtlauf-kennzahlen-hinweis"
          sx={{ ...BESCHRIFTUNG, mt: 1 }}
        >
          {hinweis}
        </Typography>
      )}
    </Box>
  )
}

const BESCHRIFTUNG = {
  fontFamily: NACHTLAUF_SCHRIFTEN.body,
  fontSize: 12,
  letterSpacing: '0.04em',
  color: NACHTLAUF_FARBEN.ink3,
} as const
