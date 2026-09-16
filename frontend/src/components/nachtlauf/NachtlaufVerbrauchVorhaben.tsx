import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { VerbrauchVorhaben } from '../../api/nightRunUsage'
import { kosten, menge } from '../../lib/nachtlaufFormat'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'

/** `null` aus der Antwort heißt „nicht gemessen" — die Formatierer kennen dafür `undefined`. */
const ohneNull = (wert: number | null): number | undefined => (wert === null ? undefined : wert)

const karten = (anzahl: number) => (anzahl === 1 ? '1 Karte' : `${anzahl} Karten`)

/**
 * Die Aufstellung je Vorhaben innerhalb eines Zeitraums (Issue #942, #926 AK 10–12).
 *
 * <p><b>Die Reihenfolge ist die des Servers</b>: absteigend nach Kosten, ohne Kosten zuletzt
 * (`NightRunUsageService`). Hier wird nicht nachsortiert — eine zweite Sortierung liefe beim
 * nächsten Gleichstand anders.
 *
 * <p>Zwei Hinweise stehen immer da, weil beide leicht zu vergessen sind: Die Aufstellung zeigt nur
 * den kartenbezogenen Anteil, der Rest gehört zu keinem Vorhaben (AK 12). Und eine Karte kann zu
 * mehreren Vorhaben gehören und zählt dann in jedem (Plan E11) — ohne den Hinweis addiert der
 * Leser Zahlen, die sich überlappen.
 */
export function NachtlaufVerbrauchVorhaben({
  epics,
  withoutEpic,
  epicsOverlap,
}: Readonly<{
  epics: readonly VerbrauchVorhaben[]
  withoutEpic: VerbrauchVorhaben
  epicsOverlap: boolean
}>) {
  return (
    <Box data-testid="verbrauch-vorhaben">
      <Typography component="h4" sx={UEBERSCHRIFT}>
        Vorhaben
      </Typography>
      <Typography component="p" data-testid="verbrauch-vorhaben-anteil-hinweis" sx={HINWEIS}>
        Die Aufstellung zeigt nur den Anteil, der einzelnen Karten zugeordnet ist — der Rest, der
        keiner Karte zuzuordnen ist, gehört zu keinem Vorhaben.
      </Typography>
      <Typography component="p" data-testid="verbrauch-vorhaben-ueberschneidung" sx={HINWEIS}>
        Eine Karte kann zu mehreren Vorhaben gehören und zählt dann in jedem — die Summen der
        Vorhaben können sich überschneiden.
        {epicsOverlap && ' In diesem Zeitraum ist das der Fall.'}
      </Typography>

      {epics.length === 0 ? (
        <Typography component="p" sx={HINWEIS}>
          In diesem Zeitraum ist keine Karte einem Vorhaben zugeordnet.
        </Typography>
      ) : (
        <Box component="ol" data-testid="verbrauch-vorhaben-liste" sx={LISTE}>
          {epics.map((vorhaben) => (
            <Box component="li" key={vorhaben.epicId} sx={ZEILE}>
              {[
                vorhaben.shortcode === null
                  ? vorhaben.title
                  : `${vorhaben.shortcode} · ${vorhaben.title}`,
                kosten(ohneNull(vorhaben.usage.costUsd)),
                karten(vorhaben.cardCount),
                `Eingabe ${menge(ohneNull(vorhaben.usage.inputTokens))}`,
              ].join(' · ')}
            </Box>
          ))}
        </Box>
      )}

      <Typography component="div" data-testid="verbrauch-ohne-vorhaben" sx={{ ...ZEILE, mt: 1 }}>
        {[
          'Ohne Vorhaben',
          kosten(ohneNull(withoutEpic.usage.costUsd)),
          karten(withoutEpic.cardCount),
          `Eingabe ${menge(ohneNull(withoutEpic.usage.inputTokens))}`,
        ].join(' · ')}
      </Typography>
    </Box>
  )
}

const UEBERSCHRIFT = {
  fontFamily: NACHTLAUF_SCHRIFTEN.display,
  fontWeight: 600,
  fontSize: 16,
  color: NACHTLAUF_FARBEN.ink,
} as const

const HINWEIS = {
  fontFamily: NACHTLAUF_SCHRIFTEN.body,
  fontSize: 13,
  color: NACHTLAUF_FARBEN.ink3,
  mt: 0.5,
} as const

const LISTE = { m: 0, mt: 1, pl: 2.5, display: 'grid', gap: 0.5 } as const

const ZEILE = {
  fontFamily: NACHTLAUF_SCHRIFTEN.mono,
  fontSize: 13,
  color: NACHTLAUF_FARBEN.ink2,
} as const
