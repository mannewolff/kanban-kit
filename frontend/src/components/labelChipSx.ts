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
 */
export function labelChipSx(color?: string | null): { bgcolor: string; color: string } {
  const bgcolor = color ?? theme.palette.grey[500]
  try {
    return { bgcolor, color: theme.palette.getContrastText(bgcolor) }
  } catch {
    return { bgcolor, color: theme.palette.common.white }
  }
}
