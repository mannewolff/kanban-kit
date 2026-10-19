import Box from '@mui/material/Box'
import type { EpicForecast, EpicForecastEntry } from '../../api/dashboard'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { epicColor } from '../../lib/epicMeta'
import { hinweisText, paketZahl, zeilenText } from '../../lib/prognose'
import { KUPFER, TEXT_SCHWACH, ZAHL } from '../../theme'
import { Erklaerung } from '../Erklaerung'
import { Fuellschiene, LeerSatz, Platte } from './LeitstandBausteine'

/** Füllung der Schiene in Prozent; ein Vorhaben ohne Pakete bleibt leer statt `NaN` (E11). */
function fuellung(v: EpicForecastEntry): number {
  return v.total === 0 ? 0 : Math.round((v.finished / v.total) * 100)
}

/**
 * Die Platte „Vorhaben“ (Issue #1829, Plan #1819 A8, A9, E10, E11): jedes nicht archivierte Vorhaben
 * mit Titel, Füllschiene und ‹fertig›/‹gesamt› nach der Zählung der Prognose, darunter die
 * Prognosezeile. Nur mit Daten folgt „‹n› offene Pakete“, und die Zeile trägt den Hinweis, der beim
 * Überfahren, beim Fokus oder auf dem Tablet per Antippen erscheint.
 */
export function VorhabenPlatte({ prognose }: Readonly<{ prognose: EpicForecast }>) {
  const t = useT().LEITSTAND
  return (
    <Platte titel={t.VORHABEN} notiz={t.OFFEN}>
      {prognose.epics.length === 0 ? (
        <LeerSatz testId="vorhaben-leer">{t.KEIN_VORHABEN}</LeerSatz>
      ) : (
        <Box component="ul" sx={{ listStyle: 'none', m: 0, display: 'flex', flexDirection: 'column', gap: '9px', px: '16px', pt: '14px', pb: '16px' }}>
          {prognose.epics.map((v) => (
            <Box component="li" key={v.id}>
              <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto', gap: '10px', alignItems: 'center' }}>
                <div>
                  <Box sx={{ fontSize: 12.5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{v.title}</Box>
                  <Fuellschiene breite={fuellung(v)} farbe={epicColor(v.id)} />
                </div>
                <Box sx={{ ...ZAHL, fontSize: 12.5, textAlign: 'right' }}>{`${v.finished}/${v.total}`}</Box>
              </Box>
              <Prognosezeile vorhaben={v} />
            </Box>
          ))}
        </Box>
      )}
    </Platte>
  )
}

/** Die zweite Zeile eines Vorhabens (E10): Prognose, mit Daten dahinter die Zahl der offenen Pakete. */
function Prognosezeile({ vorhaben }: Readonly<{ vorhaben: EpicForecastEntry }>) {
  const { sprache } = useSprache()
  const text = zeilenText(sprache, vorhaben)
  const hinweis = hinweisText(sprache, vorhaben)
  return (
    <Box data-testid={`prognose-${vorhaben.number}`} sx={{ pt: '4px', fontSize: 11.5, color: TEXT_SCHWACH }}>
      {hinweis === null ? (
        text
      ) : (
        <Erklaerung title={hinweis} describeChild>
          <Box
            component="span"
            tabIndex={0}
            data-testid={`prognose-hinweis-${vorhaben.number}`}
            sx={{
              cursor: 'help',
              textDecoration: 'underline',
              textDecorationStyle: 'dotted',
              textUnderlineOffset: 3,
              '&:focus-visible': { outlineWidth: 2, outlineStyle: 'solid', outlineColor: KUPFER, outlineOffset: 2 },
            }}
          >
            {`${text} · ${paketZahl(sprache, vorhaben.open)}`}
          </Box>
        </Erklaerung>
      )}
    </Box>
  )
}
