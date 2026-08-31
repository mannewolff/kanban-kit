import { describe, expect, it } from 'vitest'
import type { Theme } from '@mui/material/styles'
import { CODE_BG, EPIC_EDGE_WIDTH, STATUS_EDGE_WIDTH, SURFACE_HOVER_SHADOW, SURFACE_TINT, theme } from './theme'

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

describe('theme Design-Tokens (Kante, Paket 1)', () => {
  // `src/theme.ts` ist in vite.config.ts von der Coverage ausgenommen. Ohne diese
  // Zusicherungen erzwingt kein Gate die Existenz und die Werte der Tokens (#648).
  it('legt die Kanten-Stärken und Flächentöne mit den festgeschriebenen Werten fest', () => {
    expect(STATUS_EDGE_WIDTH).toBe(3)
    expect(EPIC_EDGE_WIDTH).toBe(4)
    expect(SURFACE_TINT).toBe('#F6FAFB')
    expect(CODE_BG).toBe('#f4f5f7')
  })

  it('hebt Flächen mit einem Teal-Schatten an, ohne schwarzen Farbanteil', () => {
    const shadow = SURFACE_HOVER_SHADOW.toLowerCase()
    expect(shadow).toContain('rgba(47,140,151')
    expect(shadow).not.toContain('rgba(0,0,0')
    expect(shadow).not.toContain('rgba(0, 0, 0')
    expect(shadow).not.toContain('#000')
    expect(shadow).not.toContain('black')
  })

  it('zieht den Eckradius auf 4 zurück', () => {
    expect(theme.shape.borderRadius).toBe(4)
  })
})

describe('theme AppBar-Override', () => {
  it('gibt der Kopfleiste die Papierfläche der Palette statt der Primärfarbe', () => {
    const root = theme.components?.MuiAppBar?.styleOverrides?.root
    const style = (typeof root === 'function'
      ? (root as (props: { theme: Theme }) => Record<string, unknown>)({ theme })
      : (root as Record<string, unknown>))

    expect(style.backgroundColor).toBe(theme.palette.background.paper)
    expect(style.backgroundColor).not.toBe(theme.palette.primary.main)
  })

  it('trennt die Kopfleiste mit einer Haarlinie statt mit einer Elevation', () => {
    const root = theme.components?.MuiAppBar?.styleOverrides?.root
    const style = (typeof root === 'function'
      ? (root as (props: { theme: Theme }) => Record<string, unknown>)({ theme })
      : (root as Record<string, unknown>))

    expect(style.borderBottom).toBe(`1px solid ${theme.palette.divider}`)
    // MUI setzt am AppBar eine eigene Elevation von 4; der MuiPaper-Default 0 greift dort nicht.
    expect(theme.components?.MuiAppBar?.defaultProps?.elevation).toBe(0)
  })
})
