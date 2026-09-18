import Box from '@mui/material/Box'
import type { BoardColumn } from '../../api/boards'
import { KUPFER, MELDER, NUT, SCHATTEN_NUTE } from '../../theme'

/**
 * Höchstzahl der Segmente. Darüber hinaus steht ein Segment für mehrere Plätze — eine Spalte mit
 * einer Grenze von 40 bekäme sonst eine Skala, die breiter ist als die Spalte.
 */
const MAX_SEGMENTE = 12

/** Zustand eines Segments der Belastungsskala. */
const segmentZustand = (belegt: boolean, grenze: boolean): string => {
  if (grenze) return 'grenze'
  return belegt ? 'belegt' : 'frei'
}

/**
 * Belastungsgrenze einer Spalte als Segmentskala (Entwurf `.wip`, Z. 855–866): ein Segment je Platz,
 * belegte Plätze kupfern, bei erreichter Grenze das letzte bernstein. Ein eigenes Element neben dem
 * Spaltenkopf, keine Einfärbung des Kopfes — der ist im Struktur-Editiermodus anklickbar und
 * ziehbar (AK 2, #925).
 *
 * Ohne Belastungsgrenze gibt es nichts zu zeigen; die Bedingung steht hier und nicht beim Aufrufer,
 * damit die Spalte sie nicht mitträgt (Plan #1042, P12).
 */
export function Belastungsskala({ column, count }: Readonly<{ column: BoardColumn; count: number }>) {
  const grenze = column.wipLimit
  if (grenze == null) return null
  const segmente = Math.min(grenze, MAX_SEGMENTE)
  const erreicht = count >= grenze
  return (
    <Box
      role="meter"
      aria-label={`Auslastung ${column.name}`}
      aria-valuemin={0}
      aria-valuemax={grenze}
      aria-valuenow={Math.min(count, grenze)}
      aria-valuetext={`${count} von ${grenze}`}
      data-grenze={erreicht ? 'erreicht' : 'offen'}
      sx={{ display: 'inline-flex', gap: '2px', alignItems: 'center', flex: 'none' }}
    >
      {Array.from({ length: segmente }, (_, i) => {
        const belegt = i < Math.round((Math.min(count, grenze) / grenze) * segmente)
        const letztes = erreicht && i === segmente - 1
        const farbe = letztes ? MELDER.bernst : KUPFER
        return (
          <Box
            key={i}
            component="i"
            data-testid="segment"
            data-segment={segmentZustand(belegt, letztes)}
            sx={{
              width: 5,
              height: 11,
              borderRadius: '2px',
              display: 'block',
              ...(belegt ? { bgcolor: farbe, boxShadow: `0 0 6px -2px ${farbe}` } : { bgcolor: NUT, boxShadow: SCHATTEN_NUTE }),
            }}
          />
        )
      })}
    </Box>
  )
}
