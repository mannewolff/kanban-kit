import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { VerbrauchNacht } from '../../api/nightRunUsage'
import { formatDuration } from '../../lib/formatDuration'
import { kosten, menge, ohneNull } from '../../lib/nachtlaufFormat'
import { zeitraumBeschriftung, zwischenspeicherAnteil } from '../../lib/verbrauchZeitraum'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'
import { NachtlaufKennzahlen, type NachtlaufKennzahl } from './NachtlaufKennzahlen'

const kennzahl = (label: string, wert: string): NachtlaufKennzahl => ({ label, wert, hinweis: null })

/**
 * Eine Nacht der Verbrauchs-Auswertung (Issue #941, #926 AK 1, 2, 4): alle Läufe, die in dieselbe
 * Nacht fallen, als **eine** Tagesgruppe (Plan #933 E21). Gezeigt werden die Kostensummen und die
 * Kennzahlen der Nacht — sonst nichts.
 *
 * <p><b>Keine Zeile je Karte</b> (Task #1105): Die Kartenzeilen, die Issue #941 für #926 AK 3
 * einführte, sind entfallen; der PO hat AK 3 für diese Ansicht ausdrücklich aufgehoben. Was eine
 * einzelne Karte gekostet hat und wie sie ausging, steht auf derselben Seite schon beim konkreten
 * Lauf. Hier wiederholte es sich nur und machte die Ansicht lang — im Anlassfall 16 Karten unter
 * den Kennzahlen. Das Feld `cards` der Antwort bleibt unverändert, es wird hier nur nicht gezeigt.
 *
 * <p><b>Die drei Zahlen stehen getrennt</b> (AK 2): Gesamtsumme, der Anteil, der einzelnen Karten
 * zugeordnet ist, und der Rest, der keiner Karte zuzuordnen ist. Wird nur über Karten summiert,
 * ist die ausgewiesene Summe kleiner als die Rechnung, und niemand weiß warum. Diese Erklärung
 * braucht es gerade ohne sichtbare Kartenliste.
 *
 * <p>Jede fehlende Angabe steht als „nicht gemessen" und nie als 0 (Plan E5). Die Darstellung der
 * Kennzahlen ist die der übrigen Seite — {@link NachtlaufKennzahlen} —, damit dieselbe Sache nicht
 * zweimal verschieden aussieht.
 *
 * <p><b>Gezeigt wird der Nachtlauf-Anteil</b> (Issue #1016, Plan #1007): Derselbe Abruf führt seit
 * Issue #1013 auch die interaktiven Sitzungen, und `usage` ist die Summe über beide. Die
 * Nachtlauf-Seite bleibt in ihrer Aussage auf Nachtläufe beschränkt — läse sie `usage`, wüchsen
 * ihre Zahlen still um die Arbeit am Tag, und niemand sähe, woher der Zuwachs kommt.
 */
export function NachtlaufVerbrauchNacht({ nacht }: Readonly<{ nacht: VerbrauchNacht }>) {
  const { total, cardShare, remainder } = nacht.usageByKind.night
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
        </>
      )}
    </Box>
  )
}

const BESCHRIFTUNG = {
  fontFamily: NACHTLAUF_SCHRIFTEN.body,
  fontSize: 13,
  color: NACHTLAUF_FARBEN.ink3,
} as const
