import Box from '@mui/material/Box'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { woerterbuch, type Sprache } from '../../i18n/woerterbuch'
import { cacheQuote, dollar, laufDauerGeteilt, tokenMenge } from '../../lib/leitstand'
import { useJetzt } from '../../lib/useJetzt'
import { SCHRIFT_MONO, ZAHL } from '../../theme'

/** Der aufbewahrte Verbrauch eines Laufs, so weit die Lage ihn braucht. */
export interface Laufverbrauch {
  kostenUsd: number | undefined
  eingabe: number | undefined
  ausgabe: number | undefined
  zwischenspeicher: number | undefined
}

/** Die Pakete eines Laufs nach Zustand — gerechnet in `lib/leitstand.ts` (`paketZaehlung`). */
export interface Paketzahlen {
  gruen: number
  gelb: number
  rot: number
}

/**
 * Die Kosten eines Kettenlaufs nach Planung und Umsetzung (Issue #1106) — gerechnet in der Seite.
 * Ein fehlender Anteil ist `undefined` und erscheint als „nicht gemeldet", nie als 0.
 */
export interface Kostenaufteilung {
  planungUsd: number | undefined
  umsetzungUsd: number | undefined
}

/** Der Takt der mitlaufenden Zeit (#1244): zehn Sekunden — fein genug für eine Minutenanzeige. */
const TAKT_MS = 10_000

/** Der Trenner der leisen Zeile, wie in der Metazeile. */
const TRENNER = ' · '

/** `undefined` aus dem Anzeigemodell heißt „nicht gemessen"; die Rechnung kennt dafür `null`. */
const alsNull = (wert: number | undefined): number | null => wert ?? null

const anteilText = (usd: number | undefined, sprache: Sprache): string => {
  const t = woerterbuch(sprache)
  return usd === undefined
    ? t.NACHTLAUF_UEBERGABE.NICHT_GEMELDET
    : t.NACHTLAUF_INSTRUMENTE.BETRAG(dollar(usd, sprache))
}

/** Eine Token-Menge mit Einheit; „nicht gemessen", wo nichts gemessen wurde. */
const mengeText = (anzahl: number | undefined, sprache: Sprache): string => {
  const menge = tokenMenge(alsNull(anzahl), sprache)
  return menge === null
    ? woerterbuch(sprache).LEITSTAND_BAUSTEINE.NICHT_GEMESSEN
    : `${menge.wert} ${menge.einheit}`
}

/** Die leise Zeile: Eingabe, Ausgabe, Cache-Quote und, am Kettenlauf, die Kostenaufteilung. */
function verbrauchszeile(
  verbrauch: Laufverbrauch | undefined,
  aufteilung: Kostenaufteilung | undefined,
  sprache: Sprache,
): string {
  const t = woerterbuch(sprache).NACHTLAUF_INSTRUMENTE
  const quote = cacheQuote(alsNull(verbrauch?.zwischenspeicher), alsNull(verbrauch?.eingabe))
  const teile = [
    t.ANGABE(t.EINGABE, mengeText(verbrauch?.eingabe, sprache)),
    t.ANGABE(t.AUSGABE, mengeText(verbrauch?.ausgabe, sprache)),
    // Ohne Eingabe gibt es kein Verhältnis (siehe `cacheQuote`) — deshalb „nicht berechenbar" und
    // nicht „nicht gemessen": Der Zwischenspeicher kann gemessen sein.
    t.ANGABE(t.CACHE_QUOTE, quote === null ? t.NICHT_BERECHENBAR : t.QUOTE(quote)),
  ]
  if (aufteilung !== undefined) {
    teile.push(t.AUFTEILUNG(anteilText(aufteilung.planungUsd, sprache), anteilText(aufteilung.umsetzungUsd, sprache)))
  }
  return teile.join(TRENNER)
}

/**
 * Die Lage eines Runs (Plan #1882 E3, E5; Vorlage `docs/entwurf-lauf-ansicht.html` Z. 492–502) —
 * oben in der aufgeklappten Platte, an der Stelle der früheren sechs Instrumente.
 *
 * <p><b>Nur zwei Zahlen tragen Gewicht</b>: die Kosten groß und die Pakete nach Zustand. Eingabe,
 * Ausgabe, Cache-Quote und die Kostenaufteilung stehen als leise Zeile darunter — nichts geht
 * verloren, aber nichts davon ruft.
 *
 * <p><b>Die Lauf-Summe wird angezeigt, nicht gerechnet.</b> Sie kommt aus dem Lauf selbst und liegt
 * über der Summe seiner Vorgänge, wo Sitzungen keinem Vorgang zuzuordnen waren. Aus den Vorgängen
 * gerechnet wäre dieser Rest per Konstruktion null — und damit unsichtbar.
 *
 * <p><b>„läuft seit" steht nur am laufenden Run</b> neben dem Zustandssatz (#1244): Die gemeldete
 * Dauer der Metazeile reicht nur bis zur letzten Meldung und steht zwischen zwei Meldungen still.
 * An einem beendeten Run ist sie die ganze Wahrheit.
 */
export function NachtlaufLage({
  satz,
  verbrauch,
  pakete,
  aufteilung,
  laeuftSeit,
  testId = 'lauf-lage',
}: Readonly<{
  /** Der Zustandssatz aus `zustandssatz`; `null`, wo die Daten keinen hergeben. */
  satz: string | null
  /** `undefined` am eben geparsten Lauf — dort gibt es noch keinen aufbewahrten Stand. */
  verbrauch: Laufverbrauch | undefined
  pakete: Paketzahlen
  /** Nur an einem eingelieferten Kettenlauf; sonst endet die leise Zeile mit der Cache-Quote. */
  aufteilung?: Kostenaufteilung
  /** Die Startzeit (ISO) eines Runs, der **noch läuft** (Issue #1244). */
  laeuftSeit?: string
  testId?: string
}>) {
  const t = useT().NACHTLAUF_INSTRUMENTE
  const nichtGemessen = useT().LEITSTAND_BAUSTEINE.NICHT_GEMESSEN
  const { sprache } = useSprache()
  // Reine Rechnung im Browser — kein zweiter Abruf.
  const jetzt = useJetzt(laeuftSeit === undefined ? null : TAKT_MS)
  const seit =
    laeuftSeit === undefined ? undefined : laufDauerGeteilt(jetzt - new Date(laeuftSeit).getTime())
  const kosten = verbrauch?.kostenUsd

  return (
    <Box data-testid={testId}>
      <Box
        component="section"
        aria-label={t.LAGE}
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          gap: '12px 28px',
          padding: '16px 18px 12px',
        }}
      >
        {(satz !== null || seit !== undefined) && (
          <Box data-testid="lage-zustand" sx={{ flex: '1 1 320px', minWidth: 0 }}>
            {satz !== null && (
              <Box data-testid="lage-satz" sx={{ fontSize: 16, lineHeight: 1.4 }}>
                {satz}
              </Box>
            )}
            {/* Text, keine Live-Region: Ein Vorlesewerkzeug soll die Zeile nicht alle zehn
                Sekunden erneut ansagen. */}
            {seit !== undefined && (
              <Box data-testid="lage-laeuft-seit" sx={{ fontSize: 12, color: 'text.secondary', mt: '2px' }}>
                {t.LAEUFT_SEIT(seit.wert, seit.einheit)}
              </Box>
            )}
          </Box>
        )}
        <Box
          data-testid="lage-pakete"
          role="group"
          aria-label={t.PAKETE}
          sx={{ display: 'flex', gap: '12px', fontSize: 13, color: 'text.secondary' }}
        >
          {[
            { anzahl: pakete.gruen, wort: t.GRUEN },
            { anzahl: pakete.gelb, wort: t.GELB },
            { anzahl: pakete.rot, wort: t.ROT },
          ].map(({ anzahl, wort }, position) => (
            // Der Leerraum vor jedem weiteren Teil trennt die Zahlen im Text; sichtbar trennt die
            // Lücke des Flex-Behälters, in dem er verschwindet.
            <span key={wort}>
              {position > 0 && ' '}
              <Box component="b" sx={{ ...ZAHL, fontWeight: 600, color: 'text.primary' }}>
                {anzahl}
              </Box>{' '}
              {wort}
            </span>
          ))}
        </Box>
        {/* Eine große Zahl ohne Tabellenziffern (Plan #1882 E5): Sie steht allein und muss mit
            keiner Spalte fluchten. */}
        <Box
          data-testid="lage-kosten"
          role="group"
          aria-label={t.KOSTEN}
          sx={{ fontFamily: SCHRIFT_MONO, fontSize: 26, fontWeight: 600, lineHeight: 1, color: 'text.primary' }}
        >
          {kosten === undefined ? (
            <Box component="span" sx={{ fontSize: 13, fontWeight: 400, color: 'text.secondary' }}>
              {nichtGemessen}
            </Box>
          ) : (
            <>
              {dollar(kosten, sprache)}
              <Box component="small" sx={{ fontSize: 12, fontWeight: 400, color: 'text.secondary', ml: '3px' }}>
                $
              </Box>
            </>
          )}
        </Box>
      </Box>
      <Box
        data-testid="lage-verbrauch"
        sx={{ fontFamily: SCHRIFT_MONO, fontSize: 11.5, color: 'text.secondary', padding: '0 18px 14px' }}
      >
        {verbrauchszeile(verbrauch, aufteilung, sprache)}
      </Box>
    </Box>
  )
}
