import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Tooltip from '@mui/material/Tooltip'
import type { PackageProgressView } from '../../api/nightRuns'
import { useT } from '../../i18n/SpracheContext'
import { rasterzaehlung, rasterzustand, type Rasterfarbe, type Rasterzustand } from '../../lib/laufFortschritt'
import { KUPFER, MELDER, PLATTE, TEXT_SCHWACH, ZAHL } from '../../theme'

/** Die Farbe eines Quadrats als Verweis auf die Palette — beide Erscheinungsbilder folgen von selbst. */
const FARBE: Readonly<Record<Rasterfarbe, string>> = {
  gruen: MELDER.gruen,
  stahl: MELDER.stahl,
  zinnob: MELDER.zinnob,
  'text-schwach': TEXT_SCHWACH,
}

/** Form zuerst, Farbe als zweite Auszeichnung (Plan #1882 E6; Vorlage Z. 381–395). */
function quadratStil({ form, farbe, querstrich }: Rasterzustand) {
  const ton = FARBE[farbe]
  if (querstrich) {
    return {
      border: `1px solid ${ton}`,
      background: `linear-gradient(135deg, ${ton} 0 44%, ${PLATTE} 44% 56%, ${ton} 56%)`,
    }
  }
  switch (form) {
    case 'gefuellt':
      return { border: `1px solid ${ton}`, background: ton }
    case 'schraffiert':
      return {
        border: `1px solid ${ton}`,
        background: `repeating-linear-gradient(45deg, ${ton} 0 3px, color-mix(in srgb, ${ton} 35%, ${PLATTE}) 3px 6px)`,
      }
    case 'umriss':
      return { border: `1.5px solid ${ton}`, background: 'transparent' }
  }
}

/**
 * Die Pakete einer Kette als Raster aus Quadraten (Issue #1886, Plan #1882 E6) — die Form trägt
 * den Zustand, die Farbe zeichnet ihn ein zweites Mal aus. Jedes Quadrat ist eine Schaltfläche,
 * die wie die frühere Paketliste die Karte öffnet; Nummer, Zustand und Titel stehen als
 * zugänglicher Name und als Tooltip, der bei Maus und Tastatur erscheint (#1364: Jeder Zustand
 * steht als Text). Darunter zählt eine Zeile die Zustände.
 *
 * <p>Zustandslos: Den Kartendialog öffnet die Seite über `onKarteOeffnen`.
 */
export function Paketraster({
  pakete,
  name,
  onKarteOeffnen,
}: Readonly<{
  pakete: readonly PackageProgressView[]
  /** Der zugängliche Name der Liste, etwa „Pakete der Kette #100". */
  name: string
  onKarteOeffnen: (nummer: number) => void
}>) {
  const t = useT().NACHTLAUF_FORTSCHRITT
  if (pakete.length === 0) {
    return <Box sx={TEXT_STIL}>{t.KEINE_PAKETE}</Box>
  }
  return (
    <Box>
      <Box
        component="ul"
        aria-label={name}
        sx={{ display: 'flex', flexWrap: 'wrap', gap: '4px', listStyle: 'none', margin: 0, padding: 0 }}
      >
        {pakete.map((p) => {
          const zustand = rasterzustand(p.zustand)
          const titel = t.PAKET_NAME(p.karte.number, t.PAKETZUSTAND[p.zustand], p.karte.title)
          return (
            <Box component="li" key={p.karte.number} sx={{ display: 'flex' }}>
              <Tooltip title={titel}>
                <ButtonBase
                  aria-label={titel}
                  data-form={zustand.form}
                  data-querstrich={String(zustand.querstrich)}
                  onClick={() => onKarteOeffnen(p.karte.number)}
                  sx={{
                    ...quadratStil(zustand),
                    width: 16,
                    height: 16,
                    borderRadius: '4px',
                    '&.Mui-focusVisible': { outline: `2px solid ${KUPFER}`, outlineOffset: '2px' },
                  }}
                />
              </Tooltip>
            </Box>
          )
        })}
      </Box>
      <Box data-testid="raster-anzahl" sx={{ ...TEXT_STIL, ...ZAHL, marginTop: '4px' }}>
        {rasterzaehlung(pakete)
          .map((z) => t.RASTER_ANZAHL(z.anzahl, t.PAKETZUSTAND[z.zustand]))
          .join(' · ')}
      </Box>
    </Box>
  )
}

const TEXT_STIL = { fontSize: 11, color: 'text.secondary' } as const
