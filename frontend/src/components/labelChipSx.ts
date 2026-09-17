import { grey } from '@mui/material/colors'
import { theme } from '../theme'

/**
 * Flaeche und lesbare Textfarbe eines Label-Chips.
 *
 * Labelfarben sind nutzerdefiniert und serverseitig nur laengenbegrenzt (`@NotBlank`,
 * `@Size(max = 20)`); das Domain-Modell erlaubt ausdruecklich „Hex **oder** Theme-Token".
 * `getContrastText` rechnet aber nur auf echten CSS-Farben und **wirft** bei allem anderen —
 * ohne ErrorBoundary im Frontend nimmt ein einziges solches Label den ganzen React-Baum mit.
 *
 * Deshalb eine Quelle fuer alle vier Chip-Stellen, mit Fangnetz: `bgcolor` loest Theme-Pfade
 * weiterhin selbst auf, der Text faellt dann auf Weiss zurueck — das Verhalten vor #649.
 *
 * **Farben als Verweis, Rechnung auf dem Wert (#952).** Die Farben der Palette (Grau, Weiss) liest
 * das Modul ueber `theme.vars`, damit sie mit dem Erscheinungsbild umschalten. `getContrastText`
 * bleibt eine Funktion der Palette — sie ist in beiden Erscheinungsbildern dieselbe — und rechnet
 * zum Grau auf dessen Farbwert: Auf einem Verweis wuerfe sie und fiele auf Weiss zurueck, das auf
 * Grau 500 schlechter lesbar ist als die dunkle Schrift, die es bisher trug.
 */
const kontrastText = theme.palette.getContrastText

export function labelChipSx(color?: string | null): { bgcolor: string; color: string } {
  const bgcolor = color ?? theme.vars.palette.grey[500]
  try {
    return { bgcolor, color: kontrastText(color ?? grey[500]) }
  } catch {
    return { bgcolor, color: theme.vars.palette.common.white }
  }
}
