import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import { useId, type ReactNode } from 'react'
import type { Melder } from '../../lib/leitstand'
import {
  ANZEIGE,
  ETIKETT,
  KLEIN_RADIUS,
  NUT,
  PANEL_RADIUS,
  PLATTE,
  PLATTE_FUSS,
  PLATTE_HOCH,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_PLATTE,
  TEXT_MATT,
  TEXT_SCHWACH,
  ZAHL,
} from '../../theme'
import { Led } from '../leitstand/LeitstandBausteine'

/**
 * Ein Nachtlauf als aufklappbare Platte (#988, Vorlage `docs/mockup-nachtlauf-lauf.html` Z. 377–456).
 *
 * <p><b>Die Laufblöcke haben mit diesem Paket die Nachtlauf-Ausnahme verlassen</b>
 * (`CLAUDE-design.md`): Farben, Schriften und Tiefen kommen aus den Tokens des Themes, die LED aus
 * den Leitstand-Bausteinen. Die große Überschrift der Vorlage `mockup-leitstand-nachtlauf.html`
 * („Nacht vom 14. September" in 40 px) ist damit einem 15-px-Titel gewichen — genau die Änderung,
 * die Manne am 2026-09-17 verlangt hat.
 *
 * <p><b>Warum nicht {@link Platte} aus den Leitstand-Bausteinen:</b> Deren Kopf trägt eine einzeilige
 * Überschrift mit Notiz; dieser hier trägt ein Etikett über dem Titel, eine Metazeile in
 * Ziffernschrift und einen Aufklapp-Pfeil, der den ganzen Inhalt schaltet. Die gemeinsame Gestalt
 * ist dünn, die Unterschiede sind es nicht — `Platte` um drei Sonderfälle zu erweitern hieße, sie
 * für einen einzigen Aufrufer umzubauen.
 *
 * <p><b>Der Inhalt wird zugeklappt gar nicht gerendert.</b> Das ist der Grund, warum bis zu 190
 * aufbewahrte Läufe nicht alle ihre Vorgänge, Karten und Herkunftsketten aufbauen (Plan #718, A8);
 * `Accordion` hat das vorher mit `unmountOnExit` besorgt.
 *
 * <p><b>Die ganze Kopfzeile schaltet, der Pfeil bleibt der eine Knopf</b> (#1041). Ein 22-px-Ziel
 * trifft man schlecht; ein `button` mit Titel, Metazeile und Marken als Inhalt ergäbe dagegen einen
 * überlangen zugänglichen Namen. Tastatur und Screenreader bedienen deshalb weiter den Pfeil mit
 * `aria-expanded`/`aria-controls`, die Maus zusätzlich die Fläche daneben.
 */
export function NachtlaufLaufPlatte({
  /**
   * Der Titel des Laufs, etwa „Lauf #412 · 14. September, 22:05" — auch der Name der Platte. Die
   * Nummer steht seit Issue #1127 hier und nicht mehr in der Vorzeile.
   */
  titel,
  /** Die Lauf-Art im Etikett, etwa „Kette" — davor steht der Zyklus. */
  art,
  /** Der Zyklus, dem der Lauf angehört, etwa „Zyklus vom 14.09.2026 auf den 15.09.2026" (#1127). */
  zyklus,
  /** Beginn, Dauer, bearbeitete und übergangene Vorgänge und was der Lauf sonst zu sagen hat. */
  meta,
  melder,
  /** `true` an einem Lauf, den die Kette noch nicht abgeschlossen gemeldet hat. */
  pulsiert = false,
  /**
   * Die Marken rechts im Kopf — Zustand, Kosten (zugeklappt) und Herkunft. Welche es sind,
   * entscheidet die Seite; die Platte reiht sie nur auf.
   */
  marken,
  offen,
  onUmschalten,
  testId,
  children,
}: Readonly<{
  titel: string
  art: string
  zyklus: string
  meta: string
  melder: Melder
  pulsiert?: boolean
  marken?: ReactNode
  offen: boolean
  onUmschalten: () => void
  testId: string
  children: ReactNode
}>) {
  const inhaltId = useId()

  /**
   * Ein Klick in die Kopfzeile schaltet um — außer er beendet gerade eine Textauswahl. Herkunft
   * und Token-Namen in den Marken werden kopiert; wer sie markiert, will den Lauf nicht zuklappen.
   */
  const kopfKlick = () => {
    if ((window.getSelection()?.toString() ?? '') !== '') {
      return
    }
    onUmschalten()
  }

  return (
    <Box
      component="section"
      aria-label={titel}
      data-testid={testId}
      sx={{
        borderRadius: `${PANEL_RADIUS}px`,
        border: `1px solid ${RAND}`,
        bgcolor: PLATTE,
        boxShadow: SCHATTEN_PLATTE,
        overflow: 'hidden',
      }}
    >
      {/* `role="presentation"` sagt, was der Fall ist: Die Zeile trägt keine eigene Semantik, ihr
          Klick ist die bequemere Fläche für den Pfeil darin. Ohne die Angabe verlangte jsx-a11y
          eine Tastaturbedienung an der Zeile — die gibt es, am Pfeil, und ein zweites Mal wäre sie
          ein zweiter Halt in der Tabulator-Reihenfolge. */}
      <Box
        role="presentation"
        data-testid="lauf-kopf"
        onClick={kopfKlick}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          flexWrap: 'wrap',
          px: '16px',
          py: '13px',
          cursor: 'pointer',
          background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`,
          '&:hover': { background: `linear-gradient(180deg, ${PLATTE}, ${PLATTE_FUSS})` },
          ...(offen && { borderBottom: `1px solid ${RAND}` }),
        }}
      >
        <ButtonBase
          aria-expanded={offen}
          aria-controls={offen ? inhaltId : undefined}
          aria-label={`${titel} ${offen ? 'zuklappen' : 'aufklappen'}`}
          // Die Kopfzeile schaltet ebenfalls um — ohne gestoppte Weitergabe schaltete der Pfeil
          // zweimal und damit gar nicht.
          onClick={(ereignis) => {
            ereignis.stopPropagation()
            onUmschalten()
          }}
          sx={{
            width: 22,
            height: 22,
            flex: 'none',
            display: 'grid',
            placeItems: 'center',
            borderRadius: `${KLEIN_RADIUS}px`,
            color: TEXT_MATT,
            '&:hover': { bgcolor: NUT },
          }}
        >
          {/* Größe und Ausschnitt stehen im `sx` und nicht als `width`/`height` am Element: `Box`
              verbraucht beide als Systemeigenschaften, und die Zeichenkette `"12"` wurde dabei zu
              `width:12` ohne Einheit — eine Angabe, die jeder Browser verwirft. Genau daran war
              der Pfeil nicht zu sehen (#1041). Als Zahl im `sx` hängt Emotion die Einheit an. */}
          <Box
            component="svg"
            data-testid="nachtlauf-pfeil"
            viewBox="0 0 16 16"
            fill="none"
            aria-hidden
            sx={{
              width: 12,
              height: 12,
              transition: 'transform .15s ease',
              transform: offen ? 'none' : 'rotate(-90deg)',
              '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
            }}
          >
            <path
              d="m4 6 4 4 4-4"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </Box>
        </ButtonBase>

        <Led melder={melder} pulsiert={pulsiert} />

        <Box sx={{ minWidth: 0 }}>
          <Box data-testid="nachtlauf-vorzeile" sx={{ ...ETIKETT, mb: '1px' }}>
            {`${zyklus} · ${art}`}
          </Box>
          <Box
            component="h3"
            data-testid="nachtlauf-ueberschrift"
            sx={{ ...ANZEIGE, m: 0, fontSize: 15, fontWeight: 600 }}
          >
            {titel}
          </Box>
        </Box>

        <Box
          component="span"
          data-testid="nachtlauf-meta"
          sx={{ ...ZAHL, fontSize: 11, color: TEXT_SCHWACH, minWidth: 0 }}
        >
          {meta}
        </Box>

        {marken !== undefined && (
          <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            {marken}
          </Box>
        )}
      </Box>

      {offen && <Box id={inhaltId}>{children}</Box>}
    </Box>
  )
}

/**
 * Eine Marke im Kopf der Laufplatte (Vorlage `.zustand`, Z. 345–354): eingelassen, einzeilig, mit
 * einer LED davor, wo sie einen Zustand meldet.
 *
 * <p>Sie ist die Stelle, an der die Angaben stehen, für die die Vorlage im Kopf kein eigenes
 * Element vorsieht — Herkunft, Einlieferung, Kosten des zugeklappten Laufs. Weggelassen wären sie
 * verloren; als Marke stehen sie in der Gestalt, die die Vorlage dafür kennt.
 */
export function LaufMarke({
  led,
  testId,
  children,
}: Readonly<{ led?: ReactNode; testId?: string; children: ReactNode }>) {
  return (
    <Box
      component="span"
      data-testid={testId}
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        fontSize: 11.5,
        color: TEXT_MATT,
        px: '8px',
        py: '2px',
        pl: led === undefined ? '8px' : '6px',
        borderRadius: `${KLEIN_RADIUS}px`,
        border: `1px solid ${RAND}`,
        bgcolor: NUT,
        boxShadow: SCHATTEN_NUTE,
        whiteSpace: 'nowrap',
      }}
    >
      {led}
      {children}
    </Box>
  )
}
