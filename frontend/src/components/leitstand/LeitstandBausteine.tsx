import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import { keyframes } from '@mui/material/styles'
import { useId, type ReactNode } from 'react'
import type { DeltaArt, Kachel as KachelDaten, Melder } from '../../lib/leitstand'
import { funkenPunkte } from '../../lib/leitstand'
import {
  ANZEIGE,
  BLINKER_HELL,
  CARD_RADIUS,
  ETIKETT,
  GRUND,
  KUPFER,
  KUPFER_SCHIMMER,
  LED_RING,
  MELDER,
  NUR_LESER_SX,
  NUT,
  PANEL_RADIUS,
  PLATTE,
  PLATTE_FUSS,
  PLATTE_HOCH,
  RAND,
  SCHATTEN_HOCH,
  SCHATTEN_NUTE,
  SCHATTEN_PLATTE,
  SCHATTEN_TASTE,
  TEXT_SCHWACH,
  ZAHL,
} from '../../theme'

/**
 * Bausteine des Leitstands (#979) nach dem Entwurf `docs/entwurf-leitstand.html`: LED, Platte,
 * Kachel, Sparkline, Delta, Filtertaste, Schiene eines Balkens. Farben ausschließlich aus den
 * Tokens des Themes; die Zeilenangaben verweisen auf die Vorlage.
 */

/** Der Farbverweis eines Melders. */
export const melderFarbe = (melder: Melder): string => MELDER[melder]

/**
 * Der Takt des Wechselblinkers (Issue #1136): ein Wechsel je Sekunde, jede Lampe eine halbe Sekunde
 * hell. Weit unter der Blitzgrenze aus WCAG 2.3.1 (höchstens drei Blitze je Sekunde).
 */
const BLINKER_TAKT_S = 1

/**
 * Hart umschlagend, kein Überblenden: die erste Hälfte hell, die zweite in der Melderfarbe. Die
 * Farben kommen als Variablen der Lampe herein, damit die Keyframes keine Farbe festschreiben.
 */
const wechsel = keyframes`
  0% { background-color: var(--blinker-hell); }
  50% { background-color: var(--blinker-an); }
  100% { background-color: var(--blinker-an); }
`

/**
 * Eine Lampe des Blinkers. Ihr Grundton ist die Farbe, in der sie steht, wenn die Bewegung ruht
 * (`prefers-reduced-motion`, zentrale Regel in `theme.ts`): die linke hell, die rechte im Melder.
 */
const blinkerLampe = (melder: Melder, versetzt: boolean) => ({
  '--blinker-hell': BLINKER_HELL,
  '--blinker-an': melderFarbe(melder),
  width: 9,
  height: 9,
  borderRadius: '50%',
  flex: 'none',
  bgcolor: versetzt ? melderFarbe(melder) : BLINKER_HELL,
  color: melderFarbe(melder),
  boxShadow: `${LED_RING}, 0 0 8px -1px currentColor`,
  animation: `${wechsel} ${BLINKER_TAKT_S}s step-end infinite`,
  // Die zweite Lampe um eine halbe Periode versetzt: Die beiden blinken gegeneinander.
  animationDelay: versetzt ? `-${BLINKER_TAKT_S / 2}s` : '0s',
})

/**
 * Melder-LED (Entwurf Z. 417–433). Rein schmückend.
 *
 * <p><b>Ein laufender Vorgang zeigt einen Wechselblinker</b> (Issue #1136, Entscheidung Manne,
 * abweichend vom Entwurf): zwei Lampen, die abwechselnd zwischen Melderfarbe und heller Variante
 * umschlagen. Der frühere Puls des Leuchtschleiers war an einer 9-px-Lampe kaum zu sehen. Die
 * Zweizahl bleibt auch ohne Bewegung: Der Zustand hängt so an der Form, nicht allein an Farbe oder
 * Bewegung.
 *
 * <p>`data-puls` steht neben der Animation, weil sie sonst nicht prüfbar wäre: Sie lebt in einer
 * Emotion-Klasse. Seit #1092 ist das eine Aussage über den Ausgang eines Laufs — ein verstummter
 * Lauf blinkt nicht —, und die gehört geprüft.
 */
export function Led({ melder, pulsiert = false }: Readonly<{ melder: Melder; pulsiert?: boolean }>) {
  if (pulsiert) {
    return (
      <Box
        component="span"
        aria-hidden
        data-testid={`led-${melder}`}
        data-puls="an"
        sx={{ display: 'inline-flex', gap: '3px', flex: 'none' }}
      >
        <Box component="span" data-testid="blinker-lampe" sx={blinkerLampe(melder, false)} />
        <Box component="span" data-testid="blinker-lampe" sx={blinkerLampe(melder, true)} />
      </Box>
    )
  }
  return (
    <Box
      component="span"
      aria-hidden
      data-testid={`led-${melder}`}
      data-puls="aus"
      sx={{
        width: 9,
        height: 9,
        borderRadius: '50%',
        flex: 'none',
        bgcolor: melderFarbe(melder),
        color: melderFarbe(melder),
        boxShadow: `${LED_RING}, 0 0 8px -1px currentColor`,
      }}
    />
  )
}

/** Platte mit Kopf (Entwurf `.platte`, `.platte-kopf`, Z. 543–559). */
export function Platte({
  titel,
  notiz,
  led,
  werkzeug,
  children,
}: Readonly<{ titel: string; notiz?: ReactNode; led?: ReactNode; werkzeug?: ReactNode; children: ReactNode }>) {
  const titelId = useId()
  return (
    <Box
      component="section"
      aria-labelledby={titelId}
      sx={{
        borderRadius: `${PANEL_RADIUS}px`,
        border: `1px solid ${RAND}`,
        bgcolor: PLATTE,
        boxShadow: SCHATTEN_PLATTE,
        overflow: 'hidden',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          px: '16px',
          py: '13px',
          borderBottom: `1px solid ${RAND}`,
          background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`,
          flexWrap: 'wrap',
        }}
      >
        {led}
        <Box component="h2" id={titelId} sx={{ ...ANZEIGE, m: 0, fontSize: 13.5, fontWeight: 600 }}>
          {titel}
        </Box>
        {notiz && (
          <Box component="span" sx={{ fontSize: 11.5, color: TEXT_SCHWACH }}>
            {notiz}
          </Box>
        )}
        {werkzeug && <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: '8px' }}>{werkzeug}</Box>}
      </Box>
      {children}
    </Box>
  )
}

/** Filtertaste (Entwurf `.filter`, Z. 543–559): eingelassen, gewählt als erhabene Taste. */
export function FilterTaste({
  gewaehlt,
  onClick,
  ariaLabel,
  children,
}: Readonly<{
  gewaehlt: boolean
  onClick: () => void
  /**
   * Der vorgelesene Name, wenn die Aufschrift allein ihn nicht trägt (Issue #1140).
   *
   * Eine Taste mit der Aufschrift „10" sagt vorgelesen nicht, wovon zehn — der Bezug steht auf dem
   * Bildschirm daneben und geht für ein Vorlesewerkzeug verloren.
   */
  ariaLabel?: string
  children: ReactNode
}>) {
  return (
    <ButtonBase
      aria-pressed={gewaehlt}
      aria-label={ariaLabel}
      onClick={onClick}
      sx={{
        fontSize: 11.5,
        fontWeight: 500,
        color: gewaehlt ? 'text.primary' : 'text.secondary',
        bgcolor: NUT,
        border: `1px solid ${RAND}`,
        boxShadow: gewaehlt ? SCHATTEN_TASTE : SCHATTEN_NUTE,
        borderRadius: '7px',
        px: '9px',
        py: '4px',
        ...(gewaehlt && { background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})` }),
      }}
    >
      {children}
    </ButtonBase>
  )
}

const DELTA_FARBE: Record<DeltaArt, string | undefined> = {
  gut: MELDER.gruen,
  schlecht: MELDER.zinnob,
  neutral: undefined,
}

/** Delta-Marke (Entwurf `.delta`, Z. 509–522): eingelassen, grün für gut, zinnober für schlecht. */
export function DeltaMarke({ art, children }: Readonly<{ art: DeltaArt; children: ReactNode }>) {
  const farbe = DELTA_FARBE[art]
  const randFarbe = farbe ? `color-mix(in srgb, ${farbe} 32%, ${RAND})` : RAND
  return (
    <Box
      component="span"
      data-testid={`delta-${art}`}
      sx={{
        ...ZAHL,
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        fontSize: 11,
        fontWeight: 500,
        px: '6px',
        py: '2px',
        borderRadius: '5px',
        border: `1px solid ${randFarbe}`,
        bgcolor: NUT,
        boxShadow: SCHATTEN_NUTE,
        color: farbe ?? 'text.secondary',
      }}
    >
      {children}
    </Box>
  )
}

/** Sparkline (Entwurf `.funke`, Z. 524–527): Linie mit verlaufender Fläche und Endpunkt. */
export function Funke({ werte, melder }: Readonly<{ werte: readonly number[]; melder: Melder | 'kupfer' }>) {
  const verlaufId = useId()
  const punkte = funkenPunkte(werte)
  const linie = punkte.map((p) => `${p.x},${p.y}`).join(' ')
  const linienzug = punkte.map((p) => `${p.x} ${p.y}`).join(' L')
  const letzter = punkte.at(-1)!
  return (
    <Box
      component="svg"
      data-testid="funke"
      viewBox="0 0 124 36"
      preserveAspectRatio="none"
      aria-hidden
      sx={{ display: 'block', width: '100%', height: 36, overflow: 'visible', color: melder === 'kupfer' ? KUPFER : melderFarbe(melder) }}
    >
      <defs>
        <linearGradient id={verlaufId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="currentColor" stopOpacity=".32" />
          <stop offset="1" stopColor="currentColor" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={`M${linienzug} L${letzter.x} 34 L${punkte[0].x} 34 Z`} fill={`url(#${verlaufId})`} />
      <polyline points={linie} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={letzter.x} cy={letzter.y} r="2.6" fill="currentColor" />
    </Box>
  )
}

/** Die Fläche einer Kachel (Entwurf `.kachel`, Z. 479–494), mit leichtem Kippen unter dem Zeiger. */
export const KACHEL_SX = {
  display: 'flex',
  flexDirection: 'column',
  gap: '10px',
  pt: '15px',
  px: '16px',
  pb: '13px',
  minWidth: 0,
  borderRadius: `${PANEL_RADIUS}px`,
  border: `1px solid ${RAND}`,
  background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE_FUSS})`,
  boxShadow: SCHATTEN_PLATTE,
  transition: 'transform .22s cubic-bezier(.22,.7,.3,1), box-shadow .22s ease',
  '&:hover': { transform: 'rotateX(3.2deg) translateY(-3px) scale(1.008)', boxShadow: SCHATTEN_HOCH },
  '@media (prefers-reduced-motion: reduce)': { '&:hover': { transform: 'none' } },
} as const

/**
 * Der große Wert einer Kachel (Entwurf `.kachel-wert`, Z. 496–504); ohne Datenbasis ein Leerwert.
 *
 * <p>`leerText` benennt, **warum** der Wert fehlt: Der Leitstand kennt Kennzahlen ohne Datenbasis,
 * die Verbrauchs-Auswertung Angaben, die nicht gemessen wurden (#987). Beides ist nicht 0, aber es
 * ist auch nicht dasselbe. `groesse` deckt die kleine Kachel des Mockups ab (Z. 370–371).
 */
export function KachelWert({
  wert,
  einheit,
  leerText = 'keine Datenbasis',
  groesse = 34,
}: Readonly<{ wert: string | null; einheit: string; leerText?: string; groesse?: number }>) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
      <Box
        component="span"
        data-testid="kachel-wert"
        sx={{
          ...ANZEIGE,
          fontStretch: '116%',
          fontWeight: 700,
          fontSize: groesse,
          lineHeight: 1,
          letterSpacing: '-.02em',
          fontVariantNumeric: 'tabular-nums',
          color: wert === null ? TEXT_SCHWACH : 'text.primary',
        }}
      >
        {wert ?? '—'}
      </Box>
      <Box component="span" sx={{ fontSize: 12, color: 'text.secondary', fontWeight: 500 }}>
        {wert === null ? leerText : einheit}
      </Box>
    </Box>
  )
}

/** Fuß einer Kachel (Entwurf `.kachel-fuss`, Z. 506–507): Delta links, Basis rechts. */
export function KachelFuss({ delta, basis }: Readonly<{ delta?: ReactNode; basis: ReactNode }>) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px', mt: 'auto' }}>
      {delta ?? <span />}
      <Box component="span" sx={{ fontSize: 11, color: TEXT_SCHWACH }}>
        {basis}
      </Box>
    </Box>
  )
}

/** Eine Kennzahl-Kachel aus der Rechnung in `lib/leitstand.ts`. */
export function Kachel({ titel, daten, melder }: Readonly<{ titel: string; daten: KachelDaten; melder: Melder | 'kupfer' }>) {
  return (
    <Box component="article" aria-label={titel} sx={KACHEL_SX}>
      <Box sx={ETIKETT}>{titel}</Box>
      <KachelWert wert={daten.wert} einheit={daten.einheit} />
      {daten.verlauf && <Funke werte={daten.verlauf} melder={melder} />}
      <KachelFuss
        delta={daten.delta && <DeltaMarke art={daten.delta.art}>{daten.delta.text}</DeltaMarke>}
        basis={daten.basis}
      />
    </Box>
  )
}

/** Schiene mit Füllung (Entwurf `.klasse-schiene`, `.klasse-fuellung`, Z. 692–705). */
export function Fuellschiene({ breite, farbe }: Readonly<{ breite: number; farbe: string }>) {
  return (
    <Box sx={{ height: 7, borderRadius: '4px', bgcolor: NUT, boxShadow: SCHATTEN_NUTE, overflow: 'hidden', mt: '5px' }}>
      <Box
        data-testid={`fuellung-${breite}`}
        sx={{
          height: '100%',
          width: `${breite}%`,
          borderRadius: '4px',
          color: farbe,
          background: `linear-gradient(90deg, color-mix(in srgb, currentColor 55%, transparent), currentColor)`,
          boxShadow: '0 0 8px -2px currentColor',
        }}
      />
    </Box>
  )
}

/**
 * Ein Wert eines Instruments: die Zahl und ihre kleine Einheit dahinter. Die Einheit ist Pflicht —
 * jede Zahl der Vorlage trägt eine, und eine Zahl ohne Einheit ließe offen, was sie zählt.
 */
export interface InstrumentWert {
  wert: string
  einheit: string
}

/**
 * Eingelassenes Instrument (Vorlage `docs/mockup-nachtlauf-lauf.html` Z. 306–318): Etikett über
 * einem großen Zahlenwert mit kleiner Einheit, in einer Nut statt auf einer Platte. Die Kachel des
 * Leitstands ({@link Kachel}) ist ihr erhabenes Gegenstück — sie trägt Verlauf und Delta, das
 * Instrument nur die Zahl.
 *
 * <p><b>Nicht gemessen ist nicht 0</b> (`CLAUDE-design.md`): `teile === null` zeigt „—", und den
 * Grund trägt ein Text, der allein Vorlesewerkzeugen gilt. Eine Null behauptete eine Messung, die
 * es nicht gab; ein leeres Feld ließe offen, ob nichts gemessen oder nichts nachgesehen wurde.
 *
 * <p><b>Mehrere Teile sind ein Wert</b>, nicht mehrere Instrumente: „5 grün 1 gelb 1 rot" ist die
 * Aufteilung **einer** Zahl (Mockup Z. 398) und gehört deshalb in ein Feld.
 */
export function Instrument({
  titel,
  teile,
  /** `true` gibt dem Instrument die Kupferfassung des Mockups (`.instrument-heiss`, Z. 317–318). */
  heiss = false,
  leerText = 'nicht gemessen',
  /** Eine kleine Zeile unter dem Wert, etwa die Aufteilung der Kosten (Issue #1106). */
  zusatz,
  testId,
}: Readonly<{
  titel: string
  teile: readonly InstrumentWert[] | null
  heiss?: boolean
  leerText?: string
  zusatz?: string
  testId: string
}>) {
  const randFarbe = heiss ? `color-mix(in srgb, ${KUPFER} 40%, ${RAND})` : RAND
  let wertFarbe: string
  if (teile === null) {
    wertFarbe = TEXT_SCHWACH
  } else if (heiss) {
    wertFarbe = KUPFER
  } else {
    wertFarbe = 'text.primary'
  }
  return (
    <Box
      data-testid={testId}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: '3px',
        minWidth: 0,
        px: '11px',
        pt: '9px',
        pb: '8px',
        borderRadius: `${CARD_RADIUS}px`,
        background: `linear-gradient(180deg, ${NUT}, color-mix(in srgb, ${NUT} 85%, ${GRUND}))`,
        border: `1px solid ${randFarbe}`,
        boxShadow: SCHATTEN_NUTE,
      }}
    >
      <Box sx={ETIKETT}>{titel}</Box>
      <Box
        component="span"
        data-testid={`${testId}-wert`}
        sx={{
          ...ZAHL,
          fontSize: 16,
          fontWeight: 500,
          letterSpacing: '-.01em',
          color: wertFarbe,
        }}
      >
        {teile === null ? (
          <>
            {'—'}
            <Box component="span" sx={NUR_LESER_SX}>{leerText}</Box>
          </>
        ) : (
          teile.map((teil, position) => (
            <Box component="span" key={`${teil.wert}-${teil.einheit}`}>
              {/* Das Leerzeichen zwischen zwei Werten steht im Text und nicht als Abstand einer
                  Flex-Zeile: Sonst läse ein Vorlesewerkzeug „1 grün1 gelb". */}
              {position === 0 ? '' : ' '}
              {teil.wert}
              <Box component="span" sx={{ fontSize: 10.5, fontWeight: 400, color: TEXT_SCHWACH }}>
                {` ${teil.einheit}`}
              </Box>
            </Box>
          ))
        )}
      </Box>
      {zusatz !== undefined && (
        <Box
          component="span"
          data-testid={`${testId}-zusatz`}
          sx={{ ...ZAHL, fontSize: 11, color: 'text.secondary' }}
        >
          {zusatz}
        </Box>
      )}
    </Box>
  )
}

/** Marke einer Fehlerklasse (Entwurf `.marke-klasse`, Z. 623–634). */
export function KlassenMarke({ melder, children }: Readonly<{ melder: Melder; children: ReactNode }>) {
  return (
    <Box
      component="span"
      sx={{
        ...ZAHL,
        fontSize: 10,
        fontWeight: 500,
        letterSpacing: '.02em',
        px: '6px',
        py: '1px',
        borderRadius: '5px',
        border: '1px solid currentColor',
        color: melderFarbe(melder),
        bgcolor: 'color-mix(in srgb, currentColor 12%, transparent)',
      }}
    >
      {children}
    </Box>
  )
}

/** Hover einer Zeile in einer Platte (Entwurf `.vorgang:hover`, Z. 578). */
export const ZEILE_HOVER = `color-mix(in srgb, ${PLATTE_HOCH} 75%, ${KUPFER_SCHIMMER})`

/**
 * Eine Taste im Kupferwarte-Stil (#1083).
 *
 * Gestaltet wie {@link FilterTaste}, aber ohne Umschaltzustand: Sie löst eine Handlung aus, statt
 * einen Filter zu halten. Sie steht hier und nicht in der aufrufenden Seite, weil die Nuten,
 * Ränder und Schatten der Warte hier als Konstanten liegen — ein zweiter Satz Werte in einer Seite
 * liefe beim nächsten Feinschliff auseinander.
 *
 * Das `aria-label` ist Pflicht und kein Vorschlag: In einer Liste gleichlautender Tasten ist eine
 * Vorlesehilfe sonst ohne Anhalt, welche Zeile sie gerade nennt.
 */
export function Taste({
  ariaLabel,
  onClick,
  children,
}: Readonly<{ ariaLabel: string; onClick: () => void; children: ReactNode }>) {
  return (
    <ButtonBase
      aria-label={ariaLabel}
      onClick={onClick}
      sx={{
        fontSize: 11.5,
        fontWeight: 500,
        color: 'text.secondary',
        bgcolor: NUT,
        border: `1px solid ${RAND}`,
        boxShadow: SCHATTEN_NUTE,
        borderRadius: '7px',
        px: '9px',
        py: '4px',
        '&:hover': {
          color: 'text.primary',
          boxShadow: SCHATTEN_TASTE,
          background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`,
        },
      }}
    >
      {children}
    </ButtonBase>
  )
}
