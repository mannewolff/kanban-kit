import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import { useEffect, useState, type ReactNode } from 'react'
import {
  nightRunUsageApi,
  type NightRunUsageApi,
  type VerbrauchKennzahlen,
  type VerbrauchNachtKurz,
  type VerbrauchZeitraum,
  type VerbrauchZeitraumArt,
} from '../../api/nightRunUsage'
import { dollar } from '../../lib/leitstand'
import { kosten } from '../../lib/nachtlaufFormat'
import {
  kartenText,
  laeufeText,
  nachtKurz,
  vergleichMitVorzeitraum,
  vorzeitraumName,
  zeitraumBeschriftung,
  zeitraumFall,
  zeitraumHinweis,
} from '../../lib/verbrauchZeitraum'
import {
  ETIKETT,
  KUPFER,
  NUR_LESER_SX,
  NUT,
  PLATTE,
  PLATTE_FUSS,
  PLATTE_HOCH,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_TASTE,
  SCHRIFT_ANZEIGE,
  TEXT_SCHWACH,
  ZAHL,
} from '../../theme'
import {
  DeltaMarke,
  Fuellschiene,
  KACHEL_SX,
  KachelFuss,
  KachelWert,
  Led,
  Platte,
  ZEILE_HOVER,
} from '../leitstand/LeitstandBausteine'
import { NachtlaufVerbrauchVorhaben } from './NachtlaufVerbrauchVorhaben'

/** `null` aus der Antwort heißt „nicht gemessen" — die Formatierer kennen dafür `undefined`. */
const ohneNull = (wert: number | null): number | undefined => (wert === null ? undefined : wert)

const ARTEN: ReadonlyArray<{ art: VerbrauchZeitraumArt; label: string }> = [
  { art: 'DAY', label: 'Tag' },
  { art: 'WEEK', label: 'Woche' },
  { art: 'MONTH', label: 'Monat' },
]

type Zustand = { art: 'laden' } | { art: 'fehler' } | { art: 'daten'; zeitraum: VerbrauchZeitraum }

/**
 * Die Zeitraum-Sicht der Verbrauchs-Auswertung (Issue #942, #926 AK 5–9): Art und Rückschritt
 * wählen, Zeitraum und Vorzeitraum nebeneinander, die Nächte darin und die Vorhaben-Aufstellung.
 *
 * <p><b>Gestalt seit #987:</b> Kupferwarte nach `docs/mockup-nachtlauf-verbrauch.html` — Kopfzeile
 * mit Wahl, zwei Platten mit je vier Kacheln, darunter Nächte und Vorhaben. Der Bereich verlässt
 * damit die Nachtlauf-Ausnahme aus `CLAUDE-design.md`; der Rest der Seite bleibt darin.
 *
 * <p><b>Gewählt wird ohne Datumseingabe</b> (Plan #933 E14): Art plus Rückschritt, 0 ist der zuletzt
 * abgeschlossene Zeitraum. Ein Wechsel der Art beginnt wieder dort.
 *
 * <p>Die Hinweise folgen `zeitraumFall`: Ein Zeitraum ohne Lauf und einer vor der Aufbewahrung
 * stehen anstelle der Kacheln, ein angeschnittener Zeitraum und einer ohne Messung stehen im Kopf
 * ihrer Platte **neben** den Zahlen (Plan E8, E5).
 *
 * <p><b>Gezeigt wird der Nachtlauf-Anteil</b> (Issue #1016, Plan #1007): Derselbe Abruf führt seit
 * Issue #1013 auch die interaktiven Sitzungen, und `usage` ist die Summe über beide. Die
 * Nachtlauf-Seite bleibt in ihrer Aussage auf Nachtläufe beschränkt — läse sie `usage`, wüchsen
 * ihre Zahlen still um die Arbeit am Tag, und niemand sähe, woher der Zuwachs kommt.
 */
export function NachtlaufVerbrauchZeitraum({
  projectId,
  onNachtWaehlen,
  api = nightRunUsageApi,
}: Readonly<{
  projectId: number
  onNachtWaehlen: (datum: string) => void
  api?: Pick<NightRunUsageApi, 'period'>
}>) {
  const [art, setArt] = useState<VerbrauchZeitraumArt>('WEEK')
  const [rueckschritt, setRueckschritt] = useState(0)
  const [zustand, setZustand] = useState<Zustand>({ art: 'laden' })

  useEffect(() => {
    let aktiv = true
    setZustand({ art: 'laden' })
    api.period(projectId, art, rueckschritt).then(
      (zeitraum) => {
        if (aktiv) setZustand({ art: 'daten', zeitraum })
      },
      () => {
        if (aktiv) setZustand({ art: 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, projectId, art, rueckschritt])

  return (
    <Box
      data-testid="verbrauch-zeitraum"
      sx={{ display: 'flex', flexDirection: 'column', gap: '16px' }}
    >
      {/* Kopfzeile (Mockup `.abschnitt-kopf`): Titel, gewählter Zeitraum, Schritt und Wahl. */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap', mt: '4px' }}>
        <Box
          component="h2"
          id="verbrauch-ueberschrift"
          sx={{
            m: 0,
            fontFamily: SCHRIFT_ANZEIGE,
            fontStretch: '114%',
            fontSize: 13,
            fontWeight: 700,
            letterSpacing: '.05em',
            textTransform: 'uppercase',
          }}
        >
          Verbrauch
        </Box>
        {zustand.art === 'daten' && (
          <Box
            component="span"
            data-testid="verbrauch-zeitraum-beschriftung"
            sx={{ fontSize: 11.5, color: TEXT_SCHWACH }}
          >
            {zeitraumBeschriftung(zustand.zeitraum.current)}
          </Box>
        )}
        <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <Schritt aria-label="Früherer Zeitraum" onClick={() => setRueckschritt((r) => r + 1)}>
            ← früher
          </Schritt>
          <Schritt
            aria-label="Späterer Zeitraum"
            disabled={rueckschritt === 0}
            onClick={() => setRueckschritt((r) => r - 1)}
          >
            später →
          </Schritt>
          <Box
            role="group"
            aria-label="Art des Zeitraums"
            sx={{
              display: 'inline-flex',
              gap: '3px',
              p: '3px',
              bgcolor: NUT,
              border: `1px solid ${RAND}`,
              borderRadius: '9px',
              boxShadow: SCHATTEN_NUTE,
            }}
          >
            {ARTEN.map((eintrag) => {
              const gewaehlt = eintrag.art === art
              return (
                <ButtonBase
                  key={eintrag.art}
                  aria-pressed={gewaehlt}
                  onClick={() => {
                    setArt(eintrag.art)
                    setRueckschritt(0)
                  }}
                  sx={{
                    ...ZAHL,
                    fontSize: 11,
                    fontWeight: 500,
                    color: gewaehlt ? 'text.primary' : 'text.secondary',
                    border: `1px solid ${gewaehlt ? RAND : 'transparent'}`,
                    borderRadius: '6px',
                    px: '11px',
                    py: '3px',
                    ...(gewaehlt && {
                      background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`,
                      boxShadow: SCHATTEN_TASTE,
                    }),
                  }}
                >
                  {eintrag.label}
                </ButtonBase>
              )
            })}
          </Box>
        </Box>
      </Box>

      {zustand.art === 'laden' && <Typography color="text.secondary">Der Zeitraum wird geladen …</Typography>}
      {zustand.art === 'fehler' && (
        <Typography color="text.secondary">Der Zeitraum konnte nicht geladen werden.</Typography>
      )}
      {zustand.art === 'daten' && (
        <ZeitraumInhalt zeitraum={zustand.zeitraum} onNachtWaehlen={onNachtWaehlen} />
      )}
    </Box>
  )
}

/** Schritt-Taste der Kopfzeile (Mockup `.schritt`). */
function Schritt({
  'aria-label': label,
  disabled = false,
  onClick,
  children,
}: Readonly<{
  'aria-label': string
  disabled?: boolean
  onClick: () => void
  children: ReactNode
}>) {
  return (
    <ButtonBase
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      sx={{
        fontSize: 12,
        fontWeight: 500,
        color: 'text.secondary',
        background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`,
        border: `1px solid ${RAND}`,
        boxShadow: SCHATTEN_TASTE,
        borderRadius: '8px',
        px: '10px',
        py: '4px',
        '&.Mui-disabled': { opacity: 0.45 },
      }}
    >
      {children}
    </ButtonBase>
  )
}

function ZeitraumInhalt({
  zeitraum,
  onNachtWaehlen,
}: Readonly<{ zeitraum: VerbrauchZeitraum; onNachtWaehlen: (datum: string) => void }>) {
  const { current, previous } = zeitraum
  const fall = zeitraumFall(current)
  const ohneZahlen = fall === 'kein-lauf' || fall === 'vor-aufbewahrung'
  const hinweis = zeitraumHinweis(current)
  return (
    <>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'minmax(0,1fr) minmax(0,1fr)' },
          gap: '16px',
          alignItems: 'start',
        }}
      >
        <Box data-testid="verbrauch-zeitraum-aktuell" sx={{ minWidth: 0 }}>
          <Platte
            titel="Dieser Zeitraum"
            led={<Led melder="stahl" />}
            notiz={
              <>
                {laeufeText(current.runCount)}
                {/* Ein angeschnittener Zeitraum und einer ohne Messung zeigen ihre Zahlen — ihr
                    Hinweis erklärt sie und steht deshalb daneben, im Kopf (Plan E8, E5). */}
                {!ohneZahlen && hinweis !== null && (
                  <Box component="span" role="note" data-testid="verbrauch-zeitraum-hinweis" sx={{ ml: '8px' }}>
                    {hinweis}
                  </Box>
                )}
              </>
            }
            werkzeug={<Vergleich current={current} previous={previous} />}
          >
            {ohneZahlen ? (
              // Ohne Zahlen tritt der Hinweis an ihre Stelle: Leere Kacheln läsen sich als Nullen.
              <Box
                role="note"
                data-testid="verbrauch-zeitraum-hinweis"
                sx={{ px: '16px', py: '14px', fontSize: 12.5, color: 'text.secondary' }}
              >
                {hinweis}
              </Box>
            ) : (
              <Kacheln kennzahlen={current} />
            )}
          </Platte>
        </Box>

        {!ohneZahlen && (
          <Box data-testid="verbrauch-zeitraum-vorher" sx={{ minWidth: 0, ...ZURUECKGENOMMEN }}>
            <Platte
              titel={vorzeitraumName(previous)}
              led={<Led melder="grau" />}
              notiz={
                <>
                  {`${zeitraumBeschriftung(previous)} · ${laeufeText(previous.runCount)}`}
                  {/* Der Hinweis des Zeitraums steht an seiner eigenen Platte; dieser gehört zum
                      Vorzeitraum und erklärt dessen Zahlen. */}
                  {zeitraumHinweis(previous) !== null && (
                    <Box component="span" role="note" sx={{ ml: '8px' }}>
                      {zeitraumHinweis(previous)}
                    </Box>
                  )}
                </>
              }
            >
              <Kacheln kennzahlen={previous} />
            </Platte>
          </Box>
        )}
      </Box>

      {!ohneZahlen && (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'minmax(0,1.2fr) minmax(0,1fr)' },
            gap: '16px',
            alignItems: 'start',
          }}
        >
          <Naechte naechte={zeitraum.nights} onNachtWaehlen={onNachtWaehlen} />
          <NachtlaufVerbrauchVorhaben
            epics={zeitraum.epics}
            withoutEpic={zeitraum.withoutEpic}
            epicsOverlap={zeitraum.epicsOverlap}
          />
        </Box>
      )}
    </>
  )
}

/**
 * Die Vergleichsmarke im Kopf der Platte (#926 AK 6): billiger grün mit ▼, teurer zinnober mit ▲.
 * Die Marke ist knapp, damit sie in die Kopfzeile passt — der vollständige Satz bleibt daneben für
 * Vorlesewerkzeuge stehen, sonst ginge mit der Gestalt die Aussage verloren.
 */
function Vergleich({
  current,
  previous,
}: Readonly<{ current: VerbrauchKennzahlen; previous: VerbrauchKennzahlen }>) {
  // Nachtlauf-Anteil gegen Nachtlauf-Anteil (Issue #1016): Ein Vergleich über die Gesamtsumme läse
  // sich als Aussage über die Nachtläufe und wäre in Wahrheit eine über Nächte und Tage zusammen.
  const aktuell = current.usageByKind.night.total
  const vorher = previous.usageByKind.night.total
  const vergleich = vergleichMitVorzeitraum(aktuell, vorher)
  const gerichtet = vergleich.richtung === 'teurer' || vergleich.richtung === 'billiger'
  return (
    <Box component="span" data-testid="verbrauch-zeitraum-vergleich">
      {gerichtet && (
        <Box component="span" aria-hidden>
          <DeltaMarke art={vergleich.richtung === 'billiger' ? 'gut' : 'schlecht'}>
            {`${vergleich.richtung === 'billiger' ? '▼' : '▲'} ${dollar(
              Math.abs(aktuell.costUsd! - vorher.costUsd!),
            )} $ zur ${vorzeitraumName(current)}`}
          </DeltaMarke>
        </Box>
      )}
      <Box component="span" sx={NUR_LESER_SX}>
        {vergleich.text}
      </Box>
    </Box>
  )
}

/**
 * Die vier Kacheln eines Zeitraums (Mockup `.kacheln-2`). Die Einordnung unter dem Wert entsteht
 * ausschließlich aus vorhandenen Zahlen; fehlt eine, bleibt die Zeile leer — „nicht gemessen" wird
 * nie zu 0.
 */
function Kacheln({ kennzahlen }: Readonly<{ kennzahlen: VerbrauchKennzahlen }>) {
  const { total, cardShare, remainder } = kennzahlen.usageByKind.night
  const jeLauf =
    total.costUsd !== null && kennzahlen.runCount > 0
      ? `${dollar(total.costUsd / kennzahlen.runCount)} $ je Lauf`
      : ''
  const anteil =
    total.costUsd !== null && total.costUsd > 0 && cardShare.costUsd !== null
      ? `${Math.round((cardShare.costUsd / total.costUsd) * 100)} % der Summe`
      : ''
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))' },
        gap: '10px',
        p: '14px',
        perspective: '1100px',
      }}
    >
      <Kachel etikett="Gesamtsumme" wert={total.costUsd} einheit="$" basis={jeLauf} />
      <Kachel etikett="Karten zugeordnet" wert={cardShare.costUsd} einheit="$" basis={anteil} />
      <Kachel
        etikett="Rest"
        wert={remainder.costUsd}
        einheit="$"
        basis="keiner Karte zuzuordnen"
      />
      <Kachel
        etikett="Läufe"
        wert={kennzahlen.runCount}
        einheit={kennzahlen.runCount === 1 ? 'Lauf' : 'Läufe'}
        basis={kartenText(kennzahlen.cardCount)}
        alsZahl
      />
    </Box>
  )
}

/** Eine kleine Kachel (Mockup `.kachel-klein`): Etikett, Wert mit Einheit, Einordnung darunter. */
function Kachel({
  etikett,
  wert,
  einheit,
  basis,
  alsZahl = false,
}: Readonly<{
  etikett: string
  wert: number | null
  einheit: string
  basis: string
  alsZahl?: boolean
}>) {
  return (
    <Box
      component="article"
      aria-label={etikett}
      data-testid={`verbrauch-kachel-${etikett}`}
      sx={{ ...KACHEL_SX, gap: '6px', pt: '12px', px: '13px', pb: '11px' }}
    >
      <Box sx={ETIKETT}>{etikett}</Box>
      <KachelWert
        wert={wert === null ? null : alsZahl ? String(wert) : dollar(wert)}
        einheit={einheit}
        leerText="nicht gemessen"
        groesse={26}
      />
      <KachelFuss basis={basis} />
    </Box>
  )
}

/** Die Nächte des Zeitraums als Platte; jede Zeile führt zur Nachtansicht (#926 AK 8). */
function Naechte({
  naechte,
  onNachtWaehlen,
}: Readonly<{ naechte: readonly VerbrauchNachtKurz[]; onNachtWaehlen: (datum: string) => void }>) {
  if (naechte.length === 0) {
    return null
  }
  // Auch die Nächte-Platte zeigt den Nachtlauf-Anteil (Issue #1016) — Zahl **und** Bezugsgröße des
  // Balkens, sonst stünde eine Nacht im Verhältnis zu einer Summe, die sie nicht ausweist.
  const teuerste = Math.max(0, ...naechte.map((n) => n.usageByKind.night.total.costUsd ?? 0))
  return (
    <Platte titel="Nächte" notiz="Klick öffnet die Nacht">
      <Box>
        {naechte.map((nacht, stelle) => {
          const betrag = nacht.usageByKind.night.total.costUsd
          const breite = betrag === null || teuerste === 0 ? null : Math.round((betrag / teuerste) * 100)
          return (
            <ButtonBase
              key={nacht.night}
              onClick={() => onNachtWaehlen(nacht.night)}
              aria-label={[
                zeitraumBeschriftung({ type: 'DAY', firstDay: nacht.night, lastDay: nacht.night }),
                laeufeText(nacht.runCount),
                kosten(ohneNull(betrag)),
                ...(nacht.aborted ? ['abgebrochen'] : []),
              ].join(' · ')}
              sx={{
                display: 'grid',
                gridTemplateColumns: 'minmax(0,150px) 70px minmax(0,1fr) 70px',
                gap: '12px',
                alignItems: 'center',
                width: '100%',
                textAlign: 'left',
                px: '16px',
                py: '9px',
                fontSize: 12.5,
                borderBottom:
                  stelle === naechte.length - 1
                    ? 0
                    : `1px solid color-mix(in srgb, ${RAND} 55%, transparent)`,
                '&:hover': { bgcolor: ZEILE_HOVER },
              }}
            >
              <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                {nachtKurz(nacht.night)}
                {nacht.aborted && <Led melder="zinnob" />}
              </Box>
              <Box component="span" sx={ZAHL}>
                {laeufeText(nacht.runCount)}
              </Box>
              <Box component="span">
                {breite !== null && <Fuellschiene breite={breite} farbe={KUPFER} />}
              </Box>
              <Box component="span" sx={{ ...ZAHL, textAlign: 'right' }} aria-hidden>
                {betrag === null ? '—' : `${dollar(betrag)} $`}
              </Box>
            </ButtonBase>
          )
        })}
      </Box>
    </Platte>
  )
}

/** Die Kacheln des Vorzeitraums stehen zurück (Mockup `.vorher`): matter Wert, matte Fläche. */
const ZURUECKGENOMMEN = {
  '& [data-testid="kachel-wert"]': { color: 'text.secondary' },
  '& [data-testid^="verbrauch-kachel-"]': {
    background: `linear-gradient(180deg, ${PLATTE}, ${PLATTE_FUSS})`,
  },
} as const
