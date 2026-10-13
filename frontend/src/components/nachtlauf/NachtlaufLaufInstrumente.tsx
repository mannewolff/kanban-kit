import Box from '@mui/material/Box'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { woerterbuch, type Sprache } from '../../i18n/woerterbuch'
import { cacheQuote, dollar, laufDauerGeteilt, tokenMenge } from '../../lib/leitstand'
import { useJetzt } from '../../lib/useJetzt'
import { RAND } from '../../theme'
import { Instrument, type InstrumentWert } from '../leitstand/LeitstandBausteine'

/** Der aufbewahrte Verbrauch eines Laufs, so weit die Instrumente ihn brauchen. */
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

const anteilText = (usd: number | undefined, sprache: Sprache): string => {
  const t = woerterbuch(sprache)
  return usd === undefined
    ? t.NACHTLAUF_UEBERGABE.NICHT_GEMELDET
    : t.NACHTLAUF_INSTRUMENTE.BETRAG(dollar(usd, sprache))
}

/** `undefined` aus dem Anzeigemodell heißt „nicht gemessen"; die Rechnung kennt dafür `null`. */
const alsNull = (wert: number | undefined): number | null => wert ?? null

/** Der Takt der mitlaufenden Zeit (#1244): zehn Sekunden — fein genug für eine Minutenanzeige. */
const TAKT_MS = 10_000

/** Eine Token-Menge als Instrumentenwert; `null`, wo nichts gemessen wurde. */
const mengeAlsWert = (anzahl: number | undefined, sprache: Sprache): InstrumentWert[] | null => {
  const menge = tokenMenge(alsNull(anzahl), sprache)
  return menge === null ? null : [menge]
}

/**
 * Die sechs Instrumente eines Laufs (#988, Vorlage `docs/mockup-nachtlauf-lauf.html` Z. 392–399):
 * Kosten, Eingabe, Ausgabe, Cache-Quote, Dauer und die Pakete nach Zustand.
 *
 * <p><b>Sie ersetzen die Textzeile der Laufsumme</b> („Kosten: … · Eingabe: … · Ausgabe: … ·
 * Zwischenspeicher: …", Entscheidung Manne 2026-09-17). Die Werte sind dieselben; nur die
 * Cache-Quote ist neu gerechnet, und sie kommt aus denselben zwei Zahlen.
 *
 * <p><b>Die Kosten stehen als heißes Instrument</b> — die eine Zahl, nach der ein Betreiber zuerst
 * sieht; so führt sie die Vorlage.
 *
 * <p><b>Die Lauf-Summe wird angezeigt, nicht gerechnet.</b> Sie kommt aus dem Lauf selbst und liegt
 * über der Summe seiner Vorgänge, wo Sitzungen keinem Vorgang zuzuordnen waren. Aus den Vorgängen
 * gerechnet wäre dieser Rest per Konstruktion null — und damit unsichtbar.
 *
 * <p><b>Dauer und Pakete stehen immer</b>: Beide stammen aus dem Lauf und nicht aus einer Messung,
 * die ausfallen kann. Die vier übrigen zeigen „—", wo nichts gemessen wurde.
 */
export function NachtlaufLaufInstrumente({
  verbrauch,
  dauerMs,
  pakete,
  aufteilung,
  laeuftSeit,
  testId = 'lauf-instrumente',
}: Readonly<{
  /** `undefined` am eben geparsten Lauf — dort gibt es noch keinen aufbewahrten Stand. */
  verbrauch: Laufverbrauch | undefined
  dauerMs: number
  pakete: Paketzahlen
  /** Nur an einem eingelieferten Kettenlauf; sonst steht unter den Kosten nichts (Issue #1106). */
  aufteilung?: Kostenaufteilung
  /**
   * Die Startzeit (ISO) eines Laufs, der **noch läuft** (Issue #1244). Nur dann steht unter der
   * gemeldeten Dauer, wie lange er tatsächlich schon läuft; an einem beendeten Lauf ist die
   * gemeldete Dauer die ganze Wahrheit.
   */
  laeuftSeit?: string
  testId?: string
}>) {
  const t = useT().NACHTLAUF_INSTRUMENTE
  const { sprache } = useSprache()
  const dauer = laufDauerGeteilt(dauerMs)
  // Die gemeldete Dauer reicht nur bis zur letzten Meldung und steht zwischen zwei Meldungen still
  // (#1244). Was daneben steht, ist reine Rechnung im Browser — kein zweiter Abruf.
  const jetzt = useJetzt(laeuftSeit === undefined ? null : TAKT_MS)
  const seit =
    laeuftSeit === undefined ? undefined : laufDauerGeteilt(jetzt - new Date(laeuftSeit).getTime())
  const quote = cacheQuote(alsNull(verbrauch?.zwischenspeicher), alsNull(verbrauch?.eingabe))

  return (
    <Box
      data-testid={testId}
      sx={{
        display: 'grid',
        gridTemplateColumns: {
          xs: 'repeat(2, minmax(0,1fr))',
          sm: 'repeat(3, minmax(0,1fr))',
          lg: 'repeat(6, minmax(0,1fr))',
        },
        gap: '9px',
        px: '16px',
        py: '14px',
        borderBottom: `1px solid ${RAND}`,
      }}
    >
      <Instrument
        titel={t.KOSTEN}
        heiss
        testId="instrument-kosten"
        zusatz={
          aufteilung === undefined
            ? undefined
            : t.AUFTEILUNG(anteilText(aufteilung.planungUsd, sprache), anteilText(aufteilung.umsetzungUsd, sprache))
        }
        teile={
          verbrauch?.kostenUsd === undefined
            ? null
            : [{ wert: dollar(verbrauch.kostenUsd, sprache), einheit: '$' }]
        }
      />
      <Instrument titel={t.EINGABE} testId="instrument-eingabe" teile={mengeAlsWert(verbrauch?.eingabe, sprache)} />
      <Instrument titel={t.AUSGABE} testId="instrument-ausgabe" teile={mengeAlsWert(verbrauch?.ausgabe, sprache)} />
      <Instrument
        titel={t.CACHE_QUOTE}
        testId="instrument-cache"
        // Ohne Eingabe gibt es kein Verhältnis (siehe `cacheQuote`) — deshalb hier „nicht
        // berechenbar" und nicht „nicht gemessen": Der Zwischenspeicher kann gemessen sein.
        leerText={t.NICHT_BERECHENBAR}
        teile={quote === null ? null : [{ wert: String(quote), einheit: '%' }]}
      />
      <Instrument
        titel={t.DAUER}
        testId="instrument-dauer"
        teile={[dauer]}
        // Text, keine Live-Region: Ein Vorlesewerkzeug soll die Zeile nicht alle zehn Sekunden
        // erneut ansagen.
        zusatz={seit === undefined ? undefined : t.LAEUFT_SEIT(seit.wert, seit.einheit)}
      />
      <Instrument
        titel={t.PAKETE}
        testId="instrument-pakete"
        teile={[
          { wert: String(pakete.gruen), einheit: t.GRUEN },
          { wert: String(pakete.gelb), einheit: t.GELB },
          { wert: String(pakete.rot), einheit: t.ROT },
        ]}
      />
    </Box>
  )
}
