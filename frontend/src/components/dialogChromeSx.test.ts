import { describe, expect, it } from 'vitest'
import { theme } from '../theme'
import { dialogTitleSx } from './dialogChromeSx'

/** Quelltexte der sechs Dialoge, ueber Vite statt ueber `node:fs` (wie in `designGuard.test.ts`). */
const DIALOGS: Record<string, string> = import.meta.glob(
  [
    './CardDetailModal.tsx',
    './NewCardModal.tsx',
    './TrashDialog.tsx',
    './LabelManagerDialog.tsx',
    './TransferCardDialog.tsx',
    './SpecImportDialog.tsx',
  ],
  { query: '?raw', import: 'default', eager: true },
)

describe('dialogTitleSx', () => {
  it('traegt die untere Haarlinie aus der Palette und keine eigene Flaeche', () => {
    expect(dialogTitleSx.borderBottom).toBe(`1px solid ${theme.palette.divider}`)
    expect(dialogTitleSx).not.toHaveProperty('bgcolor')
    expect(dialogTitleSx).not.toHaveProperty('backgroundColor')
  })
})

/**
 * Regressionstest ueber alle sechs Dialoge (#652). Er darf von Anfang an gruen sein: eine getoente
 * Dialog-Kopfzeile gab es im Bestand nie — `MODAL_HEADER_BG` war eine tote Konstante. Rot zu
 * bekommen waere er nur, indem man erst eine Toenung erfindet. Er haelt fest, dass alle Kopfzeilen
 * dieselbe Haarlinie tragen und keine wieder eine Flaeche bekommt.
 */
describe.each(Object.entries(DIALOGS))('Dialog-Chrome in %s', (_file, source) => {
  const titles = source.match(/<DialogTitle\b[^>]*>/g) ?? []

  it('rendert mindestens eine Dialog-Kopfzeile', () => {
    expect(titles.length).toBeGreaterThan(0)
  })

  it('fuehrt jede Kopfzeile ueber dasselbe Chrome und toent keine', () => {
    titles.forEach((title) => {
      expect(title).toContain('sx={dialogTitleSx}')
      expect(title).not.toMatch(/bgcolor|backgroundColor/)
    })
  })
})
