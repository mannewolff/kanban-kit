import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import {
  nightRunUsageApi,
  type NightRunUsageApi,
  type VerbrauchGesamt,
  type VerbrauchZeitraum,
  type VerbrauchZeitraumArt,
} from '../../api/nightRunUsage'
import { dollar, kostenText, tokenMenge, tokenText } from '../../lib/leitstand'
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
import { DeltaMarke, Funke, KACHEL_SX, KachelFuss, KachelWert } from './LeitstandBausteine'

/**
 * Verbrauch mit Zeitraum-Wahl (#979, Entwurf Z. 977–1017, 1340–1398): Eingabe-Token mit
 * Stapelbalken, Ausgabe-Token, Kosten. Gespeist aus `nightRunUsageApi.period` (#939).
 *
 * **Nacht · Woche · Monat.** Der Entwurf zeigt zusätzlich „Tag"; die Anwendung rechnet den Tag als
 * Nacht (Tagesgrenze 12:00, #969) — „Tag" und „Nacht" zeigten dieselben Zahlen.
 *
 * **Ohne Datenquelle erscheint nichts:** kein Budget („von 18,00"), kein „Cache geschrieben" und
 * keine Ersparnis durch den Zwischenspeicher — der Server kennt nur gelesene Cache-Token.
 *
 * **Beide Gattungen** (Issue #1017, #984 AK 1, 3, 4, 6): Unter der Summe jeder Kachel stehen der
 * Anteil aus Nachtläufen und der aus interaktiven Sitzungen, in der Kopfzeile die Zahl beider. Der
 * nicht zuordenbare Rest steht als Posten „ohne Karte", und daneben liegt die Summe über die ganze
 * Laufzeit. Der Stapelbalken bleibt der Anteil aus dem Zwischenspeicher — er beantwortet eine
 * andere Frage und wird nicht auf die Gattungen umgewidmet.
 */
const ZEITRAEUME: ReadonlyArray<{ art: VerbrauchZeitraumArt; name: string }> = [
  { art: 'DAY', name: 'Nacht' },
  { art: 'WEEK', name: 'Woche' },
  { art: 'MONTH', name: 'Monat' },
]

type Zustand = { art: 'laden' } | { art: 'fehler' } | { art: 'da'; zeitraum: VerbrauchZeitraum }
type GesamtZustand = { art: 'laden' } | { art: 'fehler' } | { art: 'da'; gesamt: VerbrauchGesamt }

export function LeitstandVerbrauch({
  projectId,
  api = nightRunUsageApi,
}: Readonly<{ projectId: number; api?: Pick<NightRunUsageApi, 'period' | 'total'> }>) {
  const [art, setArt] = useState<VerbrauchZeitraumArt>('DAY')
  const [zustand, setZustand] = useState<Zustand>({ art: 'laden' })
  const [gesamt, setGesamt] = useState<GesamtZustand>({ art: 'laden' })

  useEffect(() => {
    let aktiv = true
    setZustand({ art: 'laden' })
    api.period(projectId, art, 0).then(
      (zeitraum) => {
        if (aktiv) setZustand({ art: 'da', zeitraum })
      },
      () => {
        if (aktiv) setZustand({ art: 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, projectId, art])

  // Die Lebenszeit-Summe hängt nicht am gewählten Zeitraum (Issue #1014) und wird deshalb nur beim
  // Wechsel des Projekts neu geholt — ein Klick auf „Woche" ändert an ihr nichts.
  useEffect(() => {
    let aktiv = true
    setGesamt({ art: 'laden' })
    api.total(projectId).then(
      (wert) => {
        if (aktiv) setGesamt({ art: 'da', gesamt: wert })
      },
      () => {
        if (aktiv) setGesamt({ art: 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, projectId])

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
            {`${zeitraumBeschriftung(zustand.zeitraum.current)} · ${laeufeText(zustand.zeitraum.current.nightRunCount)} · ${sitzungenText(zustand.zeitraum.current.interactiveRunCount)}`}
          </Box>
        )}
        <Box
          role="group"
          aria-label="Zeitraum"
          sx={{ ml: 'auto', display: 'inline-flex', gap: '3px', p: '3px', bgcolor: NUT, border: `1px solid ${RAND}`, borderRadius: '9px', boxShadow: SCHATTEN_NUTE }}
        >
          {ZEITRAEUME.map((z) => {
            const gewaehlt = z.art === art
            return (
              <ButtonBase
                key={z.art}
                aria-pressed={gewaehlt}
                onClick={() => setArt(z.art)}
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
      {zustand.art === 'da' && <VerbrauchKacheln zeitraum={zustand.zeitraum} gesamt={gesamt} />}
    </Box>
  )
}

function VerbrauchKacheln({
  zeitraum,
  gesamt,
}: Readonly<{ zeitraum: VerbrauchZeitraum; gesamt: GesamtZustand }>) {
  const hinweis = zeitraumHinweis(zeitraum.current)
  const summe = zeitraum.current.usage.total
  const { night, interactive } = zeitraum.current.usageByKind
  const stand = erfassungsstand(zeitraum.current)
  const eingabe = tokenMenge(summe.inputTokens)
  const ausgabe = tokenMenge(summe.outputTokens)
  const karten = zeitraum.current.cardCount
  const gelesen = summe.cachedInputTokens
  const frisch = summe.inputTokens !== null && gelesen !== null ? summe.inputTokens - gelesen : null
  const anteilGelesen = summe.inputTokens && gelesen !== null ? Math.round((gelesen / summe.inputTokens) * 100) : null
  const ausgabeVerlauf = zeitraum.nights
    .map((nacht) => nacht.usage.total.outputTokens)
    .filter((wert): wert is number => wert !== null)
  const vergleich = vergleichMitVorzeitraum(summe, zeitraum.previous.usage.total)
  const ohneKarte = kostenText(zeitraum.current.usage.remainder.costUsd)

  return (
    <>
      {hinweis && <Typography color="text.secondary">{hinweis}</Typography>}
      <Box
        aria-label="Verbrauch des Zeitraums"
        role="group"
        sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))', lg: 'repeat(4, minmax(0,1fr))' }, gap: '14px', perspective: '1100px' }}
      >
        <Box component="article" aria-label="Eingabe-Token" sx={{ ...KACHEL_SX, gridColumn: { sm: 'span 2' } }}>
          <Box sx={ETIKETT}>Eingabe-Token</Box>
          <KachelWert wert={eingabe?.wert ?? null} einheit={eingabe?.einheit ?? ''} />
          {frisch !== null && gelesen !== null && anteilGelesen !== null && (
            <>
              <Box
                role="img"
                aria-label={`Aufteilung der Eingabe: ${anteilGelesen} Prozent aus dem Cache gelesen, ${100 - anteilGelesen} Prozent frisch`}
                sx={{ display: 'flex', height: 13, borderRadius: '7px', overflow: 'hidden', bgcolor: NUT, boxShadow: SCHATTEN_NUTE }}
              >
                <Box component="span" sx={{ display: 'block', width: `${anteilGelesen}%`, background: `linear-gradient(180deg, color-mix(in srgb, ${MELDER.gruen} 88%, white), ${MELDER.gruen})` }} />
                <Box component="span" sx={{ display: 'block', width: `${100 - anteilGelesen}%`, background: `linear-gradient(180deg, ${KUPFER_HELL}, ${KUPFER})` }} />
              </Box>
              <Box sx={{ display: 'flex', gap: '13px', flexWrap: 'wrap', fontSize: 10.5, color: 'text.secondary' }}>
                <Legende farbe={MELDER.gruen} text="Cache gelesen" wert={tokenText(gelesen)} />
                <Legende farbe={KUPFER} text="frisch" wert={tokenText(frisch)} />
              </Box>
            </>
          )}
          <Anteile nacht={mengeText(night.total.inputTokens)} sitzungen={mengeText(interactive.total.inputTokens)} stand={stand} />
        </Box>

        <Box component="article" aria-label="Ausgabe-Token" sx={KACHEL_SX}>
          <Box sx={ETIKETT}>Ausgabe-Token</Box>
          <KachelWert wert={ausgabe?.wert ?? null} einheit={ausgabe?.einheit ?? ''} />
          {ausgabeVerlauf.length > 1 && <Funke werte={ausgabeVerlauf} melder="stahl" />}
          <Anteile nacht={mengeText(night.total.outputTokens)} sitzungen={mengeText(interactive.total.outputTokens)} stand={stand} />
          <KachelFuss
            delta={
              summe.outputTokens !== null && karten > 0 ? (
                <DeltaMarke art="neutral">{`${tokenText(Math.round(summe.outputTokens / karten))} je Vorgang`}</DeltaMarke>
              ) : undefined
            }
            basis={zeitraum.nights.length === 1 ? '1 Nacht' : `${zeitraum.nights.length} Nächte`}
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
              vergleich.richtung === 'teurer' || vergleich.richtung === 'billiger' ? (
                <Box component="span" title={vergleich.text}>
                  <DeltaMarke art={vergleich.richtung === 'billiger' ? 'gut' : 'schlecht'}>
                    {`${vergleich.richtung === 'billiger' ? '▼' : '▲'} ${dollar(Math.abs(summe.costUsd! - zeitraum.previous.usage.total.costUsd!))} $`}
                  </DeltaMarke>
                </Box>
              ) : undefined
            }
            basis={summe.costUsd !== null && karten > 0 ? `${kostenText(summe.costUsd / karten)} je Vorgang` : ''}
          />
        </Box>

        <Lebenszeit zustand={gesamt} />
      </Box>
    </>
  )
}

/** Eine Token-Menge als Zeilentext; `null` bleibt `null` — nicht gemessen ist nicht 0. */
function mengeText(anzahl: number | null): string | null {
  return anzahl === null ? null : tokenText(anzahl)
}

/**
 * Die Summe über die ganze Laufzeit (Issue #1014, #984 AK 4). Der Fuß nennt, ab wann sie abgedeckt
 * ist: Der älteste aufbewahrte Eintrag zeigt, was der Ringpuffer verdrängt hat, der
 * Erfassungsbeginn trennt davon die Zeit, in der noch keine Sitzung gemeldet wurde.
 */
function Lebenszeit({ zustand }: Readonly<{ zustand: GesamtZustand }>) {
  const gesamt = zustand.art === 'da' ? zustand.gesamt : null
  const summe = gesamt?.usage.total.costUsd ?? null
  const abdeckung =
    gesamt === null
      ? ''
      : [
          gesamt.oldestRetainedRunStart === null
            ? 'ohne aufbewahrten Eintrag'
            : `ab ${zeitpunktDatum(gesamt.oldestRetainedRunStart)}`,
          gesamt.interactiveUsageSince === null
            ? `Sitzungen ${NICHT_ERFASST_TEXT}`
            : `Sitzungen ab ${zeitpunktDatum(gesamt.interactiveUsageSince)}`,
        ].join(' · ')
  return (
    <Box component="article" aria-label="Gesamt über die Laufzeit" sx={{ ...KACHEL_SX, gridColumn: { sm: 'span 2', lg: 'auto' } }}>
      <Box sx={ETIKETT}>Gesamt über die Laufzeit</Box>
      <KachelWert
        wert={summe === null ? null : dollar(summe)}
        einheit="$"
        leerText={zustand.art === 'laden' ? 'wird geladen' : 'nicht geladen'}
      />
      {gesamt !== null && (
        <Anteile
          nacht={kostenText(gesamt.usageByKind.night.total.costUsd)}
          sitzungen={kostenText(gesamt.usageByKind.interactive.total.costUsd)}
          stand={gesamt.interactiveUsageSince === null ? 'nicht-erfasst' : 'erfasst'}
        />
      )}
      <KachelFuss basis={abdeckung} />
    </Box>
  )
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
      <Anteil testId="anteil-nacht" text="aus Läufen" wert={nacht ?? '—'} />
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
