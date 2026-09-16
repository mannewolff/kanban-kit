import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import ButtonBase from '@mui/material/ButtonBase'
import Stack from '@mui/material/Stack'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import {
  nightRunUsageApi,
  type NightRunUsageApi,
  type VerbrauchKennzahlen,
  type VerbrauchNachtKurz,
  type VerbrauchZeitraum,
  type VerbrauchZeitraumArt,
} from '../../api/nightRunUsage'
import { kosten } from '../../lib/nachtlaufFormat'
import {
  vergleichMitVorzeitraum,
  vorzeitraumBeschriftung,
  zeitraumBeschriftung,
  zeitraumFall,
  zeitraumHinweis,
} from '../../lib/verbrauchZeitraum'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'
import { NachtlaufKennzahlen } from './NachtlaufKennzahlen'
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
 * <p><b>Gewählt wird ohne Datumseingabe</b> (Plan #933 E14): Art plus Rückschritt, 0 ist der zuletzt
 * abgeschlossene Zeitraum. Ein Wechsel der Art beginnt wieder dort.
 *
 * <p>Die Hinweise folgen `zeitraumFall`: Ein Zeitraum ohne Lauf und einer vor der Aufbewahrung
 * zeigen allein ihren Satz, ein angeschnittener Zeitraum und einer ohne Messung zeigen ihre Zahlen
 * **mit** dem Hinweis daneben (Plan E8, E5).
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
    <Box data-testid="verbrauch-zeitraum">
      <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
        <ToggleButtonGroup
          exclusive
          size="small"
          value={art}
          aria-label="Art des Zeitraums"
          onChange={(_, neu: VerbrauchZeitraumArt | null) => {
            if (neu !== null) {
              setArt(neu)
              setRueckschritt(0)
            }
          }}
        >
          {ARTEN.map((eintrag) => (
            <ToggleButton key={eintrag.art} value={eintrag.art}>
              {eintrag.label}
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
        <Button
          size="small"
          aria-label="Früherer Zeitraum"
          onClick={() => setRueckschritt((r) => r + 1)}
        >
          ← früher
        </Button>
        <Button
          size="small"
          aria-label="Späterer Zeitraum"
          disabled={rueckschritt === 0}
          onClick={() => setRueckschritt((r) => r - 1)}
        >
          später →
        </Button>
      </Stack>

      {zustand.art === 'laden' && <Typography sx={HINWEIS}>Der Zeitraum wird geladen …</Typography>}
      {zustand.art === 'fehler' && (
        <Typography sx={HINWEIS}>Der Zeitraum konnte nicht geladen werden.</Typography>
      )}
      {zustand.art === 'daten' && (
        <ZeitraumInhalt zeitraum={zustand.zeitraum} onNachtWaehlen={onNachtWaehlen} />
      )}
    </Box>
  )
}

function ZeitraumInhalt({
  zeitraum,
  onNachtWaehlen,
}: Readonly<{ zeitraum: VerbrauchZeitraum; onNachtWaehlen: (datum: string) => void }>) {
  const { current, previous } = zeitraum
  const fall = zeitraumFall(current)
  const hinweis = zeitraumHinweis(current)
  const ohneZahlen = fall === 'kein-lauf' || fall === 'vor-aufbewahrung'
  return (
    <Box sx={{ mt: 2 }}>
      <Typography component="h3" sx={UEBERSCHRIFT}>
        {zeitraumBeschriftung(current)}
      </Typography>
      {hinweis !== null && (
        <Typography
          component="p"
          role="note"
          data-testid="verbrauch-zeitraum-hinweis"
          sx={{ ...HINWEIS, color: NACHTLAUF_FARBEN.ink }}
        >
          {hinweis}
        </Typography>
      )}

      {!ohneZahlen && (
        <>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
              columnGap: 4,
            }}
          >
            <Spalte
              testId="verbrauch-zeitraum-aktuell"
              titel="Dieser Zeitraum"
              kennzahlen={current}
              hinweis={null}
            />
            {/* Der Hinweis des Vorzeitraums steht an seiner Spalte; der des Zeitraums steht schon
                über beiden. */}
            <Spalte
              testId="verbrauch-zeitraum-vorher"
              titel={vorzeitraumBeschriftung(previous)}
              kennzahlen={previous}
              hinweis={zeitraumHinweis(previous)}
            />
          </Box>
          <Typography
            component="p"
            data-testid="verbrauch-zeitraum-vergleich"
            sx={{ ...HINWEIS, color: NACHTLAUF_FARBEN.ink, fontWeight: 600, mt: 1 }}
          >
            {vergleichMitVorzeitraum(current.usage.total, previous.usage.total).text}
          </Typography>

          <Naechte naechte={zeitraum.nights} onNachtWaehlen={onNachtWaehlen} />

          <Box sx={{ mt: 3 }}>
            <NachtlaufVerbrauchVorhaben
              epics={zeitraum.epics}
              withoutEpic={zeitraum.withoutEpic}
              epicsOverlap={zeitraum.epicsOverlap}
            />
          </Box>
        </>
      )}
    </Box>
  )
}

/** Die Kennzahlen eines Zeitraums als eine Spalte; die Beschriftung sagt, welcher es ist. */
function Spalte({
  testId,
  titel,
  kennzahlen,
  hinweis,
}: Readonly<{
  testId: string
  titel: string
  kennzahlen: VerbrauchKennzahlen
  hinweis: string | null
}>) {
  const { total, cardShare, remainder } = kennzahlen.usage
  return (
    <Box data-testid={testId}>
      <Typography component="div" sx={{ ...HINWEIS, mt: 2 }}>
        {titel}
      </Typography>
      <NachtlaufKennzahlen
        testId={`${testId}-kennzahlen`}
        kennzahlen={[
          { label: 'Gesamtsumme', wert: kosten(ohneNull(total.costUsd)), hinweis: null },
          { label: 'Karten zugeordnet', wert: kosten(ohneNull(cardShare.costUsd)), hinweis: null },
          { label: 'Rest', wert: kosten(ohneNull(remainder.costUsd)), hinweis: null },
          { label: 'Läufe', wert: String(kennzahlen.runCount), hinweis },
        ]}
      />
    </Box>
  )
}

/** Die Nächte des Zeitraums; jede führt zur Nachtansicht (#926 AK 8). */
function Naechte({
  naechte,
  onNachtWaehlen,
}: Readonly<{ naechte: readonly VerbrauchNachtKurz[]; onNachtWaehlen: (datum: string) => void }>) {
  if (naechte.length === 0) {
    return null
  }
  return (
    <Box sx={{ mt: 3 }}>
      <Typography component="h4" sx={{ ...UEBERSCHRIFT, fontSize: 16 }}>
        Nächte
      </Typography>
      <Box component="ul" sx={{ listStyle: 'none', p: 0, m: 0, mt: 1, display: 'grid', gap: 0.5 }}>
        {naechte.map((nacht) => (
          <Box component="li" key={nacht.night}>
            <ButtonBase
              onClick={() => onNachtWaehlen(nacht.night)}
              sx={{
                fontFamily: NACHTLAUF_SCHRIFTEN.mono,
                fontSize: 13,
                color: NACHTLAUF_FARBEN.akzent,
                textDecoration: 'underline',
                textAlign: 'left',
              }}
            >
              {[
                zeitraumBeschriftung({ type: 'DAY', firstDay: nacht.night, lastDay: nacht.night }),
                nacht.runCount === 1 ? '1 Lauf' : `${nacht.runCount} Läufe`,
                kosten(ohneNull(nacht.usage.total.costUsd)),
                ...(nacht.aborted ? ['abgebrochen'] : []),
              ].join(' · ')}
            </ButtonBase>
          </Box>
        ))}
      </Box>
    </Box>
  )
}

const UEBERSCHRIFT = {
  fontFamily: NACHTLAUF_SCHRIFTEN.display,
  fontWeight: 600,
  fontSize: 18,
  color: NACHTLAUF_FARBEN.ink,
} as const

const HINWEIS = {
  fontFamily: NACHTLAUF_SCHRIFTEN.body,
  fontSize: 13,
  color: NACHTLAUF_FARBEN.ink3,
  mt: 1,
} as const
