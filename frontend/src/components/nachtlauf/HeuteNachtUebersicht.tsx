import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import FlagIcon from '@mui/icons-material/Flag'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import { useEffect, useId, useState, type ReactNode } from 'react'
import { ApiError, apiErrorMessage } from '../../api/client'
import {
  nightRunsApi,
  type HeuteNachtKarte,
  type KettenStation,
  type NightRunsApi,
} from '../../api/nightRuns'
import { STATIONSNAME } from '../../lib/laufFortschritt'
import {
  ANZEIGE,
  KLEIN_RADIUS,
  KUPFER,
  NUT_SX,
  PANEL_RADIUS,
  PLATTE,
  RAND,
  SCHATTEN_PLATTE,
  TEXT_MATT,
  TEXT_SCHWACH,
  ZAHL,
} from '../../theme'

/** Der Satz für den leeren Fall (Fachplan #1420: „sagt das ausdrücklich, statt leer zu bleiben“). */
export const LEER_SATZ = 'Für die nächste Nacht ist keine Karte freigegeben.'

/** Die Stationen der Kette in ihrer Reihenfolge, wie die Stufenleiste der Karte sie zeigt. */
const STATIONEN: readonly KettenStation[] = ['PLAN', 'REVIEW', 'PAKETE', 'ABDECKUNG', 'UMSETZUNG', 'VORBEREITUNG']

type Zustand = 'erbracht' | 'vorgesehen' | 'ziel' | 'nicht-vorgesehen'

/** Zustandstext und Symbol je Zustand — lesbar ohne Farbe (E6), dieselben wie an der Leiste. */
const ZUSTAND: Record<Zustand, { text: string; symbol: ReactNode }> = {
  erbracht: { text: 'vor dem Lauf erbracht', symbol: <CheckCircleOutlineIcon fontSize="inherit" /> },
  vorgesehen: { text: 'vorgesehen', symbol: <RadioButtonUncheckedIcon fontSize="inherit" /> },
  ziel: { text: 'Ziel', symbol: <FlagIcon fontSize="inherit" /> },
  'nicht-vorgesehen': { text: 'nicht vorgesehen', symbol: <RemoveCircleOutlineIcon fontSize="inherit" /> },
}

/**
 * Der Zustand einer Station vor der Übernahme: An einem Plan sind Plan und Prüfung erbracht, das
 * Ziel „Arbeitspakete“ schließt die Abdeckung ein (Fachplan #1420), alles hinter dem Ende ist
 * nicht vorgesehen.
 */
function zustand(karte: HeuteNachtKarte, station: KettenStation): Zustand {
  const position = STATIONEN.indexOf(station)
  if (karte.start === 'PLAN' && position <= STATIONEN.indexOf('REVIEW')) {
    return 'erbracht'
  }
  if (station === karte.ziel) {
    return 'ziel'
  }
  const ende = STATIONEN.indexOf(karte.ziel === 'PAKETE' ? 'ABDECKUNG' : karte.ziel)
  return position <= ende ? 'vorgesehen' : 'nicht-vorgesehen'
}

type Ladung =
  | { art: 'laden' }
  | { art: 'geladen'; karten: HeuteNachtKarte[] }
  | { art: 'verboten' }
  | { art: 'fehler'; meldung: string }

/**
 * Die Übersicht „Heute Nacht“ auf der Runner-Seite (Issue #1455, Plan #1447): alle zur Übernahme
 * freigegebenen, noch nicht übernommenen Karten des Projekts über alle Boards, je Zeile mit
 * Nummer, Titel, Board, Ziel, kleinem Stufenstand und — falls gewählt — der Prüferzahl.
 *
 * <p>Geladen wird einmal beim Öffnen der Seite. Fehlt das Leserecht (403), erscheint die Übersicht
 * gar nicht, wie die übrigen Lauf-Bereiche; ein anderer Ladefehler steht in ihr. Ein Klick auf
 * Nummer und Titel öffnet die Karte über `onKarteOeffnen`, wie die übrigen Kartenverweise.
 */
export function HeuteNachtUebersicht({
  projectId,
  onKarteOeffnen,
  api = nightRunsApi,
}: Readonly<{
  projectId: number
  onKarteOeffnen: (nummer: number) => void
  api?: Pick<NightRunsApi, 'heuteNacht'>
}>) {
  const ladung = useHeuteNacht(api, projectId)
  const ueberschriftId = useId()

  if (ladung.art === 'laden' || ladung.art === 'verboten') {
    return null
  }

  return (
    <Box
      component="section"
      aria-labelledby={ueberschriftId}
      data-testid="heute-nacht"
      sx={{
        borderRadius: `${PANEL_RADIUS}px`,
        border: `1px solid ${RAND}`,
        bgcolor: PLATTE,
        boxShadow: SCHATTEN_PLATTE,
        p: '14px 18px',
      }}
    >
      <Box component="h2" id={ueberschriftId} sx={{ ...ANZEIGE, m: 0, mb: 1, fontSize: 15, fontWeight: 600 }}>
        Heute Nacht
      </Box>
      {ladung.art === 'fehler' && (
        <Box component="p" sx={{ m: 0, color: TEXT_MATT }}>
          Die Übersicht ließ sich nicht laden: {ladung.meldung}
        </Box>
      )}
      {ladung.art === 'geladen' && ladung.karten.length === 0 && (
        <Box component="p" sx={{ m: 0, color: TEXT_MATT }}>
          {LEER_SATZ}
        </Box>
      )}
      {ladung.art === 'geladen' && ladung.karten.length > 0 && (
        <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
          {ladung.karten.map((karte) => (
            <KartenZeile key={karte.number} karte={karte} onOeffnen={() => onKarteOeffnen(karte.number)} />
          ))}
        </Box>
      )}
    </Box>
  )
}

/** Eine freigegebene Karte: Kopf mit Verweis, Board, Weg und Ziel, darunter der Stufenstand. */
function KartenZeile({ karte, onOeffnen }: Readonly<{ karte: HeuteNachtKarte; onOeffnen: () => void }>) {
  const start = karte.start === 'PLAN' ? 'PAKETE' : 'PLAN'
  return (
    <Box
      component="li"
      aria-label={`#${karte.number} ${karte.title}`}
      data-testid={`heute-nacht-karte-${karte.number}`}
      sx={{ ...NUT_SX, borderRadius: `${KLEIN_RADIUS}px`, p: 1, display: 'flex', flexDirection: 'column', gap: 0.75 }}
    >
      <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', columnGap: 2, rowGap: 0.5 }}>
        <ButtonBase
          onClick={onOeffnen}
          sx={{
            font: 'inherit',
            color: 'inherit',
            gap: 1,
            borderRadius: `${KLEIN_RADIUS}px`,
            textAlign: 'left',
            '&:hover': { textDecoration: 'underline' },
            '&:focus-visible': { outline: `2px solid ${KUPFER}`, outlineOffset: 2 },
          }}
        >
          <Box component="span" sx={ZAHL}>{`#${karte.number}`}</Box>
          <Box component="span" sx={{ fontWeight: 600 }}>{karte.title}</Box>
        </ButtonBase>
        <Box component="span" sx={{ fontSize: 13, color: TEXT_MATT }}>{`Board: ${karte.boardName}`}</Box>
        <Box component="span" sx={{ fontSize: 13 }}>{`${STATIONSNAME[start]} → ${STATIONSNAME[karte.ziel]}`}</Box>
        <Box component="span" sx={{ fontSize: 13, fontWeight: 600 }}>{`Ziel: ${STATIONSNAME[karte.ziel]}`}</Box>
        {karte.pruefer !== null && (
          <Box component="span" sx={{ fontSize: 13 }}>{`Planprüfung: ${karte.pruefer} Prüfer`}</Box>
        )}
      </Box>
      <Box
        component="ol"
        aria-label={`Stufenstand von #${karte.number}`}
        sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, listStyle: 'none', m: 0, p: 0 }}
      >
        {STATIONEN.map((station) => {
          const z = zustand(karte, station)
          return (
            <Box
              component="li"
              key={station}
              data-testid={`heute-nacht-station-${karte.number}-${station}`}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
                px: 0.75,
                py: 0.25,
                fontSize: 12,
                border: `1px solid ${RAND}`,
                borderRadius: `${KLEIN_RADIUS}px`,
                ...(z === 'ziel' ? { borderColor: KUPFER, fontWeight: 600 } : {}),
                ...(z === 'nicht-vorgesehen' ? { color: TEXT_SCHWACH } : {}),
              }}
            >
              <Box component="span" aria-hidden data-testid={`heute-nacht-symbol-${z}`} sx={{ display: 'flex', fontSize: 14 }}>
                {ZUSTAND[z].symbol}
              </Box>
              <span>{`${STATIONSNAME[station]}: ${ZUSTAND[z].text}`}</span>
            </Box>
          )
        })}
      </Box>
    </Box>
  )
}

/** Lädt die Übersicht einmal; eine Antwort nach dem Schließen verfällt. */
function useHeuteNacht(api: Pick<NightRunsApi, 'heuteNacht'>, projectId: number): Ladung {
  const [ladung, setLadung] = useState<Ladung>({ art: 'laden' })
  useEffect(() => {
    let aktiv = true
    api.heuteNacht(projectId).then(
      (karten) => {
        if (aktiv) setLadung({ art: 'geladen', karten })
      },
      (error: unknown) => {
        if (!aktiv) return
        setLadung(
          error instanceof ApiError && error.status === 403
            ? { art: 'verboten' }
            : { art: 'fehler', meldung: apiErrorMessage(error, 'unbekannter Fehler') },
        )
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, projectId])
  return ladung
}
