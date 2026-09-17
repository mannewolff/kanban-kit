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
  PLATTE_HOCH,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_PLATTE,
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
 */
export function NachtlaufLaufPlatte({
  /** Der Titel des Laufs, etwa „Nacht vom 14. September" — auch der Name der Platte. */
  titel,
  /** Die Lauf-Art im Etikett, etwa „Kette" — davor steht „Nachtlauf · ". */
  art,
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
      <Box
        data-testid="lauf-kopf"
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          flexWrap: 'wrap',
          px: '16px',
          py: '13px',
          background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`,
          ...(offen && { borderBottom: `1px solid ${RAND}` }),
        }}
      >
        <ButtonBase
          aria-expanded={offen}
          aria-controls={offen ? inhaltId : undefined}
          aria-label={`${titel} ${offen ? 'zuklappen' : 'aufklappen'}`}
          onClick={onUmschalten}
          sx={{
            width: 22,
            height: 22,
            flex: 'none',
            display: 'grid',
            placeItems: 'center',
            borderRadius: `${KLEIN_RADIUS}px`,
            color: 'text.secondary',
            '&:hover': { bgcolor: NUT },
          }}
        >
          <Box
            component="svg"
            width="12"
            height="12"
            viewBox="0 0 16 16"
            fill="none"
            aria-hidden
            sx={{
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
            {`Nachtlauf · ${art}`}
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
        color: 'text.secondary',
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
