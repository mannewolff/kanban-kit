import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type {
  CardRefView,
  ChainProgressView,
  NightRunBudgetView,
  NightRunProgressView,
} from '../../api/nightRuns'
import { useSprache, useT } from '../../i18n/SpracheContext'
import {
  kettenkosten,
  stufenzeit,
  wegleiste,
  type FortschrittArt,
  type Kostenvorgang,
  type Stufenzeit,
  type Wegleiste,
} from '../../lib/laufFortschritt'
import { dollar, laufDauerGeteilt } from '../../lib/leitstand'
import { useJetzt } from '../../lib/useJetzt'
import {
  KUPFER,
  KUPFER_HELL,
  MELDER,
  NUT,
  PLATTE,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_TASTE,
  TEXT_SCHWACH,
  ZAHL,
} from '../../theme'
import { Paketraster } from './Paketraster'

/** Der Takt des mitlaufenden Zeitbalkens (#1244, Plan #1882): wie „läuft seit" zehn Sekunden. */
const TAKT_MS = 10_000

const MINUTE_MS = 60_000

/**
 * Der Fortschritt eines Laufs in der aufgeklappten Kachel (Issue #1377, Plan #1372 E12) — seit
 * Issue #1886 je Kette eine Zeile nach Plan #1882 E6–E8 (Vorlage `docs/entwurf-lauf-ansicht.html`):
 * Nummer und Titel der Anforderung mit dem Plan als Verweis, die Stufenleiste mit Formzuständen, an
 * der laufenden Stufe die Zeit gegen ihre Vorgabe, „Ende des Wegs" als Flagge, das Paketraster und
 * rechts die Kosten der Kette. Die Umsetzungsnacht zeigt nur die Umsetzung und das Raster; darunter
 * stehen „unbekannt" und der Hinweis auf eine offene Frage.
 *
 * <p><b>Jeder Zustand steht als Text</b> (#1364) — als zugänglicher Name und Tooltip jedes Punkts
 * und jedes Quadrats. Die Form trägt den Zustand, die Farbe zeichnet ihn ein zweites Mal aus; die
 * laufende Stufe trägt für Vorlesewerkzeuge `aria-current="step"`.
 *
 * <p><b>Die Komponente bleibt zustandslos</b>: Eine Kartennummer ruft nur `onKarteOeffnen`; den
 * Kartendialog öffnet die Seite. Die Rechnung liegt in `lib/laufFortschritt.ts`; allein die Zeit
 * läuft hier im Takt von `useJetzt` mit.
 *
 * <p><b>Nach dem Lauf</b> bleibt allein der Frage-Hinweis stehen, solange eine Frage offen ist
 * (E9, Plan #1882 E11); sonst rendert die Komponente nichts.
 *
 * <p>Die Texte, auch der Zustand eines Pakets (`PackageState`), stehen im Wörterbuch
 * (`NACHTLAUF_FORTSCHRITT`, Issue #1658).
 */
export function NachtlaufFortschritt({
  fortschritt,
  art,
  laeuft,
  budget,
  vorgaenge,
  onKarteOeffnen,
}: Readonly<{
  fortschritt: NightRunProgressView
  art: FortschrittArt
  laeuft: boolean
  /** Die Vorgaben des Laufs für Zeitbalken und Kostenbalken; `null`, wo er keine trägt. */
  budget: NightRunBudgetView | null
  /** Die gemeldeten Vorgänge — der zur Anforderung trägt die Kosten der Kette (Plan #1882 E8). */
  vorgaenge: readonly Kostenvorgang[]
  onKarteOeffnen: (nummer: number) => void
}>) {
  const t = useT().NACHTLAUF_FORTSCHRITT
  // Ein beendeter Lauf zeigt keine Kettenzeilen und tickt deshalb nicht.
  const jetzt = useJetzt(laeuft ? TAKT_MS : null)
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
          {t.ZUORDNUNG_UNBEKANNT}
        </Typography>
      ) : (
        <Weg
          fortschritt={fortschritt}
          art={art}
          budget={budget}
          vorgaenge={vorgaenge}
          jetzt={jetzt}
          onKarteOeffnen={onKarteOeffnen}
        />
      )}
      {fortschritt.unbekannt.length > 0 && (
        <Box>
          <Typography component="div" sx={UEBERSCHRIFT_STIL}>
            {t.UNBEKANNT}
          </Typography>
          {fortschritt.zuordnung === 'OK' && fortschritt.unbekanntOhneAusweis && (
            <Typography data-testid="fortschritt-unbekannt-ohne-ausweis" sx={HINWEIS_STIL}>
              {t.OHNE_AUSWEIS}
            </Typography>
          )}
          <Box component="ul" aria-label={t.UNBEKANNT} sx={ZEILENLISTE_STIL}>
            {fortschritt.unbekannt.map((k) => (
              <li key={k.number}>
                <Kartenverweis art={t.UNBEKANNT} karte={k} onKarteOeffnen={onKarteOeffnen} />
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
  budget,
  vorgaenge,
  jetzt,
  onKarteOeffnen,
}: Readonly<{
  fortschritt: NightRunProgressView
  art: FortschrittArt
  budget: NightRunBudgetView | null
  vorgaenge: readonly Kostenvorgang[]
  jetzt: number
  onKarteOeffnen: (nummer: number) => void
}>) {
  const t = useT().NACHTLAUF_FORTSCHRITT
  const { sprache } = useSprache()
  if (art === 'UMSETZUNGSNACHT') {
    return (
      <Box
        data-testid="fortschritt-umsetzungsnacht"
        sx={{ ...ZEILE_STIL, gridTemplateColumns: 'minmax(0, 1fr) 230px' }}
      >
        <Stufenleiste leiste={wegleiste(null, art, sprache)} zeiten={[]} name={t.WEG_UMSETZUNGSNACHT} />
        <Paketraster pakete={fortschritt.pakete} name={t.PAKETE_UMSETZUNGSNACHT} onKarteOeffnen={onKarteOeffnen} />
      </Box>
    )
  }
  return (
    <Box sx={{ '& > * + *': { borderTop: `1px dashed ${RAND}` } }}>
      {fortschritt.ketten.map((kette) => (
        <Kettenzeile
          key={kette.anforderung.number}
          kette={kette}
          budget={budget}
          vorgaenge={vorgaenge}
          jetzt={jetzt}
          onKarteOeffnen={onKarteOeffnen}
        />
      ))}
    </Box>
  )
}

/** Eine Kette als Zeile (Plan #1882 E6–E8; Vorlage Z. 505–543, 582–640). */
function Kettenzeile({
  kette,
  budget,
  vorgaenge,
  jetzt,
  onKarteOeffnen,
}: Readonly<{
  kette: ChainProgressView
  budget: NightRunBudgetView | null
  vorgaenge: readonly Kostenvorgang[]
  jetzt: number
  onKarteOeffnen: (nummer: number) => void
}>) {
  const t = useT().NACHTLAUF_FORTSCHRITT
  const { sprache } = useSprache()
  const nummer = kette.anforderung.number
  const leiste = wegleiste(kette, 'KETTE', sprache)
  return (
    <Box
      data-testid={`fortschritt-kette-${nummer}`}
      sx={{
        ...ZEILE_STIL,
        gridTemplateColumns: '240px minmax(0, 1fr) 230px 170px',
        '@media (max-width: 1180px)': {
          gridTemplateColumns: '200px minmax(0, 1fr)',
          rowGap: '10px',
          '& > :nth-of-type(3)': { gridColumn: 2 },
          '& > :nth-of-type(4)': { gridColumn: 2, maxWidth: 240 },
        },
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Box
          sx={{
            ...ZAHL,
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: '4px 8px',
            fontSize: 12,
            color: 'text.secondary',
          }}
        >
          <Kartenverweis art={t.ART.ANFORDERUNG} karte={kette.anforderung} onKarteOeffnen={onKarteOeffnen} schlicht />
          <span aria-hidden="true">·</span>
          {kette.plan === null ? (
            <Box component="span" sx={LEER_STIL}>
              {t.KEIN_PLAN}
            </Box>
          ) : (
            <span>
              {`${t.ART.PLAN} `}
              <Kartenverweis art={t.ART.PLAN} karte={kette.plan} onKarteOeffnen={onKarteOeffnen} schlicht />
            </span>
          )}
          {leiste.ende !== null && (
            <Box
              component="span"
              role="img"
              aria-label={leiste.ende}
              sx={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: 11, fontWeight: 600, color: MELDER.gruen }}
            >
              {`⚑ ${t.ENDE_DES_WEGS}`}
            </Box>
          )}
        </Box>
        <Box sx={{ fontSize: 13.5, fontWeight: 500, lineHeight: 1.3, mt: '2px' }}>{kette.anforderung.title}</Box>
      </Box>
      <Stufenleiste
        leiste={leiste}
        zeiten={kette.stufen.map((s) => stufenzeit(s, budget, jetzt))}
        name={t.WEG_KETTE(nummer)}
      />
      <Paketraster pakete={kette.pakete} name={t.PAKETE_KETTE(nummer)} onKarteOeffnen={onKarteOeffnen} />
      <Kosten kette={kette} budget={budget} vorgaenge={vorgaenge} />
    </Box>
  )
}

/**
 * Die Stufenleiste als Punktleiste (Plan #1882 E6): erreicht ein heller Grünton mit Haken, läuft
 * ein kupferner Punkt mit Ring, offen ein Nut-Punkt mit gestricheltem Umriss. Der Zustand steht
 * als zugänglicher Name und Tooltip am Punkt; an der laufenden Stufe stehen ihr Name und die Zeit.
 */
function Stufenleiste({
  leiste,
  zeiten,
  name,
}: Readonly<{
  leiste: Wegleiste
  /** Die Zeit je Stufe in der Reihenfolge der Leiste; fehlt sie, steht keine. */
  zeiten: readonly (Stufenzeit | null)[]
  name: string
}>) {
  const t = useT().NACHTLAUF_FORTSCHRITT
  const letzte = leiste.stufen.length - 1
  return (
    <Box
      component="ol"
      aria-label={name}
      sx={{ display: 'flex', alignItems: 'center', minWidth: 0, listStyle: 'none', margin: 0, padding: 0 }}
    >
      {leiste.stufen.map((s, position) => {
        const zeit = zeiten[position] ?? null
        const zustand = s.stand
        const titel = t.STUFE_NAME(s.name, s.zustand)
        return (
          <Box
            component="li"
            key={s.stufe}
            aria-current={s.aktuell ? 'step' : undefined}
            sx={{
              display: 'flex',
              alignItems: 'center',
              minWidth: 0,
              flex: stufenbreite(position === letzte, zeit),
            }}
          >
            <Tooltip title={titel}>
              <Box component="span" role="img" aria-label={titel} data-zustand={zustand} sx={{ ...PUNKT_STIL, ...PUNKT[zustand] }}>
                {zustand === 'ERREICHT' ? '✓' : position + 1}
              </Box>
            </Tooltip>
            {s.aktuell && (
              <Box component="span" sx={{ fontSize: 11, fontWeight: 600, color: 'text.primary', ml: '6px', whiteSpace: 'nowrap' }}>
                {s.name}
              </Box>
            )}
            {zeit !== null && <Zeit zeit={zeit} />}
            {position < letzte && (
              <Box
                component="span"
                aria-hidden="true"
                sx={{
                  flex: 1,
                  height: 2,
                  borderRadius: '2px',
                  mx: '8px',
                  minWidth: 10,
                  ...(zustand === 'ERREICHT'
                    ? { background: `color-mix(in srgb, ${MELDER.gruen} 55%, ${NUT})` }
                    : { background: NUT, boxShadow: SCHATTEN_NUTE }),
                }}
              />
            )}
          </Box>
        )
      })}
    </Box>
  )
}

/** Die letzte Stufe nimmt nur ihren Platz; eine mit Zeitschiene dreimal so viel wie die übrigen. */
function stufenbreite(letzte: boolean, zeit: Stufenzeit | null): number | 'none' {
  if (letzte) {
    return 'none'
  }
  return zeit?.vorgabeMs == null ? 1 : 3
}

/** Die Zeit der laufenden Stufe (Plan #1882 E7): mit Vorgabe als Schiene, ohne als „seit <Dauer>". */
function Zeit({ zeit }: Readonly<{ zeit: Stufenzeit }>) {
  const t = useT().NACHTLAUF_FORTSCHRITT
  if (zeit.vorgabeMs === null) {
    const seit = laufDauerGeteilt(zeit.verstrichenMs)
    return (
      <Box component="span" sx={{ ...ZEIT_TEXT_STIL, ml: '8px', mt: 0, whiteSpace: 'nowrap' }}>
        {t.SEIT(seit.wert, seit.einheit)}
      </Box>
    )
  }
  const verstrichen = Math.floor(zeit.verstrichenMs / MINUTE_MS)
  const vorgabe = Math.round(zeit.vorgabeMs / MINUTE_MS)
  // Über der Vorgabe bleibt die Schiene voll und Stahl; die Zahl nennt die Überschreitung.
  const anteil = Math.min(1, zeit.verstrichenMs / zeit.vorgabeMs)
  return (
    <Box sx={{ flex: 1, ml: '12px', minWidth: 120 }}>
      <Box role="img" aria-label={t.ZEIT_SCHIENE(verstrichen, vorgabe)} sx={SCHIENE_STIL}>
        <Box
          data-testid="zeit-fuellung"
          sx={{ height: '100%', borderRadius: '3px', background: MELDER.stahl }}
          style={{ width: `${anteil * 100}%` }}
        />
      </Box>
      <Box sx={ZEIT_TEXT_STIL}>
        {t.ZEIT(verstrichen, vorgabe)}
        {verstrichen > vorgabe && t.ZEIT_UEBER(verstrichen - vorgabe)}
      </Box>
    </Box>
  )
}

/**
 * Die Kosten einer Kette (Plan #1882 E8): an einer Kette ohne Umsetzung der Betrag gegen das Budget
 * mit Kupferbalken, an einer mit Umsetzung „Planung <Betrag>" ohne Balken, ohne gemeldeten Vorgang
 * nichts — die Spalte bleibt dann leer stehen.
 */
function Kosten({
  kette,
  budget,
  vorgaenge,
}: Readonly<{ kette: ChainProgressView; budget: NightRunBudgetView | null; vorgaenge: readonly Kostenvorgang[] }>) {
  const t = useT().NACHTLAUF_FORTSCHRITT
  const { sprache } = useSprache()
  const kosten = kettenkosten(kette.anforderung, kette.stufen, vorgaenge, budget)
  if (kosten === null) {
    return <Box />
  }
  const betrag = dollar(kosten.betragUsd, sprache)
  return (
    <Box role="group" aria-label={t.KOSTEN}>
      <Box sx={{ ...ZAHL, fontSize: 13, fontWeight: 500 }}>
        {kosten.planung && (
          <>
            <Box component="small" sx={KOSTEN_KLEIN_STIL}>
              {t.KOSTEN_PLANUNG}
            </Box>{' '}
          </>
        )}
        {betrag}{' '}
        <Box component="small" sx={KOSTEN_KLEIN_STIL}>
          {kosten.anteil !== null && budget?.kostenUsd != null ? t.KOSTEN_GEGEN(dollar(budget.kostenUsd, sprache)) : t.DOLLAR}
        </Box>
      </Box>
      {kosten.anteil !== null && (
        // Der Balken bleibt Kupfer, auch über 100 % — Warnungen sind kein Ziel (#993).
        <Box aria-hidden="true" sx={{ ...SCHIENE_STIL, mt: '4px' }}>
          <Box
            data-testid="kosten-fuellung"
            sx={{ height: '100%', borderRadius: '3px', background: `linear-gradient(90deg, ${KUPFER}, ${KUPFER_HELL})` }}
            style={{ width: `${Math.min(1, kosten.anteil) * 100}%` }}
          />
        </Box>
      )}
    </Box>
  )
}

function FrageHinweis({
  fragen,
  onKarteOeffnen,
}: Readonly<{ fragen: readonly CardRefView[]; onKarteOeffnen: (nummer: number) => void }>) {
  const t = useT().NACHTLAUF_FORTSCHRITT
  return (
    <Box
      role="status"
      data-testid="fortschritt-frage"
      sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', gap: '8px' }}
    >
      <Typography component="span" sx={HINWEIS_STIL}>
        {t.FRAGE}
      </Typography>
      {fragen.map((k) => (
        <Kartenverweis key={k.number} art={t.ART.FRAGE} karte={k} onKarteOeffnen={onKarteOeffnen} />
      ))}
    </Box>
  )
}

/**
 * Eine Kartennummer als Verweis in der Form der Kartenchips (`NachtlaufKartenchips`): Sichtbar
 * steht die Nummer, der zugängliche Name trägt Art und Titel dazu (WCAG 2.5.3). `schlicht` zeigt
 * nur die Nummer ohne Chip — so steht sie im Kopf einer Kettenzeile (Vorlage `.anf-nr`).
 */
function Kartenverweis({
  art,
  karte,
  onKarteOeffnen,
  schlicht = false,
}: Readonly<{
  art: string
  karte: CardRefView
  onKarteOeffnen: (nummer: number) => void
  schlicht?: boolean
}>) {
  return (
    <Link
      component="button"
      type="button"
      underline={schlicht ? 'hover' : 'none'}
      aria-label={`${art} #${karte.number} ${karte.title}`}
      onClick={() => onKarteOeffnen(karte.number)}
      sx={{
        ...(schlicht ? { ...ZAHL, fontSize: 12, color: 'inherit', verticalAlign: 'baseline' } : CHIP_STIL),
        cursor: 'pointer',
        '&:hover': schlicht ? { color: KUPFER } : { borderColor: KUPFER, color: KUPFER },
        '&:focus-visible': { outline: `2px solid ${KUPFER}`, outlineOffset: 2 },
      }}
    >
      {!schlicht && (
        <Box component="span" sx={ART_STIL}>
          {art}
        </Box>
      )}
      {`#${karte.number}`}
    </Link>
  )
}

/** Eine Zeile des Fortschritts im Raster der Vorlage (`.kette`, Z. 316–329). */
const ZEILE_STIL = {
  display: 'grid',
  gap: '20px',
  alignItems: 'center',
  padding: '12px 0',
} as const

const ZEILENLISTE_STIL = {
  display: 'flex',
  flexWrap: 'wrap',
  alignItems: 'baseline',
  gap: '8px',
  listStyle: 'none',
  margin: 0,
  padding: 0,
} as const

const PUNKT_STIL = {
  ...ZAHL,
  width: 22,
  height: 22,
  borderRadius: '50%',
  flex: 'none',
  display: 'grid',
  placeItems: 'center',
  fontSize: 11,
  fontWeight: 600,
} as const

/** Die drei Formen eines Stufenpunkts (Plan #1882 E6; Vorlage `.punkt-*`, Z. 345–360). */
const PUNKT = {
  ERREICHT: {
    background: `linear-gradient(180deg, color-mix(in srgb, ${MELDER.gruen} 26%, ${PLATTE}), ${PLATTE})`,
    border: `1px solid color-mix(in srgb, ${MELDER.gruen} 45%, ${RAND})`,
    color: MELDER.gruen,
    boxShadow: SCHATTEN_TASTE,
  },
  LAEUFT: {
    background: `linear-gradient(180deg, ${KUPFER_HELL}, ${KUPFER})`,
    border: '1px solid',
    borderColor: 'primary.dark',
    color: 'primary.contrastText',
    boxShadow: `0 0 0 3px color-mix(in srgb, ${KUPFER} 28%, transparent), 0 0 12px -2px ${KUPFER}`,
  },
  OFFEN: {
    background: NUT,
    border: `1.5px dashed ${TEXT_SCHWACH}`,
    color: TEXT_SCHWACH,
  },
} as const

const SCHIENE_STIL = {
  height: 6,
  borderRadius: '3px',
  background: NUT,
  boxShadow: SCHATTEN_NUTE,
  overflow: 'hidden',
} as const

const ZEIT_TEXT_STIL = { ...ZAHL, fontSize: 11, color: 'text.secondary', mt: '3px' } as const

const KOSTEN_KLEIN_STIL = { fontSize: 'inherit', fontWeight: 400, color: 'text.secondary' } as const

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
