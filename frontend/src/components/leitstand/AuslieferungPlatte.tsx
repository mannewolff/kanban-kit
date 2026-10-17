import Box from '@mui/material/Box'
import type { ReactNode } from 'react'
import type { DeliveryView } from '../../api/dashboard'
import { formatierer } from '../../i18n/format'
import { useSprache, useT } from '../../i18n/SpracheContext'
import {
  auslieferungKopf,
  ausfallText,
  frequenzEinheit,
  kennzahlDauer,
  prozentWert,
  wochenHoehen,
  type WochenHoehe,
} from '../../lib/auslieferung'
import { kalenderwoche } from '../../lib/leitstand'
import { useJetzt } from '../../lib/useJetzt'
import { ETIKETT, NUT, SCHATTEN_NUTE, TEXT_SCHWACH } from '../../theme'
import { Grundlinie, KACHEL_SX, KachelWert, LeerSatz, Led, melderFarbe, Platte, WochenLeiste, wochenSpalten } from './LeitstandBausteine'

/** Ein laufender Ausfall wird je Minute nachgerechnet; seine Anzeige hat volle Stunden. */
const TAKT_MS = 60_000

/** Lücke zwischen Erfolg und abgesetztem Fehlschlag einer Woche, in Pixeln. */
const ABSATZ = 3

/**
 * Die Platte „Auslieferung“ (Issue #1836, Plan #1831 E9, E10, E11, E15): die vier DORA-Kennzahlen
 * nebeneinander, jede mit ihrem Hinweis, darunter die Wochenleiste der Versuche. Ohne je gemeldeten
 * Versuch steht nur „noch keine Auslieferung“ — keine Kachel und keine Null (AK 8).
 */
export function AuslieferungPlatte({ daten }: Readonly<{ daten: DeliveryView }>) {
  const t = useT().LEITSTAND_AUSLIEFERUNG
  const { sprache } = useSprache()
  if (!daten.hasReports) {
    return (
      <Platte titel={t.TITEL}>
        <LeerSatz testId="auslieferung-leer">{t.LEER}</LeerSatz>
      </Platte>
    )
  }
  const leadTime = daten.leadTimeMedianSeconds === null ? null : kennzahlDauer(daten.leadTimeMedianSeconds, sprache)
  const restore = daten.timeToRestoreMedianSeconds === null ? null : kennzahlDauer(daten.timeToRestoreMedianSeconds, sprache)
  return (
    <Platte titel={t.TITEL} notiz={auslieferungKopf(daten.dataSince, sprache)}>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))', lg: 'repeat(4, minmax(0,1fr))' },
          gap: '14px',
          perspective: '1100px',
          px: '16px',
          pt: '16px',
        }}
      >
        <Kennzahl
          titel={t.FREQUENZ}
          wert={formatierer(sprache).zahl(daten.deploymentCount)}
          einheit={frequenzEinheit(daten.deploymentCount, daten.dataSince, sprache)}
          hinweis={t.FREQUENZ_HINWEIS}
        />
        <Kennzahl
          titel={t.LEAD_TIME}
          wert={leadTime?.wert ?? null}
          einheit={leadTime?.einheit ?? ''}
          leerText={t.KEINE_GRUNDLAGE}
          hinweis={t.LEAD_TIME_HINWEIS}
        />
        <Kennzahl
          titel={t.CHANGE_FAILURE_RATE}
          wert={daten.changeFailurePercent === null ? null : prozentWert(daten.changeFailurePercent, sprache)}
          einheit="%"
          leerText={t.KEINE_GRUNDLAGE}
          hinweis={t.CHANGE_FAILURE_RATE_HINWEIS}
        />
        <Kennzahl
          titel={t.TIME_TO_RESTORE}
          wert={restore?.wert ?? null}
          einheit={restore?.einheit ?? ''}
          leerText={t.KEIN_AUSFALL}
          hinweis={t.TIME_TO_RESTORE_HINWEIS}
        >
          {daten.outageSince !== null && <LaufenderAusfall seit={daten.outageSince} />}
        </Kennzahl>
      </Box>
      <Wochen daten={daten} />
    </Platte>
  )
}

/** Eine Kachel der Platte: Etikett, Wert, Hinweis, was gezählt wird (AK 7), und ein optionaler Zusatz. */
function Kennzahl({
  titel,
  wert,
  einheit,
  leerText,
  hinweis,
  children,
}: Readonly<{ titel: string; wert: string | null; einheit: string; leerText?: string; hinweis: string; children?: ReactNode }>) {
  return (
    <Box component="article" aria-label={titel} sx={KACHEL_SX}>
      <Box sx={ETIKETT}>{titel}</Box>
      <KachelWert wert={wert} einheit={einheit} leerText={leerText} />
      {children}
      <Box data-testid="auslieferung-hinweis" sx={{ mt: 'auto', fontSize: 11, lineHeight: 1.45, color: TEXT_SCHWACH }}>
        {hinweis}
      </Box>
    </Box>
  )
}

/** Der laufende Ausfall neben der Time to Restore, hervorgehoben in Zinnober (E9, AK 6). */
function LaufenderAusfall({ seit }: Readonly<{ seit: string }>) {
  const { sprache } = useSprache()
  const jetzt = useJetzt(TAKT_MS)
  const farbe = melderFarbe('zinnob')
  return (
    <Box
      data-testid="auslieferung-ausfall"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '8px',
        alignSelf: 'flex-start',
        fontSize: 12,
        fontWeight: 600,
        color: farbe,
        px: '8px',
        py: '4px',
        borderRadius: '6px',
        bgcolor: NUT,
        border: 1,
        borderColor: farbe,
        boxShadow: SCHATTEN_NUTE,
      }}
    >
      <Led melder="zinnob" />
      {ausfallText(seit, jetzt, sprache)}
    </Box>
  )
}

/**
 * Die Wochenleiste (E11, AK 14): je Woche die erfolgreichen Versuche und, abgesetzt darüber, die
 * gescheiterten; eine Woche ohne Versuch trägt die Grundlinie. Vorgelesen wird das Ganze über sein
 * `aria-label`, die Farben erklärt die Legende.
 */
function Wochen({ daten }: Readonly<{ daten: DeliveryView }>) {
  const t = useT().LEITSTAND_AUSLIEFERUNG
  const hoehen = wochenHoehen(daten.weeks)
  const vorgelesen = daten.weeks
    .map((w) => t.WOCHE_ARIA(kalenderwoche(w.weekStart), w.successCount, w.failureCount))
    .join('; ')
  return (
    <>
      <Box
        role="img"
        aria-label={t.WOCHEN_ARIA(vorgelesen)}
        sx={{ display: 'grid', gridTemplateColumns: wochenSpalten(daten.weeks.length), alignItems: 'end', gap: '6px', height: 96, px: '16px', pt: '18px' }}
      >
        {daten.weeks.map((w, i) => (
          <Box
            key={w.weekStart}
            data-testid={`auslieferung-woche-${i}`}
            sx={{ display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', height: '100%' }}
          >
            <WochenSaeule hoehe={hoehen[i]} />
          </Box>
        ))}
      </Box>
      <WochenLeiste wochen={daten.weeks} />
      <Legende />
    </>
  )
}

/** Eine Säule der Wochenleiste: oben der Fehlschlag, durch einen Absatz getrennt, darunter der Erfolg. */
function WochenSaeule({ hoehe }: Readonly<{ hoehe: WochenHoehe | null }>) {
  if (hoehe === null) {
    return <Grundlinie />
  }
  const beide = hoehe.erfolg > 0 && hoehe.fehlschlag > 0
  const anteil = (prozent: number) => `calc((100% - ${beide ? ABSATZ : 0}px) * ${prozent / 100})`
  return (
    <>
      {hoehe.fehlschlag > 0 && (
        <Box data-testid={`auslieferung-fehlschlag-${hoehe.fehlschlag}`} sx={saeuleSx('zinnob', anteil(hoehe.fehlschlag))} />
      )}
      {hoehe.erfolg > 0 && (
        <Box
          data-testid={`auslieferung-erfolg-${hoehe.erfolg}`}
          sx={{ ...saeuleSx('gruen', anteil(hoehe.erfolg)), mt: beide ? `${ABSATZ}px` : 0 }}
        />
      )}
    </>
  )
}

const saeuleSx = (melder: 'gruen' | 'zinnob', hoehe: string) => ({
  height: hoehe,
  borderRadius: '3px 3px 1px 1px',
  bgcolor: melderFarbe(melder),
})

/** Die Legende der Wochenleiste: Farbe ist nie der einzige Träger. */
function Legende() {
  const t = useT().LEITSTAND_AUSLIEFERUNG
  return (
    <Box
      data-testid="auslieferung-legende"
      aria-hidden
      sx={{ display: 'flex', gap: '14px', px: '16px', pb: '14px', fontSize: 11, color: TEXT_SCHWACH }}
    >
      {(['gruen', 'zinnob'] as const).map((melder) => (
        <Box key={melder} component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
          <Box component="span" sx={{ width: 9, height: 9, borderRadius: '2px', bgcolor: melderFarbe(melder) }} />
          {melder === 'gruen' ? t.ERFOLGREICH : t.GESCHEITERT}
        </Box>
      ))}
    </Box>
  )
}
