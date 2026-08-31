import type { SxProps, Theme } from '@mui/material/styles'
import { CARD_LIFT, CARD_RADIUS, CARD_SHADOW, CARD_SHADOW_HOVER, STATUS_EDGE_WIDTH } from '../theme'

/**
 * Flächen-Semantik der Designsprache „Panel", einmal festgelegt und ab hier appweit gültig:
 * **links = Status, Haarlinie ringsum = Begrenzung, zwei Schattenebenen = Tiefe.**
 *
 * Ein gemeinsamer Baustein für Board und Ideen-Board, damit eine Kante auf beiden Seiten dasselbe
 * bedeutet und der Nutzer die Bedeutung nicht je Seite neu lernen muss.
 *
 * **Der Status sitzt links, nicht oben** (Nutzerentscheidung 2026-08-31). So stand er im Entwurf,
 * aus dem die Designsprache stammt: Variante 2 der Board-Studien vom 2026-08-22 trug
 * `border-left: 3px solid <status>` an der Karte, die Oberkante gehörte dort dem *Spaltenkopf*.
 * Bei der Umsetzung war die Oberkante an die Karte gewandert.
 *
 * **Die Vorhaben-Zugehörigkeit trägt keine Kante mehr.** Sie stand vorher links und müsste dem
 * Status weichen; stapeln ließe sich beides nur auf Kosten der Ruhe. Sichtbar bleibt sie über das
 * `EpicBadge`, das die Vorhaben-Farbe ohnehin führt (`epicColor`) — eine zweite Anzeige derselben
 * Information an derselben Karte war redundant. Es ist dasselbe Argument, mit dem die Studie den
 * farbigen Punkt im Spaltenkopf gestrichen hat: „redundant, wenn die Karten die Farbe schon tragen".
 *
 * **Tiefe im Ruhezustand, nicht erst beim Hover.** Die frühere Fassung ließ die Fläche flach und
 * hob sie nur unter dem Zeiger an. Das war die Ursache des Eindrucks „alles weiß und flach": Wer
 * die Maus nicht bewegt, sieht kein einziges Relief.
 */
export function edgeSurfaceSx(options: {
  /** Farbe der linken Status-Kante, üblicherweise `statusColors(name).dot`. */
  statusColor: string
  /** Farbe der umlaufenden Haarlinie (Default: `divider`). */
  hairlineColor?: string
}): SxProps<Theme> & Record<string, unknown> {
  return {
    border: 1,
    // Die Haarlinie steht vor der Status-Kante: eine spätere `borderColor` überschriebe deren
    // Farbe wieder.
    borderColor: options.hairlineColor ?? 'divider',
    borderLeft: `${STATUS_EDGE_WIDTH}px solid ${options.statusColor}`,
    borderRadius: `${CARD_RADIUS}px`,
    boxShadow: CARD_SHADOW,
    transition: 'box-shadow .2s ease, transform .2s ease',
    '&:hover': { boxShadow: CARD_SHADOW_HOVER, transform: `translateY(${CARD_LIFT}px)` },
    // Wer Bewegung abgestellt hat, bekommt die Tiefe trotzdem — nur ohne das Anheben.
    '@media (prefers-reduced-motion: reduce)': {
      transition: 'none',
      '&:hover': { boxShadow: CARD_SHADOW_HOVER, transform: 'none' },
    },
  }
}
