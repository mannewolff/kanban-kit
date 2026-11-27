import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import { useId, useState } from 'react'
import type { CodeAnalysisState, CodeAnalysisView, CodeAnalysisWeek } from '../../api/dashboard'
import { formatierer } from '../../i18n/format'
import { useSprache, useT } from '../../i18n/SpracheContext'
import type { Sprache } from '../../i18n/woerterbuch'
import {
  codeanalyseKopf,
  dichteText,
  grundText,
  juengsteZaehlendeWoche,
  rueckgangText,
  wochenHoehen,
  type DichteHoehe,
} from '../../lib/codeanalyse'
import { kalenderwoche } from '../../lib/leitstand'
import { ANZEIGE, ETIKETT, KUPFER, RAND, TEXT_SCHWACH, ZAHL } from '../../theme'
import { AngabenListe, Grundlinie, LeerSatz, melderFarbe, Platte, WochenLeiste, wochenSpalten, ZEILE_HOVER_SX } from './LeitstandBausteine'

/**
 * Die Farben der beiden Säulen einer Woche. Farbe ist nie der einzige Träger: Die Reihenfolge
 * (vorher links, Endzustand rechts), die Legende und der Vorlesetext jeder Woche sagen dasselbe.
 */
const FARBE_VORHER = melderFarbe('stahl')
const FARBE_ENDZUSTAND = KUPFER

/**
 * Die Platte „Codeanalyse“ (Issue #2280, Plan #2272 E9, E10, E11, E15): oben fest die Zahlen der
 * jüngsten Woche mit zählenden Messungen, darunter die Wochenleiste mit zwei Säulen je Woche. Jede
 * Woche ist eine Schaltfläche; Überfahren, Tastaturfokus und Antippen wählen sie, und der Bereich
 * unter der Leiste zeigt dann alle ihre Zahlen samt den nicht zählenden Messungen (E10). Ohne je
 * angekommene Messung steht nur „noch keine Messung“ — keine Zahl und keine Null (AK 6).
 */
export function CodeanalysePlatte({ daten }: Readonly<{ daten: CodeAnalysisView }>) {
  const t = useT().LEITSTAND_CODEANALYSE
  const { sprache } = useSprache()
  if (!daten.hasMeasurements) {
    return (
      <Platte titel={t.TITEL}>
        <LeerSatz testId="codeanalyse-leer">{t.LEER}</LeerSatz>
      </Platte>
    )
  }
  return (
    <Platte titel={t.TITEL} notiz={codeanalyseKopf(daten.dataSince, sprache)}>
      <Kopfzahlen wochen={daten.weeks} />
      <Wochen wochen={daten.weeks} />
    </Platte>
  )
}

/** Der feste obere Teil (E10): die jüngste zählende Woche, oder ein Satz, wenn keine zählt. */
function Kopfzahlen({ wochen }: Readonly<{ wochen: readonly CodeAnalysisWeek[] }>) {
  const t = useT().LEITSTAND_CODEANALYSE
  const juengste = juengsteZaehlendeWoche(wochen)
  return (
    <Box data-testid="codeanalyse-kopf" sx={{ px: '16px', pt: '14px' }}>
      {juengste === null ? (
        <Box sx={{ fontSize: 13, color: 'text.secondary', py: '4px' }}>{t.KEINE_ZAEHLENDE_WOCHE}</Box>
      ) : (
        <>
          <Box component="h3" sx={{ ...ANZEIGE, m: 0, mb: '8px', fontSize: 12.5, fontWeight: 600 }}>
            {t.JUENGSTE_WOCHE(kalenderwoche(juengste.weekStart))}
          </Box>
          <WochenZahlen woche={juengste} />
        </>
      )}
    </Box>
  )
}

/** Dichten, Funde und Zeilen beider Zustände je Art, darunter Messungen, Karten und Rückgang. */
function WochenZahlen({ woche }: Readonly<{ woche: CodeAnalysisWeek }>) {
  const t = useT().LEITSTAND_CODEANALYSE
  const { sprache } = useSprache()
  const zahl = formatierer(sprache).zahl
  const rueckgang = rueckgangText(woche.reductionPercent, sprache)
  // Die Zahlen stehen nur für eine Woche mit zählender Messung, und dort sind die Karten gesetzt (E9).
  const angaben = [
    { begriff: t.MESSUNGEN, wert: zahl(woche.countingMeasurements), nichtGemeldet: false },
    { begriff: t.KARTEN, wert: zahl(woche.cards!), nichtGemeldet: false },
    ...(rueckgang === null ? [] : [{ begriff: t.RUECKGANG, wert: `${rueckgang} %`, nichtGemeldet: false }]),
  ]
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <DichteTabelle vorher={woche.before} endzustand={woche.final} />
      <AngabenListe zeilen={angaben} />
    </Box>
  )
}

type Art = 'bugs' | 'vulnerabilities' | 'codeSmells' | 'findings'

const DICHTE: Readonly<Record<Art, keyof CodeAnalysisState>> = {
  bugs: 'bugsDensity',
  vulnerabilities: 'vulnerabilitiesDensity',
  codeSmells: 'codeSmellsDensity',
  findings: 'findingsDensity',
}

/** Die Tabelle der Dichten: je Art eine Zeile, je Zustand eine Spalte (AK 1, AK 2). */
function DichteTabelle({
  vorher,
  endzustand,
}: Readonly<{ vorher: CodeAnalysisState | null; endzustand: CodeAnalysisState | null }>) {
  const t = useT().LEITSTAND_CODEANALYSE
  const arten: readonly [Art, string][] = [
    ['bugs', t.FEHLER],
    ['vulnerabilities', t.SICHERHEITSLUECKEN],
    ['codeSmells', t.WARTBARKEITSMAENGEL],
    ['findings', t.SUMME],
  ]
  const kopfZelle = { ...ETIKETT, textAlign: 'left', fontWeight: 500, pb: '4px', pr: '12px' } as const
  return (
    <Box component="table" sx={{ borderCollapse: 'collapse', width: '100%', fontSize: 12 }}>
      <Box component="caption" sx={{ captionSide: 'bottom', textAlign: 'left', fontSize: 11, color: TEXT_SCHWACH, pt: '6px' }}>
        {t.DICHTE_EINHEIT}
      </Box>
      <thead>
        <tr>
          <Box component="th" scope="col" sx={kopfZelle}>
            {t.ART}
          </Box>
          <Box component="th" scope="col" sx={kopfZelle}>
            {t.VORHER}
          </Box>
          <Box component="th" scope="col" sx={kopfZelle}>
            {t.ENDZUSTAND}
          </Box>
        </tr>
      </thead>
      <tbody>
        {arten.map(([art, name]) => (
          <Box component="tr" key={art} sx={{ borderTop: 1, borderColor: RAND, ...(art === 'findings' && { fontWeight: 600 }) }}>
            <Box component="th" scope="row" sx={{ textAlign: 'left', fontWeight: 'inherit', py: '5px', pr: '12px', color: 'text.secondary' }}>
              {name}
            </Box>
            <DichteZelle zustand={vorher} art={art} />
            <DichteZelle zustand={endzustand} art={art} />
          </Box>
        ))}
      </tbody>
    </Box>
  )
}

/** Eine Zelle: die Dichte, daneben ihre Funde und Zeilen; ohne Zeilen der Hinweis statt Dichte (AK 2). */
function DichteZelle({ zustand, art }: Readonly<{ zustand: CodeAnalysisState | null; art: Art }>) {
  const t = useT().LEITSTAND_CODEANALYSE
  const { sprache } = useSprache()
  // Die Tabelle steht nur für eine Woche mit zählender Messung, und dort sind beide Zustände gesetzt.
  const z = zustand!
  const dichte = dichteText(z[DICHTE[art]] as number | null, sprache)
  const zahl = formatierer(sprache).zahl
  return (
    <Box component="td" sx={{ py: '5px', pr: '12px', verticalAlign: 'baseline' }}>
      <Box component="span" sx={{ ...ZAHL, fontSize: 13, color: dichte === null ? TEXT_SCHWACH : 'text.primary' }}>
        {dichte ?? t.KEINE_DICHTE}
      </Box>
      <Box component="span" sx={{ ...ZAHL, fontSize: 10.5, fontWeight: 400, color: TEXT_SCHWACH }}>
        {` · ${t.FUNDE} ${zahl(z[art])} · ${t.ZEILEN} ${zahl(z.changedLines)}`}
      </Box>
    </Box>
  )
}

/**
 * Die Wochenleiste mit ihren Schaltflächen, der Legende und dem Bereich der gewählten Woche (E10).
 * Die Wahl bleibt stehen, wenn der Zeiger die Leiste verlässt — so lässt sich der Bereich lesen.
 */
function Wochen({ wochen }: Readonly<{ wochen: readonly CodeAnalysisWeek[] }>) {
  const t = useT().LEITSTAND_CODEANALYSE
  const { sprache } = useSprache()
  const [gewaehlt, setGewaehlt] = useState<number | null>(null)
  const bereichId = useId()
  const hinweisId = useId()
  const knopfId = useId()
  const hoehen = wochenHoehen(wochen)
  const namen = wochen.map((w) => wochenName(w, sprache, t))
  const gewaehlteWoche = gewaehlt === null ? null : wochen[gewaehlt]
  return (
    <>
      <Box
        role="group"
        aria-label={t.WOCHEN_ARIA(namen.join('; '))}
        sx={{ display: 'grid', gridTemplateColumns: wochenSpalten(wochen.length), alignItems: 'stretch', gap: '6px', height: 112, px: '16px', pt: '18px' }}
      >
        {wochen.map((w, i) => (
          <ButtonBase
            key={w.weekStart}
            id={`${knopfId}-${i}`}
            aria-label={namen[i]}
            aria-pressed={gewaehlt === i}
            aria-controls={bereichId}
            onMouseEnter={() => setGewaehlt(i)}
            onFocus={() => setGewaehlt(i)}
            onClick={() => setGewaehlt(i)}
            sx={{
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'flex-end',
              alignItems: 'stretch',
              height: '100%',
              minWidth: 0,
              px: '2px',
              borderRadius: '5px',
              bgcolor: gewaehlt === i ? ZEILE_HOVER_SX : 'transparent',
              '&.Mui-focusVisible': { outlineWidth: 2, outlineStyle: 'solid', outlineColor: KUPFER, outlineOffset: '1px' },
            }}
          >
            <SaeulenPaar hoehe={hoehen[i]} ohneZeilen={w.countingMeasurements > 0} />
          </ButtonBase>
        ))}
      </Box>
      <WochenLeiste wochen={wochen} />
      <Legende />
      <Box
        id={bereichId}
        role="region"
        aria-labelledby={gewaehlt === null ? hinweisId : `${knopfId}-${gewaehlt}`}
        aria-live="polite"
        data-testid="codeanalyse-woche"
        sx={{ borderTop: 1, borderColor: RAND, px: '16px', py: '14px', minHeight: 48 }}
      >
        {gewaehlteWoche === null ? (
          <Box id={hinweisId} sx={{ fontSize: 12, color: TEXT_SCHWACH }}>
            {t.WOCHE_WAEHLEN}
          </Box>
        ) : (
          <GewaehlteWoche woche={gewaehlteWoche} />
        )}
      </Box>
    </>
  )
}

/** Der Vorlesetext einer Woche: beide Summen-Dichten, sonst die Lücke oder der Hinweis ohne Zeilen. */
function wochenName(
  woche: CodeAnalysisWeek,
  sprache: Sprache,
  t: ReturnType<typeof useT>['LEITSTAND_CODEANALYSE'],
): string {
  const kw = kalenderwoche(woche.weekStart)
  if (woche.countingMeasurements === 0) {
    return t.WOCHE_LUECKE_ARIA(kw)
  }
  // Mit zählender Messung sind beide Zustände gesetzt (E9); nur ihre Dichte kann fehlen.
  const vorher = dichteText(woche.before!.findingsDensity, sprache)
  const endzustand = dichteText(woche.final!.findingsDensity, sprache)
  if (vorher === null && endzustand === null) {
    return t.WOCHE_OHNE_DICHTE_ARIA(kw)
  }
  return t.WOCHE_ARIA(kw, vorher ?? t.KEINE_DICHTE, endzustand ?? t.KEINE_DICHTE)
}

/**
 * Die beiden Säulen einer Woche (vorher, Endzustand). Ohne Dichte steht die Grundlinie, bei
 * Messungen ohne Zeilen mit dem Hinweis darunter; eine Dichte von null ist ebenfalls die Grundlinie.
 * `ohneZeilen` sagt, ob die Woche zählende Messungen hat — es wirkt nur, wenn die Höhe fehlt.
 */
function SaeulenPaar({ hoehe, ohneZeilen }: Readonly<{ hoehe: DichteHoehe | null; ohneZeilen: boolean }>) {
  const t = useT().LEITSTAND_CODEANALYSE
  if (hoehe === null) {
    return (
      <>
        <Grundlinie />
        {ohneZeilen && (
          <Box component="span" aria-hidden sx={{ fontSize: 9, lineHeight: 1.15, color: TEXT_SCHWACH, textAlign: 'center', pt: '3px', overflowWrap: 'anywhere' }}>
            {t.KEINE_DICHTE}
          </Box>
        )}
      </>
    )
  }
  return (
    <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: '2px', height: '100%' }}>
      <Saeule art="vorher" prozent={hoehe.vorher} farbe={FARBE_VORHER} />
      <Saeule art="endzustand" prozent={hoehe.endzustand} farbe={FARBE_ENDZUSTAND} />
    </Box>
  )
}

function Saeule({ art, prozent, farbe }: Readonly<{ art: 'vorher' | 'endzustand'; prozent: number | null; farbe: string }>) {
  return (
    <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', height: '100%' }}>
      {prozent === null || prozent === 0 ? (
        <Grundlinie />
      ) : (
        <Box
          data-testid={`codeanalyse-saeule-${art}-${prozent}`}
          sx={{ height: `${prozent}%`, borderRadius: '3px 3px 1px 1px', bgcolor: farbe }}
        />
      )}
    </Box>
  )
}

/** Die Legende der Säulen: Farbe ist nie der einzige Träger. */
function Legende() {
  const t = useT().LEITSTAND_CODEANALYSE
  const eintraege = [
    { name: t.VORHER, farbe: FARBE_VORHER },
    { name: t.ENDZUSTAND, farbe: FARBE_ENDZUSTAND },
  ]
  return (
    <Box
      data-testid="codeanalyse-legende"
      aria-hidden
      sx={{ display: 'flex', flexWrap: 'wrap', gap: '14px', px: '16px', pb: '14px', fontSize: 11, color: TEXT_SCHWACH }}
    >
      {eintraege.map(({ name, farbe }) => (
        <Box key={name} component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
          <Box component="span" sx={{ width: 9, height: 9, borderRadius: '2px', bgcolor: farbe }} />
          {name}
        </Box>
      ))}
    </Box>
  )
}

/**
 * Alle Zahlen der gewählten Woche samt den nicht zählenden Messungen und ihren Gründen (AK 3, E10).
 * Ohne zählende Messung steht die Lücke statt Karten und Messungen als Zahl (AK 6).
 */
function GewaehlteWoche({ woche }: Readonly<{ woche: CodeAnalysisWeek }>) {
  const t = useT().LEITSTAND_CODEANALYSE
  const { sprache } = useSprache()
  const listeId = useId()
  const kw = kalenderwoche(woche.weekStart)
  const zaehlt = woche.countingMeasurements > 0
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <Box component="h3" sx={{ ...ANZEIGE, m: 0, fontSize: 12.5, fontWeight: 600 }}>
        {zaehlt ? t.WOCHE_TITEL(kw) : t.WOCHE_LUECKE_ARIA(kw)}
      </Box>
      {zaehlt && <WochenZahlen woche={woche} />}
      {woche.notCounting.length > 0 && (
        <Box>
          <Box id={listeId} sx={ETIKETT}>
            {t.NICHT_ZAEHLEND}
          </Box>
          <Box component="ul" aria-labelledby={listeId} sx={{ m: 0, mt: '4px', pl: '18px', fontSize: 12 }}>
            {woche.notCounting.map((n) => (
              <li key={n.reason}>{`${grundText(n.reason, sprache)}: ${formatierer(sprache).zahl(n.count)}`}</li>
            ))}
          </Box>
        </Box>
      )}
    </Box>
  )
}
