import Box from '@mui/material/Box'
import { useCallback, useEffect, useId, useState } from 'react'
import { ApiError, apiErrorMessage } from '../../api/client'
import {
  nightRunsApi,
  type NightRunsApi,
  type RunnerJetzt as RunnerJetztDaten,
  type RunnerLauf,
  type RunnerLetzterLauf,
  type RunnerStufe,
} from '../../api/nightRuns'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { tagZeit } from '../../lib/leitstand'
import { stufenname, verstummtSatz, type RunnerJetztTexte } from '../../lib/runnerJetzt'
import { ANZEIGE, KLEIN_RADIUS, NUT_SX, PANEL_RADIUS, PLATTE, RAND, SCHATTEN_PLATTE, TEXT_MATT, ZAHL } from '../../theme'

type Ladung =
  | { art: 'laden' }
  | { art: 'geladen'; lage: RunnerJetztDaten }
  | { art: 'verboten' }
  | { art: 'fehler'; meldung: string }

/** Der Teilschritt in Worten; ein unbekannter Code erscheint als „Teilschritt: <Code>“ (E5). */
function schritt(t: RunnerJetztTexte, step: string): string {
  return step in t.SCHRITT ? t.SCHRITT[step as keyof RunnerJetztTexte['SCHRITT']] : t.SCHRITT_UNBEKANNT(step)
}

/**
 * Der Bereich „Runner jetzt“ auf der Runner-Seite (Issue #1729, Plan #1724 E14): was der Runner
 * des Projekts gerade tut. Je Lauf Art und Zustand, je laufender Stufe Karte, Stufe, Beginn, Frist,
 * Teilschritt und ob sie die Umsetzung hält; läuft keiner, steht das ausdrücklich da, mit dem
 * letzten Lauf oder „noch kein Lauf gestartet“.
 *
 * <p>Aufgefrischt wird im Takt `taktMs`, nur bei sichtbarem Tab und nur, solange ein Lauf läuft
 * oder verstummt ist — ein verstummter kann sich wieder melden. Fehlt das Leserecht (403), erscheint
 * der Bereich gar nicht, wie `HeuteNachtUebersicht`; ein anderer Ladefehler steht in ihm.
 */
export function RunnerJetzt({
  projectId,
  taktMs,
  api = nightRunsApi,
}: Readonly<{
  projectId: number
  taktMs: number
  api?: Pick<NightRunsApi, 'jetzt'>
}>) {
  const alleT = useT()
  const t = alleT.RUNNER_JETZT
  const ladung = useRunnerJetzt(api, projectId, taktMs, alleT.FEHLER_UNBEKANNT)
  const ueberschriftId = useId()

  if (ladung.art === 'laden' || ladung.art === 'verboten') {
    return null
  }

  return (
    <Box
      component="section"
      aria-labelledby={ueberschriftId}
      data-testid="runner-jetzt"
      sx={{
        borderRadius: `${PANEL_RADIUS}px`,
        border: `1px solid ${RAND}`,
        bgcolor: PLATTE,
        boxShadow: SCHATTEN_PLATTE,
        p: '14px 18px',
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
      }}
    >
      <Box component="h2" id={ueberschriftId} sx={{ ...ANZEIGE, m: 0, fontSize: 15, fontWeight: 600 }}>
        {t.TITEL}
      </Box>
      {ladung.art === 'fehler' && (
        <Box component="p" sx={{ m: 0, color: TEXT_MATT }}>
          {t.LADEFEHLER(ladung.meldung)}
        </Box>
      )}
      {ladung.art === 'geladen' && <Lage lage={ladung.lage} />}
    </Box>
  )
}

function Lage({ lage }: Readonly<{ lage: RunnerJetztDaten }>) {
  const t = useT().RUNNER_JETZT
  return (
    <>
      {lage.state === 'KEIN_RUNNER' && <Box component="p" sx={{ m: 0, fontWeight: 600 }}>{t.KEIN_RUNNER}</Box>}
      {lage.state === 'NIE' && <Box component="p" sx={{ m: 0, fontWeight: 600 }}>{t.NIE}</Box>}
      {lage.runs.length > 0 && (
        <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
          {lage.runs.map((lauf) => (
            <LaufZeile key={lauf.runId} lauf={lauf} />
          ))}
        </Box>
      )}
      {lage.lastRun !== null && <LetzterLauf lauf={lage.lastRun} />}
    </>
  )
}

/** Ein laufender oder verstummter Lauf: Kopf mit Art und Zustand, darunter seine Stufen. */
function LaufZeile({ lauf }: Readonly<{ lauf: RunnerLauf }>) {
  const { sprache } = useSprache()
  const t = useT().RUNNER_JETZT
  return (
    <Box
      component="li"
      data-testid={`runner-jetzt-lauf-${lauf.runId}`}
      sx={{ ...NUT_SX, borderRadius: `${KLEIN_RADIUS}px`, p: 1, display: 'flex', flexDirection: 'column', gap: 0.75 }}
    >
      <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', columnGap: 2, rowGap: 0.5 }}>
        <Box component="span" sx={{ fontWeight: 600 }}>{t.ART[lauf.mode]}</Box>
        {lauf.state === 'LAEUFT' ? (
          <>
            <Box component="span" sx={{ fontWeight: 600 }}>{t.LAEUFT}</Box>
            <Box component="span" sx={{ fontSize: 13, color: TEXT_MATT }}>{t.SEIT(tagZeit(lauf.startedAt, sprache))}</Box>
            <Box component="span" sx={{ fontSize: 13, color: TEXT_MATT }}>
              {t.LETZTE_MELDUNG(tagZeit(lauf.lastReportAt, sprache))}
            </Box>
          </>
        ) : (
          <Box component="span" sx={{ fontWeight: 600 }}>
            {verstummtSatz(t, tagZeit(lauf.lastReportAt, sprache), lauf.lastStage?.stage ?? null)}
          </Box>
        )}
      </Box>
      {lauf.stages.length > 0 && (
        <Box
          component="ul"
          aria-label={t.STUFEN(t.ART[lauf.mode])}
          sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: 0.5 }}
        >
          {lauf.stages.map((stufe, index) => (
            <StufenZeile key={`${stufe.cardNumber}-${stufe.stage}`} runId={lauf.runId} index={index} stufe={stufe} />
          ))}
        </Box>
      )}
    </Box>
  )
}

/** Eine laufende Stufe: Karte und Stufe, Beginn, Frist, Teilschritt und ob sie die Umsetzung hält. */
function StufenZeile({ runId, index, stufe }: Readonly<{ runId: number; index: number; stufe: RunnerStufe }>) {
  const { sprache } = useSprache()
  const t = useT().RUNNER_JETZT
  return (
    <Box
      component="li"
      data-testid={`runner-jetzt-stufe-${runId}-${index}`}
      sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', columnGap: 2, rowGap: 0.25, fontSize: 13 }}
    >
      <Box component="span" sx={{ ...ZAHL, fontWeight: 600 }}>{t.KARTE_STUFE(stufe.cardNumber, stufenname(t, stufe.stage))}</Box>
      <span>{t.BEGINN(tagZeit(stufe.startedAt, sprache))}</span>
      <span>{stufe.deadline === null ? t.KEINE_FRIST : t.FRIST(tagZeit(stufe.deadline, sprache))}</span>
      <span>{schritt(t, stufe.step)}</span>
      {stufe.holdsImplementation && <Box component="span" sx={{ fontWeight: 600 }}>{t.HAELT}</Box>}
    </Box>
  )
}

/**
 * Der zuletzt beendete Lauf mit Ende und Ergebnis; ein Lauf ohne Paket nennt seinen Grund (Plan
 * #1724 E14).
 */
function LetzterLauf({ lauf }: Readonly<{ lauf: RunnerLetzterLauf }>) {
  const { sprache } = useSprache()
  const alleT = useT()
  const t = alleT.RUNNER_JETZT
  const ergebnis =
    lauf.outcome === 'NO_WORK' && lauf.noWorkReason !== null
      ? t.OHNE_PAKET(lauf.noWorkReason)
      : alleT.NACHTLAUF_UEBERGABE.AUSGANG[lauf.outcome]
  return (
    <Box component="p" data-testid="runner-jetzt-letzter-lauf" sx={{ m: 0, fontSize: 13, color: TEXT_MATT }}>
      {t.LETZTER_LAUF(t.ART[lauf.mode], tagZeit(lauf.endedAt, sprache), ergebnis)}
    </Box>
  )
}

/**
 * Lädt die Lage beim Öffnen und danach im Takt, solange ein Lauf läuft oder verstummt ist — nur bei
 * sichtbarem Tab. Eine Antwort nach dem Schließen verfällt.
 */
function useRunnerJetzt(
  api: Pick<NightRunsApi, 'jetzt'>,
  projectId: number,
  taktMs: number,
  fallback: string,
): Ladung {
  const [ladung, setLadung] = useState<Ladung>({ art: 'laden' })

  const laden = useCallback(
    (istAktiv: () => boolean) => {
      api.jetzt(projectId).then(
        (lage) => {
          if (istAktiv()) setLadung({ art: 'geladen', lage })
        },
        (error: unknown) => {
          if (!istAktiv()) return
          setLadung(
            error instanceof ApiError && error.status === 403
              ? { art: 'verboten' }
              : { art: 'fehler', meldung: apiErrorMessage(error, fallback) },
          )
        },
      )
    },
    [api, projectId, fallback],
  )

  useEffect(() => {
    let aktiv = true
    laden(() => aktiv)
    return () => {
      aktiv = false
    }
  }, [laden])

  const imTakt = ladung.art === 'geladen' && ladung.lage.runs.length > 0
  useEffect(() => {
    if (!imTakt) {
      return
    }
    let aktiv = true
    const takt = setInterval(() => {
      if (document.visibilityState === 'visible') {
        laden(() => aktiv)
      }
    }, taktMs)
    return () => {
      aktiv = false
      clearInterval(takt)
    }
  }, [imTakt, laden, taktMs])

  return ladung
}
