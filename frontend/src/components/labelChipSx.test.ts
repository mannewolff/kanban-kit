import { grey } from '@mui/material/colors'
import { describe, expect, it } from 'vitest'
import { theme } from '../theme'
import { labelChipSx } from './labelChipSx'

describe('labelChipSx', () => {
  it('waehlt zu einer Hex-Farbe die lesbare Textfarbe', () => {
    expect(labelChipSx('#FFF59D')).toEqual({
      bgcolor: '#FFF59D',
      color: theme.palette.getContrastText('#FFF59D'),
    })
    expect(labelChipSx('#1E5F68').color).toBe(theme.palette.getContrastText('#1E5F68'))
  })

  // Seit #952 liest das Modul die Farben der Palette als Variablen-Verweis (`theme.vars`), damit sie
  // mit dem Erscheinungsbild umschalten. Die lesbare Textfarbe zum Grau wird weiter auf dem Farbwert
  // gerechnet — auf einem Verweis kann `getContrastText` nicht rechnen und fiele auf Weiss zurueck.
  it('faellt ohne Farbe auf den Grauton der Palette zurueck', () => {
    expect(labelChipSx(undefined)).toEqual({
      bgcolor: theme.vars.palette.grey[500],
      color: theme.palette.getContrastText(grey[500]),
    })
  })

  it('traegt Grau und Weiss der Palette als Variable, nicht als festen Hellwert (#960)', () => {
    expect(labelChipSx(undefined).bgcolor).toMatch(/^var\(--mb-palette-grey-500,/)
    expect(labelChipSx('primary.main').color).toMatch(/^var\(--mb-palette-common-white,/)
  })

  it('traegt auf dem Grau dieselbe dunkle Schrift wie vor der Umstellung', () => {
    expect(labelChipSx(null).color).toBe(theme.palette.getContrastText(theme.palette.grey[500]))
  })

  // Der eigentliche Grund für dieses Modul: `getContrastText` wirft für alles, was keine
  // CSS-Farbe ist. Labelfarben sind serverseitig nur längenbegrenzt, und das Domain-Modell
  // erlaubt ausdrücklich auch Theme-Token. Ohne Fangnetz nimmt ein einziges solches Label
  // beim Rendern den ganzen React-Baum mit — es gibt keine ErrorBoundary.
  it.each(['primary.main', 'red', '', 'nicht-mal-eine-farbe'])(
    'wirft bei der unbrauchbaren Farbe %p nicht, sondern nimmt Weiss',
    (farbe) => {
      expect(() => labelChipSx(farbe)).not.toThrow()
      expect(labelChipSx(farbe)).toEqual({ bgcolor: farbe, color: theme.vars.palette.common.white })
    },
  )

  it('gibt die unbrauchbare Farbe unveraendert als Flaeche weiter', () => {
    // `bgcolor` loest Theme-Pfade weiterhin auf — genau das Verhalten vor #649.
    expect(labelChipSx('primary.main').bgcolor).toBe('primary.main')
  })
})
