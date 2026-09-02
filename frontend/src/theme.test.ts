import { describe, expect, it } from 'vitest'
import type { Theme } from '@mui/material/styles'
import {
  APP_BACKGROUND,
  CARD_LIFT,
  CARD_RADIUS,
  CARD_SHADOW,
  CARD_SHADOW_HOVER,
  CODE_BG,
  EPIC_EDGE_WIDTH,
  HEADER_BG,
  PANEL_HEAD_GRADIENT,
  PANEL_RADIUS,
  PANEL_SHADOW,
  STATUS_EDGE_WIDTH,
  SURFACE_HOVER_SHADOW,
  SURFACE_TINT,
  theme,
} from './theme'

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

describe('theme Design-Tokens (Panel)', () => {
  // `src/theme.ts` ist in vite.config.ts von der Coverage ausgenommen. Ohne diese
  // Zusicherungen erzwingt kein Gate die Existenz und die Werte der Tokens.
  it('legt die Kanten-Stärken und Flächentöne mit den festgeschriebenen Werten fest', () => {
    expect(STATUS_EDGE_WIDTH).toBe(3)
    expect(EPIC_EDGE_WIDTH).toBe(4)
    expect(SURFACE_TINT).toBe('#F6FAFB')
    expect(CODE_BG).toBe('#f4f5f7')
  })

  it('hebt einfache Flächen mit einem Teal-Schatten an, ohne schwarzen Farbanteil', () => {
    const shadow = SURFACE_HOVER_SHADOW.toLowerCase()
    expect(shadow).toContain('rgba(47,140,151')
    expect(shadow).not.toContain('rgba(0,0,0')
    expect(shadow).not.toContain('rgba(0, 0, 0')
    expect(shadow).not.toContain('#000')
    expect(shadow).not.toContain('black')
  })

  it('rundet Karten und Panels stärker als die Bedienelemente', () => {
    expect(theme.shape.borderRadius).toBe(8)
    expect(CARD_RADIUS).toBe(10)
    expect(PANEL_RADIUS).toBe(14)
  })

  // Kern der Variante „Panel": Die Karte trägt eine Lichtkante an der Oberkante. Ein Pixel Licht
  // macht den plastischen Eindruck — ohne sie ist es nur ein Schatten unter einem flachen Rechteck.
  it('gibt der Karte eine Lichtkante und zwei Schattenebenen, im Ruhezustand wie beim Hover', () => {
    for (const shadow of [CARD_SHADOW, CARD_SHADOW_HOVER]) {
      expect(shadow).toContain('inset 0 1px 0 #FFFFFF')
      // Zwei abgesetzte Ebenen: eine harte Kontaktschattierung, eine weiche Streuung.
      expect(shadow.match(/rgba\(36,53,57/g)).toHaveLength(2)
    }
    // Der Hover öffnet weiter, als der Ruhezustand steht.
    expect(CARD_SHADOW_HOVER).toContain('30px')
  })

  it('führt alle Flächen-Schatten in der Marken-Tinte, nie in Schwarz', () => {
    for (const shadow of [CARD_SHADOW, CARD_SHADOW_HOVER, PANEL_SHADOW]) {
      const lower = shadow.toLowerCase()
      expect(lower).toContain('rgba(36,53,57')
      expect(lower).not.toContain('rgba(0,0,0')
      expect(lower).not.toContain('rgba(0, 0, 0')
      expect(lower).not.toContain('#000')
      expect(lower).not.toContain('black')
    }
  })

  it('lässt den Panel-Kopf auf Weiß auslaufen, damit er kein eigener Kasten wird', () => {
    expect(PANEL_HEAD_GRADIENT).toContain('linear-gradient')
    expect(PANEL_HEAD_GRADIENT).toContain('#FFFFFF')
  })

  it('hebt die Karte nach oben an, nicht nach unten', () => {
    expect(CARD_LIFT).toBeLessThan(0)
  })
})

/** Relative Luminanz nach WCAG 2.1, aus einem `#rrggbb`-Wert. */
const luminanz = (hex: string): number => {
  const kanal = (paar: string): number => {
    const v = Number.parseInt(paar, 16) / 255
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
  }
  const r = kanal(hex.slice(1, 3))
  const g = kanal(hex.slice(3, 5))
  const b = kanal(hex.slice(5, 7))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

const kontrast = (a: string, b: string): number => {
  const [hell, dunkel] = [luminanz(a), luminanz(b)].sort((x, y) => y - x)
  return (hell + 0.05) / (dunkel + 0.05)
}

describe('theme Kopfleiste', () => {
  // Die Leiste war vor #653 mittleres Teal mit weißer Schrift (3,85:1, AA verfehlt) und danach
  // weiß. Diese Zusicherung hält den Weg zurück zur Farbe offen, ohne den alten Fehler zu
  // wiederholen: Farbe ja, aber nur mit einem Kontrast, der die AA-Schwelle hält.
  it('hält mit der dunklen Marken-Tinte die AA-Schwelle für normalen Text', () => {
    expect(kontrast(HEADER_BG, theme.palette.text.primary)).toBeGreaterThanOrEqual(4.5)
  })

  it('wäre mit weißer Schrift schlechter — deshalb trägt die Leiste dunkle', () => {
    expect(kontrast(HEADER_BG, '#FFFFFF')).toBeLessThan(4.5)
  })
})

describe('theme AppBar-Override', () => {
  it('gibt der Kopfleiste den hellen Teal der Palette, nicht Weiß und nicht die Primärfarbe', () => {
    const root = theme.components?.MuiAppBar?.styleOverrides?.root
    const style = (typeof root === 'function'
      ? (root as (props: { theme: Theme }) => Record<string, unknown>)({ theme })
      : (root as Record<string, unknown>))

    expect(style.backgroundColor).toBe(HEADER_BG)
    expect(HEADER_BG).toBe('#5BABB5')
    // Weiß war der Zustand zwischen #653 und dem 2026-08-31; die Primärfarbe war der davor und
    // verfehlte mit weißer Schrift die AA-Schwelle.
    expect(style.backgroundColor).not.toBe(theme.palette.background.paper)
    expect(style.backgroundColor).not.toBe(theme.palette.primary.main)
  })

  it('gibt der Kopfleiste auch ihre Textfarbe', () => {
    const root = theme.components?.MuiAppBar?.styleOverrides?.root
    const style = (typeof root === 'function'
      ? (root as (props: { theme: Theme }) => Record<string, unknown>)({ theme })
      : (root as Record<string, unknown>))

    // Geerbtes Weiss aus primary.contrastText waere auf dem hellen Teal schlechter lesbar.
    expect(style.color).toBe(theme.palette.text.primary)
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

describe('theme Grund der Anwendung', () => {
  it('tönt den Grund aus dem Eis der Palette, ohne eine neue Hexfarbe einzuführen', () => {
    // Zwei radiale Verläufe, beide aus ICE — der Grund trägt keinen Ton, den die Palette nicht kennt.
    expect(APP_BACKGROUND.match(/#EDF5F6/g)).toHaveLength(2)
    expect(APP_BACKGROUND.match(/radial-gradient/g)).toHaveLength(2)
  })

  it('legt eine getönte Grundfläche unter die Verläufe, kein Weiß', () => {
    // Der tragende Teil der Tönung. Mit `#FFFFFF` als Grundfläche war der Grund nur dort getönt,
    // wo die Verläufe reichten — bei 1920px Breite blieben Mitte, unterer Bereich und beide
    // unteren Ecken reines Weiß, die Tönung war auf einem breiten Bildschirm unsichtbar.
    expect(APP_BACKGROUND).toContain(SURFACE_TINT)
    expect(APP_BACKGROUND).not.toContain('#FFFFFF')
  })

  it('lässt die Verläufe weit genug auslaufen, um Fläche zu tragen', () => {
    // Die erste Fassung lief bei 55 % von 1200px aus und deckte damit ab etwa 660px nichts mehr.
    // Ein Verlauf, der auf einem Drittel der Fläche endet, setzt einen Akzent statt einen Grund.
    const radien = [...APP_BACKGROUND.matchAll(/(\d+)px (\d+)px at/g)].map((m) => Number(m[1]))
    expect(radien.every((r) => r >= 1400)).toBe(true)
  })

  it('legt den Grund auf eine fixierte eigene Schicht hinter dem Inhalt', () => {
    const vorSatz = theme.components?.MuiCssBaseline?.styleOverrides as
      | Record<string, unknown>
      | undefined
    const schicht = vorSatz?.['body::before'] as Record<string, unknown> | undefined

    expect(schicht).toBeDefined()
    // Fixiert und hinter allem: eine eigene Schicht, damit der Grund beim Scrollen stehenbleibt.
    expect(schicht).toMatchObject({
      content: '""',
      position: 'fixed',
      inset: 0,
      zIndex: -1,
      background: APP_BACKGROUND,
    })
  })

  it('fixiert den Grund über eine eigene Schicht statt über background-attachment', () => {
    // iOS Safari ignoriert `background-attachment: fixed` und fällt auf `scroll` zurück; auf einem
    // langen Board läge die Mitte des Verlaufs dann im Scrollbereich.
    expect(APP_BACKGROUND).not.toContain('background-attachment')
    expect(JSON.stringify(theme.components?.MuiCssBaseline?.styleOverrides)).not.toContain(
      'attachment',
    )
  })

  it('hält background.default als Farbwert, nicht als Verlaufsstring', () => {
    // MUI leitet aus diesem Feld Kontraste ab; ein Verlauf bräche die Komponenten, die das tun.
    expect(theme.palette.background.default).toMatch(/^#[0-9A-Fa-f]{6}$/)
    expect(theme.palette.background.default).not.toContain('gradient')
  })
})

describe('theme Textkontrast auf den Flächen des Leitstands', () => {
  // Kontrast wird gegen die Fläche gerechnet, auf der der Text steht (CLAUDE-design.md). Seit der
  // Grund getönt ist, sind das drei Flächen — Weiß allein genügt als Nachweis nicht mehr.
  const flaechen = ['#EDF5F6', '#F6FAFB', '#FFFFFF']

  it.each(flaechen)('hält Fließtext auf %s die AA-Schwelle', (flaeche) => {
    expect(kontrast(flaeche, theme.palette.text.primary)).toBeGreaterThanOrEqual(4.5)
  })

  it.each(flaechen)('hält Sekundärtext auf %s die AA-Schwelle', (flaeche) => {
    expect(kontrast(flaeche, theme.palette.text.secondary)).toBeGreaterThanOrEqual(4.5)
  })
})
