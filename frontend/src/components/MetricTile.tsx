import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { EPIC_EDGE_WIDTH, TABELLENZIFFERN } from '../theme'

interface Props {
  /** Beschriftung der Kennzahl, z. B. der Spaltenname. */
  label: string
  /** Bereits formatierter Wert — die Kachel rechnet nicht und formatiert nicht selbst. */
  value: string
  /**
   * Anzahl der Messungen hinter dem Wert. Eine Zahl aus 3 Messungen liest sich anders als eine
   * aus 34. `0` heißt „keine Datenbasis": der Wert ist dann eine Leerangabe, die Kachel nimmt ihn
   * optisch zurück und sagt es ausdrücklich. Der Leerwert-Zustand hat damit genau eine Quelle —
   * eine zweite Prop daneben könnte der Stichprobengröße widersprechen, ohne dass es auffiele.
   * `undefined` heißt dagegen „Datenbasis nicht angegeben": normale Optik, keine Messungszeile.
   */
  sample?: number
  /**
   * Grund der Hervorhebung, z. B. „längste Spalte“. Farbe und Text hängen bewusst zusammen:
   * eine farblich markierte Kachel trägt immer auch die Begründung als Text, damit die Aussage
   * nicht allein an der Farbe hängt.
   */
  emphasis?: string
  /**
   * Zeitraum, auf den sich der Wert bezieht, z. B. „gesamter Verlauf" (AK 11, #959). Er steht auch
   * ohne Datenbasis da: Wer „keine Messung" liest, soll wissen, in welchem Zeitraum nichts gemessen
   * wurde. Fehlt die Angabe, entfällt die Zeile.
   */
  zeitraum?: string
}

/**
 * Eine Kennzahl als Kachel: Beschriftung, Wert und Datenbasis stehen als Text da — ohne Hovern
 * und ohne Umrechnen. Bewusst ohne Vergleichsbalken: Verweildauern liegen im selben Board
 * zwischen Minuten und Tagen, ein gemeinsamer Maßstab macht die kurzen Werte unsichtbar und
 * täuscht eine Vergleichbarkeit vor, die es nicht gibt.
 */
export function MetricTile({ label, value, sample, emphasis, zeitraum }: Readonly<Props>) {
  const noMeasurement = sample === 0
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        // Betonung an der Kante statt auf der Flaeche (#651, E3/E7): die Flaeche bleibt weiss,
        // die linke Teal-Kante hebt hervor -- dieselbe Breite wie die Vorhaben-Kante am Board.
        ...(emphasis ? { borderLeft: `${EPIC_EDGE_WIDTH}px solid`, borderLeftColor: 'primary.main' } : {}),
      }}
    >
      <Stack spacing={0.25}>
        <Typography variant="caption" color="text.secondary">
          {label}
        </Typography>
        <Typography
          variant="h6"
          sx={{
            fontWeight: noMeasurement ? 400 : 700,
            color: noMeasurement ? 'text.secondary' : 'text.primary',
            // Kacheln stehen nebeneinander und werden verglichen: gleich breite Ziffern (AK 10).
            ...TABELLENZIFFERN,
          }}
        >
          {value}
        </Typography>
        {zeitraum && (
          <Typography variant="caption" color="text.secondary">
            {zeitraum}
          </Typography>
        )}
        {noMeasurement ? (
          <Typography variant="caption" color="text.secondary">
            keine Messung
          </Typography>
        ) : (
          sample != null && (
            <Typography variant="caption" color="text.secondary">
              {sample === 1 ? '1 Messung' : `${sample} Messungen`}
            </Typography>
          )
        )}
        {emphasis && (
          <Typography variant="caption" sx={{ color: 'primary.dark', fontWeight: 700 }}>
            {emphasis}
          </Typography>
        )}
      </Stack>
    </Paper>
  )
}
