import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { dashboardApi, type DashboardApi, type ImplementationTime } from '../../api/dashboard'
import {
  nightRunUsageApi,
  type NightRunUsageApi,
  type VerbrauchAngaben,
  type VerbrauchBremsen,
  type VerbrauchGesamt,
  type VerbrauchKennzahlen,
  type VerbrauchNachtKurz,
  type VerbrauchZeitraum,
  type VerbrauchZeitraumArt,
} from '../../api/nightRunUsage'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { woerterbuch, type Sprache } from '../../i18n/woerterbuch'
import { dollar, implementierungKachel, kostenText, laufDauer, tokenMenge, tokenText } from '../../lib/leitstand'
import {
  erfassungsstand,
  laeufeText,
  sitzungenText,
  vergleichMitVorzeitraum,
  zeitpunktDatum,
  zeitraumBeschriftung,
  zeitraumHinweis,
  type Erfassungsstand,
} from '../../lib/verbrauchZeitraum'
import { ETIKETT, KUPFER, KUPFER_HELL, MELDER, NUT, PLATTE, PLATTE_HOCH, RAND, SCHATTEN_NUTE, SCHATTEN_TASTE, SCHRIFT_ANZEIGE, TEXT_SCHWACH, ZAHL } from '../../theme'
import { Erklaerung } from '../Erklaerung'
import { DeltaMarke, Funke, Kachel, KACHEL_SX, KachelFuss, KachelWert } from './LeitstandBausteine'

/**
 * Verbrauch mit Zeitraum-Wahl (#979, Entwurf Z. 977–1017, 1340–1398): Eingabe-Token mit
 * Stapelbalken, Ausgabe-Token, Kosten und Implementierungszeit. Gespeist aus
 * `nightRunUsageApi.period` (#939) bzw. `.total` und `dashboardApi.implementationTime` (#1540).
 *
 * **Schicht · Woche · Monat · Gesamt.** Der Entwurf zeigt zusätzlich „Tag"; die Anwendung rechnet
 * den Tag als Nacht (Tagesgrenze 12:00, #969) — „Tag" und „Nacht" zeigten dieselben Zahlen.
 * „Gesamt" (Issue #1541) ersetzt die frühere Dauerkachel mit der Summe über die ganze Laufzeit,
 * die neben den Zeitraum-Zahlen stand und so wirkte, als hinge sie an der Wahl. Unter „Gesamt" gelten dieselben
 * Kacheln; es fehlen nur, was einen Zeitraum braucht: Vorzeitraum-Vergleich, Schichtverlauf und
 * Schichtzahl. Der Fuß der Kosten nennt dann, ab wann die Summe abgedeckt ist. Die Summe wird erst
 * bei dieser Wahl geholt.
 *
 * **Implementierungszeit** gilt für die Karten **dieses Boards**, die im Zeitraum fertig wurden —
 * der Verbrauch daneben zählt projektweit. Sie wird erst geholt, wenn der Verbrauch steht, mit
 * dessen Grenzen; unter „Gesamt" ohne Grenzen. Scheitert sie, bleiben die Verbrauchs-Kacheln
 * stehen; scheitert der Verbrauch, entfällt sie mit.
 *
 * **Ohne Datenquelle erscheint nichts:** kein Budget („von 18,00"), kein „Cache geschrieben" und
 * keine Ersparnis durch den Zwischenspeicher — der Server kennt nur gelesene Cache-Token.
 *
 * **Beide Gattungen** (Issue #1017, #984 AK 1, 3, 4, 6): Unter der Summe jeder Kachel stehen der
 * Anteil aus Nachtläufen und der aus interaktiven Sitzungen, in der Kopfzeile die Zahl beider. Der
 * nicht zuordenbare Rest steht als Posten „ohne Karte". Der Stapelbalken bleibt der Anteil aus dem
 * Zwischenspeicher — er beantwortet eine andere Frage und wird nicht auf die Gattungen umgewidmet.
 *
 * **Bremse** (Issue #1553, Plan #1547 E12, E14, E19): wie oft die Bremse des Kits im Zeitraum
 * gegriffen hat und wie viel Zeit sie geschätzt gespart hat — aus `current.brakes`, unter „Gesamt"
 * aus `total.brakes`, ohne Vorzeitraum-Vergleich.
 */
type Wahl = VerbrauchZeitraumArt | 'TOTAL'

/** Die Zeitraum-Wahl in ihrer Reihenfolge; die Namen stehen im Wörterbuch. */
const ZEITRAEUME: readonly Wahl[] = ['DAY', 'WEEK', 'MONTH', 'TOTAL']

/** Was der Bereich zeigt: ein Zeitraum samt Vorzeitraum oder die Summe über alles Aufbewahrte. */
type Ansicht = { art: 'zeitraum'; zeitraum: VerbrauchZeitraum } | { art: 'gesamt'; gesamt: VerbrauchGesamt }

type Zustand = { art: 'laden' } | { art: 'fehler' } | { art: 'da'; ansicht: Ansicht }
type ImplementierungZustand = { art: 'laden' } | { art: 'fehler' } | { art: 'da'; wert: ImplementationTime }

type VerbrauchApi = Pick<NightRunUsageApi, 'period' | 'total'> & Pick<DashboardApi, 'implementationTime'>

/** Ein Objekt für alle Aufrufe — ein neues je Rendern stieße den Ladeeffekt endlos neu an. */
const STANDARD_API: VerbrauchApi = {
  period: nightRunUsageApi.period,
  total: nightRunUsageApi.total,
  implementationTime: dashboardApi.implementationTime,
}

export function LeitstandVerbrauch({
  projectId,
  boardId,
  api = STANDARD_API,
}: Readonly<{ projectId: number; boardId: number; api?: VerbrauchApi }>) {
  const t = useT().LEITSTAND_VERBRAUCH
  const { sprache } = useSprache()
  const [wahl, setWahl] = useState<Wahl>('DAY')
  const [zustand, setZustand] = useState<Zustand>({ art: 'laden' })
  // Stryker disable next-line ObjectLiteral,StringLiteral: gleichwertig — der Effekt setzt vor jeder Anzeige der Kachel selbst 'laden'
  const [implementierung, setImplementierung] = useState<ImplementierungZustand>({ art: 'laden' })

  // Zweistufig: Die Implementierungszeit braucht die Grenzen, die erst der Verbrauch nennt.
  useEffect(() => {
    let aktiv = true
    setZustand({ art: 'laden' })
    setImplementierung({ art: 'laden' })
    const verbrauch: Promise<Ansicht> =
      wahl === 'TOTAL'
        ? // Stryker disable next-line StringLiteral: gleichwertig — kachelWerte und umfangText nehmen jedes art außer 'zeitraum' als „Gesamt"
          api.total(projectId).then((gesamt) => ({ art: 'gesamt', gesamt }))
        : api.period(projectId, wahl, 0).then((zeitraum) => ({ art: 'zeitraum', zeitraum }))
    verbrauch.then(
      (ansicht) => {
        if (!aktiv) return
        setZustand({ art: 'da', ansicht })
        const grenzen =
          ansicht.art === 'zeitraum'
            ? { from: ansicht.zeitraum.current.from, to: ansicht.zeitraum.current.to }
            : undefined
        api.implementationTime(boardId, grenzen).then(
          (wert) => {
            if (aktiv) setImplementierung({ art: 'da', wert })
          },
          () => {
            if (aktiv) setImplementierung({ art: 'fehler' })
          },
        )
      },
      () => {
        if (aktiv) setZustand({ art: 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, projectId, boardId, wahl])

  return (
    <Box component="section" aria-labelledby="verbrauch-titel" sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap', mt: '4px' }}>
        <Box
          component="h2"
          id="verbrauch-titel"
          sx={{ m: 0, fontFamily: SCHRIFT_ANZEIGE, fontStretch: '114%', fontSize: 13, fontWeight: 700, letterSpacing: '.05em', textTransform: 'uppercase' }}
        >
          {t.TITEL}
        </Box>
        {zustand.art === 'da' && (
          <Box component="span" data-testid="verbrauch-umfang" sx={{ fontSize: 11.5, color: TEXT_SCHWACH }}>
            {umfangText(zustand.ansicht, sprache)}
          </Box>
        )}
        <Box
          role="group"
          aria-label={t.ZEITRAUM_ARIA}
          sx={{ ml: 'auto', display: 'inline-flex', gap: '3px', p: '3px', bgcolor: NUT, border: `1px solid ${RAND}`, borderRadius: '9px', boxShadow: SCHATTEN_NUTE }}
        >
          {ZEITRAEUME.map((art) => {
            const gewaehlt = art === wahl
            return (
              <ButtonBase
                key={art}
                aria-pressed={gewaehlt}
                onClick={() => setWahl(art)}
                sx={{
                  ...ZAHL,
                  fontSize: 11,
                  fontWeight: 500,
                  color: gewaehlt ? 'text.primary' : 'text.secondary',
                  border: `1px solid ${gewaehlt ? RAND : 'transparent'}`,
                  borderRadius: '6px',
                  px: '11px',
                  py: '3px',
                  ...(gewaehlt && { background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`, boxShadow: SCHATTEN_TASTE }),
                }}
              >
                {t.ZEITRAUM[art]}
              </ButtonBase>
            )
          })}
        </Box>
      </Box>

      {zustand.art === 'laden' && <Typography color="text.secondary">{t.LAEDT}</Typography>}
      {zustand.art === 'fehler' && <Typography color="text.secondary">{t.FEHLER}</Typography>}
      {zustand.art === 'da' && <VerbrauchKacheln {...kachelWerte(zustand.ansicht, sprache)} implementierung={implementierung} />}
    </Box>
  )
}

/** Kopfzeile: der Zeitraum bzw. „Gesamt", dahinter Läufe und Sitzungen (Issue #1541, E3). */
function umfangText(ansicht: Ansicht, sprache: Sprache): string {
  const { titel, zaehlung } =
    ansicht.art === 'zeitraum'
      ? { titel: zeitraumBeschriftung(ansicht.zeitraum.current, sprache), zaehlung: ansicht.zeitraum.current }
      : { titel: woerterbuch(sprache).LEITSTAND_VERBRAUCH.GESAMT, zaehlung: ansicht.gesamt }
  return `${titel} · ${laeufeText(zaehlung.nightRunCount, sprache)} · ${sitzungenText(zaehlung.interactiveRunCount, sprache)}`
}

/** Die Größen, die Zeitraum und „Gesamt" gemeinsam haben. */
type Gemeinsam = Pick<VerbrauchKennzahlen, 'cardCount' | 'usage' | 'usageByKind' | 'brakes'>

/** Was nur ein Zeitraum hat: Vorzeitraum zum Vergleich und die Schichten darin. */
interface Zeitbezug {
  vorher: VerbrauchAngaben
  schichten: VerbrauchNachtKurz[]
}

interface KachelWerte {
  werte: Gemeinsam
  stand: Erfassungsstand
  hinweis: string | null
  zeitbezug?: Zeitbezug
  abdeckung?: string
}

function kachelWerte(ansicht: Ansicht, sprache: Sprache): KachelWerte {
  if (ansicht.art === 'zeitraum') {
    const { current, previous, nights } = ansicht.zeitraum
    return {
      werte: current,
      stand: erfassungsstand(current),
      hinweis: zeitraumHinweis(current, sprache),
      zeitbezug: { vorher: previous.usage.total, schichten: nights },
    }
  }
  const { gesamt } = ansicht
  return {
    werte: gesamt,
    // Stryker disable next-line StringLiteral: gleichwertig — Anteile unterscheidet nur 'nicht-erfasst' und 'teilweise-erfasst'
    stand: gesamt.interactiveUsageSince === null ? 'nicht-erfasst' : 'erfasst',
    hinweis: null,
    abdeckung: abdeckungText(gesamt, sprache),
  }
}

/**
 * Ab wann die Summe über alles Aufbewahrte reicht (Issue #1014, #984 AK 4): Der älteste
 * aufbewahrte Eintrag zeigt, was der Ringpuffer verdrängt hat, der Erfassungsbeginn trennt davon
 * die Zeit, in der noch keine Sitzung gemeldet wurde.
 */
function abdeckungText(gesamt: VerbrauchGesamt, sprache: Sprache): string {
  const t = woerterbuch(sprache)
  return [
    gesamt.oldestRetainedRunStart === null
      ? t.LEITSTAND_VERBRAUCH.OHNE_EINTRAG
      : t.LEITSTAND_VERBRAUCH.AB(zeitpunktDatum(gesamt.oldestRetainedRunStart, sprache)),
    gesamt.interactiveUsageSince === null
      ? t.LEITSTAND_VERBRAUCH.SITZUNGEN_NICHT_ERFASST(t.VERBRAUCH_ZEITRAUM.NICHT_ERFASST)
      : t.LEITSTAND_VERBRAUCH.SITZUNGEN_AB(zeitpunktDatum(gesamt.interactiveUsageSince, sprache)),
  ].join(' · ')
}

function VerbrauchKacheln({
  werte,
  stand,
  hinweis,
  zeitbezug,
  abdeckung,
  implementierung,
}: Readonly<KachelWerte & { implementierung: ImplementierungZustand }>) {
  const t = useT().LEITSTAND_VERBRAUCH
  const { sprache } = useSprache()
  const summe = werte.usage.total
  const { night, interactive } = werte.usageByKind
  const eingabe = tokenMenge(summe.inputTokens, sprache)
  const ausgabe = tokenMenge(summe.outputTokens, sprache)
  const karten = werte.cardCount
  const gelesen = summe.cachedInputTokens
  // Eine Bedingung für die ganze Aufteilung (Issue #1281): Vorher prüften `frisch`, der Anteil und
  // die Anzeige je für sich, und die doppelten Prüfungen waren von außen nicht zu unterscheiden.
  const aufteilung =
    summe.inputTokens && gelesen !== null
      ? { gelesen, frisch: summe.inputTokens - gelesen, anteil: Math.round((gelesen / summe.inputTokens) * 100) }
      : null
  const ausgabeVerlauf = (zeitbezug?.schichten ?? [])
    .map((nacht) => nacht.usage.total.outputTokens)
    .filter((wert): wert is number => wert !== null)
  const vergleich = zeitbezug ? vergleichMitVorzeitraum(summe, zeitbezug.vorher, sprache) : null
  const ohneKarte = kostenText(werte.usage.remainder.costUsd, sprache)
  const jeVorgang = summe.costUsd !== null && karten > 0 ? t.JE_VORGANG(kostenText(summe.costUsd / karten, sprache)!) : ''
  const menge = (anzahl: number | null) => mengeText(anzahl, sprache)

  return (
    <>
      {hinweis && <Typography color="text.secondary">{hinweis}</Typography>}
      <Box
        aria-label={t.ZEITRAUM_GRUPPE_ARIA}
        role="group"
        sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))', lg: 'repeat(5, minmax(0,1fr))' }, gap: '14px', perspective: '1100px' }}
      >
        <Box component="article" aria-label={t.EINGABE} sx={{ ...KACHEL_SX, gridColumn: { sm: 'span 2', lg: 'auto' } }}>
          <Box sx={ETIKETT}>{t.EINGABE}</Box>
          <KachelWert
            wert={eingabe?.wert ?? null}
            // Stryker disable next-line StringLiteral: gleichwertig — ohne Menge zeigt KachelWert den Leertext, nie die Einheit
            einheit={eingabe?.einheit ?? ''}
          />
          {aufteilung && (
            <>
              <Box
                role="img"
                aria-label={t.AUFTEILUNG_ARIA(aufteilung.anteil, 100 - aufteilung.anteil)}
                sx={{ display: 'flex', height: 13, borderRadius: '7px', overflow: 'hidden', bgcolor: NUT, boxShadow: SCHATTEN_NUTE }}
              >
                <Box component="span" sx={{ display: 'block', width: `${aufteilung.anteil}%`, background: `linear-gradient(180deg, color-mix(in srgb, ${MELDER.gruen} 88%, white), ${MELDER.gruen})` }} />
                <Box component="span" sx={{ display: 'block', width: `${100 - aufteilung.anteil}%`, background: `linear-gradient(180deg, ${KUPFER_HELL}, ${KUPFER})` }} />
              </Box>
              <Box sx={{ display: 'flex', gap: '13px', flexWrap: 'wrap', fontSize: 10.5, color: 'text.secondary' }}>
                <Legende farbe={MELDER.gruen} text={t.CACHE_GELESEN} wert={tokenText(aufteilung.gelesen, sprache)} />
                <Legende farbe={KUPFER} text={t.FRISCH} wert={tokenText(aufteilung.frisch, sprache)} />
              </Box>
            </>
          )}
          <Anteile nacht={menge(night.total.inputTokens)} sitzungen={menge(interactive.total.inputTokens)} stand={stand} />
        </Box>

        <Box component="article" aria-label={t.AUSGABE} sx={KACHEL_SX}>
          <Box sx={ETIKETT}>{t.AUSGABE}</Box>
          <KachelWert
            wert={ausgabe?.wert ?? null}
            // Stryker disable next-line StringLiteral: gleichwertig — ohne Menge zeigt KachelWert den Leertext, nie die Einheit
            einheit={ausgabe?.einheit ?? ''}
          />
          {ausgabeVerlauf.length > 1 && <Funke werte={ausgabeVerlauf} melder="stahl" />}
          <Anteile nacht={menge(night.total.outputTokens)} sitzungen={menge(interactive.total.outputTokens)} stand={stand} />
          <KachelFuss
            delta={
              summe.outputTokens !== null && karten > 0 ? (
                <DeltaMarke art="neutral">{t.JE_VORGANG(tokenText(Math.round(summe.outputTokens / karten), sprache))}</DeltaMarke>
              ) : undefined
            }
            basis={zeitbezug ? t.SCHICHTEN(zeitbezug.schichten.length) : ''}
          />
        </Box>

        <Box component="article" aria-label={t.KOSTEN} sx={KACHEL_SX}>
          <Box sx={ETIKETT}>{t.KOSTEN}</Box>
          <KachelWert wert={summe.costUsd === null ? null : dollar(summe.costUsd, sprache)} einheit="$" />
          <Anteile nacht={kostenText(night.total.costUsd, sprache)} sitzungen={kostenText(interactive.total.costUsd, sprache)} stand={stand} />
          {/* Verbrauch ohne Kartenbezug geht nicht verloren, er hat seinen eigenen Namen (AK 3).
              Ohne gemessenen Rest bleibt der Posten weg — eine 0 behauptete, es gäbe keinen. */}
          {ohneKarte !== null && (
            <Box data-testid="posten-ohne-karte" sx={POSTEN_SX}>
              <Box component="span">{t.OHNE_KARTE}</Box>
              <Box component="b" sx={{ ...ZAHL, fontWeight: 500, color: 'text.primary' }}>
                {ohneKarte}
              </Box>
            </Box>
          )}
          <KachelFuss
            delta={
              // Stryker disable next-line OptionalChaining: gleichwertig — mit zeitbezug ist vergleich nie null
              zeitbezug && (vergleich?.richtung === 'teurer' || vergleich?.richtung === 'billiger') ? (
                // Auf dem Tablet quer öffnet Antippen den Vergleich (Plan #1707, E7).
                <Erklaerung nativ title={vergleich.text}>
                  <Box component="span">
                    <DeltaMarke art={vergleich.richtung === 'billiger' ? 'gut' : 'schlecht'}>
                      {`${vergleich.richtung === 'billiger' ? '▼' : '▲'} ${t.BETRAG(dollar(Math.abs(summe.costUsd! - zeitbezug.vorher.costUsd!), sprache))}`}
                    </DeltaMarke>
                  </Box>
                </Erklaerung>
              ) : undefined
            }
            basis={jeVorgang}
          />
          {/* Unter „Gesamt" sagt der Fuß, ab wann die Summe reicht (Issue #1541, E2). */}
          {abdeckung !== undefined && <KachelFuss basis={abdeckung} />}
        </Box>

        <ImplementierungKachel zustand={implementierung} />
        <BremseKachel bremsen={werte.brakes} />
      </Box>
    </>
  )
}

/**
 * Die Implementierungszeit des gewählten Zeitraums (Issue #1541, #1537 AK 4–6). Bis zur Antwort
 * und nach einem Fehler steht nur der Leerstrich mit seinem Grund, ohne Kartenzahl im Fuß — eine
 * „0 Karten" behauptete eine Antwort, die es nicht gab.
 */
function ImplementierungKachel({ zustand }: Readonly<{ zustand: ImplementierungZustand }>) {
  const t = useT().LEITSTAND_VERBRAUCH
  const { sprache } = useSprache()
  const daten =
    zustand.art === 'da'
      ? implementierungKachel(zustand.wert.avgImplementationSeconds, zustand.wert.implementationSampleCount, sprache)
      : { ...implementierungKachel(null, 0, sprache), basis: '' }
  const leerText = { laden: t.WIRD_GELADEN, fehler: t.NICHT_GELADEN, da: undefined }[zustand.art]
  return (
    <Box sx={{ display: 'grid' }}>
      <Kachel titel={t.IMPLEMENTIERUNGSZEIT} daten={daten} melder="stahl" leerText={leerText} />
    </Box>
  )
}

/**
 * Die Bremsbilanz des gewählten Zeitraums (Issue #1553, Plan #1547 E14, E19). Bei null Bremsungen
 * steht nur „0 Bremsungen" — 0 ist gezählt, eine Zeitsumme über nichts wäre erfunden. Dasselbe gilt,
 * wenn keine Bremsung einen Zeitwert trägt: Dann bleibt nur „davon n ohne Zeitwert".
 */
function BremseKachel({ bremsen }: Readonly<{ bremsen: VerbrauchBremsen }>) {
  const t = useT().LEITSTAND_VERBRAUCH
  const { sprache } = useSprache()
  const { count, withoutTimeCount, savedMs } = bremsen
  return (
    <Box component="article" aria-label={t.BREMSE} sx={KACHEL_SX}>
      <Box sx={ETIKETT}>{t.BREMSE}</Box>
      <KachelWert wert={String(count)} einheit={t.BREMSUNGEN(count)} />
      {count > withoutTimeCount && (
        <Box sx={{ fontSize: 12, color: 'text.secondary' }}>
          {'≈ '}
          <Box component="b" sx={{ ...ZAHL, fontWeight: 500, color: 'text.primary' }}>
            {laufDauer(savedMs, sprache)}
          </Box>
          {t.GESPART}
        </Box>
      )}
      {withoutTimeCount > 0 && <KachelFuss basis={t.OHNE_ZEITWERT(withoutTimeCount)} />}
    </Box>
  )
}

/** Eine Token-Menge als Zeilentext; `null` bleibt `null` — nicht gemessen ist nicht 0. */
function mengeText(anzahl: number | null, sprache: Sprache): string | null {
  return anzahl === null ? null : tokenText(anzahl, sprache)
}

/**
 * Die beiden Anteile unter der Summe (#984 AK 1). Der interaktive Anteil sagt vor dem
 * Erfassungsbeginn „nicht erfasst" und nie 0 (AK 6); schneidet der Zeitraum den Beginn, steht die
 * Zahl da und trägt den Vorbehalt neben sich — die Nachtlauf-Zahlen bleiben in beiden Fällen
 * unverändert stehen.
 */
function Anteile({
  nacht,
  sitzungen,
  stand,
}: Readonly<{ nacht: string | null; sitzungen: string | null; stand: Erfassungsstand }>) {
  const t = useT()
  const zusatz = stand === 'teilweise-erfasst' ? ` (${t.VERBRAUCH_ZEITRAUM.TEILWEISE_ERFASST})` : ''
  const interaktivText = stand === 'nicht-erfasst' ? t.VERBRAUCH_ZEITRAUM.NICHT_ERFASST : `${sitzungen ?? '—'}${zusatz}`
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '2px', fontSize: 10.5, color: 'text.secondary' }}>
      <Anteil testId="anteil-nacht" text={t.LEITSTAND_VERBRAUCH.AUS_RUNS} wert={nacht ?? '—'} />
      <Anteil testId="anteil-interaktiv" text={t.LEITSTAND_VERBRAUCH.AUS_SITZUNGEN} wert={interaktivText} />
    </Box>
  )
}

function Anteil({ testId, text, wert }: Readonly<{ testId: string; text: string; wert: string }>) {
  return (
    <Box data-testid={testId} sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: '8px' }}>
      <Box component="span">{text}</Box>
      <Box component="b" sx={{ ...ZAHL, fontWeight: 500, color: 'text.primary', textAlign: 'right' }}>
        {wert}
      </Box>
    </Box>
  )
}

/** Ein eingelassener Posten unter den Anteilen (Entwurf `.zustand`). */
const POSTEN_SX = {
  display: 'flex',
  alignItems: 'baseline',
  justifyContent: 'space-between',
  gap: '8px',
  fontSize: 10.5,
  color: 'text.secondary',
  bgcolor: NUT,
  border: `1px solid ${RAND}`,
  boxShadow: SCHATTEN_NUTE,
  borderRadius: '7px',
  px: '8px',
  py: '3px',
} as const

function Legende({ farbe, text, wert }: Readonly<{ farbe: string; text: string; wert: string }>) {
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
      <Box component="i" sx={{ width: 8, height: 8, borderRadius: '2px', display: 'block', bgcolor: farbe }} />
      {text}{' '}
      <Box component="b" sx={{ ...ZAHL, fontWeight: 500, color: 'text.primary' }}>
        {wert}
      </Box>
    </Box>
  )
}
