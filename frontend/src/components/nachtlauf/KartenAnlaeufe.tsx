import Box from '@mui/material/Box'
import Divider from '@mui/material/Divider'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import {
  nightRunsApi,
  type NightRunAnlauf,
  type NightRunsApi,
  type NightRunServerMode,
  type NightRunUsageView,
} from '../../api/nightRuns'
import { formatDuration } from '../../lib/formatDuration'
import { kosten, menge } from '../../lib/nachtlaufFormat'
import { nightRunZustandsText } from '../../lib/nightRunHandoff'

/**
 * Die drei Lauf-Arten, die eingeliefert werden, in fester Reihenfolge. Als `Record` über
 * `NightRunServerMode`: `NIGHTPLAN` entsteht allein im Browser und wird nie eingeliefert (`V30`,
 * Plan #803) — er kann hier gar nicht erst stehen, und ein neuer einlieferbarer Modus bricht den
 * Build, statt still zu fehlen. Die Wörter sind die der Nachtlauf-Auswertung.
 */
const LAUF_ART_TEXT: Record<NightRunServerMode, string> = {
  IMPLEMENTATION: 'Umsetzungs-Lauf',
  REVIEW: 'Prüf-Lauf',
  CHAIN: 'Ketten-Lauf',
}

const LAUF_ARTEN = Object.keys(LAUF_ART_TEXT) as NightRunServerMode[]

/** Die vier Verbrauchssummen: Schlüssel am Server, Benennung, Formatierer. */
const SUMMEN: ReadonlyArray<{
  schluessel: keyof NightRunUsageView
  testId: string
  label: string
  format: (wert: number | undefined) => string
}> = [
  { schluessel: 'costUsd', testId: 'kosten', label: 'Kosten', format: kosten },
  { schluessel: 'inputTokens', testId: 'eingabe', label: 'Eingabe', format: menge },
  { schluessel: 'outputTokens', testId: 'ausgabe', label: 'Ausgabe', format: menge },
  {
    schluessel: 'cachedInputTokens',
    testId: 'zwischenspeicher',
    label: 'Zwischenspeicher',
    format: menge,
  },
]

const ZEITPUNKT_FORMAT = new Intl.DateTimeFormat('de-DE', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

type Zustand =
  | { art: 'laden' }
  | { art: 'verborgen' }
  | { art: 'fehler' }
  | { art: 'geladen'; anlaeufe: NightRunAnlauf[] }

/**
 * Die Zahl der Wiederaufnahmen: jeder Anlauf mit `RED`, auf den **später** ein Anlauf derselben
 * Karte mit einem Zustand außer `GREY` folgt.
 *
 * <p>Ein `GREY` heißt „übersprungen oder liegengeblieben": Die Karte wurde gerade nicht wieder
 * aufgenommen, und mitgezählt behauptete die Zahl einen Versuch, den es nicht gab. Folgt auf das
 * `GREY` noch ein echter Anlauf, wurde sie aber doch wieder aufgenommen — gezählt wird deshalb, ob
 * irgendein späterer Anlauf kein `GREY` ist, nicht allein der unmittelbar nächste.
 */
export function zaehleWiederaufnahmen(anlaeufe: readonly NightRunAnlauf[]): number {
  const aufsteigend = [...anlaeufe].sort((a, b) => a.startedAt.localeCompare(b.startedAt))
  return aufsteigend.filter(
    (anlauf, index) =>
      anlauf.state === 'RED' && aufsteigend.slice(index + 1).some((spaeter) => spaeter.state !== 'GREY'),
  ).length
}

/** Die Dauer einer Lauf-Art über alle ihre Anläufe — oder die ausdrückliche Fehlanzeige. */
function dauerDerArt(anlaeufe: readonly NightRunAnlauf[], art: NightRunServerMode): string {
  const derArt = anlaeufe.filter((anlauf) => anlauf.mode === art)
  if (derArt.length === 0) {
    return 'nicht gelaufen'
  }
  const gemessen = derArt.flatMap((anlauf) => (anlauf.durationMs === null ? [] : [anlauf.durationMs]))
  if (gemessen.length === 0) {
    return 'nicht gemessen'
  }
  return formatDuration(gemessen.reduce((summe, wert) => summe + wert, 0) / 1000)
}

/**
 * Eine Summe mit ihrer Grundlage. Der Upload-Weg liefert je Paket allein den Kostenwert (Issue
 * #948); die Mengen bleiben dort „nicht gemessen", und eine nackte Summe wäre darum regelmäßig zu
 * klein, ohne dass man es sähe.
 */
function summeMitGrundlage(
  anlaeufe: readonly NightRunAnlauf[],
  schluessel: keyof NightRunUsageView,
  format: (wert: number | undefined) => string,
): string {
  const werte = anlaeufe.flatMap((anlauf) => {
    const wert = anlauf.usage?.[schluessel]
    return wert === null || wert === undefined ? [] : [wert]
  })
  if (werte.length === 0) {
    return format(undefined)
  }
  const summe = werte.reduce((gesamt, wert) => gesamt + wert, 0)
  return `${format(summe)} (aus ${werte.length} von ${anlaeufe.length} Anläufen)`
}

/**
 * Die Anläufe einer Karte im Karten-Detail (Issue #968): Dauer je Lauf-Art, Verbrauch über alle
 * Anläufe, die Anläufe selbst und die Zahl der Wiederaufnahmen.
 *
 * <p><b>Wer den Block sieht, entscheidet der Server.</b> Die Karte fragt ab und behandelt 403 und
 * 404 still — kein Block, keine Meldung. Die Projekt-Rolle liegt am Karten-Detail nicht vor, und
 * sie eigens zu laden hieße, eine zweite Rechtelogik im Browser zu führen, die mit der des Servers
 * auseinanderlaufen kann. Solange die Karte in keinem Lauf vorkam, bleibt der Block ebenfalls weg:
 * Drei Zeilen „nicht gelaufen" an jeder Karte sagten nichts.
 */
export function KartenAnlaeufe({
  projectId,
  cardNumber,
  api = nightRunsApi,
}: Readonly<{
  projectId: number
  cardNumber: number
  api?: Pick<NightRunsApi, 'anlaeufeDerKarte'>
}>) {
  const [zustand, setZustand] = useState<Zustand>({ art: 'laden' })

  useEffect(() => {
    let aktiv = true
    setZustand({ art: 'laden' })
    api.anlaeufeDerKarte(projectId, cardNumber).then(
      (anlaeufe) => {
        if (aktiv) setZustand({ art: 'geladen', anlaeufe })
      },
      (fehler: unknown) => {
        if (!aktiv) return
        const verborgen =
          fehler instanceof ApiError && (fehler.status === 403 || fehler.status === 404)
        setZustand({ art: verborgen ? 'verborgen' : 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, projectId, cardNumber])

  if (zustand.art === 'laden' || zustand.art === 'verborgen') {
    return null
  }
  if (zustand.art === 'geladen' && zustand.anlaeufe.length === 0) {
    return null
  }

  return (
    <>
      <Divider />
      <Box data-testid="karten-anlaeufe">
        <Typography variant="subtitle2" gutterBottom>
          Nachtlauf-Anläufe
        </Typography>
        {zustand.art === 'fehler' ? (
          <Typography color="text.secondary">Die Anläufe konnten nicht geladen werden.</Typography>
        ) : (
          <AnlaufInhalt anlaeufe={zustand.anlaeufe} />
        )}
      </Box>
    </>
  )
}

function AnlaufInhalt({ anlaeufe }: Readonly<{ anlaeufe: NightRunAnlauf[] }>) {
  const juengsterZuerst = [...anlaeufe].sort((a, b) => b.startedAt.localeCompare(a.startedAt))
  return (
    <Stack spacing={1.5}>
      <Box component="dl" sx={{ m: 0 }}>
        {LAUF_ARTEN.map((art) => (
          <Angabe
            key={art}
            testId={`anlaeufe-dauer-${art}`}
            label={`Dauer ${LAUF_ART_TEXT[art]}`}
            wert={dauerDerArt(anlaeufe, art)}
          />
        ))}
        {SUMMEN.map(({ schluessel, testId, label, format }) => (
          <Angabe
            key={schluessel}
            testId={`anlaeufe-summe-${testId}`}
            label={label}
            wert={summeMitGrundlage(anlaeufe, schluessel, format)}
          />
        ))}
      </Box>
      <Typography variant="body2" data-testid="anlaeufe-wiederaufnahmen">
        Wiederaufnahmen: {zaehleWiederaufnahmen(anlaeufe)}
      </Typography>
      <Box component="ul" data-testid="anlaeufe-liste" sx={{ m: 0, pl: 2.5 }}>
        {juengsterZuerst.map((anlauf, index) => (
          <Typography
            component="li"
            variant="caption"
            color="text.secondary"
            // Startzeitpunkt allein ist kein Schlüssel: Dieselbe Karte kann in einem Lauf zweimal stehen.
            key={`${anlauf.startedAt}-${index}`}
          >
            {[
              ZEITPUNKT_FORMAT.format(new Date(anlauf.startedAt)),
              nightRunZustandsText(anlauf.state, anlauf.errorClass ?? undefined),
              anlauf.durationMs === null ? 'Dauer nicht gemessen' : formatDuration(anlauf.durationMs / 1000),
              `Kosten ${kosten(anlauf.usage?.costUsd ?? undefined)}`,
            ].join(' · ')}
          </Typography>
        ))}
      </Box>
    </Stack>
  )
}

/** Ein Paar aus Benennung und Wert — als `dt`/`dd`, damit ein Vorlesewerkzeug es als Paar liest. */
function Angabe({ testId, label, wert }: Readonly<{ testId: string; label: string; wert: string }>) {
  return (
    <Stack direction="row" spacing={1} data-testid={testId}>
      <Typography component="dt" variant="body2" color="text.secondary">
        {label}:
      </Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0 }}>
        {wert}
      </Typography>
    </Stack>
  )
}
