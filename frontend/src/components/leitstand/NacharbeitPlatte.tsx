import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import { useId, useState } from 'react'
import type { NightRunRework, NightRunReworkCard, NightRunReworkWeek, NightRunReworkWeekValue } from '../../api/nightRuns'
import { formatierer } from '../../i18n/format'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { kalenderwoche } from '../../lib/leitstand'
import { balkenZustand, dokumentSeit, kopfText, wochenHinweis, zeilenZustand, type ZeilenZustand } from '../../lib/nacharbeit'
import { KUPFER, MELDER, RAND, TEXT_SCHWACH, ZAHL } from '../../theme'
import { Erklaerung } from '../Erklaerung'
import { Grundlinie, LeerSatz, Platte, Taste, WochenLeiste, wochenSpalten } from './LeitstandBausteine'

/**
 * Die Platte „Nacharbeit“ (Issue #1759, Plan #1752): wie oft die Runs Pakete aus In Progress
 * zurückzogen und Dokumente anhielten, gemessen an den Karten, die sie bearbeitet haben.
 *
 * <p>Zwei Zeilen mit Quote; die Quote ist eine Taste, die die betroffenen Karten unter der Zeile
 * auf- und zuklappt (E14). Darunter der Verlauf als Balkenpaar je Woche — Pakete in Kupfer wie
 * „Durchsatz“, Dokumente in Stahl — mit drei sichtbar verschiedenen Zuständen (E13): ein Balken
 * (0 % als 2 px hoher Stummel), die gestrichelte Grundlinie, oder ein leerer Platz ohne Daten.
 * Die Kalenderwochen kommen aus den eigenen `weeks[].weekStart` (E6).
 */
export function NacharbeitPlatte({
  daten,
  onOeffnen,
}: Readonly<{ daten: NightRunRework; onOeffnen: (nummer: number) => void }>) {
  const t = useT().LEITSTAND
  const { sprache } = useSprache()
  const datum = formatierer(sprache).datum
  const pakete = zeilenZustand(daten.runsInPeriod, daten.packages)
  return (
    <Platte titel={t.NACHARBEIT} notiz={kopfText(t, daten, datum)}>
      {pakete.art === 'keineRuns' ? (
        <LeerSatz testId="nacharbeit-keine-runs">{t.NACHARBEIT_KEINE_RUNS}</LeerSatz>
      ) : (
        <>
          <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, pt: '6px' }}>
            <Zeile
              gruppe="pakete"
              titel={t.NACHARBEIT_PAKETE}
              zustand={pakete}
              satzOhneKarte={t.NACHARBEIT_KEIN_PAKET}
              anzahl={t.NACHARBEIT_ZURUECKGEZOGEN}
              karten={daten.packages.cards}
              farbe={KUPFER}
              onOeffnen={onOeffnen}
            />
            <Zeile
              gruppe="dokumente"
              titel={t.NACHARBEIT_DOKUMENTE}
              seit={dokumentSeit(t, daten, datum)}
              zustand={zeilenZustand(daten.runsInPeriod, daten.documents)}
              satzOhneKarte={t.NACHARBEIT_KEIN_DOKUMENT}
              anzahl={t.NACHARBEIT_ANGEHALTEN}
              karten={daten.documents.cards}
              farbe={MELDER.stahl}
              onOeffnen={onOeffnen}
            />
          </Box>
          <Verlauf wochen={daten.weeks} />
        </>
      )}
    </Platte>
  )
}

type Gruppe = 'pakete' | 'dokumente'

/** Eine Zeile der Platte: Name, Quote als Taste oder Satz statt „0 von 0“ (AK 6), Kartenliste. */
function Zeile({
  gruppe,
  titel,
  seit = null,
  zustand,
  satzOhneKarte,
  anzahl,
  karten,
  farbe,
  onOeffnen,
}: Readonly<{
  gruppe: Gruppe
  titel: string
  seit?: string | null
  zustand: ZeilenZustand
  satzOhneKarte: string
  anzahl: (anzahl: number) => string
  karten: readonly NightRunReworkCard[]
  farbe: string
  onOeffnen: (nummer: number) => void
}>) {
  const t = useT().LEITSTAND
  const [offen, setOffen] = useState(false)
  const listeId = useId()
  let rechts
  if (zustand.art === 'quote') {
    const quote = t.NACHARBEIT_QUOTE(zustand.betroffen, zustand.bearbeitet)
    // Ohne betroffene Karte gäbe die Taste eine leere Liste frei — die Quote steht dann als Text.
    rechts =
      zustand.betroffen > 0 ? (
        <Taste
          ariaLabel={t.NACHARBEIT_QUOTE_ARIA(titel, quote)}
          ariaExpanded={offen}
          ariaControls={listeId}
          onClick={() => setOffen((war) => !war)}
        >
          <Box component="span" sx={ZAHL}>
            {quote}
          </Box>
        </Taste>
      ) : (
        <Box component="span" sx={{ ...ZAHL, fontSize: 11.5, color: 'text.secondary', px: '10px' }}>
          {quote}
        </Box>
      )
  } else {
    rechts = (
      <Box component="span" sx={{ fontSize: 12, color: 'text.secondary' }}>
        {satzOhneKarte}
      </Box>
    )
  }
  return (
    <Box component="li" data-testid={`nacharbeit-zeile-${gruppe}`}>
      <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '8px', px: '16px', py: '7px' }}>
        <Box component="span" aria-hidden sx={{ width: 9, height: 9, borderRadius: '2px', bgcolor: farbe, flex: 'none' }} />
        <Box component="span" sx={{ fontSize: 12.5, fontWeight: 500 }}>
          {titel}
        </Box>
        {seit !== null && (
          <Box component="span" sx={{ fontSize: 11, color: TEXT_SCHWACH }}>
            {seit}
          </Box>
        )}
        <Box sx={{ ml: 'auto' }}>{rechts}</Box>
      </Box>
      {offen && (
        <Box
          component="ul"
          id={listeId}
          data-testid={`nacharbeit-karten-${gruppe}`}
          sx={{ listStyle: 'none', m: 0, pl: '33px', pr: '16px', pb: '8px' }}
        >
          {karten.map((karte) => (
            <Box component="li" key={karte.number} sx={{ display: 'flex', alignItems: 'baseline', gap: '6px', py: '2px', fontSize: 12 }}>
              <ButtonBase
                aria-label={t.KARTE_OEFFNEN_ARIA(karte.number, karte.title)}
                onClick={() => onOeffnen(karte.number)}
                sx={{ ...ZAHL, fontSize: 12, color: 'text.primary', textDecoration: 'underline', '&:hover': { color: KUPFER } }}
              >
                {`#${karte.number}`}
              </ButtonBase>
              <Box component="span" sx={{ ...ZAHL, color: 'text.secondary', whiteSpace: 'nowrap' }}>
                {` · ${anzahl(karte.count)}`}
              </Box>
              <Box component="span" sx={{ color: TEXT_SCHWACH, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {` ${karte.title}`}
              </Box>
            </Box>
          ))}
        </Box>
      )}
    </Box>
  )
}

/** Der Verlauf: je Woche ein Balkenpaar, beim Überfahren die Zahlen dahinter (AK 5, 14). */
function Verlauf({ wochen }: Readonly<{ wochen: readonly NightRunReworkWeek[] }>) {
  const t = useT().LEITSTAND
  return (
    <>
      <Box
        role="group"
        aria-label={t.NACHARBEIT_VERLAUF_ARIA(wochen.length)}
        data-testid="nacharbeit-verlauf"
        sx={{ display: 'grid', gridTemplateColumns: wochenSpalten(wochen.length), alignItems: 'end', gap: '6px', height: 104, px: '16px', pt: '14px', mt: '6px', borderTop: 1, borderColor: RAND }}
      >
        {wochen.map((woche, i) => {
          const hinweis = wochenHinweis(t, woche)
          // Auf dem Tablet quer öffnet Antippen den Hinweis (Plan #1707, E7).
          return (
            <Erklaerung key={woche.weekStart} nativ title={hinweis}>
              <Box
                role="img"
                aria-label={t.NACHARBEIT_WOCHE_ARIA(kalenderwoche(woche.weekStart), hinweis)}
                data-testid={`nacharbeit-woche-${i}`}
                sx={{ display: 'flex', alignItems: 'flex-end', gap: '3px', height: '100%' }}
              >
                <Saeule gruppe="pakete" index={i} wert={woche.packages} farbe={KUPFER} />
                <Saeule gruppe="dokumente" index={i} wert={woche.documents} farbe={MELDER.stahl} />
              </Box>
            </Erklaerung>
          )
        })}
      </Box>
      <WochenLeiste wochen={wochen} />
    </>
  )
}

/** Eine Hälfte des Balkenpaars in einem der drei Zustände (E13). */
function Saeule({
  gruppe,
  index,
  wert,
  farbe,
}: Readonly<{ gruppe: Gruppe; index: number; wert: NightRunReworkWeekValue; farbe: string }>) {
  const zustand = balkenZustand(wert)
  const saeule = { flex: 1, minWidth: 0, height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end' } as const
  if (zustand.art === 'keineDaten') {
    return <Box data-testid={`nacharbeit-leer-${gruppe}-${index}`} sx={saeule} />
  }
  if (zustand.art === 'grundlinie') {
    return (
      <Box sx={saeule}>
        <Grundlinie />
      </Box>
    )
  }
  return (
    <Box sx={saeule}>
      <Box
        data-testid={`nacharbeit-balken-${gruppe}-${index}`}
        data-prozent={zustand.prozent}
        sx={{
          // 0 % bleibt als 2 px hoher Stummel sichtbar — ein Balken ohne Höhe sähe aus wie keine Daten.
          height: `${zustand.prozent}%`,
          minHeight: '2px',
          borderRadius: '3px 3px 1px 1px',
          bgcolor: farbe,
        }}
      />
    </Box>
  )
}
