import Box from '@mui/material/Box'
import { useId } from 'react'
import type { RunnerJetzt as RunnerJetztDaten } from '../../api/nightRuns'
import { useSprache, useT } from '../../i18n/SpracheContext'
import type { Woerterbuch } from '../../i18n/de'
import type { Sprache } from '../../i18n/woerterbuch'
import { laufSprungziel, zeitleiste, type Zeitbalken, type Zeitleistenlauf } from '../../lib/laufZeitleiste'
import { uhrzeit } from '../../lib/leitstand'
import { stufenname } from '../../lib/runnerJetzt'
import {
  CARD_RADIUS,
  ETIKETT,
  KUPFER,
  MELDER,
  NUR_LESER_SX,
  NUT,
  PANEL_RADIUS,
  PLATTE,
  PLATTE_HOCH,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_PLATTE,
  TEXT_MATT,
  ZAHL,
} from '../../theme'

/** So viele Zeitmarken stehen über der Achse, Anfang und Ende eingeschlossen (Vorlage Z. 459). */
const MARKEN = 5

/** Der Abstand der Bahnen zum Rand der Achse (Vorlage `.bahn`, `.achse-marken`). */
const EINZUG = 12

/** Laufend: Stahl schraffiert (Vorlage `.balken-laeuft`). */
const SCHRAFFUR_LAEUFT = `repeating-linear-gradient(45deg, ${MELDER.stahl} 0 5px, color-mix(in srgb, ${MELDER.stahl} 72%, ${PLATTE_HOCH}) 5px 10px)`

/** Beendet: die Farbe seines Ausgangs (Vorlage `.balken-fertig`). */
const flaeche = (balken: Zeitbalken): string => (balken.endeMs === null ? SCHRAFFUR_LAEUFT : MELDER[balken.melder])

/** Ein Anteil der Achse als Prozentangabe, auf zwei Stellen gerundet. */
const prozent = (anteil: number): string => `${Math.round(anteil * 10_000) / 100}%`

const zeitVon = (ms: number, sprache: Sprache): string => uhrzeit(new Date(ms).toISOString(), sprache)

/** Die jüngste gemeldete Stufe je Run-Nummer, in Worten (Plan #1882 E2). */
function stufenJeLauf(runner: RunnerJetztDaten | null, t: Woerterbuch['RUNNER_JETZT']): Map<number, string> {
  const stufen = new Map<number, string>()
  for (const lauf of runner?.runs ?? []) {
    const juengste = lauf.stages.reduce<(typeof lauf.stages)[number] | null>(
      (bisher, stufe) => (bisher === null || Date.parse(stufe.startedAt) > Date.parse(bisher.startedAt) ? stufe : bisher),
      null,
    )
    if (juengste !== null) {
      stufen.set(lauf.runId, stufenname(t, juengste.stage))
    }
  }
  return stufen
}

/** Die Beschriftung am Balken: Nummer und Stufe; ohne Nummer die Startzeit. */
function beschriftung(balken: Zeitbalken, t: Woerterbuch['JETZT_IM_WERK'], sprache: Sprache): string {
  const kopf = balken.nummer === undefined ? uhrzeit(balken.startedAt, sprache) : t.BALKEN_NUMMER(balken.nummer)
  return balken.stufe === null ? kopf : t.BALKEN_STUFE(kopf, balken.stufe)
}

/** Der Satz der Vorleseliste: „Run #691, seit 10:32, läuft, Umsetzung“ oder „Run #690, 10:30 bis 10:44, fertig“. */
function vorlesesatz(balken: Zeitbalken, t: Woerterbuch['JETZT_IM_WERK'], sprache: Sprache): string {
  const run = t.RUN(balken.nummer)
  if (balken.endeMs !== null) {
    return t.FERTIG(run, uhrzeit(balken.startedAt, sprache), zeitVon(balken.endeMs, sprache))
  }
  const satz = t.LAEUFT(run, uhrzeit(balken.startedAt, sprache))
  return balken.stufe === null ? satz : t.MIT_STUFE(satz, balken.stufe)
}

/**
 * Die Platte „Jetzt im Werk“ (Issue #1887, Plan #1882 E2; Vorlage `docs/entwurf-lauf-ansicht.html`
 * Z. 452–475): je Run ein Balken auf einer Zeitachse, beschriftet mit Nummer und laufender Stufe,
 * und ein kupferner Strich für „jetzt“. Laufende Runs stehen schraffiert in Stahl, im Fenster
 * beendete in der Farbe ihres Ausgangs. Ohne laufenden Run erscheint die Platte nicht.
 *
 * <p>Die Achse ist Gestalt und für Vorlesewerkzeuge verborgen; ihre Balken verweisen auf die
 * Platte des Runs, stehen aber nicht in der Tabulator-Reihenfolge. Dieselben Verweise trägt die
 * Vorleseliste darunter in ganzen Sätzen — so gibt es jeden Halt genau einmal.
 *
 * <p>Zustandslos: Die Rechnung steht in `lib/laufZeitleiste.ts`, die Läufe und die Lage von `/now`
 * reicht die Seite herein, ebenso den Zeitpunkt `jetzt`.
 */
export function JetztImWerk({
  laeufe,
  runner,
  jetzt,
}: Readonly<{
  laeufe: readonly Zeitleistenlauf[]
  /** Die Lage aus „Runner jetzt“; `null`, solange sie nicht geladen ist — dann ohne Stufen. */
  runner: RunnerJetztDaten | null
  jetzt: number
}>) {
  const alleT = useT()
  const t = alleT.JETZT_IM_WERK
  const { sprache } = useSprache()
  const ueberschriftId = useId()
  const leiste = zeitleiste(laeufe, stufenJeLauf(runner, alleT.RUNNER_JETZT), jetzt)
  if (leiste === null) {
    return null
  }
  const spanne = leiste.bisMs - leiste.vonMs
  const anteil = (ms: number) => (ms - leiste.vonMs) / spanne
  const laufend = leiste.balken.filter((b) => b.endeMs === null).length
  const marken = Array.from({ length: MARKEN }, (_, i) => leiste.vonMs + (spanne * i) / (MARKEN - 1))

  return (
    <Box
      component="section"
      aria-labelledby={ueberschriftId}
      data-testid="jetzt-im-werk"
      sx={{
        borderRadius: `${PANEL_RADIUS}px`,
        border: `1px solid ${RAND}`,
        bgcolor: PLATTE,
        boxShadow: SCHATTEN_PLATTE,
        p: '14px 18px 12px',
      }}
    >
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 2 }}>
        <Box component="h2" id={ueberschriftId} sx={{ ...ETIKETT, m: 0 }}>
          {t.TITEL(laufend)}
        </Box>
        <Box component="span" sx={{ ...ZAHL, fontSize: 12, color: TEXT_MATT }}>
          {t.JETZT(zeitVon(jetzt, sprache))}
        </Box>
      </Box>
      <Box
        aria-hidden="true"
        sx={{
          position: 'relative',
          mt: '12px',
          p: '22px 0 6px',
          borderRadius: `${CARD_RADIUS}px`,
          bgcolor: NUT,
          boxShadow: SCHATTEN_NUTE,
        }}
      >
        <Box
          data-testid="werk-marken"
          sx={{
            ...ZAHL,
            position: 'absolute',
            left: EINZUG,
            right: EINZUG,
            top: 4,
            display: 'flex',
            justifyContent: 'space-between',
            fontSize: 11,
            color: TEXT_MATT,
          }}
        >
          {marken.map((ms) => (
            <span key={ms}>{zeitVon(ms, sprache)}</span>
          ))}
        </Box>
        {leiste.balken.map((balken, index) => (
          <Box key={balken.startedAt} sx={{ position: 'relative', height: 22, m: `4px ${EINZUG}px` }}>
            <Box
              component="a"
              href={`#${laufSprungziel(balken.startedAt)}`}
              tabIndex={-1}
              data-testid={`werk-balken-${index}`}
              data-melder={balken.endeMs === null ? 'laeuft' : balken.melder}
              sx={{
                ...ZAHL,
                position: 'absolute',
                top: 2,
                height: 18,
                left: prozent(anteil(balken.anfangMs)),
                width: prozent(anteil(balken.endeMs ?? leiste.bisMs) - anteil(balken.anfangMs)),
                borderRadius: '5px',
                display: 'flex',
                alignItems: 'center',
                px: '8px',
                boxSizing: 'border-box',
                overflow: 'hidden',
                whiteSpace: 'nowrap',
                fontSize: 11,
                fontWeight: 600,
                textDecoration: 'none',
                color: 'primary.contrastText',
                background: flaeche(balken),
              }}
            >
              {beschriftung(balken, t, sprache)}
            </Box>
          </Box>
        ))}
        <Box
          sx={{
            position: 'absolute',
            top: 18,
            bottom: 4,
            width: 2,
            left: `calc(${EINZUG}px + (100% - ${2 * EINZUG}px) * ${anteil(jetzt)})`,
            bgcolor: KUPFER,
            boxShadow: `0 0 8px -1px ${KUPFER}`,
          }}
        />
      </Box>
      <Box component="ul" sx={{ ...NUR_LESER_SX, m: 0, p: 0 }}>
        {leiste.balken.map((balken) => (
          <li key={balken.startedAt}>
            <a href={`#${laufSprungziel(balken.startedAt)}`}>{vorlesesatz(balken, t, sprache)}</a>
          </li>
        ))}
      </Box>
      <Box
        aria-hidden="true"
        sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mt: '10px', fontSize: 11.5, color: TEXT_MATT }}
      >
        <Legende muster={SCHRAFFUR_LAEUFT} text={t.LEGENDE_LAEUFT} />
        <Legende muster={MELDER.gruen} text={t.LEGENDE_BEENDET} />
        <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
          <Box component="span" sx={{ display: 'inline-block', width: 2, height: 12, bgcolor: KUPFER }} />
          {t.LEGENDE_JETZT}
        </Box>
      </Box>
    </Box>
  )
}

/** Ein Eintrag der Legende: ein kleiner Balken im Muster und sein Wort. */
function Legende({ muster, text }: Readonly<{ muster: string; text: string }>) {
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
      <Box component="span" sx={{ display: 'inline-block', width: 22, height: 10, borderRadius: '5px', background: muster }} />
      {text}
    </Box>
  )
}
