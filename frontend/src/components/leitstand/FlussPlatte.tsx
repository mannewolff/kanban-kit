import Box from '@mui/material/Box'
import { useState } from 'react'
import type { BoardFlow, FlowDay } from '../../api/dashboard'
import { formatierer } from '../../i18n/format'
import { useSprache, useT } from '../../i18n/SpracheContext'
import type { Sprache } from '../../i18n/woerterbuch'
import { FLUSS_BAENDER, flussKopf, flussPfade, flussTagZeilen, type FlussBand } from '../../lib/fluss'
import { statusColors } from '../../lib/statusColors'
import { TEXT_SCHWACH, ZAHL } from '../../theme'
import { LeerSatz, Platte } from './LeitstandBausteine'

/** Breite eines Tages und Höhe der Fläche im Koordinatensystem des SVG; gestreckt wird es per CSS. */
const TAG_BREITE = 10
const HOEHE = 120

/**
 * Füllung und Kante je Band (Plan #1765, E16): die Statusfarben, Ready und Backlog teilen dort Grau
 * und werden über die Füllung getrennt — Ready im Melder-Ton, Backlog als getönte Fläche mit Kante.
 */
const BAND_FARBE: Readonly<Record<FlussBand, { fill: string; stroke?: string }>> = {
  done: { fill: statusColors('done').dot },
  inReview: { fill: statusColors('review').dot },
  inProgress: { fill: statusColors('progress').dot },
  ready: { fill: statusColors('ready').dot },
  backlog: { fill: statusColors('backlog').bg, stroke: statusColors('backlog').dot },
}

/** Ein Kalendertag `YYYY-MM-DD` als lokaler Tag, damit er in keiner Zone auf den Vortag rutscht. */
function tagDatum(tag: FlowDay, sprache: Sprache): string {
  const [jahr, monat, tagImMonat] = tag.date.split('-').map(Number)
  return formatierer(sprache).datum(new Date(jahr, monat - 1, tagImMonat))
}

/**
 * Die Platte „Fluss“ (Issue #1776, Plan #1765 E9, E16, E17): je Tag fünf gestapelte Bänder, von unten
 * Done, In Review, In Progress, Ready, Backlog. Über der Fläche liegt je Tag eine Taste; Überfahren,
 * Tastaturfokus oder Antippen zeigen darunter Datum und Zahl je Band. Ein unbekannter Tag hat keine
 * Fläche und sagt „keine Daten“. Farbe ist nie der einzige Träger: Legende, Anzeige, vorgelesene
 * Namen der Tage und die Zusammenfassung des letzten Tages tragen dieselben Zahlen als Text.
 */
export function FlussPlatte({ daten }: Readonly<{ daten: BoardFlow }>) {
  const t = useT().LEITSTAND_FLUSS
  const { sprache } = useSprache()
  const [aktiv, setAktiv] = useState<number | null>(null)
  const tage = daten.days
  return (
    <Platte titel={t.TITEL} notiz={t.NOTIZ(flussKopf(daten.dataSince, sprache))}>
      {tage.length === 0 ? (
        <LeerSatz testId="fluss-leer">{t.LEER}</LeerSatz>
      ) : (
        <Box sx={{ px: '16px', pt: '16px', pb: '14px' }}>
          <Bild tage={tage} sprache={sprache} onAktiv={setAktiv} />
          <Anzeige tag={aktiv === null ? null : tage[aktiv]} sprache={sprache} />
          <Legende />
        </Box>
      )}
    </Platte>
  )
}

/** Die Bänder als SVG und darüber je Tag eine Taste, in einer Spalte genau über seinem Punkt. */
function Bild({
  tage,
  sprache,
  onAktiv,
}: Readonly<{ tage: readonly FlowDay[]; sprache: Sprache; onAktiv: (i: number | null) => void }>) {
  const t = useT().LEITSTAND_FLUSS
  const pfade = flussPfade(tage, (tage.length - 1) * TAG_BREITE, HOEHE)
  const vorgelesen = (tag: FlowDay) => t.TAG_ARIA(tagDatum(tag, sprache), flussTagZeilen(tag, sprache).join(', '))
  const letzter = tage[tage.length - 1]
  return (
    <Box sx={{ position: 'relative', height: 132 }}>
      <Box
        component="svg"
        role="img"
        aria-label={t.ARIA(tage.length, tagDatum(letzter, sprache), flussTagZeilen(letzter, sprache).join(', '))}
        viewBox={`0 0 ${tage.length * TAG_BREITE} ${HOEHE}`}
        preserveAspectRatio="none"
        sx={{ display: 'block', width: '100%', height: '100%' }}
      >
        {/* Um einen halben Tag verschoben: Punkt i liegt so in der Mitte seiner Taste. */}
        <g data-testid="fluss-versatz" transform={`translate(${TAG_BREITE / 2} 0)`}>
          {FLUSS_BAENDER.map((band) => (
            <Box
              component="path"
              key={band}
              data-testid={`fluss-band-${band}`}
              d={pfade[band]}
              sx={{ ...BAND_FARBE[band], strokeWidth: 1, vectorEffect: 'non-scaling-stroke' }}
            />
          ))}
        </g>
      </Box>
      <Box
        role="group"
        aria-label={t.TAGE_GRUPPE}
        sx={{ position: 'absolute', inset: 0, display: 'grid', gridTemplateColumns: `repeat(${tage.length}, minmax(0, 1fr))` }}
      >
        {tage.map((tag, i) => (
          <Box
            component="button"
            type="button"
            key={tag.date}
            data-testid={`fluss-tag-${i}`}
            aria-label={vorgelesen(tag)}
            onMouseEnter={() => onAktiv(i)}
            onMouseLeave={() => onAktiv(null)}
            onFocus={() => onAktiv(i)}
            onBlur={() => onAktiv(null)}
            onClick={() => onAktiv(i)}
            sx={{
              p: 0,
              m: 0,
              border: 0,
              bgcolor: 'transparent',
              cursor: 'default',
              '&:hover, &:focus-visible': { bgcolor: 'action.hover' },
            }}
          />
        ))}
      </Box>
    </Box>
  )
}

/** Datum und Zahl je Band des gewählten Tages, von oben gelesen wie die Bänder im Bild. */
function Anzeige({ tag, sprache }: Readonly<{ tag: FlowDay | null; sprache: Sprache }>) {
  const t = useT().LEITSTAND_FLUSS
  return (
    <Box data-testid="fluss-anzeige" sx={{ minHeight: 40, pt: '10px', fontSize: 12 }}>
      {tag === null ? (
        <Box component="span" sx={{ color: TEXT_SCHWACH }}>
          {t.HINWEIS}
        </Box>
      ) : (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', columnGap: '14px', rowGap: '4px' }}>
          <Box component="span" sx={{ ...ZAHL, fontWeight: 600 }}>
            {tagDatum(tag, sprache)}
          </Box>
          <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexWrap: 'wrap', columnGap: '14px', rowGap: '4px' }}>
            {flussTagZeilen(tag, sprache).map((zeile) => (
              <Box component="li" key={zeile} sx={ZAHL}>
                {zeile}
              </Box>
            ))}
          </Box>
        </Box>
      )}
    </Box>
  )
}

/** Die Legende in Bandreihenfolge, darunter was Done zählt. */
function Legende() {
  const t = useT().LEITSTAND_FLUSS
  return (
    <>
      <Box
        component="ul"
        aria-label={t.LEGENDE}
        sx={{ listStyle: 'none', m: 0, p: 0, pt: '8px', display: 'flex', flexWrap: 'wrap', gap: '6px 14px', fontSize: 11.5, color: 'text.secondary' }}
      >
        {FLUSS_BAENDER.map((band) => (
          <Box component="li" key={band} sx={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Box
              component="span"
              aria-hidden
              data-testid={`fluss-legende-${band}`}
              sx={{
                width: 10,
                height: 10,
                borderRadius: '2px',
                bgcolor: BAND_FARBE[band].fill,
                border: 1,
                borderColor: BAND_FARBE[band].stroke ?? 'transparent',
              }}
            />
            {t.BAND[band]}
          </Box>
        ))}
      </Box>
      <Box sx={{ pt: '6px', fontSize: 11.5, color: TEXT_SCHWACH }}>{t.DONE_HINWEIS}</Box>
    </>
  )
}
