import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { MELDER, NUT, RAND, SCHATTEN_NUTE, TEXT_SCHWACH, ZAHL } from '../../theme'

/** Ein Abschnitt des Bands — alles, was seine Darstellung und seine Ansage brauchen. */
export interface Bandabschnitt {
  schluessel: string
  label: string
  /** Der Breitenanteil: die Zeitvorgabe in Minuten; ohne Vorgabe ein fester Ersatzwert. */
  anteil: number
  /** Die Füllung in Prozent, **bei 100 gekappt** — der tatsächliche Wert steht in `zahlen`. */
  fuellung: number
  /** Ob der Vorgang diesen Arbeitsschritt überhaupt erreicht hat. */
  erreicht: boolean
  /** Verbrauch und Vorgabe als Zahlen. */
  zahlen: string
  /** Der Vermerk unter dem Abschnitt; `null`, wo keiner steht. */
  vermerk: string | null
  /** Die Füllfarbe — der Ampelton nur an dem Abschnitt, an dem der Vorgang endete. */
  farbe: string
}

/**
 * Das Stufenband eines Ketten-Vorgangs in der Gestaltung des Entwurfs (#916, AK 5): die vier
 * Arbeitsschritte in der Breite ihrer Zeitvorgaben, jeder gefüllt, soweit er seine Zeit verbraucht
 * hat, darunter Verbrauch und Vorgabe als Zahlen. Wer hinsieht, erkennt ohne Zahlenlesen, an
 * welchem Schritt eine Kette riss.
 *
 * <p><b>Die Aussage hängt nie an der Farbe</b> (`CLAUDE-react.md`): „nicht erreicht", „am
 * Zeitbudget beendet" und „hier abgebrochen" stehen unter dem betroffenen Abschnitt in Worten. Die
 * Schraffur für einen nie erreichten Schritt (AK 8) ist die zweite, nicht die einzige
 * Unterscheidung von einem erreichten, aber leeren Balken.
 *
 * <p><b>Die Ansage für Vorlesewerkzeuge</b> ist der Grund für `role="img"`: Die Zahlen darunter
 * werden damit nicht ein zweites Mal einzeln vorgelesen, sondern genau einmal in dieser
 * Reihenfolge. Sie trägt denselben Inhalt wie vor dem Umbau.
 *
 * <p><b>Die Rechnung steht nicht hier</b>, sondern weiter in der Seite (`bandabschnitt`): Die
 * Komponente stellt die fertigen Abschnitte dar, damit die Werte nachweislich dieselben bleiben.
 */
export function NachtlaufStufenband({
  abschnitte,
  ansage,
  testId,
  /** Der Prefix der Testkennungen je Abschnitt — `<abschnittTestId>-<schluessel>`. */
  abschnittTestId,
}: Readonly<{
  abschnitte: readonly Bandabschnitt[]
  ansage: string
  testId: string
  abschnittTestId: string
}>) {
  return (
    <Box
      role="img"
      aria-label={ansage}
      data-testid={testId}
      sx={{ display: 'flex', gap: '3px' }}
    >
      {abschnitte.map((abschnitt) => (
        <Box
          key={abschnitt.schluessel}
          data-testid={`${abschnittTestId}-${abschnitt.schluessel}`}
          data-anteil={abschnitt.anteil}
          data-fuellung={abschnitt.fuellung}
          data-erreicht={abschnitt.erreicht ? 'ja' : 'nein'}
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: '5px',
            flexGrow: abschnitt.anteil,
            flexBasis: 0,
            minWidth: 0,
          }}
        >
          <Box
            sx={{
              height: 26,
              borderRadius: '3px',
              overflow: 'hidden',
              backgroundColor: NUT,
              boxShadow: SCHATTEN_NUTE,
              // AK 8: Ein nie erreichter Schritt trägt die Schraffur des Entwurfs und einen
              // Innenrahmen — sonst sähe er aus wie ein erreichter Schritt ohne Verbrauch.
              ...(abschnitt.erreicht
                ? {}
                : {
                    backgroundImage: `repeating-linear-gradient(135deg, color-mix(in srgb, ${MELDER.grau} 26%, transparent) 0 6px, transparent 6px 12px)`,
                    boxShadow: `${SCHATTEN_NUTE}, inset 0 0 0 1px ${RAND}`,
                  }),
            }}
          >
            {abschnitt.erreicht && (
              <Box sx={{ height: '100%', width: `${abschnitt.fuellung}%`, bgcolor: abschnitt.farbe }} />
            )}
          </Box>
          <Typography component="div" sx={LABEL_STIL}>
            {abschnitt.label}
          </Typography>
          <Typography
            component="div"
            sx={{ ...ZEIT_STIL, color: abschnitt.erreicht ? 'text.secondary' : TEXT_SCHWACH }}
          >
            {abschnitt.zahlen}
          </Typography>
          {abschnitt.vermerk !== null && (
            <Typography component="div" sx={{ ...ZEIT_STIL, color: MELDER.bernst }}>
              {abschnitt.vermerk}
            </Typography>
          )}
        </Box>
      ))}
    </Box>
  )
}

const LABEL_STIL = {
  fontSize: 11,
  letterSpacing: '0.04em',
  color: TEXT_SCHWACH,
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
} as const

const ZEIT_STIL = {
  ...ZAHL,
  fontSize: 12,
} as const
