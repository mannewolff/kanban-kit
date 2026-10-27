import Box from '@mui/material/Box'
import { useId } from 'react'
import type { NightRunModelRow, NightRunModels, NightRunModelStage } from '../../api/nightRuns'
import { formatierer } from '../../i18n/format'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { erfolgErklaerung, kopfText, kostenText, modellzeitText, quoteText, zeilenName } from '../../lib/modelle'
import { ETIKETT, RAND, TEXT_SCHWACH, ZAHL } from '../../theme'
import { Erklaerung } from '../Erklaerung'
import { LeerSatz, Platte } from './LeitstandBausteine'

/**
 * Die Platte „Modelle“ (Issue #1949, Plan #1905): je Stufe der Kette eine Tabelle mit einer Zeile je
 * Modell und Denktiefe — Vorgänge, Median der Kosten und der Modellzeit je Vorgang, Erfolgsquote.
 *
 * <p>Die Platte ordnet nicht, empfiehlt nichts und hebt keine Zeile hervor (AK 5): Die Zeilen stehen
 * in der Reihenfolge des Backends, alle im selben Stil. Am Kopf „erfolgreich“ erklärt sie je Stufe,
 * was das dort heißt — beim Überfahren, mit dem Tastaturfokus und auf dem Tablet per Antippen.
 */
export function ModellePlatte({ daten }: Readonly<{ daten: NightRunModels }>) {
  const t = useT().LEITSTAND
  const { sprache } = useSprache()
  return (
    <Platte titel={t.MODELLE} notiz={kopfText(t, daten, formatierer(sprache).datum)}>
      {daten.attemptsInPeriod === 0 ? (
        <LeerSatz testId="modelle-kein-vorgang">{t.MODELLE_KEIN_VORGANG}</LeerSatz>
      ) : (
        daten.stages.map((stufe) => <Stufe key={stufe.stage} stufe={stufe} />)
      )}
    </Platte>
  )
}

const ZELLE = { px: '10px', py: '6px', borderBottom: 1, borderColor: RAND, textAlign: 'right', whiteSpace: 'nowrap' } as const
const KOPF = { ...ZELLE, ...ETIKETT, fontWeight: 500, color: TEXT_SCHWACH } as const

/** Ein Stufenblock: Name, dann die Tabelle oder der Satz einer leeren Stufe (AK 1). */
function Stufe({ stufe }: Readonly<{ stufe: NightRunModelStage }>) {
  const t = useT().LEITSTAND
  const titelId = useId()
  return (
    <Box sx={{ pt: '10px', '& + &': { borderTop: 1, borderColor: RAND } }}>
      <Box component="h3" id={titelId} sx={{ m: 0, px: '16px', fontSize: 12.5, fontWeight: 500 }}>
        {t.MODELLE_STUFE[stufe.stage]}
      </Box>
      {stufe.rows.length === 0 ? (
        <Box sx={{ px: '16px', pt: '4px', pb: '10px', fontSize: 12, color: 'text.secondary' }}>{t.MODELLE_STUFE_LEER}</Box>
      ) : (
        <Box sx={{ overflowX: 'auto', px: '6px', pb: '8px' }}>
          <Box component="table" aria-labelledby={titelId} sx={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
            <thead>
              <tr>
                <Box component="th" scope="col" sx={{ ...KOPF, textAlign: 'left' }}>
                  {t.MODELLE_SPALTE_MODELL}
                </Box>
                <Box component="th" scope="col" sx={KOPF}>
                  {t.MODELLE_SPALTE_VORGAENGE}
                </Box>
                <Box component="th" scope="col" sx={KOPF}>
                  {t.MODELLE_SPALTE_KOSTEN}
                </Box>
                <Box component="th" scope="col" sx={KOPF}>
                  {t.MODELLE_SPALTE_MODELLZEIT}
                </Box>
                <Box component="th" scope="col" sx={KOPF}>
                  <Erklaerung title={erfolgErklaerung(t, stufe.stage)} describeChild>
                    <Box component="span" tabIndex={0} sx={{ cursor: 'help', borderBottom: 1, borderBottomStyle: 'dotted', borderColor: TEXT_SCHWACH }}>
                      {t.MODELLE_SPALTE_ERFOLGREICH}
                    </Box>
                  </Erklaerung>
                </Box>
              </tr>
            </thead>
            <tbody>
              {stufe.rows.map((zeile) => (
                <Zeile key={`${zeile.model}\u0000${zeile.effort ?? ''}`} zeile={zeile} />
              ))}
            </tbody>
          </Box>
        </Box>
      )}
    </Box>
  )
}

/** Eine Zeile: Name als Zeilenkopf, danach die Werte; alle Zeilen im selben Stil. */
function Zeile({ zeile }: Readonly<{ zeile: NightRunModelRow }>) {
  const t = useT().LEITSTAND
  const f = formatierer(useSprache().sprache)
  return (
    <tr>
      <Box component="th" scope="row" sx={{ ...ZELLE, ...ZAHL, textAlign: 'left', fontWeight: 400 }}>
        {zeilenName(t, zeile)}
      </Box>
      <Box component="td" sx={{ ...ZELLE, ...ZAHL }}>
        {f.zahl(zeile.attempts)}
      </Box>
      <Box component="td" sx={{ ...ZELLE, ...ZAHL }}>
        {kostenText(t, f, zeile)}
      </Box>
      <Box component="td" sx={{ ...ZELLE, ...ZAHL }}>
        {modellzeitText(t, f, zeile)}
      </Box>
      <Box component="td" sx={{ ...ZELLE, color: zeile.enoughData ? 'text.primary' : 'text.secondary' }}>
        {quoteText(t, zeile)}
      </Box>
    </tr>
  )
}
