import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { VerbrauchKarte, VerbrauchNacht } from '../../api/nightRunUsage'
import { formatDuration } from '../../lib/formatDuration'
import { kosten, menge } from '../../lib/nachtlaufFormat'
import { zeitraumBeschriftung, zwischenspeicherAnteil } from '../../lib/verbrauchZeitraum'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'
import { NachtlaufAnteilsbalken } from './NachtlaufAnteilsbalken'
import { NachtlaufKennzahlen, type NachtlaufKennzahl } from './NachtlaufKennzahlen'

/** `null` aus der Antwort heißt „nicht gemessen" — die Formatierer kennen dafür `undefined`. */
const ohneNull = (wert: number | null): number | undefined => (wert === null ? undefined : wert)

const kennzahl = (label: string, wert: string): NachtlaufKennzahl => ({ label, wert, hinweis: null })

/**
 * Eine Nacht der Verbrauchs-Auswertung (Issue #941, #926 AK 1–4): alle Läufe, die in dieselbe
 * Nacht fallen, als **eine** Tagesgruppe (Plan #933 E21).
 *
 * <p><b>Die drei Zahlen stehen getrennt</b> (AK 2): Gesamtsumme, der Anteil, der einzelnen Karten
 * zugeordnet ist, und der Rest, der keiner Karte zuzuordnen ist. Wird nur über Karten summiert,
 * ist die ausgewiesene Summe kleiner als die Rechnung, und niemand weiß warum.
 *
 * <p>Jede fehlende Angabe steht als „nicht gemessen" und nie als 0 (Plan E5). Die Darstellung der
 * Kennzahlen und der Anteile ist die der übrigen Seite — {@link NachtlaufKennzahlen} und {@link
 * NachtlaufAnteilsbalken} —, damit dieselbe Sache nicht zweimal verschieden aussieht.
 */
export function NachtlaufVerbrauchNacht({ nacht }: Readonly<{ nacht: VerbrauchNacht }>) {
  const { total, cardShare, remainder } = nacht.usage
  return (
    <Box data-testid="verbrauch-nacht">
      <Typography
        component="h3"
        sx={{
          fontFamily: NACHTLAUF_SCHRIFTEN.display,
          fontWeight: 600,
          fontSize: 18,
          color: NACHTLAUF_FARBEN.ink,
        }}
      >
        {zeitraumBeschriftung({ type: 'DAY', firstDay: nacht.night, lastDay: nacht.night })}
      </Typography>
      <Typography
        component="div"
        data-testid="verbrauch-nacht-laeufe"
        sx={{ ...BESCHRIFTUNG, mt: 0.5 }}
      >
        {nacht.runCount === 1 ? '1 Lauf' : `${nacht.runCount} Läufe`}
      </Typography>

      {nacht.runCount === 0 ? (
        <Typography component="p" sx={{ ...BESCHRIFTUNG, mt: 2 }}>
          In dieser Nacht hat kein Lauf stattgefunden.
        </Typography>
      ) : (
        <>
          {nacht.aborted && (
            <Typography
              component="div"
              role="note"
              data-testid="verbrauch-nacht-abbruch"
              sx={{ ...BESCHRIFTUNG, mt: 2, color: NACHTLAUF_FARBEN.ink }}
            >
              Der Lauf wurde abgebrochen — die Werte sind unvollständig.
            </Typography>
          )}

          <NachtlaufKennzahlen
            testId="verbrauch-nacht-summen"
            kennzahlen={[
              kennzahl('Gesamtsumme', kosten(ohneNull(total.costUsd))),
              kennzahl('Einzelnen Karten zugeordnet', kosten(ohneNull(cardShare.costUsd))),
              kennzahl('Keiner Karte zuzuordnen', kosten(ohneNull(remainder.costUsd))),
            ]}
          />
          <NachtlaufKennzahlen
            testId="verbrauch-nacht-kennzahlen"
            kennzahlen={[
              kennzahl('Bearbeitete Karten', String(nacht.cardCount)),
              kennzahl('Dauer', formatDuration(nacht.durationMs / 1000)),
              kennzahl('Eingabe', menge(ohneNull(total.inputTokens))),
              kennzahl('Ausgabe', menge(ohneNull(total.outputTokens))),
              kennzahl('Zwischenspeicher', menge(ohneNull(total.cachedInputTokens))),
              kennzahl('Anteil aus dem Zwischenspeicher', zwischenspeicherAnteil(total)),
            ]}
          />

          <Box
            component="ul"
            data-testid="verbrauch-nacht-karten"
            sx={{ listStyle: 'none', p: 0, m: 0, mt: 2, display: 'grid', gap: 1.5 }}
          >
            {nacht.cards.map((karte) => (
              <Kartenzeile key={karte.cardNumber} karte={karte} nachtKosten={total.costUsd} />
            ))}
          </Box>
        </>
      )}
    </Box>
  )
}

/**
 * Eine Karte mit ihren Summen über die Anläufe dieser Nacht (AK 3). Den Balken gibt es nur mit
 * Bezugsgröße: Fehlen die Kosten der Karte oder der Nacht, oder kostete die Nacht nichts, gäbe er
 * ein Verhältnis vor, das es nicht gibt.
 */
function Kartenzeile({
  karte,
  nachtKosten,
}: Readonly<{ karte: VerbrauchKarte; nachtKosten: number | null }>) {
  const anteil =
    karte.usage.costUsd === null || nachtKosten === null || nachtKosten <= 0
      ? null
      : Math.round((karte.usage.costUsd / nachtKosten) * 100)
  const angaben = [
    `#${karte.cardNumber}`,
    karte.attemptCount === 1 ? '1 Anlauf' : `${karte.attemptCount} Anläufe`,
    karte.durationMs === null ? 'Dauer nicht gemessen' : formatDuration(karte.durationMs / 1000),
    `Kosten ${kosten(ohneNull(karte.usage.costUsd))}`,
    `Eingabe ${menge(ohneNull(karte.usage.inputTokens))}`,
    `Ausgabe ${menge(ohneNull(karte.usage.outputTokens))}`,
  ]
  return (
    <Box component="li">
      <Typography
        component="div"
        sx={{ fontFamily: NACHTLAUF_SCHRIFTEN.mono, fontSize: 13, color: NACHTLAUF_FARBEN.ink2 }}
      >
        {angaben.join(' · ')}
      </Typography>
      {anteil !== null && (
        <Box sx={{ mt: 0.5 }}>
          <NachtlaufAnteilsbalken
            anteil={anteil}
            beschriftung={`${anteil} % der Kosten`}
            ansage={`Karte #${karte.cardNumber}: ${anteil} % der Kosten der Nacht`}
            farbe={NACHTLAUF_FARBEN.akzent}
            testId={`verbrauch-karte-${karte.cardNumber}-anteil`}
            fuellungTestId={`verbrauch-karte-${karte.cardNumber}-fuellung`}
          />
        </Box>
      )}
    </Box>
  )
}

const BESCHRIFTUNG = {
  fontFamily: NACHTLAUF_SCHRIFTEN.body,
  fontSize: 13,
  color: NACHTLAUF_FARBEN.ink3,
} as const
