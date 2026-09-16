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
  /**
   * Die Fläche wird gerade gezogen (AK 7, AK 8, #956). Sie bleibt als Platzhalter an ihrem Platz:
   * zurückgenommen, mit gestrichelter Haarlinie, ohne Tiefe, Inhalt unsichtbar. Das Ziehbild des
   * Browsers zeigt die Karte selbst — wer zieht, sieht also, was sich bewegt und wo es herkommt.
   */
  bewegt?: boolean
}): SxProps<Theme> & Record<string, unknown> {
  return {
    border: 1,
    // Die Haarlinie steht vor der Status-Kante: eine spätere `borderColor` überschriebe deren
    // Farbe wieder.
    borderColor: options.hairlineColor ?? 'divider',
    // Ebenso die Strichelung: Sie gilt der Haarlinie, die Status-Kante danach bleibt durchgezogen.
    ...(options.bewegt ? { borderStyle: 'dashed' } : {}),
    borderLeft: `${STATUS_EDGE_WIDTH}px solid ${options.statusColor}`,
    borderRadius: `${CARD_RADIUS}px`,
    boxShadow: CARD_SHADOW,
    transition: 'box-shadow .2s ease, transform .2s ease',
    // Wer Bewegung abgestellt hat, bekommt dieselbe Tiefe ohne Übergang: Die Dauer setzt die
    // zentrale Regel in `theme.ts` auf null (#953), statt dass jede Fläche ihren eigenen Vorbehalt führt.
    '&:hover': { boxShadow: CARD_SHADOW_HOVER, transform: `translateY(${CARD_LIFT}px)` },
    ...(options.bewegt ? PLATZHALTER_SX : {}),
  }
}

/**
 * Platzhalter an der Stelle eines gezogenen Elements (AK 8, #956, #957): zurückgenommen, ohne
 * Tiefe, auf getönter Fläche, Inhalt unsichtbar. `visibility` statt Entfernen: Der Platzhalter
 * behält die Höhe, und das Element bleibt im Dokument — verschwände die Quelle, bräche das native
 * Ziehen ab. Die Strichelung der Haarlinie setzt der Aufrufer vor seine Status-Kante, damit jene
 * durchgezogen bleibt.
 */
export const PLATZHALTER_SX = {
  opacity: 0.6,
  boxShadow: 'none',
  bgcolor: 'action.hover',
  '& > *': { visibility: 'hidden' },
  '&:hover': { boxShadow: 'none', transform: 'none' },
} as const

/**
 * Ablagefläche während eines Ziehvorgangs (AK 8, #956): ein gestrichelter Rahmen in der
 * Primärfarbe auf getönter Fläche, nach innen versetzt, damit er die Kanten der Spalte nicht
 * überdeckt. Außerhalb eines Ziehvorgangs trägt die Fläche nichts.
 */
export function ablageflaecheSx(aktiv: boolean): Record<string, unknown> {
  if (!aktiv) {
    return {}
  }
  return {
    outline: '2px dashed',
    outlineColor: 'primary.main',
    outlineOffset: '-4px',
    bgcolor: 'action.hover',
    borderRadius: `${CARD_RADIUS}px`,
  }
}
