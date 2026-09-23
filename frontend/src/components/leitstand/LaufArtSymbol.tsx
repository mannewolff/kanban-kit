import Box from '@mui/material/Box'
import Tooltip from '@mui/material/Tooltip'
import type { NightRunServerMode } from '../../api/nightRuns'
import { modusName } from '../../lib/leitstand'
import type { NightRunMode } from '../../lib/nightRunLog'
import { strichSymbol, strich } from '../../layout/navIcons'
import { TEXT_MATT } from '../../theme'

/**
 * Die Arten, die in den Ansichten vorkommen: die des Browser-Parsers **und** die, die der Server
 * melden darf. `NIGHTPLAN` bleibt browser-only (Plan #803, Entscheidung 8), steht auf der
 * Läufe-Seite aber sehr wohl.
 */
export type LaufArt = NightRunMode | NightRunServerMode

/** Die feste Kantenlänge eines Symbols in Pixeln — dieselbe wie in der Navigationsschiene. */
const BREITE = 16

const KetteSymbol = strichSymbol(
  'KetteSymbol',
  <>
    <rect x="1.5" y="5" width="7.5" height="6" rx="3" {...strich} />
    <rect x="7" y="5" width="7.5" height="6" rx="3" {...strich} />
  </>,
)

const UmsetzungSymbol = strichSymbol(
  'UmsetzungSymbol',
  <path
    d="M10.4 1.9a3.6 3.6 0 0 0-4 5.4l-4.4 4.4a1.3 1.3 0 0 0 1.9 1.9l4.4-4.4a3.6 3.6 0 0 0 5.4-4l-2.2 2.2-2-2 2-2Z"
    {...strich}
    strokeLinejoin="round"
  />,
)

const PruefungSymbol = strichSymbol(
  'PruefungSymbol',
  <>
    <circle cx="7" cy="7" r="4.6" {...strich} />
    <path d="m10.4 10.4 3.1 3.1" {...strich} strokeLinecap="round" />
  </>,
)

const SitzungSymbol = strichSymbol(
  'SitzungSymbol',
  <path
    d="M13.5 9.3c0 .9-.7 1.6-1.6 1.6H6.6L3.2 13.4v-2.5h-.1c-.9 0-1.6-.7-1.6-1.6V4.2c0-.9.7-1.6 1.6-1.6h8.8c.9 0 1.6.7 1.6 1.6v5.1Z"
    {...strich}
    strokeLinejoin="round"
  />,
)

const NachtplanSymbol = strichSymbol(
  'NachtplanSymbol',
  <path d="M13 9.6A5.6 5.6 0 0 1 6.4 3 5.6 5.6 0 1 0 13 9.6Z" {...strich} strokeLinejoin="round" />,
)

/**
 * Das Symbol je Art als `Record` über {@link LaufArt}: Eine weitere Art ohne Eintrag bricht `tsc`,
 * statt still eine Lücke in die Zeile zu reißen — und genau die Lücke wäre wieder das Flattern,
 * das dieses Symbol abstellt (Issue #1141).
 */
const SYMBOL: Record<LaufArt, ReturnType<typeof strichSymbol>> = {
  CHAIN: KetteSymbol,
  IMPLEMENTATION: UmsetzungSymbol,
  REVIEW: PruefungSymbol,
  INTERACTIVE: SitzungSymbol,
  NIGHTPLAN: NachtplanSymbol,
}

/**
 * Das Wort zu einer Art — der zugängliche Name des Symbols und sein Tooltip.
 *
 * <p>Kein zweites Wörterbuch: Für die Arten des Servers antwortet `modusName` aus
 * `lib/leitstand.ts`. Allein `NIGHTPLAN` steht hier, weil `modusName` den browser-only-Modus nicht
 * kennt.
 */
export function laufArtName(art: LaufArt): string {
  return art === 'NIGHTPLAN' ? 'Nachtplan' : modusName(art)
}

/**
 * Die Art eines Laufs als Strichsymbol an fester Stelle (Issue #1141) — im Plattform-Leitstand
 * direkt nach dem Lämpchen, auf der Läufe-Seite direkt vor „Lauf #n".
 *
 * <p><b>Warum kein Wort mehr</b> (es stand seit #1128 als Marke da): Die Marken sind je nach Art
 * verschieden breit, und alles dahinter begann deshalb in jeder Zeile woanders. Das Symbol sitzt in
 * einem Feld fester Breite, bei jeder Art gleich. Das Wort bleibt als zugänglicher Name und als
 * Tooltip — es geht also niemandem verloren.
 *
 * <p><b>Ohne eigene Farbe:</b> `currentColor` im gedämpften Textton. Die Farbe trägt in beiden
 * Ansichten den Zustand (das Lämpchen), und eine zweite Farbaussage daneben führte in die Irre.
 */
export function LaufArtSymbol({ art }: Readonly<{ art: LaufArt }>) {
  const wort = laufArtName(art)
  const Symbol = SYMBOL[art]
  return (
    <Tooltip title={wort}>
      <Box
        component="span"
        role="img"
        aria-label={wort}
        data-testid={`art-${art}`}
        data-breite={String(BREITE)}
        sx={{
          flex: 'none',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: BREITE,
          height: BREITE,
          color: TEXT_MATT,
        }}
      >
        <Symbol sx={{ width: BREITE, height: BREITE, fontSize: BREITE }} />
      </Box>
    </Tooltip>
  )
}
