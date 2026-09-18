import Box from '@mui/material/Box'
import type { VerbrauchVorhaben } from '../../api/nightRunUsage'
import { epicColor } from '../../lib/epicMeta'
import { kosten } from '../../lib/nachtlaufFormat'
import { kartenText } from '../../lib/verbrauchZeitraum'
import { MELDER, TEXT_SCHWACH, ZAHL } from '../../theme'
import { Fuellschiene, Platte } from '../leitstand/LeitstandBausteine'

/** `null` aus der Antwort heißt „nicht gemessen" — die Formatierer kennen dafür `undefined`. */
const ohneNull = (wert: number | null): number | undefined => (wert === null ? undefined : wert)

/**
 * Die Aufstellung je Vorhaben innerhalb eines Zeitraums (Issue #942, #926 AK 10–12), seit #987 als
 * Balkenliste in der Platte „Vorhaben" nach `docs/mockup-nachtlauf-verbrauch.html`.
 *
 * <p><b>Die Reihenfolge ist die des Servers</b>: absteigend nach Kosten, ohne Kosten zuletzt
 * (`NightRunUsageService`). Hier wird nicht nachsortiert — eine zweite Sortierung liefe beim
 * nächsten Gleichstand anders.
 *
 * <p><b>Der Balken zeigt das Verhältnis zum teuersten Posten</b>, „ohne Vorhaben" eingerechnet. Wo
 * die Kosten nicht gemessen wurden, bleibt die Schiene weg: Eine leere Schiene läse sich als 0.
 *
 * <p>Zwei Hinweise stehen immer da, weil beide leicht zu vergessen sind: Die Aufstellung zeigt nur
 * den kartenbezogenen Anteil, der Rest gehört zu keinem Vorhaben (AK 12). Und eine Karte kann zu
 * mehreren Vorhaben gehören und zählt dann in jedem (Plan E11) — ohne den Hinweis addiert der
 * Leser Zahlen, die sich überlappen.
 *
 * <p><b>Ohne Aufteilung nach Gattung</b> (Issue #1016, Plan #1007): Die übrigen Bausteine des
 * Bereichs sind auf den Nachtlauf-Anteil festgelegt — dieser kann es nicht. Die Antwort trägt je
 * Vorhaben nur eine Summe (`EpicResponse`, ohne `usageByKind`), und der Zeitraum-Abruf liefert die
 * Kartenzeilen nicht mit, aus denen sie entstand; im Browser ist der Anteil deshalb nicht
 * herstellbar. Sobald interaktive Sitzungen eingehen, enthalten diese Zahlen sie mit. Die Lücke ist
 * im Plan benannt und gehört in ein Paket „Vorhaben-Aufstellung nach Gattung" (Backend-Aggregation
 * plus Anzeige) — sie hier durch eine Rechnung im Browser zu schließen, erfände eine Zuordnung, die
 * nur der Server kennt.
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
  const hoechste = Math.max(
    0,
    ...[...epics, withoutEpic].map((v) => v.usage.costUsd ?? 0),
  )
  const breite = (costUsd: number | null): number | null =>
    costUsd === null || hoechste === 0 ? null : Math.round((costUsd / hoechste) * 100)

  return (
    <Box data-testid="verbrauch-vorhaben">
      <Platte titel="Vorhaben" notiz="Kosten im Zeitraum">
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '9px', px: '16px', pt: '14px', pb: '16px' }}>
          {epics.length === 0 ? (
            <Box sx={{ fontSize: 12.5, color: 'text.secondary' }}>
              In diesem Zeitraum ist keine Karte einem Vorhaben zugeordnet.
            </Box>
          ) : (
            <Box
              component="ol"
              data-testid="verbrauch-vorhaben-liste"
              sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: '9px' }}
            >
              {epics.map((vorhaben) => (
                <Zeile
                  key={vorhaben.epicId}
                  alsPunkt
                  name={
                    vorhaben.shortcode === null
                      ? vorhaben.title
                      : `${vorhaben.shortcode} · ${vorhaben.title}`
                  }
                  vorhaben={vorhaben}
                  breite={breite(vorhaben.usage.costUsd)}
                  // Kennung und Titel trägt jedes Vorhaben der Aufstellung; `null` steht nur am
                  // Posten „ohne Vorhaben", und der kommt aus `withoutEpic`, nicht aus `epics`.
                  farbe={epicColor(vorhaben.epicId!)}
                />
              ))}
            </Box>
          )}

          <Box data-testid="verbrauch-ohne-vorhaben">
            <Zeile
              alsPunkt={false}
              name="Ohne Vorhaben"
              vorhaben={withoutEpic}
              breite={breite(withoutEpic.usage.costUsd)}
              farbe={MELDER.grau}
            />
          </Box>

          <Box data-testid="verbrauch-vorhaben-anteil-hinweis" sx={FUSSNOTE}>
            Die Aufstellung zeigt nur den Anteil, der einzelnen Karten zugeordnet ist — der Rest, der
            keiner Karte zuzuordnen ist, gehört zu keinem Vorhaben.
          </Box>
          <Box data-testid="verbrauch-vorhaben-ueberschneidung" sx={FUSSNOTE}>
            Eine Karte kann zu mehreren Vorhaben gehören und zählt dann in jedem — die Summen der
            Vorhaben können sich überschneiden.
            {epicsOverlap && ' In diesem Zeitraum ist das der Fall.'}
          </Box>
        </Box>
      </Platte>
    </Box>
  )
}

/** Eine Zeile der Aufstellung (Mockup `.klasse`): Name mit Kartenzahl, Balken, Kosten rechts. */
function Zeile({
  alsPunkt,
  name,
  vorhaben,
  breite,
  farbe,
}: Readonly<{
  alsPunkt: boolean
  name: string | null
  vorhaben: VerbrauchVorhaben
  breite: number | null
  farbe: string
}>) {
  return (
    <Box
      component={alsPunkt ? 'li' : 'div'}
      sx={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto', gap: '10px', alignItems: 'center' }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Box sx={{ fontSize: 12.5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {name}
          <Box component="span" sx={{ fontSize: 11, color: TEXT_SCHWACH }}>
            {` · ${kartenText(vorhaben.cardCount)}`}
          </Box>
        </Box>
        {breite !== null && <Fuellschiene breite={breite} farbe={farbe} />}
      </Box>
      <Box sx={{ ...ZAHL, fontSize: 12.5, textAlign: 'right' }}>
        {kosten(ohneNull(vorhaben.usage.costUsd))}
      </Box>
    </Box>
  )
}

const FUSSNOTE = { fontSize: 11, color: TEXT_SCHWACH, mt: '5px' } as const
