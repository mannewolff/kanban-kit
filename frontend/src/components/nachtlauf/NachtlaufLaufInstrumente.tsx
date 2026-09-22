import Box from '@mui/material/Box'
import { cacheQuote, dollar, laufDauerGeteilt, tokenMenge } from '../../lib/leitstand'
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

const anteilText = (usd: number | undefined): string =>
  usd === undefined ? 'nicht gemeldet' : `${dollar(usd)} $`

/** `undefined` aus dem Anzeigemodell heißt „nicht gemessen"; die Rechnung kennt dafür `null`. */
const alsNull = (wert: number | undefined): number | null => wert ?? null

/** Eine Token-Menge als Instrumentenwert; `null`, wo nichts gemessen wurde. */
const mengeAlsWert = (anzahl: number | undefined): InstrumentWert[] | null => {
  const menge = tokenMenge(alsNull(anzahl))
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
  testId = 'lauf-instrumente',
}: Readonly<{
  /** `undefined` am eben geparsten Lauf — dort gibt es noch keinen aufbewahrten Stand. */
  verbrauch: Laufverbrauch | undefined
  dauerMs: number
  pakete: Paketzahlen
  /** Nur an einem eingelieferten Kettenlauf; sonst steht unter den Kosten nichts (Issue #1106). */
  aufteilung?: Kostenaufteilung
  testId?: string
}>) {
  const dauer = laufDauerGeteilt(dauerMs)
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
        titel="Kosten"
        heiss
        testId="instrument-kosten"
        zusatz={
          aufteilung === undefined
            ? undefined
            : `Planung ${anteilText(aufteilung.planungUsd)} · Umsetzung ${anteilText(aufteilung.umsetzungUsd)}`
        }
        teile={
          verbrauch?.kostenUsd === undefined
            ? null
            : [{ wert: dollar(verbrauch.kostenUsd), einheit: '$' }]
        }
      />
      <Instrument titel="Eingabe" testId="instrument-eingabe" teile={mengeAlsWert(verbrauch?.eingabe)} />
      <Instrument titel="Ausgabe" testId="instrument-ausgabe" teile={mengeAlsWert(verbrauch?.ausgabe)} />
      <Instrument
        titel="Cache-Quote"
        testId="instrument-cache"
        // Ohne Eingabe gibt es kein Verhältnis (siehe `cacheQuote`) — deshalb hier „nicht
        // berechenbar" und nicht „nicht gemessen": Der Zwischenspeicher kann gemessen sein.
        leerText="nicht berechenbar"
        teile={quote === null ? null : [{ wert: String(quote), einheit: '%' }]}
      />
      <Instrument titel="Dauer" testId="instrument-dauer" teile={[dauer]} />
      <Instrument
        titel="Pakete"
        testId="instrument-pakete"
        teile={[
          { wert: String(pakete.gruen), einheit: 'grün' },
          { wert: String(pakete.gelb), einheit: 'gelb' },
          { wert: String(pakete.rot), einheit: 'rot' },
        ]}
      />
    </Box>
  )
}
