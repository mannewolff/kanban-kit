import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { dashboardApi, type DashboardApi, type ImplementationTime } from '../../api/dashboard'
import {
  nightRunUsageApi,
  type NightRunUsageApi,
  type VerbrauchAngaben,
  type VerbrauchGesamt,
  type VerbrauchKennzahlen,
  type VerbrauchNachtKurz,
  type VerbrauchZeitraum,
  type VerbrauchZeitraumArt,
} from '../../api/nightRunUsage'
import { dollar, implementierungKachel, kostenText, tokenMenge, tokenText } from '../../lib/leitstand'
import {
  NICHT_ERFASST_TEXT,
  TEILWEISE_ERFASST_TEXT,
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
 */
type Wahl = VerbrauchZeitraumArt | 'TOTAL'

const ZEITRAEUME: ReadonlyArray<{ art: Wahl; name: string }> = [
  { art: 'DAY', name: 'Schicht' },
  { art: 'WEEK', name: 'Woche' },
  { art: 'MONTH', name: 'Monat' },
  { art: 'TOTAL', name: 'Gesamt' },
]

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
        ? api.total(projectId).then((gesamt) => ({ art: 'gesamt', gesamt }))
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
          Verbrauch
        </Box>
        {zustand.art === 'da' && (
          <Box component="span" data-testid="verbrauch-umfang" sx={{ fontSize: 11.5, color: TEXT_SCHWACH }}>
            {umfangText(zustand.ansicht)}
          </Box>
        )}
        <Box
          role="group"
          aria-label="Zeitraum"
          sx={{ ml: 'auto', display: 'inline-flex', gap: '3px', p: '3px', bgcolor: NUT, border: `1px solid ${RAND}`, borderRadius: '9px', boxShadow: SCHATTEN_NUTE }}
        >
          {ZEITRAEUME.map((z) => {
            const gewaehlt = z.art === wahl
            return (
              <ButtonBase
                key={z.art}
                aria-pressed={gewaehlt}
                onClick={() => setWahl(z.art)}
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
                {z.name}
              </ButtonBase>
            )
          })}
        </Box>
      </Box>

      {zustand.art === 'laden' && <Typography color="text.secondary">Der Verbrauch wird geladen …</Typography>}
      {zustand.art === 'fehler' && <Typography color="text.secondary">Der Verbrauch konnte nicht geladen werden.</Typography>}
      {zustand.art === 'da' && <VerbrauchKacheln {...kachelWerte(zustand.ansicht)} implementierung={implementierung} />}
    </Box>
  )
}

/** Kopfzeile: der Zeitraum bzw. „Gesamt", dahinter Läufe und Sitzungen (Issue #1541, E3). */
function umfangText(ansicht: Ansicht): string {
  const { titel, zaehlung } =
    ansicht.art === 'zeitraum'
      ? { titel: zeitraumBeschriftung(ansicht.zeitraum.current), zaehlung: ansicht.zeitraum.current }
      : { titel: 'Gesamt', zaehlung: ansicht.gesamt }
  return `${titel} · ${laeufeText(zaehlung.nightRunCount)} · ${sitzungenText(zaehlung.interactiveRunCount)}`
}

/** Die Größen, die Zeitraum und „Gesamt" gemeinsam haben. */
type Gemeinsam = Pick<VerbrauchKennzahlen, 'cardCount' | 'usage' | 'usageByKind'>

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

function kachelWerte(ansicht: Ansicht): KachelWerte {
  if (ansicht.art === 'zeitraum') {
    const { current, previous, nights } = ansicht.zeitraum
    return {
      werte: current,
      stand: erfassungsstand(current),
      hinweis: zeitraumHinweis(current),
      zeitbezug: { vorher: previous.usage.total, schichten: nights },
    }
  }
  const { gesamt } = ansicht
  return {
    werte: gesamt,
    // Stryker disable next-line StringLiteral: gleichwertig — Anteile unterscheidet nur 'nicht-erfasst' und 'teilweise-erfasst'
    stand: gesamt.interactiveUsageSince === null ? 'nicht-erfasst' : 'erfasst',
    hinweis: null,
    abdeckung: abdeckungText(gesamt),
  }
}

/**
 * Ab wann die Summe über alles Aufbewahrte reicht (Issue #1014, #984 AK 4): Der älteste
 * aufbewahrte Eintrag zeigt, was der Ringpuffer verdrängt hat, der Erfassungsbeginn trennt davon
 * die Zeit, in der noch keine Sitzung gemeldet wurde.
 */
function abdeckungText(gesamt: VerbrauchGesamt): string {
  return [
    gesamt.oldestRetainedRunStart === null
      ? 'ohne aufbewahrten Eintrag'
      : `ab ${zeitpunktDatum(gesamt.oldestRetainedRunStart)}`,
    gesamt.interactiveUsageSince === null
      ? `Sitzungen ${NICHT_ERFASST_TEXT}`
      : `Sitzungen ab ${zeitpunktDatum(gesamt.interactiveUsageSince)}`,
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
  const summe = werte.usage.total
  const { night, interactive } = werte.usageByKind
  const eingabe = tokenMenge(summe.inputTokens)
  const ausgabe = tokenMenge(summe.outputTokens)
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
  const vergleich = zeitbezug ? vergleichMitVorzeitraum(summe, zeitbezug.vorher) : null
  const ohneKarte = kostenText(werte.usage.remainder.costUsd)
  const jeVorgang = summe.costUsd !== null && karten > 0 ? `${kostenText(summe.costUsd / karten)} je Vorgang` : ''

  return (
    <>
      {hinweis && <Typography color="text.secondary">{hinweis}</Typography>}
      <Box
        aria-label="Verbrauch des Zeitraums"
        role="group"
        sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))', lg: 'repeat(4, minmax(0,1fr))' }, gap: '14px', perspective: '1100px' }}
      >
        <Box component="article" aria-label="Eingabe-Token" sx={{ ...KACHEL_SX, gridColumn: { sm: 'span 2', lg: 'auto' } }}>
          <Box sx={ETIKETT}>Eingabe-Token</Box>
          <KachelWert
            wert={eingabe?.wert ?? null}
            // Stryker disable next-line StringLiteral: gleichwertig — ohne Menge zeigt KachelWert den Leertext, nie die Einheit
            einheit={eingabe?.einheit ?? ''}
          />
          {aufteilung && (
            <>
              <Box
                role="img"
                aria-label={`Aufteilung der Eingabe: ${aufteilung.anteil} Prozent aus dem Cache gelesen, ${100 - aufteilung.anteil} Prozent frisch`}
                sx={{ display: 'flex', height: 13, borderRadius: '7px', overflow: 'hidden', bgcolor: NUT, boxShadow: SCHATTEN_NUTE }}
              >
                <Box component="span" sx={{ display: 'block', width: `${aufteilung.anteil}%`, background: `linear-gradient(180deg, color-mix(in srgb, ${MELDER.gruen} 88%, white), ${MELDER.gruen})` }} />
                <Box component="span" sx={{ display: 'block', width: `${100 - aufteilung.anteil}%`, background: `linear-gradient(180deg, ${KUPFER_HELL}, ${KUPFER})` }} />
              </Box>
              <Box sx={{ display: 'flex', gap: '13px', flexWrap: 'wrap', fontSize: 10.5, color: 'text.secondary' }}>
                <Legende farbe={MELDER.gruen} text="Cache gelesen" wert={tokenText(aufteilung.gelesen)} />
                <Legende farbe={KUPFER} text="frisch" wert={tokenText(aufteilung.frisch)} />
              </Box>
            </>
          )}
          <Anteile nacht={mengeText(night.total.inputTokens)} sitzungen={mengeText(interactive.total.inputTokens)} stand={stand} />
        </Box>

        <Box component="article" aria-label="Ausgabe-Token" sx={KACHEL_SX}>
          <Box sx={ETIKETT}>Ausgabe-Token</Box>
          <KachelWert
            wert={ausgabe?.wert ?? null}
            // Stryker disable next-line StringLiteral: gleichwertig — ohne Menge zeigt KachelWert den Leertext, nie die Einheit
            einheit={ausgabe?.einheit ?? ''}
          />
          {ausgabeVerlauf.length > 1 && <Funke werte={ausgabeVerlauf} melder="stahl" />}
          <Anteile nacht={mengeText(night.total.outputTokens)} sitzungen={mengeText(interactive.total.outputTokens)} stand={stand} />
          <KachelFuss
            delta={
              summe.outputTokens !== null && karten > 0 ? (
                <DeltaMarke art="neutral">{`${tokenText(Math.round(summe.outputTokens / karten))} je Vorgang`}</DeltaMarke>
              ) : undefined
            }
            basis={zeitbezug ? schichtenText(zeitbezug.schichten.length) : ''}
          />
        </Box>

        <Box component="article" aria-label="Kosten" sx={KACHEL_SX}>
          <Box sx={ETIKETT}>Kosten</Box>
          <KachelWert wert={summe.costUsd === null ? null : dollar(summe.costUsd)} einheit="$" />
          <Anteile nacht={kostenText(night.total.costUsd)} sitzungen={kostenText(interactive.total.costUsd)} stand={stand} />
          {/* Verbrauch ohne Kartenbezug geht nicht verloren, er hat seinen eigenen Namen (AK 3).
              Ohne gemessenen Rest bleibt der Posten weg — eine 0 behauptete, es gäbe keinen. */}
          {ohneKarte !== null && (
            <Box data-testid="posten-ohne-karte" sx={POSTEN_SX}>
              <Box component="span">ohne Karte</Box>
              <Box component="b" sx={{ ...ZAHL, fontWeight: 500, color: 'text.primary' }}>
                {ohneKarte}
              </Box>
            </Box>
          )}
          <KachelFuss
            delta={
              zeitbezug && (vergleich?.richtung === 'teurer' || vergleich?.richtung === 'billiger') ? (
                <Box component="span" title={vergleich.text}>
                  <DeltaMarke art={vergleich.richtung === 'billiger' ? 'gut' : 'schlecht'}>
                    {`${vergleich.richtung === 'billiger' ? '▼' : '▲'} ${dollar(Math.abs(summe.costUsd! - zeitbezug.vorher.costUsd!))} $`}
                  </DeltaMarke>
                </Box>
              ) : undefined
            }
            basis={jeVorgang}
          />
          {/* Unter „Gesamt" sagt der Fuß, ab wann die Summe reicht (Issue #1541, E2). */}
          {abdeckung !== undefined && <KachelFuss basis={abdeckung} />}
        </Box>

        <ImplementierungKachel zustand={implementierung} />
      </Box>
    </>
  )
}

/** „1 Schicht" bzw. „n Schichten". */
function schichtenText(anzahl: number): string {
  return anzahl === 1 ? '1 Schicht' : `${anzahl} Schichten`
}

/**
 * Die Implementierungszeit des gewählten Zeitraums (Issue #1541, #1537 AK 4–6). Bis zur Antwort
 * und nach einem Fehler steht nur der Leerstrich mit seinem Grund, ohne Kartenzahl im Fuß — eine
 * „0 Karten" behauptete eine Antwort, die es nicht gab.
 */
function ImplementierungKachel({ zustand }: Readonly<{ zustand: ImplementierungZustand }>) {
  const daten =
    zustand.art === 'da'
      ? implementierungKachel(zustand.wert.avgImplementationSeconds, zustand.wert.implementationSampleCount)
      : { ...implementierungKachel(null, 0), basis: '' }
  const leerText = { laden: 'wird geladen', fehler: 'nicht geladen', da: undefined }[zustand.art]
  return (
    <Box sx={{ display: 'grid', gridColumn: { sm: 'span 2', lg: 'auto' } }}>
      <Kachel titel="Implementierungszeit" daten={daten} melder="stahl" leerText={leerText} />
    </Box>
  )
}

/** Eine Token-Menge als Zeilentext; `null` bleibt `null` — nicht gemessen ist nicht 0. */
function mengeText(anzahl: number | null): string | null {
  return anzahl === null ? null : tokenText(anzahl)
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
  const zusatz = stand === 'teilweise-erfasst' ? ` (${TEILWEISE_ERFASST_TEXT})` : ''
  const interaktivText = stand === 'nicht-erfasst' ? NICHT_ERFASST_TEXT : `${sitzungen ?? '—'}${zusatz}`
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '2px', fontSize: 10.5, color: 'text.secondary' }}>
      <Anteil testId="anteil-nacht" text="aus Runs" wert={nacht ?? '—'} />
      <Anteil testId="anteil-interaktiv" text="aus interaktiven Sitzungen" wert={interaktivText} />
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
