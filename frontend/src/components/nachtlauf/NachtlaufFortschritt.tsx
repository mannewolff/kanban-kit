import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import type {
  CardRefView,
  ChainProgressView,
  NightRunProgressView,
  PackageProgressView,
  PackageState,
} from '../../api/nightRuns'
import { wegleiste, type FortschrittArt, type Wegleiste } from '../../lib/laufFortschritt'
import { KUPFER, MELDER, NUT, RAND, SCHATTEN_NUTE, TEXT_SCHWACH, ZAHL } from '../../theme'

/** Der Zustand eines Pakets als Text — nie allein über die Farbe (Plan #1372 E12). */
const PAKETZUSTAND: Record<PackageState, string> = {
  ANGELEGT: 'angelegt',
  GEZOGEN: 'gezogen',
  IN_UMSETZUNG: 'in Umsetzung',
  FERTIG: 'fertig',
  ZURUECKGESTELLT: 'zurückgestellt',
}

/**
 * Der Fortschritt eines Laufs in der aufgeklappten Kachel (Issue #1377, Plan #1372 E12): je Kette
 * die Wegleiste mit Anforderung, Plan und Paketen, in der Umsetzungsnacht nur die Umsetzung und die
 * Paketliste, darunter „unbekannt" und der Hinweis auf eine offene Frage.
 *
 * <p><b>Jeder Zustand steht als Text</b> — „erreicht", „läuft", „offen", „Ende des Wegs erreicht"
 * und die Paketzustände; die Kupfermarke der aktuellen Stufe ist die zweite, nicht die einzige
 * Auszeichnung, und für Vorlesewerkzeuge trägt sie `aria-current="step"`.
 *
 * <p><b>Die Komponente bleibt zustandslos</b>: Eine Kartennummer ruft nur `onKarteOeffnen`; den
 * Kartendialog öffnet die Seite. Die Rechnung liegt in `lib/laufFortschritt.ts`.
 *
 * <p><b>Nach dem Lauf</b> bleibt allein der Frage-Hinweis stehen, solange eine Frage offen ist
 * (E9); sonst rendert die Komponente nichts.
 */
export function NachtlaufFortschritt({
  fortschritt,
  art,
  laeuft,
  onKarteOeffnen,
}: Readonly<{
  fortschritt: NightRunProgressView
  art: FortschrittArt
  laeuft: boolean
  onKarteOeffnen: (nummer: number) => void
}>) {
  const frage =
    fortschritt.offeneFragen.length > 0 ? (
      <FrageHinweis fragen={fortschritt.offeneFragen} onKarteOeffnen={onKarteOeffnen} />
    ) : null
  if (!laeuft) {
    return frage
  }
  return (
    <Box data-testid="fortschritt" sx={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {fortschritt.zuordnung === 'UNBEKANNT' ? (
        <Typography data-testid="fortschritt-zuordnung-unbekannt" sx={HINWEIS_STIL}>
          Welche Karten zu diesem Lauf gehören, lässt sich nicht feststellen — der Fortschritt
          bleibt unbekannt.
        </Typography>
      ) : (
        <Weg fortschritt={fortschritt} art={art} onKarteOeffnen={onKarteOeffnen} />
      )}
      {fortschritt.unbekannt.length > 0 && (
        <Box>
          <Typography component="div" sx={UEBERSCHRIFT_STIL}>
            unbekannt
          </Typography>
          <Box component="ul" aria-label="unbekannt" sx={ZEILENLISTE_STIL}>
            {fortschritt.unbekannt.map((k) => (
              <li key={k.number}>
                <Kartenverweis art="unbekannt" karte={k} onKarteOeffnen={onKarteOeffnen} />
              </li>
            ))}
          </Box>
        </Box>
      )}
      {frage}
    </Box>
  )
}

function Weg({
  fortschritt,
  art,
  onKarteOeffnen,
}: Readonly<{
  fortschritt: NightRunProgressView
  art: FortschrittArt
  onKarteOeffnen: (nummer: number) => void
}>) {
  if (art === 'UMSETZUNGSNACHT') {
    return (
      <Box data-testid="fortschritt-umsetzungsnacht" sx={BLOCK_STIL}>
        <Stufenleiste leiste={wegleiste(null, art)} name="Weg der Umsetzungsnacht" />
        <Paketliste
          pakete={fortschritt.pakete}
          name="Pakete der Umsetzungsnacht"
          onKarteOeffnen={onKarteOeffnen}
        />
      </Box>
    )
  }
  return (
    <>
      {fortschritt.ketten.map((kette) => (
        <Kette key={kette.anforderung.number} kette={kette} onKarteOeffnen={onKarteOeffnen} />
      ))}
    </>
  )
}

function Kette({
  kette,
  onKarteOeffnen,
}: Readonly<{ kette: ChainProgressView; onKarteOeffnen: (nummer: number) => void }>) {
  const nummer = kette.anforderung.number
  return (
    <Box data-testid={`fortschritt-kette-${nummer}`} sx={BLOCK_STIL}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', gap: '8px' }}>
        <Kartenverweis art="Anforderung" karte={kette.anforderung} onKarteOeffnen={onKarteOeffnen} />
        {kette.plan === null ? (
          <Typography component="span" sx={LEER_STIL}>
            noch kein Plan
          </Typography>
        ) : (
          <Kartenverweis art="Plan" karte={kette.plan} onKarteOeffnen={onKarteOeffnen} />
        )}
      </Box>
      <Stufenleiste leiste={wegleiste(kette, 'KETTE')} name={`Weg der Kette #${nummer}`} />
      <Paketliste
        pakete={kette.pakete}
        name={`Pakete der Kette #${nummer}`}
        onKarteOeffnen={onKarteOeffnen}
      />
    </Box>
  )
}

function Stufenleiste({ leiste, name }: Readonly<{ leiste: Wegleiste; name: string }>) {
  return (
    <Box component="ol" aria-label={name} sx={{ ...ZEILENLISTE_STIL, gap: '6px' }}>
      {leiste.stufen.map((s) => (
        <Box
          component="li"
          key={s.stufe}
          aria-current={s.aktuell ? 'step' : undefined}
          sx={{
            ...STUFE_STIL,
            ...(s.aktuell ? { borderColor: KUPFER, color: KUPFER, fontWeight: 600 } : {}),
          }}
        >
          {`${s.name}: ${s.zustand}`}
        </Box>
      ))}
      {leiste.ende !== null && (
        <Box component="li" sx={{ ...STUFE_STIL, borderColor: KUPFER, fontWeight: 600 }}>
          {leiste.ende}
        </Box>
      )}
    </Box>
  )
}

function Paketliste({
  pakete,
  name,
  onKarteOeffnen,
}: Readonly<{
  pakete: readonly PackageProgressView[]
  name: string
  onKarteOeffnen: (nummer: number) => void
}>) {
  if (pakete.length === 0) {
    return (
      <Typography component="div" sx={LEER_STIL}>
        noch keine Pakete
      </Typography>
    )
  }
  return (
    <Box component="ul" aria-label={name} sx={ZEILENLISTE_STIL}>
      {pakete.map((p) => (
        <Box
          component="li"
          key={p.karte.number}
          sx={{ display: 'inline-flex', alignItems: 'baseline', gap: '6px' }}
        >
          <Kartenverweis art="Paket" karte={p.karte} onKarteOeffnen={onKarteOeffnen} />
          <Typography component="span" sx={{ fontSize: 12, color: 'text.secondary' }}>
            {PAKETZUSTAND[p.zustand]}
          </Typography>
        </Box>
      ))}
    </Box>
  )
}

function FrageHinweis({
  fragen,
  onKarteOeffnen,
}: Readonly<{ fragen: readonly CardRefView[]; onKarteOeffnen: (nummer: number) => void }>) {
  return (
    <Box
      role="status"
      data-testid="fortschritt-frage"
      sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', gap: '8px' }}
    >
      <Typography component="span" sx={HINWEIS_STIL}>
        Offene Frage an den Menschen an Karte
      </Typography>
      {fragen.map((k) => (
        <Kartenverweis key={k.number} art="Frage" karte={k} onKarteOeffnen={onKarteOeffnen} />
      ))}
    </Box>
  )
}

/**
 * Eine Kartennummer als Verweis in der Form der Kartenchips (`NachtlaufKartenchips`): Sichtbar
 * steht die Nummer, der zugängliche Name trägt Art und Titel dazu (WCAG 2.5.3).
 */
function Kartenverweis({
  art,
  karte,
  onKarteOeffnen,
}: Readonly<{ art: string; karte: CardRefView; onKarteOeffnen: (nummer: number) => void }>) {
  return (
    <Link
      component="button"
      type="button"
      underline="none"
      aria-label={`${art} #${karte.number} ${karte.title}`}
      onClick={() => onKarteOeffnen(karte.number)}
      sx={{
        ...CHIP_STIL,
        cursor: 'pointer',
        '&:hover': { borderColor: KUPFER, color: KUPFER },
        '&:focus-visible': { outline: `2px solid ${KUPFER}`, outlineOffset: 2 },
      }}
    >
      <Box component="span" sx={ART_STIL}>
        {art}
      </Box>
      {`#${karte.number}`}
    </Link>
  )
}

const BLOCK_STIL = { display: 'flex', flexDirection: 'column', gap: '8px' } as const

const ZEILENLISTE_STIL = {
  display: 'flex',
  flexWrap: 'wrap',
  alignItems: 'baseline',
  gap: '8px',
  listStyle: 'none',
  margin: 0,
  padding: 0,
} as const

const STUFE_STIL = {
  fontSize: 12,
  letterSpacing: '0.04em',
  color: 'text.secondary',
  backgroundColor: NUT,
  border: `1px solid ${RAND}`,
  boxShadow: SCHATTEN_NUTE,
  borderRadius: '6px',
  padding: '3px 9px',
} as const

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

const UEBERSCHRIFT_STIL = {
  fontSize: 11,
  letterSpacing: '0.04em',
  color: TEXT_SCHWACH,
  marginBottom: '4px',
} as const

const LEER_STIL = {
  fontSize: 13,
  fontStyle: 'italic',
  color: TEXT_SCHWACH,
} as const

const HINWEIS_STIL = {
  fontSize: 13,
  fontWeight: 600,
  color: MELDER.bernst,
} as const
