import { describe, expect, it } from 'vitest'
import { theme } from './theme'

describe('theme Zebra-Streifen', () => {
  it('streift nur gerade Datenzeilen im TableBody, nicht den Header', () => {
    const root = theme.components?.MuiTable?.styleOverrides?.root as Record<string, unknown>
    expect(root).toBeDefined()

    const zebra = root['& .MuiTableBody-root .MuiTableRow-root:nth-of-type(even)']
    // Nur Body-Zeilen: der Selektor ist auf TableBody eingeschränkt (kein TableHead).
    expect(zebra).toMatchObject({ backgroundColor: '#F6FAFB' })
    expect(JSON.stringify(root)).not.toContain('MuiTableHead')
  })
})
