import Box from '@mui/material/Box'
import Divider from '@mui/material/Divider'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { ApiError, apiErrorMessage } from '../../api/client'
import {
  nightRunsApi,
  type NightRunsApi,
  type Wartegrund as RunnerGrund,
  type WartegrundAusLauf,
  type Wartegruende,
  type WartegrundVermerk,
} from '../../api/nightRuns'
import type { Woerterbuch } from '../../i18n/de'
import { useSprache, useT } from '../../i18n/SpracheContext'
import type { Sprache } from '../../i18n/woerterbuch'
import { tagZeit } from '../../lib/leitstand'
import { stufenname, verstummtSatz } from '../../lib/runnerJetzt'

type Ladung =
  | { art: 'laden' }
  | { art: 'geladen'; gruende: Wartegruende }
  | { art: 'verborgen' }
  | { art: 'fehler'; meldung: string }

/** Der Satz eines Grundes aus den Läufen (Plan #1724 E8); „verstummt“ über `RUNNER_JETZT.VERSTUMMT`. */
function laufgrundSatz(alleT: Woerterbuch, sprache: Sprache, g: WartegrundAusLauf): string {
  const t = alleT.WARTEGRUND
  const r = alleT.RUNNER_JETZT
  switch (g.code) {
    case 'KEINE_UMSETZUNGSNACHT':
      return t.KEINE_UMSETZUNGSNACHT
    case 'VERSTUMMT':
      return verstummtSatz(r, tagZeit(g.since, sprache), g.stage)
    case 'BELEGT':
      return g.runMode === 'CHAIN'
        ? t.BELEGT_KETTE(g.cardNumber, stufenname(r, g.stage))
        : t.BELEGT_LAUF(r.ART[g.runMode], g.cardNumber)
  }
}

/**
 * Der Satz eines Grundes des Runners. Ein Code, den die Oberfläche nicht kennt, erscheint mit dem
 * Wortlaut des Runners — der Dialog verschweigt keinen Grund (Fachplan #1721).
 */
function kartengrundSatz(t: Woerterbuch['WARTEGRUND'], g: RunnerGrund): string {
  switch (g.code) {
    case 'NOT_A_WORK_PACKAGE':
      return g.kind !== null && g.kind in t.KEIN_ARBEITSPAKET
        ? t.KEIN_ARBEITSPAKET[g.kind as keyof Woerterbuch['WARTEGRUND']['KEIN_ARBEITSPAKET']]
        : t.KEIN_ARBEITSPAKET_SONST
    case 'NO_LABEL':
      return t.NO_LABEL
    case 'DEPENDENCY':
      return t.DEPENDENCY(g.cardNumbers.map((n) => `#${n}`).join(', '))
    case 'CLARIFY':
      return t.CLARIFY
    case 'PROTECTED_LABEL':
      return t.PROTECTED_LABEL
    case 'PROTECTED_FILE':
      return t.PROTECTED_FILE(g.paths.join(', '))
    case 'REVIEW_MISSING':
      return t.REVIEW_MISSING
    default:
      return g.text
  }
}

/** Der Satz eines Vermerks über das Alter der Bewertung (E9). */
function vermerkSatz(t: Woerterbuch['WARTEGRUND'], sprache: Sprache, v: WartegrundVermerk): string {
  if (v.code === 'SEIT_BEWERTUNG_GEAENDERT') {
    return t.SEIT_BEWERTUNG_GEAENDERT
  }
  return v.assessedAt === null ? t.NIE_BEWERTET : t.NICHT_BEWERTET(tagZeit(v.assessedAt, sprache))
}

/**
 * Der Abschnitt „Wartegrund“ im Kartendialog (Issue #1730, Plan #1724 E9 bis E12): warum diese
 * Karte in Ready wartet — je Grund ein Satz, sonst „startbereit“, dazu „Pakete davor“ und die
 * Vermerke über das Alter der Bewertung.
 *
 * <p>Geladen wird für jede Karte; ob sie in Ready steht, entscheidet der Server (E12). Bei 403
 * (kein Leserecht) und 404 (nicht in Ready) erscheint nichts, auch kein leerer Rahmen; ein anderer
 * Ladefehler steht im Abschnitt.
 */
export function Wartegrund({
  cardId,
  api = nightRunsApi,
}: Readonly<{
  cardId: number
  api?: Pick<NightRunsApi, 'wartegrund'>
}>) {
  const alleT = useT()
  const { sprache } = useSprache()
  const t = alleT.WARTEGRUND
  const ladung = useWartegrund(api, cardId, alleT.FEHLER_UNBEKANNT)

  if (ladung.art === 'laden' || ladung.art === 'verborgen') {
    return null
  }

  const saetze =
    ladung.art === 'geladen'
      ? [
          ...ladung.gruende.runReasons.map((g) => laufgrundSatz(alleT, sprache, g)),
          ...ladung.gruende.cardReasons.map((g) => kartengrundSatz(t, g)),
        ]
      : []

  return (
    <>
      <Divider />
      <Box data-testid="wartegrund">
        <Typography variant="subtitle2" gutterBottom>
          {t.TITEL}
        </Typography>
        {ladung.art === 'fehler' && <Typography color="text.secondary">{t.LADEFEHLER(ladung.meldung)}</Typography>}
        {ladung.art === 'geladen' && (
          <>
            {ladung.gruende.startable && <Typography variant="body2">{t.STARTBEREIT}</Typography>}
            {saetze.length > 0 && (
              <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                {saetze.map((satz, index) => (
                  // Zwei Gründe können denselben Satz tragen, etwa zwei verstummte Läufe.
                  <Typography component="li" variant="body2" key={`${index}-${satz}`}>
                    {satz}
                  </Typography>
                ))}
              </Box>
            )}
            {ladung.gruende.packagesAhead !== null && (
              <Typography variant="body2">{t.PAKETE_DAVOR(ladung.gruende.packagesAhead)}</Typography>
            )}
            {ladung.gruende.notes.map((v) => (
              <Typography key={`${v.code}-${v.assessedAt}`} variant="body2" color="text.secondary">
                {vermerkSatz(t, sprache, v)}
              </Typography>
            ))}
          </>
        )}
      </Box>
    </>
  )
}

/** Lädt den Wartegrund einmal je Karte; eine Antwort nach dem Schließen verfällt. */
function useWartegrund(api: Pick<NightRunsApi, 'wartegrund'>, cardId: number, fallback: string): Ladung {
  const [ladung, setLadung] = useState<Ladung>({ art: 'laden' })
  useEffect(() => {
    let aktiv = true
    api.wartegrund(cardId).then(
      (gruende) => {
        if (aktiv) setLadung({ art: 'geladen', gruende })
      },
      (error: unknown) => {
        if (!aktiv) return
        setLadung(
          error instanceof ApiError && (error.status === 403 || error.status === 404)
            ? { art: 'verborgen' }
            : { art: 'fehler', meldung: apiErrorMessage(error, fallback) },
        )
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, cardId, fallback])
  return ladung
}
