import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import type { CardByNumber } from '../../api/cards'
import type { NightRunState } from '../../lib/nightRunLog'
import { NACHTLAUF_FARBEN, NACHTLAUF_MASSE, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'
import { NachtlaufKartenchips, type Kartenchip } from './NachtlaufKartenchips'
import { NachtlaufStufenband, type Bandabschnitt } from './NachtlaufStufenband'

/** Eine Gruppe entstandener Karten — je Arbeitsschritt eine. */
export interface Chipgruppe {
  label: string
  /** Was steht, wenn in diesem Schritt keine Karte entstand. */
  leer: string
  testId: string
  chips: readonly Kartenchip[]
}

/**
 * Ein Vorgang eines Ketten-Laufs als abgegrenzter Block (#916, AK 4): Kopfzeile aus Nummer, Titel
 * und Ausgang, darunter der Grund in Worten, das Stufenband und die Ergebniszeile.
 *
 * <p><b>Der Ausgang hängt nie allein an der Farbe</b> (AK 7, `CLAUDE-react.md`): Die Pille trägt
 * den farbigen Punkt **und** das Wort, und wo ein Vorgang an einer Grenze endete, steht der Grund
 * als eigener Satz darunter.
 *
 * <p><b>Die Testkennungen sind die des Bestands</b> (E14): `paket-N` am Block, `zustand-N` an der
 * Pille, `abbruch-N` am Grund, `stufenband-N` und `stufe-N-<schritt>` am Band. Der Block vereint,
 * was bis #916 auf die Zeile des Arbeitspakets und die Vorgangsliste der Übersicht verteilt war;
 * die Aussagen sind dieselben geblieben, also bleiben es ihre Kennungen.
 *
 * <p><b>Was der Entwurf nicht vorsieht, steht als `children` unter dem Block</b> — Häufigkeit eines
 * Befunds, Herkunftskette, Vorhaben-Zeile, Übernahmetext. AK 10 verlangt, diese Fähigkeiten zu
 * erhalten und einzupassen; sie gehören zum Vorgang, aber nicht zu seiner Gestalt im Entwurf.
 */
export function NachtlaufVorgang({
  nummer,
  titel,
  zustand,
  /** Der Ausgang in Worten, etwa „fertig" oder „Zeitbudget". */
  ausgangswort,
  /** Warum der Vorgang endete, wo er endete (AK 7); `null` an einem regulär beendeten. */
  grund,
  /** Das Stufenband; `null` an einem Lauf ohne Ergebnisstand, der keine Arbeitsschritte kennt. */
  band,
  chipgruppen,
  /** Kosten, Züge und der Anteil der Modellarbeit als fertige Zeile (AK 6). */
  kennzahlen,
  onOeffnen,
  children,
}: Readonly<{
  nummer: number
  titel: string
  zustand: NightRunState
  ausgangswort: string
  grund: string | null
  band: { abschnitte: readonly Bandabschnitt[]; ansage: string } | null
  chipgruppen: readonly Chipgruppe[]
  kennzahlen: string
  onOeffnen: (karte: CardByNumber) => void
  children?: ReactNode
}>) {
  const pille = PILLE[zustand]

  return (
    <Box
      component="article"
      data-testid={`paket-${nummer}`}
      sx={{
        backgroundColor: NACHTLAUF_FARBEN.surface,
        border: `1px solid ${NACHTLAUF_FARBEN.line}`,
        borderRadius: `${NACHTLAUF_MASSE.vorgangRadius}px`,
        padding: NACHTLAUF_MASSE.vorgangPolsterung,
        display: 'flex',
        flexDirection: 'column',
        gap: `${NACHTLAUF_MASSE.vorgangInnenabstand}px`,
      }}
    >
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: '10px 14px', alignItems: 'baseline' }}>
        <Typography
          component="span"
          sx={{
            fontFamily: NACHTLAUF_SCHRIFTEN.mono,
            fontWeight: 600,
            fontSize: 14,
            color: NACHTLAUF_FARBEN.ink3,
          }}
        >
          {`#${nummer}`}
        </Typography>
        <Typography
          component="span"
          sx={{
            fontFamily: NACHTLAUF_SCHRIFTEN.body,
            fontWeight: 500,
            color: NACHTLAUF_FARBEN.ink,
            flex: '1 1 260px',
            minWidth: 0,
            textWrap: 'balance',
          }}
        >
          {titel}
        </Typography>
        <Typography
          component="span"
          data-testid={`zustand-${nummer}`}
          sx={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '7px',
            fontFamily: NACHTLAUF_SCHRIFTEN.body,
            fontSize: 12,
            fontWeight: 600,
            letterSpacing: '0.02em',
            padding: '3px 10px 3px 8px',
            borderRadius: '999px',
            whiteSpace: 'nowrap',
            color: pille.ton,
            backgroundColor: pille.flaeche,
          }}
        >
          {/* Dekorativ und redundant zum Wort daneben: Die Aussage trägt der Text. */}
          <Box
            aria-hidden="true"
            component="span"
            data-testid={`ampel-${nummer}`}
            sx={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: 'currentColor', flexShrink: 0 }}
          />
          {ausgangswort}
        </Typography>
      </Box>

      {grund !== null && (
        <Typography
          component="p"
          data-testid={`abbruch-${nummer}`}
          sx={{
            fontFamily: NACHTLAUF_SCHRIFTEN.body,
            fontSize: 13,
            color: NACHTLAUF_FARBEN.budget,
            margin: '-4px 0 0',
            whiteSpace: 'pre-wrap',
          }}
        >
          {grund}
        </Typography>
      )}

      {band !== null && (
        <NachtlaufStufenband
          abschnitte={band.abschnitte}
          ansage={band.ansage}
          testId={`stufenband-${nummer}`}
          abschnittTestId={`stufe-${nummer}`}
        />
      )}

      <Box
        data-testid={`ergebnis-${nummer}`}
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          gap: '8px',
          paddingTop: '14px',
          borderTop: `1px solid ${NACHTLAUF_FARBEN.line2}`,
        }}
      >
        {chipgruppen.length > 0 && (
          <Typography
            component="span"
            sx={{ fontSize: 12, color: NACHTLAUF_FARBEN.ink3, mr: '2px' }}
          >
            Entstanden
          </Typography>
        )}
        {chipgruppen.map((gruppe) => (
          <NachtlaufKartenchips
            key={gruppe.testId}
            chips={gruppe.chips}
            leer={gruppe.leer}
            testId={gruppe.testId}
            onOeffnen={onOeffnen}
          />
        ))}
        <Typography
          component="span"
          data-testid={`kennzahlen-${nummer}`}
          sx={{
            ml: 'auto',
            fontFamily: NACHTLAUF_SCHRIFTEN.mono,
            fontSize: 13,
            fontVariantNumeric: 'tabular-nums',
            color: NACHTLAUF_FARBEN.ink3,
          }}
        >
          {kennzahlen}
        </Typography>
      </Box>

      {children}
    </Box>
  )
}

/**
 * Ausgang eines Vorgangs auf Ton und Füllfläche seiner Pille. Der Entwurf führt nur die Pillen
 * „fertig" und „Budget" (`a-fertig`, `a-budget`); Rot und Grau sind nach demselben Muster gebildet,
 * ihre Töne stehen als abgeleitete Werte in `nachtlaufDesign.ts` (E10).
 */
const PILLE: Record<NightRunState, { ton: string; flaeche: string }> = {
  GREEN: { ton: NACHTLAUF_FARBEN.gut, flaeche: NACHTLAUF_FARBEN.gutFill },
  YELLOW: { ton: NACHTLAUF_FARBEN.budget, flaeche: NACHTLAUF_FARBEN.budgetFill },
  RED: { ton: NACHTLAUF_FARBEN.leer, flaeche: NACHTLAUF_FARBEN.leerFill },
  GREY: { ton: NACHTLAUF_FARBEN.nie, flaeche: NACHTLAUF_FARBEN.nieFill },
}
