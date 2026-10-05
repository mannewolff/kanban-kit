import BlockIcon from '@mui/icons-material/Block'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import FlagIcon from '@mui/icons-material/Flag'
import HighlightOffIcon from '@mui/icons-material/HighlightOff'
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircleOutline'
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import ButtonBase from '@mui/material/ButtonBase'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import TaskAltIcon from '@mui/icons-material/TaskAlt'
import { useEffect, useId, useState, type KeyboardEvent, type ReactNode } from 'react'
import { apiErrorMessage } from '../../api/client'
import type { Label } from '../../api/labels'
import { nightRunsApi, type KettenStand, type NightRunsApi } from '../../api/nightRuns'
import { kettenAnzeige, type Stationssymbol } from '../../lib/laufFortschritt'
import { KLEIN_RADIUS, KUPFER, NUT_SX, TASTE_SX, TEXT_SCHWACH } from '../../theme'
import { dialogTitleSx } from '../dialogChromeSx'

/** Ob eine Karte die Stufenleiste trägt: ihr Titel beginnt mit `[Fachlich]` oder `[Plan]` (E1). */
export function traegtStufenleiste(titel: string): boolean {
  return titel.startsWith('[Fachlich]') || titel.startsWith('[Plan]')
}

type Station = 'fachplan' | 'plan' | 'pruefung' | 'pakete' | 'umsetzung' | 'push-vorbereitet'

/**
 * Die sechs Stationen der Kette in ihrer Reihenfolge; `ziel` trägt das Label der vier wählbaren
 * (Kit-Vertrag, E10). Fachplan und Prüfung sind Stationen auf dem Weg, aber kein Ziel.
 */
const STATIONEN: ReadonlyArray<{ schluessel: Station; name: string; ziel: string | null }> = [
  { schluessel: 'fachplan', name: 'Fachplan', ziel: null },
  { schluessel: 'plan', name: 'Plan', ziel: 'ziel:plan' },
  { schluessel: 'pruefung', name: 'Prüfung', ziel: null },
  { schluessel: 'pakete', name: 'Arbeitspakete', ziel: 'ziel:pakete' },
  { schluessel: 'umsetzung', name: 'Umsetzung', ziel: 'ziel:umsetzung' },
  { schluessel: 'push-vorbereitet', name: 'Veröffentlichung vorbereitet', ziel: 'ziel:push-vorbereitet' },
]

const ZIEL_PRAEFIX = 'ziel:'
const PRUEFER_PRAEFIX = 'planreview:'
const DURCHZIEHEN = 'kit:durchziehen'
const PRUEFER = ['planreview:1', 'planreview:2'] as const
/** Das eine Start-Label des Kit-Vertrags (E10): gesetzt heißt freigegeben, noch nicht übernommen. */
const NACHT = 'kit:night'
const REVIEW_FERTIG = 'review:fertig'
/** Labels, mit denen an der Karte eine offene Frage auf einen Menschen wartet. */
const OFFENE_FRAGE = ['kit:klaeren', 'lauf:wartet'] as const
const LAUFSTAND_ANKER = '## Laufstand'
/** Die Lauf-ID ist der Stempel des Runners, etwa `2026-10-05-175710` im Protokollpfad. */
const LAUF_ID = /\b\d{4}-\d{2}-\d{2}-\d{6}\b/

/**
 * Die feste Texttabelle der Sperrhinweise (E8, E14). Die Leiste legt nie ein Label an: Fehlt eins,
 * nennt der Hinweis seinen Namen, und ein Mensch legt es am Board an.
 */
const HINWEIS = {
  labelFehlt: (name: string) =>
    `Am Board fehlt das Label „${name}“. Die Leiste legt es nicht an — ein Mensch legt es in den Board-Labels an.`,
  durchziehen: `Die Karte trägt ${DURCHZIEHEN} und läuft damit mindestens bis zur Umsetzung; ein kürzeres Ziel bliebe wirkungslos.`,
  planKarte: 'Die Karte ist schon ein Plan: Plan und Prüfung sind vor dem Lauf erbracht.',
  fachlichOhneReview: `Die fachliche Prüfung ist nicht abgeschlossen: Die Karte trägt kein ${REVIEW_FERTIG}.`,
  planOhneReview: 'Die Planprüfung ist nicht abgeschlossen: Im Plan fehlt die Zeile „Plan-Review:“.',
  offeneFrage: (name: string) => `An der Karte wartet eine offene Frage auf einen Menschen (${name}).`,
  go: 'Mit diesem Ziel gibst du das GO für alle Arbeitspakete dieser Karte.',
  bestaetigung: 'Damit gibst du das GO für alle Arbeitspakete dieser Karte. Kette starten?',
  wartet: 'Kette gestartet — sie wartet auf die Übernahme durch einen Runner.',
  uebernommen: 'Ein Runner hat die Kette übernommen — die Leiste ist nur noch Anzeige.',
  planReviewDa:
    'Der Plan dieser Anforderung trägt schon „Plan-Review:“ — die Prüferzahl lässt sich nicht mehr wählen.',
  ladefehler: (meldung: string) =>
    `Der Stand der Kette ließ sich nicht laden: ${meldung}. Die Leiste zeigt den Stand vor dem Lauf.`,
} as const

/** Ob ein Kommentar der Laufstand des Runners ist: seine erste Zeile ist der Anker, wie im Kit. */
const istLaufstand = (body: string) => body.replaceAll('\r', '').trimStart().split('\n')[0].trim() === LAUFSTAND_ANKER

/**
 * Ob ein Runner die Karte übernommen hat — die vorläufige Erkennung aus Gruppe A (E15): Ihr
 * jüngster Laufstand trägt eine Lauf-ID, und `kit:night` ist abgenommen. Sie gilt nur noch, solange
 * der Kettenstand lädt oder nicht geladen werden konnte (Issue #1453); sonst entscheidet der
 * Endpunkt. Die Kommentare kommen wie im Modal, der jüngste zuerst.
 */
const uebernommenVon = (kommentare: readonly { body: string }[], gestartet: boolean) =>
  !gestartet && LAUF_ID.test(kommentare.find((k) => istLaufstand(k.body))?.body ?? '')

type Zustand = 'erbracht' | 'ziel' | 'vorgesehen' | 'nicht-vorgesehen'

/** Zustandstext und Symbol je Zustand — lesbar ohne Farbe (E6). */
const ZUSTAND: Record<Zustand, { text: string; symbol: ReactNode }> = {
  erbracht: { text: 'vor dem Lauf erbracht', symbol: <CheckCircleOutlineIcon fontSize="small" /> },
  ziel: { text: 'Ziel', symbol: <FlagIcon fontSize="small" /> },
  vorgesehen: { text: 'vorgesehen', symbol: <RadioButtonUncheckedIcon fontSize="small" /> },
  'nicht-vorgesehen': { text: 'nicht vorgesehen', symbol: <RemoveCircleOutlineIcon fontSize="small" /> },
}

/** Das Symbol je Stationszustand während und nach dem Lauf (Issue #1453, E6). */
const STATIONSSYMBOL: Record<Stationssymbol, ReactNode> = {
  erledigt: <CheckCircleIcon fontSize="small" />,
  'ziel-erreicht': <TaskAltIcon fontSize="small" />,
  laeuft: <PlayCircleOutlineIcon fontSize="small" />,
  wartet: <PauseCircleOutlineIcon fontSize="small" />,
  projektgrenze: <BlockIcon fontSize="small" />,
  abgebrochen: <HighlightOffIcon fontSize="small" />,
  'steht-aus': <RadioButtonUncheckedIcon fontSize="small" />,
  'nicht-vorgesehen': <RemoveCircleOutlineIcon fontSize="small" />,
  erbracht: <CheckCircleOutlineIcon fontSize="small" />,
}

/** Der Ladezustand des Kettenstands: Bis er da ist, gilt der Stand aus Gruppe A. */
type Ladung =
  | { art: 'laden' }
  | { art: 'geladen'; stand: KettenStand }
  | { art: 'fehler'; meldung: string }

const index = (schluessel: Station) => STATIONEN.findIndex((s) => s.schluessel === schluessel)

/**
 * Die Stufenleiste an `[Fachlich]`- und `[Plan]`-Karten (Issue #1449, Plan #1447): Ziel der
 * Nacht-Kette und Prüferzahl wählen, statt die Labels des Kit-Vertrags von Hand zu setzen.
 *
 * <p><b>Die Leiste lässt keine Einstellung zu, die das Kit ablehnen würde</b> (E14): höchstens ein
 * `ziel:*` und ein `planreview:*`, an einem Plan weder `ziel:plan` noch Prüferschalter, und mit
 * `kit:durchziehen` nur die Ziele ab „Umsetzung“ — das Label gilt als Ziel „Umsetzung“ (E10).
 *
 * <p>Gespeichert wird über `onChange` mit der vollen Label-Liste der Karte, dem Aufruf für
 * `PUT /api/cards/{id}/labels`, den die Label-Sektion schon nutzt. Mit `disabled` ist die Leiste
 * reine Anzeige ohne Knöpfe (E3).
 *
 * <p><b>Start</b> (Issue #1450): „Kette starten“ setzt `kit:night`, ab „Umsetzung“ erst nach
 * Bestätigung im Dialog (E7); „Start zurücknehmen“ nimmt nur `kit:night` ab (E10). Solange die
 * Kette gestartet ist, ruhen Ziel- und Prüferwahl — ein späterer Zielwechsel umginge die
 * Bestätigung. Hat ein Runner übernommen (E15), ist die Leiste nur noch Anzeige.
 *
 * <p><b>Kettenstand</b> (Issue #1453): Beim Öffnen lädt die Leiste einmal
 * `GET /api/cards/{cardId}/night-chain`, ohne Nachladen. Ob ein Runner übernommen hat, sagt dann
 * der Endpunkt; nach der Übernahme zeigt die Leiste je Station Symbol, Zustandstext und Grund, wie
 * der Server sie liefert (E5). Vor der Übernahme sperrt `planReviewVorhanden` den Prüferschalter
 * (E14). Lädt der Stand nicht, bleibt die Leiste beim Stand vor dem Lauf und nennt den Fehler.
 */
export function KettenStufenleiste({
  titel,
  labelIds,
  boardLabels,
  disabled,
  beschreibung,
  kommentare,
  onChange,
  cardId,
  api = nightRunsApi,
}: Readonly<{
  titel: string
  labelIds: readonly number[]
  boardLabels: readonly Label[]
  disabled: boolean
  /** Der Body der Karte — an einem Plan steht dort die Zeile `Plan-Review:`. */
  beschreibung: string
  /** Die Kommentare der Karte, der jüngste zuerst — darunter der Laufstand des Runners. */
  kommentare: readonly { body: string }[]
  onChange: (ids: number[]) => void
  /** Die interne ID der Karte, für den Kettenstand. */
  cardId: number
  api?: Pick<NightRunsApi, 'kettenstand'>
}>) {
  const ladung = useKettenstand(api, cardId)
  const stand = ladung.art === 'geladen' ? ladung.stand : null

  const istPlan = titel.startsWith('[Plan]')
  const nameVon = (id: number) => boardLabels.find((l) => l.id === id)?.name
  const idVon = (name: string) => boardLabels.find((l) => l.name === name)?.id
  const gesetzt = new Set(labelIds.map(nameVon))
  const durchziehen = gesetzt.has(DURCHZIEHEN)
  const gestartet = gesetzt.has(NACHT)
  const uebernommen = stand === null ? uebernommenVon(kommentare, gestartet) : stand.uebernommen
  const planReviewDa = !istPlan && stand?.planReviewVorhanden === true
  // Ziel und Prüferzahl sind nur vor dem Start wählbar.
  const anzeige = disabled || uebernommen || gestartet

  // Das wirksame Ziel: das gesetzte Label, an einem Plan nie `ziel:plan`, ohne Label die Vorgabe
  // „Arbeitspakete“ — und mit `kit:durchziehen` mindestens „Umsetzung“ (Kit A3).
  const gesetztesZiel = STATIONEN.find(
    (s) => s.ziel !== null && gesetzt.has(s.ziel) && !(istPlan && s.schluessel === 'plan'),
  )?.schluessel
  const untergrenze: Station = durchziehen ? 'umsetzung' : 'pakete'
  const ziel: Station =
    gesetztesZiel !== undefined && !(durchziehen && index(gesetztesZiel) < index(untergrenze))
      ? gesetztesZiel
      : untergrenze

  const zustand = (schluessel: Station): Zustand => {
    if (schluessel === 'fachplan' || (istPlan && (schluessel === 'plan' || schluessel === 'pruefung'))) {
      return 'erbracht'
    }
    if (schluessel === ziel) return 'ziel'
    return index(schluessel) < index(ziel) ? 'vorgesehen' : 'nicht-vorgesehen'
  }

  /** Der Grund, warum eine Station nicht wählbar ist, oder `null`. */
  const sperre = (schluessel: Station, zielLabel: string | null): string | null => {
    if (istPlan && (schluessel === 'plan' || schluessel === 'pruefung')) return HINWEIS.planKarte
    if (zielLabel === null) return null
    if (durchziehen && index(schluessel) < index('umsetzung')) return HINWEIS.durchziehen
    if (idVon(zielLabel) === undefined) return HINWEIS.labelFehlt(zielLabel)
    return null
  }

  /** Der Grund, warum ein Prüferknopf gesperrt ist, oder `null` (E8, E14). */
  const prueferSperre = (p: string): string | null => {
    if (planReviewDa) return HINWEIS.planReviewDa
    return idVon(p) === undefined ? HINWEIS.labelFehlt(p) : null
  }

  /**
   * Tauscht alle Labels einer Familie gegen das eine gewählte; fremde Labels bleiben stehen. Die
   * Aufrufer bieten nur Labels an, die das Board führt (E8).
   */
  const tausche = (praefix: string, neu: string) =>
    onChange([
      ...labelIds.filter((id) => !nameVon(id)?.startsWith(praefix)),
      ...boardLabels.filter((l) => l.name === neu).map((l) => l.id),
    ])

  const prueferGesetzt = PRUEFER.find((p) => gesetzt.has(p))

  /** Was vor dem Start fehlt — leer heißt startbereit. */
  const sperrgruende = [
    ...(!istPlan && !gesetzt.has(REVIEW_FERTIG) ? [HINWEIS.fachlichOhneReview] : []),
    ...(istPlan && !/^Plan-Review:/m.test(beschreibung) ? [HINWEIS.planOhneReview] : []),
    ...OFFENE_FRAGE.filter((name) => gesetzt.has(name)).map(HINWEIS.offeneFrage),
    ...(idVon(NACHT) === undefined ? [HINWEIS.labelFehlt(NACHT)] : []),
  ]

  if (stand?.uebernommen === true) {
    return <LaufAnsicht stand={stand} istPlan={istPlan} />
  }

  return (
    <Box data-testid="ketten-stufenleiste">
      <Typography variant="subtitle2" gutterBottom>
        Nacht-Kette
      </Typography>
      {ladung.art === 'fehler' && (
        <Typography variant="body2" role="status" sx={{ mb: 1 }}>
          {HINWEIS.ladefehler(ladung.meldung)}
        </Typography>
      )}
      <Box
        component="ol"
        {...(anzeige ? {} : { role: 'group', 'aria-label': 'Ziel der Kette', onKeyDown: pfeilNavigation })}
        sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, listStyle: 'none', m: 0, p: 0 }}
      >
        {STATIONEN.map(({ schluessel, name, ziel: zielLabel }) => {
          const z = ZUSTAND[zustand(schluessel)]
          const grund = sperre(schluessel, zielLabel)
          const inhalt = (
            <>
              <Box component="span" aria-hidden data-testid="stationssymbol" sx={{ display: 'flex' }}>
                {z.symbol}
              </Box>
              <Box component="span" sx={{ fontWeight: 600 }}>{name}</Box>
              <Box component="span" sx={{ fontSize: 12 }}>{z.text}</Box>
              {grund !== null && (
                <Box component="span" sx={{ fontSize: 12 }}>nicht wählbar</Box>
              )}
            </>
          )
          const waehlbar = !anzeige && zielLabel !== null
          return (
            <Box
              component="li"
              key={schluessel}
              data-testid={`station-${schluessel}`}
              aria-disabled={grund === null ? undefined : true}
              sx={{ flex: '1 1 0', minWidth: 110, display: 'flex' }}
            >
              {waehlbar ? (
                <Tooltip title={grund ?? ''} describeChild>
                  <ButtonBase
                    aria-pressed={schluessel === ziel}
                    aria-disabled={grund === null ? undefined : true}
                    onClick={() => {
                      if (grund === null && schluessel !== ziel) tausche(ZIEL_PRAEFIX, zielLabel)
                    }}
                    sx={knopfSx(schluessel === ziel, grund !== null)}
                  >
                    {inhalt}
                  </ButtonBase>
                </Tooltip>
              ) : (
                <Box sx={stationSx(schluessel === ziel, grund !== null)}>{inhalt}</Box>
              )}
            </Box>
          )
        })}
      </Box>
      {!istPlan && (
        <PrueferSchalter
          anzeige={anzeige}
          prueferGesetzt={prueferGesetzt}
          planReviewDa={planReviewDa}
          sperre={prueferSperre}
          onWahl={(p) => tausche(PRUEFER_PRAEFIX, p)}
        />
      )}
      <StartBereich
        disabled={disabled}
        gestartet={gestartet}
        uebernommen={uebernommen}
        sperrgruende={sperrgruende}
        goNoetig={index(ziel) >= index('umsetzung')}
        onStart={() => onChange([...labelIds, ...boardLabels.filter((l) => l.name === NACHT).map((l) => l.id)])}
        onRuecknahme={() => onChange(labelIds.filter((id) => nameVon(id) !== NACHT))}
      />
    </Box>
  )
}

/**
 * Der Schalter für ein oder zwei Planprüfer an `[Fachlich]` (Issue #1449); gesperrt, solange ein
 * Label fehlt oder der Plan schon `Plan-Review:` trägt (Issue #1453, E14). Als Anzeige nur die Zahl.
 */
function PrueferSchalter({
  anzeige,
  prueferGesetzt,
  planReviewDa,
  sperre,
  onWahl,
}: Readonly<{
  anzeige: boolean
  prueferGesetzt: string | undefined
  planReviewDa: boolean
  sperre: (p: string) => string | null
  onWahl: (p: string) => void
}>) {
  return (
    <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
      <Typography variant="body2" color="text.secondary">
        Planprüfung:
      </Typography>
      {anzeige ? (
        <Typography variant="body2">
          {prueferGesetzt === undefined ? 'Vorgabe des Projekts' : `${prueferGesetzt.slice(PRUEFER_PRAEFIX.length)} Prüfer`}
        </Typography>
      ) : (
        <>
          <Box
            role="group"
            aria-label="Planprüfung"
            onKeyDown={pfeilNavigation}
            sx={{ display: 'flex', gap: 1 }}
          >
            {PRUEFER.map((p) => {
              const grund = sperre(p)
              const gewaehlt = prueferGesetzt === p
              return (
                <Tooltip key={p} title={grund ?? ''} describeChild>
                  <ButtonBase
                    aria-pressed={gewaehlt}
                    aria-disabled={grund === null ? undefined : true}
                    onClick={() => {
                      if (grund === null && !gewaehlt) onWahl(p)
                    }}
                    sx={knopfSx(gewaehlt, grund !== null)}
                  >
                    {`${p.slice(PRUEFER_PRAEFIX.length)} Prüfer`}
                  </ButtonBase>
                </Tooltip>
              )
            })}
          </Box>
          {prueferGesetzt === undefined && !planReviewDa && (
            <Typography variant="body2" color="text.secondary">
              ohne Wahl gilt die Vorgabe des Projekts
            </Typography>
          )}
          {planReviewDa && (
            <Typography variant="body2" color="text.secondary">
              {HINWEIS.planReviewDa}
            </Typography>
          )}
        </>
      )}
    </Box>
  )
}

/** Lädt den Kettenstand einer Karte einmal; eine Antwort nach dem Schließen verfällt. */
function useKettenstand(api: Pick<NightRunsApi, 'kettenstand'>, cardId: number): Ladung {
  const [ladung, setLadung] = useState<Ladung>({ art: 'laden' })
  useEffect(() => {
    let aktiv = true
    setLadung({ art: 'laden' })
    api.kettenstand(cardId).then(
      (stand) => {
        if (aktiv) setLadung({ art: 'geladen', stand })
      },
      (error: unknown) => {
        if (aktiv) setLadung({ art: 'fehler', meldung: apiErrorMessage(error, 'unbekannter Fehler') })
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, cardId])
  return ladung
}

/**
 * Die Leiste einer übernommenen Kette (Issue #1453): nur Anzeige. Je Station Symbol, Name,
 * Zustandstext und Grund, wie der Server sie liefert; die aktuelle Station trägt
 * `aria-current="step"` wie die Wegleiste in `NachtlaufFortschritt`, das Ziel bleibt mit Kupferrand
 * und dem Wort „Ziel“ markiert.
 */
function LaufAnsicht({ stand, istPlan }: Readonly<{ stand: KettenStand; istPlan: boolean }>) {
  return (
    <Box data-testid="ketten-stufenleiste">
      <Typography variant="subtitle2" gutterBottom>
        Nacht-Kette
      </Typography>
      <Box
        component="ol"
        aria-label="Stand der Kette"
        sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, listStyle: 'none', m: 0, p: 0 }}
      >
        {kettenAnzeige(stand).map((a) => (
          <Box
            component="li"
            key={a.station}
            data-testid={`kette-station-${a.station}`}
            aria-current={a.aktuell ? 'step' : undefined}
            sx={{ flex: '1 1 0', minWidth: 110, display: 'flex' }}
          >
            <Box sx={stationSx(a.ziel, false)}>
              <Box component="span" aria-hidden data-testid={`stationssymbol-${a.symbol}`} sx={{ display: 'flex' }}>
                {STATIONSSYMBOL[a.symbol]}
              </Box>
              <Box component="span" sx={{ fontWeight: 600 }}>{a.name}</Box>
              <Box component="span" sx={{ fontSize: 12 }}>{a.text}</Box>
              {a.grund !== null && (
                <Box component="span" sx={{ fontSize: 12 }}>{a.grund}</Box>
              )}
              {a.ziel && (
                <Box component="span" sx={{ fontSize: 12, fontWeight: 600 }}>Ziel</Box>
              )}
            </Box>
          </Box>
        ))}
      </Box>
      {!istPlan && (
        <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Planprüfung:
          </Typography>
          <Typography variant="body2">
            {stand.pruefer === null ? 'Vorgabe des Projekts' : `${stand.pruefer} Prüfer`}
          </Typography>
        </Box>
      )}
      <Typography variant="body2" sx={{ mt: 1 }}>
        {HINWEIS.uebernommen}
      </Typography>
    </Box>
  )
}

/**
 * Start und Rücknahme der Kette (Issue #1450): der Übernahme- und Wartehinweis, „Start
 * zurücknehmen“, und vor dem Start der GO-Satz, „Kette starten“ mit seinen Sperrgründen und die
 * Bestätigung ab „Umsetzung“ (E7).
 */
function StartBereich({
  disabled,
  gestartet,
  uebernommen,
  sperrgruende,
  goNoetig,
  onStart,
  onRuecknahme,
}: Readonly<{
  disabled: boolean
  gestartet: boolean
  uebernommen: boolean
  sperrgruende: readonly string[]
  goNoetig: boolean
  onStart: () => void
  onRuecknahme: () => void
}>) {
  const [fragt, setFragt] = useState(false)
  const gruendeId = useId()
  const dialogTitelId = useId()
  const dialogTextId = useId()
  const gesperrt = sperrgruende.length > 0
  const starte = () => {
    setFragt(false)
    onStart()
  }

  return (
    <>
      {uebernommen && (
        <Typography variant="body2" sx={{ mt: 1 }}>
          {HINWEIS.uebernommen}
        </Typography>
      )}
      {gestartet && (
        <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Typography variant="body2">{HINWEIS.wartet}</Typography>
          {!disabled && (
            <Button variant="outlined" size="small" onClick={onRuecknahme}>
              Start zurücknehmen
            </Button>
          )}
        </Box>
      )}
      {!(disabled || uebernommen || gestartet) && (
        <Box sx={{ mt: 1 }}>
          {goNoetig && (
            <Typography variant="body2" gutterBottom>
              {HINWEIS.go}
            </Typography>
          )}
          <Button
            variant={gesperrt ? 'outlined' : 'contained'}
            size="small"
            aria-disabled={gesperrt ? true : undefined}
            aria-describedby={gesperrt ? gruendeId : undefined}
            onClick={() => {
              if (gesperrt) return
              if (goNoetig) setFragt(true)
              else starte()
            }}
            sx={gesperrt ? { color: TEXT_SCHWACH, cursor: 'not-allowed' } : undefined}
          >
            Kette starten
          </Button>
          {gesperrt && (
            <Box component="ul" id={gruendeId} sx={{ m: 0, mt: 0.5, pl: 2.5 }}>
              {sperrgruende.map((g) => (
                <Typography component="li" variant="body2" color="text.secondary" key={g}>
                  {g}
                </Typography>
              ))}
            </Box>
          )}
          <Dialog
            open={fragt}
            onClose={() => setFragt(false)}
            maxWidth="xs"
            fullWidth
            aria-labelledby={dialogTitelId}
            aria-describedby={dialogTextId}
          >
            <DialogTitle id={dialogTitelId} sx={dialogTitleSx}>
              Kette starten?
            </DialogTitle>
            <DialogContent>
              <DialogContentText id={dialogTextId} sx={{ mt: 2 }}>
                {HINWEIS.bestaetigung}
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              {/* Der Fokus liegt zuerst auf „Abbrechen“: Ein Enter ohne Lesen gibt kein GO (E7). */}
              <Button onClick={() => setFragt(false)} autoFocus>
                Abbrechen
              </Button>
              <Button variant="contained" onClick={starte}>
                Kette starten
              </Button>
            </DialogActions>
          </Dialog>
        </Box>
      )}
    </>
  )
}

/**
 * Pfeiltasten wandern durch die bedienbaren Knöpfe einer Gruppe, am Ende wieder an den Anfang;
 * gesperrte (`aria-disabled`) werden übersprungen. Die Leertaste wählt wie bei jedem Knopf (E6).
 */
function pfeilNavigation(event: KeyboardEvent<HTMLElement>) {
  const schritt = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key]
  if (schritt === undefined) return
  const knoepfe = Array.from(
    event.currentTarget.querySelectorAll<HTMLElement>('button:not([aria-disabled="true"])'),
  )
  const jetzt = knoepfe.indexOf(document.activeElement as HTMLElement)
  event.preventDefault()
  knoepfe[(jetzt + schritt + knoepfe.length) % knoepfe.length]?.focus()
}

/** Die Fläche einer Station: markiert mit Kupferrand, gesperrt in schwacher Schrift. */
function stationSx(markiert: boolean, gesperrt: boolean) {
  return {
    ...NUT_SX,
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    gap: 0.25,
    p: 1,
    borderRadius: `${KLEIN_RADIUS}px`,
    textAlign: 'left',
    ...(markiert ? { borderColor: KUPFER, boxShadow: `inset 0 0 0 1px ${KUPFER}` } : {}),
    ...(gesperrt ? { color: TEXT_SCHWACH } : {}),
  } as const
}

/** Ein Knopf der Leiste: erhabene Taste, gewählt eingelassen mit Kupferrand. */
function knopfSx(gewaehlt: boolean, gesperrt: boolean) {
  return {
    ...stationSx(gewaehlt, gesperrt),
    ...(gewaehlt ? {} : TASTE_SX),
    ...(gesperrt ? { color: TEXT_SCHWACH } : {}),
    font: 'inherit',
    px: 1.5,
    py: 1,
    cursor: gesperrt ? 'not-allowed' : 'pointer',
    '&:focus-visible': { outline: `2px solid ${KUPFER}`, outlineOffset: 2 },
  } as const
}
