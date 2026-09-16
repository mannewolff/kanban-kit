import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { nightRunUsageApi, type VerbrauchZeitraum, type VerbrauchZeitraumArt } from '../../api/nightRunUsage'
import { dollar, kostenText, tokenMenge, tokenText } from '../../lib/leitstand'
import { vergleichMitVorzeitraum, zeitraumBeschriftung, zeitraumHinweis } from '../../lib/verbrauchZeitraum'
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
 */
const ZEITRAEUME: ReadonlyArray<{ art: VerbrauchZeitraumArt; name: string }> = [
  { art: 'DAY', name: 'Nacht' },
  { art: 'WEEK', name: 'Woche' },
  { art: 'MONTH', name: 'Monat' },
]

type Zustand = { art: 'laden' } | { art: 'fehler' } | { art: 'da'; zeitraum: VerbrauchZeitraum }

export function LeitstandVerbrauch({
  projectId,
  api = nightRunUsageApi,
}: Readonly<{ projectId: number; api?: Pick<typeof nightRunUsageApi, 'period'> }>) {
  const [art, setArt] = useState<VerbrauchZeitraumArt>('DAY')
  const [zustand, setZustand] = useState<Zustand>({ art: 'laden' })

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
          <Box component="span" sx={{ fontSize: 11.5, color: TEXT_SCHWACH }}>
            {`${zeitraumBeschriftung(zustand.zeitraum.current)} · ${zustand.zeitraum.current.runCount === 1 ? '1 Lauf' : `${zustand.zeitraum.current.runCount} Läufe`}`}
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
      {zustand.art === 'da' && <VerbrauchKacheln zeitraum={zustand.zeitraum} />}
    </Box>
  )
}

function VerbrauchKacheln({ zeitraum }: Readonly<{ zeitraum: VerbrauchZeitraum }>) {
  const hinweis = zeitraumHinweis(zeitraum.current)
  const gesamt = zeitraum.current.usage.total
  const eingabe = tokenMenge(gesamt.inputTokens)
  const ausgabe = tokenMenge(gesamt.outputTokens)
  const karten = zeitraum.current.cardCount
  const gelesen = gesamt.cachedInputTokens
  const frisch = gesamt.inputTokens !== null && gelesen !== null ? gesamt.inputTokens - gelesen : null
  const anteilGelesen = gesamt.inputTokens && gelesen !== null ? Math.round((gelesen / gesamt.inputTokens) * 100) : null
  const ausgabeVerlauf = zeitraum.nights
    .map((nacht) => nacht.usage.total.outputTokens)
    .filter((wert): wert is number => wert !== null)
  const vergleich = vergleichMitVorzeitraum(gesamt, zeitraum.previous.usage.total)

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
        </Box>

        <Box component="article" aria-label="Ausgabe-Token" sx={KACHEL_SX}>
          <Box sx={ETIKETT}>Ausgabe-Token</Box>
          <KachelWert wert={ausgabe?.wert ?? null} einheit={ausgabe?.einheit ?? ''} />
          {ausgabeVerlauf.length > 1 && <Funke werte={ausgabeVerlauf} melder="stahl" />}
          <KachelFuss
            delta={
              gesamt.outputTokens !== null && karten > 0 ? (
                <DeltaMarke art="neutral">{`${tokenText(Math.round(gesamt.outputTokens / karten))} je Vorgang`}</DeltaMarke>
              ) : undefined
            }
            basis={zeitraum.nights.length === 1 ? '1 Nacht' : `${zeitraum.nights.length} Nächte`}
          />
        </Box>

        <Box component="article" aria-label="Kosten" sx={KACHEL_SX}>
          <Box sx={ETIKETT}>Kosten</Box>
          <KachelWert wert={gesamt.costUsd === null ? null : dollar(gesamt.costUsd)} einheit="$" />
          <KachelFuss
            delta={
              vergleich.richtung === 'teurer' || vergleich.richtung === 'billiger' ? (
                <Box component="span" title={vergleich.text}>
                  <DeltaMarke art={vergleich.richtung === 'billiger' ? 'gut' : 'schlecht'}>
                    {`${vergleich.richtung === 'billiger' ? '▼' : '▲'} ${dollar(Math.abs(gesamt.costUsd! - zeitraum.previous.usage.total.costUsd!))} $`}
                  </DeltaMarke>
                </Box>
              ) : undefined
            }
            basis={gesamt.costUsd !== null && karten > 0 ? `${kostenText(gesamt.costUsd / karten)} je Vorgang` : ''}
          />
        </Box>
      </Box>
    </>
  )
}

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
