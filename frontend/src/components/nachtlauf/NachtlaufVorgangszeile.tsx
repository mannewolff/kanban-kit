import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Link from '@mui/material/Link'
import type { ReactNode } from 'react'
import type { CardByNumber } from '../../api/cards'
import type { Melder } from '../../lib/leitstand'
import {
  KLEIN_RADIUS,
  MELDER,
  NUR_LESER_SX,
  NUT,
  PLATTE,
  RAND,
  SCHATTEN_NUTE,
  TEXT_SCHWACH,
  ZAHL,
} from '../../theme'
import { KlassenMarke, Led, ZEILE_HOVER } from '../leitstand/LeitstandBausteine'

/** Die Fehlerklasse eines Abbruchs als Marke: ihr Schlüsselwort und ihre Farbe. */
export interface Zeilenklasse {
  marke: string
  melder: Melder
}

/** Das Vorhaben eines Vorgangs in der Zeile: sein Titel und die Farbe seines Mals. */
export interface Zeilenvorhaben {
  titel: string
  farbe: string
}

/** Die Spalten der Zeile — dieselben Breiten wie in der Vorlage (Z. 365). */
const SPALTEN = '14px 56px minmax(0,1fr) 64px 60px 70px'

/**
 * Ein Vorgang eines Nachtlaufs als kompakte Zeile (#988, Vorlage `docs/mockup-nachtlauf-lauf.html`
 * Z. 402–438) — dieselbe Gestalt wie die Zeilen der Platte „Letzter Lauf" im Leitstand: LED nach
 * Zustand, Nummer, Titel, darunter Vorhaben oder Fehlerklasse mit Auszug und Häufigkeit, rechts
 * Dauer, Kosten und Commit.
 *
 * <p><b>Sie ersetzt den Vorgangsblock der Nachtlauf-Ausnahme</b> und mit ihm die Zeile „Kosten:
 * nicht gemessen · Eingabe: nicht gemessen …" je Vorgang (Entscheidung Manne 2026-09-17): Die
 * Kosten stehen jetzt in ihrer Spalte, und „nicht gemessen" steht dort als „—".
 *
 * <p><b>Die Nummer öffnet die Karte, die Zeile klappt auf.</b> Zwei Ziele, zwei Bedienelemente —
 * ein Knopf im Knopf wäre ungültiges HTML, und ein Klick, der beides täte, wäre nicht vorhersagbar.
 * Alles, was die Vorlage in der Zeile nicht zeigt, steht aufgeklappt darunter: der Befund mit
 * „Kopieren", die Herkunftskette, das Vorhaben als Verweis, Verlauf und Ergebniszeile.
 *
 * <p><b>Der Zustand hängt nie allein an der Farbe</b> (`CLAUDE-react.md`): Sein Wort steht als Text
 * für Vorlesewerkzeuge an der LED und zusätzlich sichtbar im aufgeklappten Teil; einen Abbruch
 * benennt darüber hinaus sichtbar seine Klassenmarke.
 */
export function NachtlaufVorgangszeile({
  nummer,
  titel,
  /**
   * Die Wurzelkarte: `undefined`, solange sie lädt, `null`, wenn es sie nicht mehr gibt. Nur mit
   * ihr ist die Nummer ein Verweis — sonst gäbe es kein Ziel.
   */
  wurzel,
  melder,
  /** Der Zustand in Worten, etwa „Erfolg" oder „gescheitert — Prüfungen rot". */
  zustandswort,
  /** Die Fehlerklasse eines Abbruchs; `null`, wo keiner vorliegt. */
  klasse,
  /** Die erste Zeile des Auszugs — mehr passt in eine Zeile nicht. */
  auszug,
  /** Wie oft die Fehlerklasse in den aufbewahrten Läufen vorkam; `null`, wo keine Zahl gilt. */
  haeufigkeit,
  /** Das Vorhaben des Vorgangs; steht nur, wo kein Abbruch die Zeile beansprucht. */
  vorhaben,
  dauer,
  /** Die Kosten des Vorgangs; `null` heißt „nicht gemessen" und erscheint als „—". */
  kosten,
  /** Der kurze Commit-Hash; `null`, wo der Vorgang keinen hinterließ. */
  commit,
  offen,
  onUmschalten,
  onOeffnen,
  children,
}: Readonly<{
  nummer: number
  titel: string
  wurzel: CardByNumber | null | undefined
  melder: Melder
  zustandswort: string
  klasse: Zeilenklasse | null
  auszug: string | null
  haeufigkeit: string | null
  vorhaben: Zeilenvorhaben | null
  dauer: string
  kosten: string | null
  commit: string | null
  offen: boolean
  onUmschalten: () => void
  onOeffnen: (karte: CardByNumber) => void
  children?: ReactNode
}>) {
  return (
    <Box
      component="li"
      data-testid={`paket-${nummer}`}
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '14px 56px minmax(0,1fr)', md: SPALTEN },
        alignItems: 'center',
        gap: '12px',
        px: '16px',
        py: '11px',
        // Der rote Vorgang trägt einen Hauch seiner Farbe (Vorlage Z. 414) — zusätzlich zur LED und
        // zur Klassenmarke, nie an ihrer Stelle.
        ...(melder === 'zinnob' && { bgcolor: `color-mix(in srgb, ${MELDER.zinnob} 5%, ${PLATTE})` }),
        '&:not(:last-child)': {
          borderBottom: `1px solid color-mix(in srgb, ${RAND} 55%, transparent)`,
        },
        '&:hover': { background: ZEILE_HOVER },
      }}
    >
      <Led melder={melder} />
      {/* Der Zustand in Worten steht **einmal**: zugeklappt nur für Vorlesewerkzeuge, aufgeklappt
          sichtbar unter der Zeile. Zweimal derselbe Satz ließe ein Vorlesewerkzeug ihn doppelt
          lesen, und ein Test könnte ihn nicht mehr auseinanderhalten. */}
      {!offen && (
        <Box component="span" data-testid={`zustand-${nummer}`} sx={NUR_LESER_SX}>
          {zustandswort}
        </Box>
      )}

      {wurzel == null ? (
        <Box component="span" sx={NUMMER_SX}>
          {`#${nummer}`}
        </Box>
      ) : (
        <Link
          component="button"
          type="button"
          underline="hover"
          aria-label={`#${nummer} ${titel}`}
          onClick={() => onOeffnen(wurzel)}
          sx={{ ...NUMMER_SX, textAlign: 'left', justifySelf: 'start' }}
        >
          {`#${nummer}`}
        </Link>
      )}

      <ButtonBase
        aria-expanded={offen}
        data-testid={`vorgang-taste-${nummer}`}
        onClick={onUmschalten}
        sx={{
          gridColumn: 3,
          minWidth: 0,
          display: 'block',
          textAlign: 'left',
          borderRadius: `${KLEIN_RADIUS}px`,
        }}
      >
        <Box
          sx={{
            fontSize: 13.5,
            fontWeight: 500,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {titel}
        </Box>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            mt: '3px',
            flexWrap: 'wrap',
            fontSize: 11,
            color: 'text.secondary',
          }}
        >
          {wurzel === null && <span>{`Karte #${nummer} nicht gefunden`}</span>}
          {klasse !== null && <KlassenMarke melder={klasse.melder}>{klasse.marke}</KlassenMarke>}
          {klasse !== null && auszug !== null && (
            <Box component="span" sx={{ fontSize: 11.5 }}>
              {auszug}
            </Box>
          )}
          {klasse === null && vorhaben !== null && (
            <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
              <Box
                component="span"
                aria-hidden
                sx={{ width: 7, height: 7, borderRadius: '2px', flex: 'none', bgcolor: vorhaben.farbe }}
              />
              {vorhaben.titel}
            </Box>
          )}
          {haeufigkeit !== null && (
            <Box
              component="span"
              data-testid={`haeufigkeit-${nummer}`}
              sx={{ color: TEXT_SCHWACH }}
            >
              {haeufigkeit}
            </Box>
          )}
        </Box>
      </ButtonBase>

      <Box
        component="span"
        data-testid={`dauer-${nummer}`}
        sx={{ ...ZAHL, fontSize: 12, color: 'text.secondary', display: { xs: 'none', md: 'block' } }}
      >
        {dauer}
      </Box>
      <Box
        component="span"
        data-testid={`kosten-${nummer}`}
        sx={{
          ...ZAHL,
          fontSize: 12,
          textAlign: 'right',
          color: kosten === null ? TEXT_SCHWACH : 'text.secondary',
          display: { xs: 'none', md: 'block' },
        }}
      >
        {kosten ?? (
          <>
            {'—'}
            <Box component="span" sx={NUR_LESER_SX}>
              nicht gemessen
            </Box>
          </>
        )}
      </Box>
      <Box sx={{ display: { xs: 'none', md: 'block' } }}>
        {commit !== null && (
          <Box
            component="span"
            data-testid={`commit-${nummer}`}
            sx={{
              ...ZAHL,
              fontSize: 11,
              color: TEXT_SCHWACH,
              bgcolor: NUT,
              border: `1px solid ${RAND}`,
              boxShadow: SCHATTEN_NUTE,
              borderRadius: '5px',
              px: '5px',
              py: '1px',
            }}
          >
            {commit}
          </Box>
        )}
      </Box>

      {offen && (
        <Box
          data-testid={`einzelheiten-${nummer}`}
          sx={{
            gridColumn: { xs: '1 / -1', md: '3 / -1' },
            mt: '8px',
            display: 'flex',
            flexDirection: 'column',
            gap: '8px',
          }}
        >
          <Box component="span" data-testid={`zustand-${nummer}`} sx={{ fontSize: 12.5 }}>
            {zustandswort}
          </Box>
          {children}
        </Box>
      )}
    </Box>
  )
}

const NUMMER_SX = {
  ...ZAHL,
  fontSize: 12,
  fontWeight: 500,
  color: TEXT_SCHWACH,
} as const
