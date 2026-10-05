import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import FlagIcon from '@mui/icons-material/Flag'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { KeyboardEvent, ReactNode } from 'react'
import type { Label } from '../../api/labels'
import { KLEIN_RADIUS, KUPFER, NUT_SX, TASTE_SX, TEXT_SCHWACH } from '../../theme'

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

/**
 * Die feste Texttabelle der Sperrhinweise (E8, E14). Die Leiste legt nie ein Label an: Fehlt eins,
 * nennt der Hinweis seinen Namen, und ein Mensch legt es am Board an.
 */
const HINWEIS = {
  labelFehlt: (name: string) =>
    `Am Board fehlt das Label „${name}“. Die Leiste legt es nicht an — ein Mensch legt es in den Board-Labels an.`,
  durchziehen: `Die Karte trägt ${DURCHZIEHEN} und läuft damit mindestens bis zur Umsetzung; ein kürzeres Ziel bliebe wirkungslos.`,
  planKarte: 'Die Karte ist schon ein Plan: Plan und Prüfung sind vor dem Lauf erbracht.',
} as const

type Zustand = 'erbracht' | 'ziel' | 'vorgesehen' | 'nicht-vorgesehen'

/** Zustandstext und Symbol je Zustand — lesbar ohne Farbe (E6). */
const ZUSTAND: Record<Zustand, { text: string; symbol: ReactNode }> = {
  erbracht: { text: 'vor dem Lauf erbracht', symbol: <CheckCircleOutlineIcon fontSize="small" /> },
  ziel: { text: 'Ziel', symbol: <FlagIcon fontSize="small" /> },
  vorgesehen: { text: 'vorgesehen', symbol: <RadioButtonUncheckedIcon fontSize="small" /> },
  'nicht-vorgesehen': { text: 'nicht vorgesehen', symbol: <RemoveCircleOutlineIcon fontSize="small" /> },
}

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
 */
export function KettenStufenleiste({
  titel,
  labelIds,
  boardLabels,
  disabled,
  onChange,
}: Readonly<{
  titel: string
  labelIds: readonly number[]
  boardLabels: readonly Label[]
  disabled: boolean
  onChange: (ids: number[]) => void
}>) {
  const istPlan = titel.startsWith('[Plan]')
  const nameVon = (id: number) => boardLabels.find((l) => l.id === id)?.name
  const idVon = (name: string) => boardLabels.find((l) => l.name === name)?.id
  const gesetzt = new Set(labelIds.map(nameVon))
  const durchziehen = gesetzt.has(DURCHZIEHEN)

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

  return (
    <Box data-testid="ketten-stufenleiste">
      <Typography variant="subtitle2" gutterBottom>
        Nacht-Kette
      </Typography>
      <Box
        component="ol"
        {...(disabled ? {} : { role: 'group', 'aria-label': 'Ziel der Kette', onKeyDown: pfeilNavigation })}
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
          const waehlbar = !disabled && zielLabel !== null
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
        <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Typography variant="body2" color="text.secondary">
            Planprüfung:
          </Typography>
          {disabled ? (
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
                  const grund = idVon(p) === undefined ? HINWEIS.labelFehlt(p) : null
                  const gewaehlt = prueferGesetzt === p
                  return (
                    <Tooltip key={p} title={grund ?? ''} describeChild>
                      <ButtonBase
                        aria-pressed={gewaehlt}
                        aria-disabled={grund === null ? undefined : true}
                        onClick={() => {
                          if (grund === null && !gewaehlt) tausche(PRUEFER_PRAEFIX, p)
                        }}
                        sx={knopfSx(gewaehlt, grund !== null)}
                      >
                        {`${p.slice(PRUEFER_PRAEFIX.length)} Prüfer`}
                      </ButtonBase>
                    </Tooltip>
                  )
                })}
              </Box>
              {prueferGesetzt === undefined && (
                <Typography variant="body2" color="text.secondary">
                  ohne Wahl gilt die Vorgabe des Projekts
                </Typography>
              )}
            </>
          )}
        </Box>
      )}
    </Box>
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
