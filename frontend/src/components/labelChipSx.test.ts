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

  it('faellt ohne Farbe auf den Grauton der Palette zurueck', () => {
    expect(labelChipSx(undefined)).toEqual({
      bgcolor: theme.palette.grey[500],
      color: theme.palette.getContrastText(theme.palette.grey[500]),
    })
  })

  // Der eigentliche Grund für dieses Modul: `getContrastText` wirft für alles, was keine
  // CSS-Farbe ist. Labelfarben sind serverseitig nur längenbegrenzt, und das Domain-Modell
  // erlaubt ausdrücklich auch Theme-Token. Ohne Fangnetz nimmt ein einziges solches Label
  // beim Rendern den ganzen React-Baum mit — es gibt keine ErrorBoundary.
  it.each(['primary.main', 'red', '', 'nicht-mal-eine-farbe'])(
    'wirft bei der unbrauchbaren Farbe %p nicht, sondern nimmt Weiss',
    (farbe) => {
      expect(() => labelChipSx(farbe)).not.toThrow()
      expect(labelChipSx(farbe)).toEqual({ bgcolor: farbe, color: theme.palette.common.white })
    },
  )

  it('gibt die unbrauchbare Farbe unveraendert als Flaeche weiter', () => {
    // `bgcolor` loest Theme-Pfade weiterhin auf — genau das Verhalten vor #649.
    expect(labelChipSx('primary.main').bgcolor).toBe('primary.main')
  })
})
