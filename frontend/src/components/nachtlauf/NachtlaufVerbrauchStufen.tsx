import Box from '@mui/material/Box'
import type { VerbrauchStufe } from '../../api/nightRunUsage'
import type { NightRunStage } from '../../api/nightRuns'
import { kosten, ohneNull } from '../../lib/nachtlaufFormat'
import { KUPFER, TEXT_SCHWACH, ZAHL } from '../../theme'
import { Fuellschiene, Platte } from '../leitstand/LeitstandBausteine'

/**
 * Die Aufstellung der Kosten je Stufe der Kette (Issue #1117, #993 AK 8) — gebaut nach dem Muster
 * von {@link NachtlaufVerbrauchVorhaben}, damit dieselbe Sache in derselben Ansicht nicht zweimal
 * verschieden aussieht.
 *
 * <p><b>Die Reihenfolge ist die des Servers</b>: die der Kette, vom Plan bis zur Abdeckung
 * (`NightRunRepositoryAdapter`). Hier wird nicht nachsortiert — eine zweite Sortierung liefe beim
 * nächsten Schritt der Kette anders.
 *
 * <p><b>Der Balken zeigt das Verhältnis zur teuersten Stufe.</b> Wo die Kosten nicht gemessen
 * wurden, bleibt die Schiene weg und die Zahl steht als Fehlanzeige aus `nachtlaufFormat.ts`
 * (Plan E16): Eine leere Schiene und eine 0 läsen sich beide als „hat nichts gekostet".
 *
 * <p><b>Kein Posten „ohne Stufe"</b> nach dem Muster von „ohne Vorhaben" (Plan E6): Er trüge bei
 * einem Umsetzungs-Lauf den Verbrauch einer ganzen Nacht, und diese Aufstellung handelt von der
 * Kette. Aus demselben Grund erscheint sie ohne Stufen gar nicht, statt als leere Platte zu
 * behaupten, eine Kette sei gelaufen und habe nichts gebraucht.
 */
export function NachtlaufVerbrauchStufen({
  stufen,
}: Readonly<{ stufen: readonly VerbrauchStufe[] }>) {
  if (stufen.length === 0) {
    return null
  }
  const hoechste = Math.max(0, ...stufen.map((stufe) => stufe.usage.costUsd ?? 0))
  const breite = (costUsd: number | null): number | null =>
    costUsd === null || hoechste === 0 ? null : Math.round((costUsd / hoechste) * 100)

  return (
    <Box data-testid="verbrauch-stufen">
      <Platte titel="Stufen der Kette" notiz="Kosten je Stufe">
        <Box
          component="ol"
          data-testid="verbrauch-stufen-liste"
          sx={{
            listStyle: 'none',
            m: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: '9px',
            px: '16px',
            pt: '14px',
            pb: '16px',
          }}
        >
          {stufen.map((stufe) => (
            <Zeile key={stufe.stage} stufe={stufe} breite={breite(stufe.usage.costUsd)} />
          ))}
        </Box>
      </Platte>
    </Box>
  )
}

/** Eine Zeile der Aufstellung: Name mit Vorgangszahl, Balken, Kosten rechts. */
function Zeile({
  stufe,
  breite,
}: Readonly<{ stufe: VerbrauchStufe; breite: number | null }>) {
  return (
    <Box
      component="li"
      sx={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0,1fr) auto',
        gap: '10px',
        alignItems: 'center',
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Box
          sx={{ fontSize: 12.5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
        >
          {STUFEN_LABEL[stufe.stage]}
          <Box component="span" sx={{ fontSize: 11, color: TEXT_SCHWACH }}>
            {` · ${vorgaengeText(stufe.itemCount)}`}
          </Box>
        </Box>
        {breite !== null && <Fuellschiene breite={breite} farbe={KUPFER} />}
      </Box>
      <Box sx={{ ...ZAHL, fontSize: 12.5, textAlign: 'right' }}>
        {kosten(ohneNull(stufe.usage.costUsd))}
      </Box>
    </Box>
  )
}

/**
 * Die deutschen Namen der vier Stufen — dieselben Worte wie im Stufenband der Seite
 * (`NightRunPage`, `KETTEN_STUFEN`). Als vollständiger Record und nicht als Suche mit Rückfall:
 * Eine fünfte Stufe der Kette bräuchte hier eine Benennung, statt still als Schlüssel
 * durchzurutschen.
 */
const STUFEN_LABEL: Readonly<Record<NightRunStage, string>> = {
  PLAN: 'Plan',
  REVIEW: 'Prüfung',
  PAKETE: 'Pakete',
  ABDECKUNG: 'Abdeckung',
}

/** „1 Vorgang" bzw. „n Vorgänge" — die Zahl der Vorgänge, die diese Stufe durchlaufen haben. */
const vorgaengeText = (anzahl: number): string =>
  anzahl === 1 ? '1 Vorgang' : `${anzahl} Vorgänge`
