import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'

interface Props {
  /** Beschriftung der Kennzahl, z. B. der Spaltenname. */
  label: string
  /** Bereits formatierter Wert — die Kachel rechnet nicht und formatiert nicht selbst. */
  value: string
  /** Anzahl der Messungen hinter dem Wert. Eine Zahl aus 3 Messungen liest sich anders als eine aus 34. */
  sample?: number
  /**
   * Grund der Hervorhebung, z. B. „längste Spalte“. Farbe und Text hängen bewusst zusammen:
   * eine farblich markierte Kachel trägt immer auch die Begründung als Text, damit die Aussage
   * nicht allein an der Farbe hängt.
   */
  emphasis?: string
  /**
   * true = keine Datenbasis. Der Wert ist dann eine Leerangabe und darf nicht wie ein
   * Messergebnis aussehen — die Kachel nimmt ihn optisch zurück und sagt es ausdrücklich.
   */
  noMeasurement?: boolean
}

/**
 * Eine Kennzahl als Kachel: Beschriftung, Wert und Datenbasis stehen als Text da — ohne Hovern
 * und ohne Umrechnen. Bewusst ohne Vergleichsbalken: Verweildauern liegen im selben Board
 * zwischen Minuten und Tagen, ein gemeinsamer Maßstab macht die kurzen Werte unsichtbar und
 * täuscht eine Vergleichbarkeit vor, die es nicht gibt.
 */
export function MetricTile({ label, value, sample, emphasis, noMeasurement }: Readonly<Props>) {
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        ...(emphasis ? { borderColor: 'primary.main', bgcolor: 'action.hover' } : {}),
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
          }}
        >
          {value}
        </Typography>
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
