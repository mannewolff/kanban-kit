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
import CircularProgress from '@mui/material/CircularProgress'
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
import { artAusTitel, boardName, namenDerSache, type KitNamen, type KitNamenZustand } from '../../api/kitNamen'
import type { Label } from '../../api/labels'
import { nightRunsApi, type KettenStand, type KettenStation, type NightRunsApi } from '../../api/nightRuns'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { LAUFSTAND, PLAN_REVIEW } from '../../lib/kitWortformen'
import { kettenAnzeige, stationsnamen, type Stationssymbol } from '../../lib/laufFortschritt'
import { KLEIN_RADIUS, KUPFER, NUT_SX, TASTE_SX, TEXT_SCHWACH } from '../../theme'
import { dialogTitleSx } from '../dialogChromeSx'

/**
 * Ob eine Karte die Stufenleiste trägt: ihr Titel-Präfix nennt die Kartenart `FACHLICH` oder `PLAN`
 * — `[Fachlich]`, `[Requirement]` oder `[Plan]` (E1; Issue #1595, Plan #1582 E2).
 */
export function traegtStufenleiste(daten: KitNamen, titel: string): boolean {
  const art = artAusTitel(daten, titel)
  return art === 'FACHLICH' || art === 'PLAN'
}

type Station = 'plan' | 'pruefung' | 'pakete' | 'abdeckung' | 'umsetzung' | 'push-vorbereitet'

/**
 * Die sechs Stationen der Kette in ihrer Reihenfolge — dieselben wie während des Laufs und in
 * „Heute Nacht“ (Fachplan #1420, Issue #1472); `ziel` trägt die Sache des Labels der vier wählbaren
 * (Kit-Vertrag, E10). Prüfung und Abdeckung sind Stationen auf dem Weg, aber kein Ziel. Die Namen
 * der Sachen kommen vom Server (Issue #1595, Plan #1582 A4); der angezeigte Name einer Station ist
 * der ihrer `KettenStation` aus dem Wörterbuch (Issue #1658).
 */
const STATIONEN: ReadonlyArray<{ schluessel: Station; station: KettenStation; ziel: string | null }> = [
  { schluessel: 'plan', station: 'PLAN', ziel: 'ZIEL_PLAN' },
  { schluessel: 'pruefung', station: 'REVIEW', ziel: null },
  { schluessel: 'pakete', station: 'PAKETE', ziel: 'ZIEL_PAKETE' },
  { schluessel: 'abdeckung', station: 'ABDECKUNG', ziel: null },
  { schluessel: 'umsetzung', station: 'UMSETZUNG', ziel: 'ZIEL_UMSETZUNG' },
  { schluessel: 'push-vorbereitet', station: 'VORBEREITUNG', ziel: 'ZIEL_PUSH_VORBEREITET' },
]

const ZIEL_SACHEN = STATIONEN.flatMap((s) => (s.ziel === null ? [] : [s.ziel]))
/** Die Prüfer-Sachen mit ihrer Zahl — die Anzeige entsteht aus der Sache, nicht aus dem Namen. */
const PRUEFER = [
  { sache: 'PLANREVIEW_1', zahl: 1 },
  { sache: 'PLANREVIEW_2', zahl: 2 },
] as const
const PRUEFER_SACHEN = PRUEFER.map((p) => p.sache)
type PrueferSache = (typeof PRUEFER)[number]['sache']
const PRUEFER_ZAHL = Object.fromEntries(PRUEFER.map((p) => [p.sache, p.zahl])) as Record<PrueferSache, number>
/** Das eine Start-Label des Kit-Vertrags (E10): gesetzt heißt freigegeben, noch nicht übernommen. */
const NACHT = 'NIGHT'
/** Sachen, mit denen an der Karte eine offene Frage auf einen Menschen wartet. */
const OFFENE_FRAGE = ['KLAEREN', 'LAUF_WARTET'] as const
const PLAN_REVIEW_ZEILE = new RegExp(`^${PLAN_REVIEW}`, 'm')
/** Die Lauf-ID ist der Stempel des Runners, etwa `2026-10-05-175710` im Protokollpfad. */
const LAUF_ID = /\b\d{4}-\d{2}-\d{2}-\d{6}\b/

/*
 * Die Sperrhinweise (E8, E14) stehen im Wörterbuch (`NACHT_KETTE`, Issue #1658). Die Leiste legt nie
 * ein Label an: Fehlt eins, nennt der Hinweis seinen Namen, und ein Mensch legt es am Board an.
 */

/**
 * Ob ein Kommentar der Laufstand des Runners ist: seine erste Zeile ist der Anker in einer der
 * beiden Sprachen, wie im Kit.
 */
const istLaufstand = (body: string) =>
  (LAUFSTAND.kopf as readonly string[]).includes(body.replaceAll('\r', '').trimStart().split('\n')[0].trim())

/**
 * Ob ein Runner die Karte übernommen hat — die vorläufige Erkennung aus Gruppe A (E15): Ihr
 * jüngster Laufstand trägt eine Lauf-ID, und `kit:night` ist abgenommen. Sie gilt nur noch, solange
 * der Kettenstand lädt oder nicht geladen werden konnte (Issue #1453); sonst entscheidet der
 * Endpunkt. Die Kommentare kommen wie im Modal, der jüngste zuerst.
 */
const uebernommenVon = (kommentare: readonly { body: string }[], gestartet: boolean) =>
  !gestartet && LAUF_ID.test(kommentare.find((k) => istLaufstand(k.body))?.body ?? '')

type Zustand = 'erbracht' | 'ziel' | 'vorgesehen' | 'nicht-vorgesehen'

/** Wörterbuchschlüssel des Zustandstexts und Symbol je Zustand — lesbar ohne Farbe (E6). */
const ZUSTAND: Record<Zustand, { text: 'ERBRACHT' | 'ZIEL' | 'VORGESEHEN' | 'NICHT_VORGESEHEN'; symbol: ReactNode }> = {
  erbracht: { text: 'ERBRACHT', symbol: <CheckCircleOutlineIcon fontSize="small" /> },
  ziel: { text: 'ZIEL', symbol: <FlagIcon fontSize="small" /> },
  vorgesehen: { text: 'VORGESEHEN', symbol: <RadioButtonUncheckedIcon fontSize="small" /> },
  'nicht-vorgesehen': { text: 'NICHT_VORGESEHEN', symbol: <RemoveCircleOutlineIcon fontSize="small" /> },
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
 * <p>Gespeichert wird über `onChange` mit genau der Änderung eines Klicks — abzunehmende und zu
 * setzende Labels —, nie mit der vollen Liste: Ein veralteter Stand der Seite schriebe sonst ein
 * vom Runner verbrauchtes `kit:night` zurück (Issue #1512). Mit `disabled` ist die Leiste reine
 * Anzeige ohne Knöpfe (E3).
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
 *
 * <p><b>Namen des Kits</b> (Issue #1595, Plan #1582 E9, E10): Labels, Titel-Präfixe und Laufstand
 * liest die Leiste über die Sachen des Registers aus `useKitNamen` — ein englischer Name wirkt wie
 * sein deutsches Gegenstück. Gesetzt wird der Name, den das Board trägt, bei beiden der deutsche.
 * Solange das Register lädt, zeigt sie den Ladezustand, scheitert es, den Hinweis „Kit-Namen nicht
 * ladbar“ statt der Leiste.
 *
 * <p><b>Sprache</b> (Issue #1658, Plan #1638 A11): Nennt ein Hinweis ein Kit-Label, heißt es bei
 * Englisch nach seinem englischen Namen aus dem Register (dem letzten der Sache), bei Deutsch wie
 * bisher nach {@link boardName} bzw. dem Namen an der Karte. Gesetzt wird unverändert der Name, den
 * das Board trägt.
 */
/** Die Änderung eines Klicks: Label-IDs, die abgenommen, und solche, die gesetzt werden. */
export type LabelAenderung = Readonly<{ ab: number[]; an: number[] }>

type LeisteProps = Readonly<{
  titel: string
  labelIds: readonly number[]
  boardLabels: readonly Label[]
  disabled: boolean
  /** Der Body der Karte — an einem Plan steht dort die Zeile `Plan-Review:`. */
  beschreibung: string
  /** Die Kommentare der Karte, der jüngste zuerst — darunter der Laufstand des Runners. */
  kommentare: readonly { body: string }[]
  onChange: (aenderung: LabelAenderung) => void
  /** Die interne ID der Karte, für den Kettenstand. */
  cardId: number
  api?: Pick<NightRunsApi, 'kettenstand'>
}>

export function KettenStufenleiste({
  kitNamen,
  ...props
}: LeisteProps & Readonly<{ /** Das Register aus `useKitNamen`. */ kitNamen: KitNamenZustand }>) {
  const t = useT().NACHT_KETTE
  if (kitNamen.status === 'fertig') return <Leiste daten={kitNamen.daten} {...props} />
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {t.TITEL}
      </Typography>
      {kitNamen.status === 'laden' ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
          <CircularProgress size={24} aria-label={t.KIT_NAMEN_LADEN} />
        </Box>
      ) : (
        <Typography variant="body2" role="status">
          {t.KIT_NAMEN_FEHLEN}
        </Typography>
      )}
    </Box>
  )
}

function Leiste({
  daten,
  titel,
  labelIds,
  boardLabels,
  disabled,
  beschreibung,
  kommentare,
  onChange,
  cardId,
  api = nightRunsApi,
}: LeisteProps & Readonly<{ daten: KitNamen }>) {
  const alleT = useT()
  const t = alleT.NACHT_KETTE
  const { sprache } = useSprache()
  const stationsname = stationsnamen(sprache)
  const ladung = useKettenstand(api, cardId, alleT.FEHLER_UNBEKANNT)
  const stand = ladung.art === 'geladen' ? ladung.stand : null

  const istPlan = artAusTitel(daten, titel) === 'PLAN'
  const nameVon = (id: number) => boardLabels.find((l) => l.id === id)?.name
  const idVon = (name: string) => boardLabels.find((l) => l.name === name)?.id
  /** Die IDs der Karte, deren Label einen der Namen trägt — in der Reihenfolge der Karte. */
  const idsMit = (namen: ReadonlySet<string>) =>
    labelIds.filter((id) => boardLabels.some((l) => l.id === id && namen.has(l.name)))
  const gesetzteNamen = labelIds.map(nameVon).filter((n): n is string => n !== undefined)
  /** Der Name der Sache, den die Karte trägt, oder `undefined`. */
  const getragen = (sache: string) => gesetzteNamen.find((n) => namenDerSache(daten, sache).includes(n))
  const hat = (sache: string) => getragen(sache) !== undefined
  /**
   * Der Name, unter dem die Sache an diesem Board gesetzt wird (E9); kennt das Register die Sache
   * nicht, steht ihr Schlüssel im Sperrhinweis.
   */
  const zuSetzen = (sache: string) => boardName(daten, sache, boardLabels.map((l) => l.name)) ?? sache
  /**
   * Der Name, unter dem ein Hinweis das Label der Sache nennt (A11): bei Englisch der englische des
   * Registers, bei Deutsch `deutsch` — der Name an der Karte oder der zu setzende.
   */
  const genannt = (sache: string, deutsch: string) =>
    sprache === 'en' ? (namenDerSache(daten, sache).at(-1) ?? deutsch) : deutsch
  const nennen = (sache: string) => genannt(sache, zuSetzen(sache))
  const durchziehen = getragen('DURCHZIEHEN')
  const gestartet = hat(NACHT)
  const uebernommen = stand === null ? uebernommenVon(kommentare, gestartet) : stand.uebernommen
  const planReviewDa = !istPlan && stand?.planReviewVorhanden === true
  // Ziel und Prüferzahl sind nur vor dem Start wählbar.
  const anzeige = disabled || uebernommen || gestartet

  // Das wirksame Ziel: das gesetzte Label, an einem Plan nie `ziel:plan`, ohne Label die Vorgabe
  // „Arbeitspakete“ — und mit `kit:durchziehen` mindestens „Umsetzung“ (Kit A3).
  const gesetztesZiel = STATIONEN.find(
    (s) => s.ziel !== null && hat(s.ziel) && !(istPlan && s.schluessel === 'plan'),
  )?.schluessel
  const untergrenze: Station = durchziehen === undefined ? 'pakete' : 'umsetzung'
  const ziel: Station =
    gesetztesZiel !== undefined && !(durchziehen !== undefined && index(gesetztesZiel) < index(untergrenze))
      ? gesetztesZiel
      : untergrenze

  const zustand = (schluessel: Station): Zustand => {
    if (istPlan && (schluessel === 'plan' || schluessel === 'pruefung')) return 'erbracht'
    if (schluessel === ziel) return 'ziel'
    // Das Ziel „Arbeitspakete“ schließt die Abdeckungsprüfung ein (Fachplan #1420).
    if (schluessel === 'abdeckung' && ziel === 'pakete') return 'vorgesehen'
    return index(schluessel) < index(ziel) ? 'vorgesehen' : 'nicht-vorgesehen'
  }

  /** Der Grund, warum eine Station nicht wählbar ist, oder `null`. */
  const sperre = (schluessel: Station, zielSache: string | null): string | null => {
    if (istPlan && (schluessel === 'plan' || schluessel === 'pruefung')) return t.PLAN_KARTE
    if (zielSache === null) return null
    if (durchziehen !== undefined && index(schluessel) < index('umsetzung')) {
      return t.DURCHZIEHEN(genannt('DURCHZIEHEN', durchziehen))
    }
    if (idVon(zuSetzen(zielSache)) === undefined) return t.LABEL_FEHLT(nennen(zielSache))
    return null
  }

  /** Der Grund, warum ein Prüferknopf gesperrt ist, oder `null` (E8, E14). */
  const prueferSperre = (sache: PrueferSache): string | null => {
    if (planReviewDa) return t.PLAN_REVIEW_DA(PLAN_REVIEW)
    return idVon(zuSetzen(sache)) === undefined ? t.LABEL_FEHLT(nennen(sache)) : null
  }

  /**
   * Tauscht alle Namen einer Familie von Sachen gegen den Board-Namen der gewählten (E9); fremde
   * Labels bleiben stehen. Die Aufrufer bieten nur Sachen an, deren Label das Board führt (E8).
   */
  const tausche = (familie: readonly string[], neu: string) => {
    const alt = new Set(familie.flatMap((sache) => namenDerSache(daten, sache)))
    const name = zuSetzen(neu)
    onChange({
      ab: idsMit(alt),
      an: boardLabels.filter((l) => l.name === name).map((l) => l.id),
    })
  }

  const prueferGesetzt = PRUEFER.find((p) => hat(p.sache))?.sache
  const nachtNamen = new Set(namenDerSache(daten, NACHT))

  /** Was vor dem Start fehlt — leer heißt startbereit. */
  const sperrgruende = [
    ...(!istPlan && !hat('REVIEW_FERTIG') ? [t.FACHLICH_OHNE_REVIEW(nennen('REVIEW_FERTIG'))] : []),
    ...(istPlan && !PLAN_REVIEW_ZEILE.test(beschreibung) ? [t.PLAN_OHNE_REVIEW(PLAN_REVIEW)] : []),
    ...OFFENE_FRAGE.flatMap((sache) =>
      gesetzteNamen.filter((n) => namenDerSache(daten, sache).includes(n)).map((n) => genannt(sache, n)),
    ).map(t.OFFENE_FRAGE),
    ...(idVon(zuSetzen(NACHT)) === undefined ? [t.LABEL_FEHLT(nennen(NACHT))] : []),
  ]

  if (stand?.uebernommen === true) {
    return <LaufAnsicht stand={stand} istPlan={istPlan} />
  }

  return (
    <Box data-testid="ketten-stufenleiste">
      <Typography variant="subtitle2" gutterBottom>
        {t.TITEL}
      </Typography>
      {ladung.art === 'fehler' && (
        <Typography variant="body2" role="status" sx={{ mb: 1 }}>
          {t.LADEFEHLER(ladung.meldung)}
        </Typography>
      )}
      <Box
        component="ol"
        {...(anzeige ? {} : { role: 'group', 'aria-label': t.ZIEL_DER_KETTE, onKeyDown: pfeilNavigation })}
        sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, listStyle: 'none', m: 0, p: 0 }}
      >
        {STATIONEN.map(({ schluessel, station, ziel: zielSache }) => {
          const z = ZUSTAND[zustand(schluessel)]
          const grund = sperre(schluessel, zielSache)
          const inhalt = (
            <>
              <Box component="span" aria-hidden data-testid="stationssymbol" sx={{ display: 'flex' }}>
                {z.symbol}
              </Box>
              <Box component="span" sx={{ fontWeight: 600 }}>{stationsname[station]}</Box>
              <Box component="span" sx={{ fontSize: 12 }}>{t.ZUSTAND[z.text]}</Box>
              {grund !== null && (
                <Box component="span" sx={{ fontSize: 12 }}>{t.NICHT_WAEHLBAR}</Box>
              )}
            </>
          )
          const waehlbar = !anzeige && zielSache !== null
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
                      if (grund === null && schluessel !== ziel) tausche(ZIEL_SACHEN, zielSache)
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
          onWahl={(p) => tausche(PRUEFER_SACHEN, p)}
        />
      )}
      <StartBereich
        disabled={disabled}
        gestartet={gestartet}
        uebernommen={uebernommen}
        sperrgruende={sperrgruende}
        goNoetig={index(ziel) >= index('umsetzung')}
        onStart={() => onChange({ ab: [], an: boardLabels.filter((l) => l.name === zuSetzen(NACHT)).map((l) => l.id) })}
        onRuecknahme={() => onChange({ ab: idsMit(nachtNamen), an: [] })}
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
  prueferGesetzt: PrueferSache | undefined
  planReviewDa: boolean
  sperre: (p: PrueferSache) => string | null
  onWahl: (p: PrueferSache) => void
}>) {
  const t = useT().NACHT_KETTE
  return (
    <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
      <Typography variant="body2" color="text.secondary">
        {t.PLANPRUEFUNG_ANGABE}
      </Typography>
      {anzeige ? (
        <Typography variant="body2">
          {prueferGesetzt === undefined ? t.VORGABE : t.PRUEFER(PRUEFER_ZAHL[prueferGesetzt])}
        </Typography>
      ) : (
        <>
          <Box
            role="group"
            aria-label={t.PLANPRUEFUNG}
            onKeyDown={pfeilNavigation}
            sx={{ display: 'flex', gap: 1 }}
          >
            {PRUEFER_SACHEN.map((p) => {
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
                    {t.PRUEFER(PRUEFER_ZAHL[p])}
                  </ButtonBase>
                </Tooltip>
              )
            })}
          </Box>
          {prueferGesetzt === undefined && !planReviewDa && (
            <Typography variant="body2" color="text.secondary">
              {t.OHNE_WAHL}
            </Typography>
          )}
          {planReviewDa && (
            <Typography variant="body2" color="text.secondary">
              {t.PLAN_REVIEW_DA(PLAN_REVIEW)}
            </Typography>
          )}
        </>
      )}
    </Box>
  )
}

/** Lädt den Kettenstand einer Karte einmal; eine Antwort nach dem Schließen verfällt. */
function useKettenstand(api: Pick<NightRunsApi, 'kettenstand'>, cardId: number, fallback: string): Ladung {
  const [ladung, setLadung] = useState<Ladung>({ art: 'laden' })
  useEffect(() => {
    let aktiv = true
    setLadung({ art: 'laden' })
    api.kettenstand(cardId).then(
      (stand) => {
        if (aktiv) setLadung({ art: 'geladen', stand })
      },
      (error: unknown) => {
        if (aktiv) setLadung({ art: 'fehler', meldung: apiErrorMessage(error, fallback) })
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, cardId, fallback])
  return ladung
}

/**
 * Die Leiste einer übernommenen Kette (Issue #1453): nur Anzeige. Je Station Symbol, Name,
 * Zustandstext und Grund, wie der Server sie liefert; die aktuelle Station trägt
 * `aria-current="step"` wie die Wegleiste in `NachtlaufFortschritt`, das Ziel bleibt mit Kupferrand
 * und dem Wort „Ziel“ markiert.
 */
function LaufAnsicht({ stand, istPlan }: Readonly<{ stand: KettenStand; istPlan: boolean }>) {
  const t = useT().NACHT_KETTE
  const { sprache } = useSprache()
  return (
    <Box data-testid="ketten-stufenleiste">
      <Typography variant="subtitle2" gutterBottom>
        {t.TITEL}
      </Typography>
      <Box
        component="ol"
        aria-label={t.STAND_DER_KETTE}
        sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, listStyle: 'none', m: 0, p: 0 }}
      >
        {kettenAnzeige(stand, sprache).map((a) => (
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
                <Box component="span" sx={{ fontSize: 12, fontWeight: 600 }}>{t.ZUSTAND.ZIEL}</Box>
              )}
            </Box>
          </Box>
        ))}
      </Box>
      {!istPlan && (
        <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {t.PLANPRUEFUNG_ANGABE}
          </Typography>
          <Typography variant="body2">
            {stand.pruefer === null ? t.VORGABE : t.PRUEFER(stand.pruefer)}
          </Typography>
        </Box>
      )}
      <Typography variant="body2" sx={{ mt: 1 }}>
        {t.UEBERNOMMEN}
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
  const alleT = useT()
  const t = alleT.NACHT_KETTE
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
          {t.UEBERNOMMEN}
        </Typography>
      )}
      {gestartet && (
        <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Typography variant="body2">{t.WARTET}</Typography>
          {!disabled && (
            <Button variant="outlined" size="small" onClick={onRuecknahme}>
              {t.START_ZURUECKNEHMEN}
            </Button>
          )}
        </Box>
      )}
      {!(disabled || uebernommen || gestartet) && (
        <Box sx={{ mt: 1 }}>
          {goNoetig && (
            <Typography variant="body2" gutterBottom>
              {t.GO}
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
            {t.STARTEN}
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
              {t.STARTEN_FRAGE}
            </DialogTitle>
            <DialogContent>
              <DialogContentText id={dialogTextId} sx={{ mt: 2 }}>
                {t.BESTAETIGUNG}
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              {/* Der Fokus liegt zuerst auf „Abbrechen“: Ein Enter ohne Lesen gibt kein GO (E7). */}
              <Button onClick={() => setFragt(false)} autoFocus>
                {alleT.ALLGEMEIN.ABBRECHEN}
              </Button>
              <Button variant="contained" onClick={starte}>
                {t.STARTEN}
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
