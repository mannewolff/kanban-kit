import type { SxProps, Theme } from '@mui/material/styles'
import { EPIC_EDGE_WIDTH, STATUS_EDGE_WIDTH, SURFACE_HOVER_SHADOW } from '../theme'

/**
 * Kanten-Semantik der Designsprache „Kante" (#649, Plandokument #617), einmal festgelegt und ab
 * hier appweit gültig: **oben = Status, links = Zugehörigkeit (Vorhaben), Haarlinie ringsum =
 * Begrenzung.** Die Fläche bleibt weiß, Tiefe entsteht ausschließlich bei Interaktion.
 *
 * Ein gemeinsamer Baustein für Board und Ideen-Board, damit eine Kante auf beiden Seiten dasselbe
 * bedeutet und der Nutzer die Bedeutung nicht je Seite neu lernen muss (E5).
 */
export function edgeSurfaceSx(options: {
  /** Farbe der Status-Oberkante, üblicherweise `statusColors(name).dot`. */
  statusColor: string
  /** Farbe der linken Kante; ohne Zugehörigkeit bleibt die Kante unbelegt. */
  epicColor?: string
  /** Farbe der umlaufenden Haarlinie (Default: `divider`). */
  hairlineColor?: string
}): SxProps<Theme> & Record<string, unknown> {
  return {
    border: 1,
    // Die Haarlinie steht vor den Bedeutungs-Kanten: eine spätere `borderColor` überschriebe deren
    // Farbe wieder — genau der Fehler, den E10 am bisherigen Hover korrigiert.
    borderColor: options.hairlineColor ?? 'divider',
    borderTop: `${STATUS_EDGE_WIDTH}px solid ${options.statusColor}`,
    ...(options.epicColor != null ? { borderLeft: `${EPIC_EDGE_WIDTH}px solid ${options.epicColor}` } : {}),
    transition: 'box-shadow .15s',
    '&:hover': { boxShadow: SURFACE_HOVER_SHADOW },
  }
}
