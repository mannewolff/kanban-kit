import Box from '@mui/material/Box'
import type { ReactNode } from 'react'
import type { VerbrauchAufteilungGattung } from '../../api/nightRunUsage'
import { dollar } from '../../lib/leitstand'
import { kartenText } from '../../lib/verbrauchZeitraum'
import { ETIKETT } from '../../theme'
import { KACHEL_SX, KachelFuss, KachelWert } from '../leitstand/LeitstandBausteine'

/**
 * Die Kachel der Verbrauchs-Auswertung (Mockup `docs/mockup-nachtlauf-verbrauch.html`,
 * `.kachel-klein`): Etikett, Wert mit abgesetzter Einheit, Einordnung darunter.
 *
 * <p><b>Gemeinsam seit Task #1108.</b> Die Kachel stand in `NachtlaufVerbrauchZeitraum.tsx`,
 * während die Nachtansicht dieselben Zahlen als flache Kennzahlenreihen zeigte. Beide Ansichten
 * führen sie nun von hier, damit dieselbe Sache nicht zweimal verschieden aussieht — eine
 * nachgebaute zweite Kachel liefe beim nächsten Feinschliff auseinander.
 */

/**
 * Ein auf null gerundeter Betrag trägt kein Vorzeichen (Task #1108): Der nicht zuordenbare Rest
 * kann knapp unter null liegen, und `Intl` schreibt ihn dann als „-0,00". Das Minus behauptet
 * einen negativen Betrag, den die zwei Nachkommastellen gar nicht mehr zeigen — ein Leser sieht
 * eine Auffälligkeit, wo eine Rundung ist. Ein sichtbar negativer Wert behält sein Vorzeichen.
 */
const GERUNDETE_NULL = /^-(0(?:[.,]0+)?)$/

/** Eine kleine Kachel: Etikett, fertig formatierter Wert mit Einheit, Einordnung. */
export function VerbrauchKachel({
  etikett,
  /** Der fertig formatierte Wert; `null` heißt „nicht gemessen" und nie 0. */
  wert,
  einheit,
  basis,
}: Readonly<{ etikett: string; wert: string | null; einheit: string; basis: string }>) {
  return (
    <Box
      component="article"
      aria-label={etikett}
      data-testid={`verbrauch-kachel-${etikett}`}
      sx={{ ...KACHEL_SX, gap: '6px', pt: '12px', px: '13px', pb: '11px' }}
    >
      <Box sx={ETIKETT}>{etikett}</Box>
      <KachelWert
        wert={wert === null ? null : wert.replace(GERUNDETE_NULL, '$1')}
        einheit={einheit}
        leerText="nicht gemessen"
        groesse={26}
      />
      <KachelFuss basis={basis} />
    </Box>
  )
}

/** Das Raster, in dem die Kacheln stehen (Mockup `.kacheln-2`): zwei Spalten, ab `xs` eine. */
export function VerbrauchKachelRaster({
  /** Kennung der Gruppe, wo eine Ansicht mehrere Raster führt. */
  testId,
  children,
}: Readonly<{ testId?: string; children: ReactNode }>) {
  return (
    <Box
      data-testid={testId}
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))' },
        gap: '10px',
        p: '14px',
        perspective: '1100px',
      }}
    >
      {children}
    </Box>
  )
}

/**
 * Die Zahlen, die eine Nacht und ein Zeitraum gleichermaßen führen: Gesamtsumme, der einzelnen
 * Karten zugeordnete Anteil, der Rest und die Läufe. Die Form ist absichtlich schmal — Nacht und
 * Zeitraum reichen verschiedene Antworten herein, zeigen aber dasselbe.
 *
 * <p>Die Einordnung unter dem Wert entsteht ausschließlich aus vorhandenen Zahlen; fehlt eine,
 * bleibt die Zeile leer — „nicht gemessen" wird nie zu 0.
 */
export function VerbrauchKostenKacheln({
  kennzahlen,
  testId,
}: Readonly<{
  kennzahlen: {
    runCount: number
    cardCount: number
    usageByKind: VerbrauchAufteilungGattung
  }
  testId?: string
}>) {
  const { total, cardShare, remainder } = kennzahlen.usageByKind.night
  const jeLauf =
    total.costUsd !== null && kennzahlen.runCount > 0
      ? `${dollar(total.costUsd / kennzahlen.runCount)} $ je Lauf`
      : ''
  const anteil =
    total.costUsd !== null && total.costUsd > 0 && cardShare.costUsd !== null
      ? `${Math.round((cardShare.costUsd / total.costUsd) * 100)} % der Summe`
      : ''
  return (
    <VerbrauchKachelRaster testId={testId}>
      <VerbrauchKachel etikett="Gesamtsumme" wert={betrag(total.costUsd)} einheit="$" basis={jeLauf} />
      <VerbrauchKachel
        etikett="Karten zugeordnet"
        wert={betrag(cardShare.costUsd)}
        einheit="$"
        basis={anteil}
      />
      <VerbrauchKachel
        etikett="Rest"
        wert={betrag(remainder.costUsd)}
        einheit="$"
        basis="keiner Karte zuzuordnen"
      />
      <VerbrauchKachel
        etikett="Läufe"
        wert={String(kennzahlen.runCount)}
        einheit={kennzahlen.runCount === 1 ? 'Lauf' : 'Läufe'}
        basis={kartenText(kennzahlen.cardCount)}
      />
    </VerbrauchKachelRaster>
  )
}

/** Ein Dollarbetrag für die Kachel; `null` bleibt `null` — nicht gemessen ist nicht 0. */
export function betrag(usd: number | null): string | null {
  return usd === null ? null : dollar(usd)
}
