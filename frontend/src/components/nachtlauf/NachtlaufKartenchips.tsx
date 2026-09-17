import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import type { CardByNumber } from '../../api/cards'
import { KUPFER, MELDER, NUT, RAND, SCHATTEN_NUTE, TEXT_SCHWACH, ZAHL } from '../../theme'

/**
 * Der Zustand eines Verweises auf eine entstandene Karte (Issue #868) — die drei Fälle, die der
 * Kartenkatalog unterscheidet (E7 aus Plan #863):
 *
 * - **geladen** — die Karte liegt vor; der Verweis öffnet den Kartendialog.
 * - **fort** — der Katalog hat sie ausdrücklich als nicht auflösbar vermerkt (404).
 * - **laedt** — zu dieser Nummer steht noch gar nichts im Katalog.
 *
 * „laedt" und „fort" auseinanderzuhalten ist der Kern: Eine Karte, die gerade geladen wird, als
 * „nicht mehr vorhanden" zu zeigen, wäre eine Falschaussage, die sich Sekunden später selbst
 * widerlegt.
 */
export type VerweisZustand =
  | { art: 'geladen'; karte: CardByNumber }
  | { art: 'fort' }
  | { art: 'laedt' }

/** Eine entstandene Karte: ihre Nummer, der Arbeitsschritt, aus dem sie stammt, ihr Zustand. */
export interface Kartenchip {
  id: string
  /** Der Arbeitsschritt, etwa „Plan" oder „Pakete" — im Chip die kleine Vorzeile. */
  art: string
  zustand: VerweisZustand
}

/**
 * Die entstandenen Karten eines Vorgangs als Chips (#916, Entwurf `a.chip` mit `span.art`).
 *
 * <p><b>Sichtbar steht allein die Nummer</b>, der zugängliche Name trägt Arbeitsschritt und Titel
 * dazu — ein Vorgang bringt bis zu drei Karten mit, und volle Titel machten daraus eine Textwand.
 * Die sichtbare Nummer bleibt Teilstring des Namens (WCAG 2.5.3).
 *
 * <p><b>Ein leerer Arbeitsschritt wird benannt</b> („keine Pakete"), nicht weggelassen: Ein leerer
 * Platz ließe offen, ob nichts entstand oder nichts nachgesehen wurde.
 */
export function NachtlaufKartenchips({
  chips,
  /** Was steht, wenn kein einziger Chip entstand — etwa „keine Pakete". */
  leer,
  testId,
  onOeffnen,
}: Readonly<{
  chips: readonly Kartenchip[]
  leer: string
  testId: string
  onOeffnen: (karte: CardByNumber) => void
}>) {
  if (chips.length === 0) {
    return (
      <Typography component="span" data-testid={testId} sx={LEER_STIL}>
        {leer}
      </Typography>
    )
  }
  return (
    <Box
      component="span"
      data-testid={testId}
      sx={{ display: 'inline-flex', flexWrap: 'wrap', alignItems: 'baseline', gap: '8px' }}
    >
      {chips.map((chip) => (
        <ChipVerweis key={`${chip.art}-${chip.id}`} chip={chip} onOeffnen={onOeffnen} />
      ))}
    </Box>
  )
}

function ChipVerweis({
  chip,
  onOeffnen,
}: Readonly<{ chip: Kartenchip; onOeffnen: (karte: CardByNumber) => void }>) {
  if (chip.zustand.art === 'laedt') {
    return (
      <Box component="span" sx={CHIP_STIL}>
        <Box component="span" sx={ART_STIL}>
          {chip.art}
        </Box>
        {`#${chip.id}`}
      </Box>
    )
  }
  if (chip.zustand.art === 'fort') {
    return (
      <Box component="span" sx={CHIP_STIL}>
        <Box component="span" sx={ART_STIL}>
          {chip.art}
        </Box>
        {`#${chip.id} nicht mehr vorhanden`}
      </Box>
    )
  }
  const karte = chip.zustand.karte
  return (
    <Link
      component="button"
      type="button"
      underline="none"
      aria-label={`${chip.art} #${chip.id} ${karte.title}`}
      onClick={() => onOeffnen(karte)}
      sx={{
        ...CHIP_STIL,
        cursor: 'pointer',
        '&:hover': { borderColor: KUPFER, color: KUPFER },
        '&:focus-visible': { outline: `2px solid ${KUPFER}`, outlineOffset: 2 },
      }}
    >
      <Box component="span" sx={ART_STIL}>
        {chip.art}
      </Box>
      {`#${chip.id}`}
    </Link>
  )
}

const CHIP_STIL = {
  ...ZAHL,
  display: 'inline-flex',
  alignItems: 'baseline',
  gap: '7px',
  fontSize: 13,
  fontWeight: 600,
  color: 'text.primary',
  backgroundColor: NUT,
  border: `1px solid ${RAND}`,
  boxShadow: SCHATTEN_NUTE,
  borderRadius: '6px',
  padding: '4px 10px',
} as const

const ART_STIL = {
  fontSize: 11,
  fontWeight: 500,
  letterSpacing: '0.04em',
  color: TEXT_SCHWACH,
} as const

const LEER_STIL = {
  fontSize: 13,
  fontStyle: 'italic',
  color: MELDER.bernst,
} as const
