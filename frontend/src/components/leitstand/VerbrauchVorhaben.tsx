import Box from '@mui/material/Box'
import type { VerbrauchVorhaben as Posten } from '../../api/nightRunUsage'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { kostenText } from '../../lib/leitstand'
import { TEXT_SCHWACH, ZAHL } from '../../theme'
import { Platte } from './LeitstandBausteine'

/**
 * Die Kosten je Vorhaben im Bereich „Verbrauch" (Issue #2000, Plan #1990, fachlich #1954 AK 1):
 * je Vorhaben „Name Betrag" in der Reihenfolge des Servers — das teuerste zuerst —, darunter der
 * Posten „ohne Vorhaben". Jede Karte zählt beim Server genau einmal, und nur ihr Anteil aus
 * Nachtläufen; Sitzungen stehen weiter in den Kacheln darüber.
 *
 * Ohne Posten erscheint nichts: Ein Vorhaben ohne Messung fehlt schon in der Antwort, und
 * „ohne Vorhaben" ist `null`, wenn dafür nichts gemessen wurde. Fehlende Kosten stehen als Strich,
 * nie als 0.
 */
export function VerbrauchVorhaben({ epics, withoutEpic }: Readonly<{ epics: Posten[]; withoutEpic: Posten | null }>) {
  const t = useT().LEITSTAND_VERBRAUCH
  if (epics.length === 0 && withoutEpic === null) return null
  return (
    <Platte titel={t.VORHABEN_TITEL}>
      <Box component="ul" sx={{ listStyle: 'none', m: 0, display: 'flex', flexDirection: 'column', gap: '7px', px: '16px', pt: '12px', pb: '14px' }}>
        {epics.map((v) => (
          <Zeile key={v.epicId} name={v.title} zusatz={v.archived ? t.VORHABEN_ARCHIVIERT : null} usd={v.usage.costUsd} />
        ))}
        {withoutEpic !== null && <Zeile name={t.VORHABEN_OHNE} zusatz={null} usd={withoutEpic.usage.costUsd} schwach />}
      </Box>
    </Platte>
  )
}

function Zeile({
  name,
  zusatz,
  usd,
  schwach = false,
}: Readonly<{ name: string | null; zusatz: string | null; usd: number | null; schwach?: boolean }>) {
  const { sprache } = useSprache()
  return (
    <Box component="li" sx={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto', gap: '10px', alignItems: 'baseline', fontSize: 12.5 }}>
      <Box component="span" sx={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', color: schwach ? TEXT_SCHWACH : 'text.primary' }}>
        {name}
        {zusatz !== null && (
          <Box component="span" sx={{ color: TEXT_SCHWACH }}>
            {` · ${zusatz}`}
          </Box>
        )}
      </Box>
      <Box component="span" sx={{ ...ZAHL, textAlign: 'right' }}>
        {kostenText(usd, sprache) ?? '—'}
      </Box>
    </Box>
  )
}
