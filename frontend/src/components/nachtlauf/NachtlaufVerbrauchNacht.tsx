import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { VerbrauchNacht } from '../../api/nightRunUsage'
import { laufDauerGeteilt, tokenMenge } from '../../lib/leitstand'
import { zeitraumBeschriftung, zwischenspeicherAnteil } from '../../lib/verbrauchZeitraum'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'
import { NachtlaufVerbrauchStufen } from './NachtlaufVerbrauchStufen'
import { VerbrauchKachel, VerbrauchKachelRaster, VerbrauchKostenKacheln } from './VerbrauchKacheln'

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
 * <p>Jede fehlende Angabe steht als „nicht gemessen" und nie als 0 (Plan E5). Die Darstellung ist
 * seit Task #1108 die von Leitstand und Zeitraumansicht — zwei Raster aus je vier
 * {@link VerbrauchKachel} statt der beiden flachen Kennzahlenreihen von vorher, damit dieselbe
 * Sache nicht zweimal verschieden aussieht. Die vier Kosten-Kacheln sind buchstäblich dieselben
 * wie die des Zeitraums ({@link VerbrauchKostenKacheln}).
 *
 * <p><b>Gezeigt wird der Nachtlauf-Anteil</b> (Issue #1016, Plan #1007): Derselbe Abruf führt seit
 * Issue #1013 auch die interaktiven Sitzungen, und `usage` ist die Summe über beide. Die
 * Nachtlauf-Seite bleibt in ihrer Aussage auf Nachtläufe beschränkt — läse sie `usage`, wüchsen
 * ihre Zahlen still um die Arbeit am Tag, und niemand sähe, woher der Zuwachs kommt.
 */
export function NachtlaufVerbrauchNacht({ nacht }: Readonly<{ nacht: VerbrauchNacht }>) {
  const { total } = nacht.usageByKind.night
  const dauer = laufDauerGeteilt(nacht.durationMs)
  // Der Anteil steht als Einordnung unter der Kachel, zu der er gehört — wie „81 Karten" unter den
  // Läufen. Ohne Anteil bleibt die Zeile leer: „nicht bestimmt aus dem Zwischenspeicher" wäre kein
  // Satz, und eine gerechnete 0 behauptete eine Quote, die es nicht gibt.
  const zwischenspeicherBasis =
    total.cachedInputSharePercent === null
      ? ''
      : `${zwischenspeicherAnteil(total)} aus dem Zwischenspeicher`
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
        {nacht.runCount === 1 ? '1 Run' : `${nacht.runCount} Runs`}
      </Typography>

      {nacht.runCount === 0 ? (
        <Typography component="p" sx={{ ...BESCHRIFTUNG, mt: 2 }}>
          In dieser Schicht hat kein Run stattgefunden.
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
              Der Run wurde abgebrochen — die Werte sind unvollständig.
            </Typography>
          )}

          <VerbrauchKostenKacheln kennzahlen={nacht} testId="verbrauch-nacht-summen" />
          <VerbrauchKachelRaster testId="verbrauch-nacht-kennzahlen">
            <VerbrauchKachel etikett="Dauer" wert={dauer.wert} einheit={dauer.einheit} basis="" />
            <TokenKachel etikett="Eingabe-Token" anzahl={total.inputTokens} basis="" />
            <TokenKachel etikett="Ausgabe-Token" anzahl={total.outputTokens} basis="" />
            <TokenKachel
              etikett="Zwischenspeicher"
              anzahl={total.cachedInputTokens}
              basis={zwischenspeicherBasis}
            />
          </VerbrauchKachelRaster>
          <Box sx={{ mt: 2 }}>
            <NachtlaufVerbrauchStufen stufen={nacht.stages} />
          </Box>
        </>
      )}
    </Box>
  )
}

/**
 * Eine Tokenmenge als Kachel — in der Einheit des Leitstands („70,45 Mio", „415 Tsd"). Ohne
 * Messung trägt die Kachel keinen Wert und keine Einheit: `null` heißt „nicht gemessen", und eine
 * Einheit ohne Zahl sagte nichts.
 */
function TokenKachel({
  etikett,
  anzahl,
  basis,
}: Readonly<{ etikett: string; anzahl: number | null; basis: string }>) {
  const menge = tokenMenge(anzahl)
  return (
    <VerbrauchKachel
      etikett={etikett}
      wert={menge?.wert ?? null}
      einheit={menge?.einheit ?? ''}
      basis={basis}
    />
  )
}

const BESCHRIFTUNG = {
  fontFamily: NACHTLAUF_SCHRIFTEN.body,
  fontSize: 13,
  color: NACHTLAUF_FARBEN.ink3,
} as const
