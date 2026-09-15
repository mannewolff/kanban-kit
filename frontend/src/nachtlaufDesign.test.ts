import { describe, expect, it } from 'vitest'
import type { NightRunState } from './lib/nightRunLog'
import {
  NACHTLAUF_FARBEN,
  NACHTLAUF_MASSE,
  NACHTLAUF_SCHRIFTEN,
  NACHTLAUF_TON,
  nachtlaufTheme,
} from './nachtlaufDesign'

/**
 * Wertequelle der Nachtlauf-Auswertung (#912). Die Datei ist wie `theme.ts` von der Coverage
 * ausgenommen — ohne diese Zusicherungen erzwingt kein Gate ihre Werte.
 *
 * Schwerpunkt ist die Kontrastprüfung: Der Entwurf ist eine Gestaltungsvorlage, kein Nachweis.
 * Nach `CLAUDE-design.md` ist ein verfehlter Kontrast ein Fehler, und Accessibility steht in der
 * Prioritätenordnung vor der visuellen Präferenz. Verfehlt ein Ton die Schwelle, wird der Ton
 * angepasst und die Abweichung an seiner Konstante vermerkt — nicht die Schwelle gesenkt.
 */

/** Relative Luminanz nach WCAG 2.1, aus einem `#rrggbb`-Wert (Vorbild: `theme.test.ts`). */
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

const QUELLE: Record<string, string> = import.meta.glob('./nachtlaufDesign.ts', {
  query: '?raw',
  import: 'default',
  eager: true,
})

describe('nachtlaufDesign Tokens des Entwurfs', () => {
  it('führt alle Farben des hellen :root-Blocks als Hex-Wert', () => {
    const erwartet: ReadonlyArray<keyof typeof NACHTLAUF_FARBEN> = [
      'ground',
      'surface',
      'ink',
      'ink2',
      'ink3',
      'line',
      'line2',
      'akzent',
      'rail',
      'gut',
      'gutFill',
      'budget',
      'budgetFill',
      'leer',
      'leerFill',
      'nie',
      'nieFill',
    ]
    for (const name of erwartet) {
      expect(NACHTLAUF_FARBEN[name], name).toMatch(/^#[0-9A-F]{6}$/)
    }
    expect(Object.keys(NACHTLAUF_FARBEN).sort()).toEqual([...erwartet].sort())
  })

  it('führt die drei Schriftfamilien des Entwurfs mit Rückfallkette', () => {
    expect(NACHTLAUF_SCHRIFTEN.display).toContain('Chivo')
    expect(NACHTLAUF_SCHRIFTEN.body).toContain('IBM Plex Sans')
    expect(NACHTLAUF_SCHRIFTEN.mono).toContain('IBM Plex Mono')
    // Eine Familie ohne Rückfall lässt die Seite auf dem Systemstandard landen, sobald die
    // mitgelieferte Schrift fehlt (#913 liefert sie aus).
    for (const familie of Object.values(NACHTLAUF_SCHRIFTEN)) {
      expect(familie.split(',').length).toBeGreaterThan(1)
    }
  })

  it('führt die Größen und Abstände des Entwurfs', () => {
    expect(NACHTLAUF_MASSE.ueberschrift).toBe('clamp(28px, 5vw, 40px)')
    expect(NACHTLAUF_MASSE.kennzahlWert).toBe(24)
    expect(NACHTLAUF_MASSE.vorzeile).toBe(11)
    expect(NACHTLAUF_MASSE.metazeile).toBe(13)
    expect(NACHTLAUF_MASSE.vorgangAbstand).toBe(14)
    expect(NACHTLAUF_MASSE.vorgangPolsterung).toBe('18px 20px 20px')
    expect(NACHTLAUF_MASSE.vorgangInnenabstand).toBe(16)
    expect(NACHTLAUF_MASSE.vorgangRadius).toBe(10)
  })

  it('übernimmt keinen Wert der Dunkelmodus-Blöcke des Entwurfs', () => {
    // Nicht-Ziel 1 des Fachplans (#903), Entscheidung E13 des Plans: kein Dunkelmodus.
    const quelle = Object.values(QUELLE)[0]
    expect(quelle, 'Quelltext von nachtlaufDesign.ts eingelesen').toBeTruthy()
    for (const dunkel of ['#0E1116', '#161A21', '#8B97F0', '#5FB587', '#E0685C']) {
      expect(quelle).not.toContain(dunkel)
    }
    expect(quelle).not.toContain('prefers-color-scheme')
    expect(quelle).not.toContain("data-theme")
  })
})

describe('nachtlaufDesign Abbildung NightRunState → Ton', () => {
  it('bildet alle vier Zustände auf einen Zustandston ab', () => {
    const erwartet: Record<NightRunState, string> = {
      GREEN: NACHTLAUF_FARBEN.gut,
      YELLOW: NACHTLAUF_FARBEN.budget,
      RED: NACHTLAUF_FARBEN.leer,
      GREY: NACHTLAUF_FARBEN.nie,
    }
    expect(NACHTLAUF_TON).toEqual(erwartet)
  })
})

describe('nachtlaufDesign Textkontrast', () => {
  // Gerechnet wird gegen die Fläche, auf der der Text tatsächlich steht: `surface` im
  // Vorgangsblock, `ground` außerhalb, die Füllfläche in der jeweiligen Ausgangspille.
  // Kein Kreuzprodukt aller Töne mit allen Flächen — ein Paar, das der Entwurf nie zeigt,
  // wäre ein erfundener Nachweis.
  const paare: ReadonlyArray<readonly [string, string, string]> = [
    ['Fließtext auf dem Grund', NACHTLAUF_FARBEN.ground, NACHTLAUF_FARBEN.ink],
    ['Fließtext im Vorgangsblock', NACHTLAUF_FARBEN.surface, NACHTLAUF_FARBEN.ink],
    ['Sekundärtext auf dem Grund', NACHTLAUF_FARBEN.ground, NACHTLAUF_FARBEN.ink2],
    ['Sekundärtext im Vorgangsblock', NACHTLAUF_FARBEN.surface, NACHTLAUF_FARBEN.ink2],
    ['Label auf dem Grund', NACHTLAUF_FARBEN.ground, NACHTLAUF_FARBEN.ink3],
    ['Label im Vorgangsblock', NACHTLAUF_FARBEN.surface, NACHTLAUF_FARBEN.ink3],
    ['Akzent auf dem Grund', NACHTLAUF_FARBEN.ground, NACHTLAUF_FARBEN.akzent],
    ['Warnton auf dem Grund', NACHTLAUF_FARBEN.ground, NACHTLAUF_FARBEN.budget],
    ['Warnton im Vorgangsblock', NACHTLAUF_FARBEN.surface, NACHTLAUF_FARBEN.budget],
    ['Grauton im Vorgangsblock', NACHTLAUF_FARBEN.surface, NACHTLAUF_FARBEN.nie],
    ['Pille „fertig"', NACHTLAUF_FARBEN.gutFill, NACHTLAUF_FARBEN.gut],
    ['Pille „Budget"', NACHTLAUF_FARBEN.budgetFill, NACHTLAUF_FARBEN.budget],
    ['Pille „leer"', NACHTLAUF_FARBEN.leerFill, NACHTLAUF_FARBEN.leer],
    ['Pille „nie gelaufen"', NACHTLAUF_FARBEN.nieFill, NACHTLAUF_FARBEN.nie],
  ]

  it.each(paare)('hält %s die AA-Schwelle für Text (4,5:1)', (_name, flaeche, ton) => {
    // Kein Text des Entwurfs ist „groß" im Sinne der WCAG (≥ 24px, oder ≥ 18,66px fett):
    // die größte Schrift dieser Paare ist die 14px-Vorgangsnummer. Also gilt überall 4,5:1.
    expect(kontrast(flaeche, ton)).toBeGreaterThanOrEqual(4.5)
  })
})

describe('nachtlaufDesign Kontrast der Zustandsflächen', () => {
  // Die vier Zustandstöne dienen im Stufenband und im Anteilsbalken als ausgefüllte Fläche.
  // Sie stehen dort auf der Schiene (`rail`), die die Bezugsgröße zeigt — für ein
  // bedeutungstragendes Grafikelement gilt 3:1, nicht 4,5:1.
  //
  // Der Kontrast der Schiene selbst gegen den Vorgangsblock wird hier bewusst nicht geprüft:
  // Was der Balken aussagt, liest man an der Kante zwischen Füllung und Schiene ab, und die
  // ist über die Paare unten gesichert.
  const zustaende = ['gut', 'budget', 'leer', 'nie'] as const

  it.each(zustaende)('hält die Fläche „%s" auf der Schiene die Schwelle (3:1)', (zustand) => {
    expect(kontrast(NACHTLAUF_FARBEN.rail, NACHTLAUF_FARBEN[zustand])).toBeGreaterThanOrEqual(3)
  })

  it.each(zustaende)('hält die Fläche „%s" auf dem Vorgangsblock die Schwelle (3:1)', (zustand) => {
    expect(kontrast(NACHTLAUF_FARBEN.surface, NACHTLAUF_FARBEN[zustand])).toBeGreaterThanOrEqual(3)
  })
})

describe('nachtlaufTheme', () => {
  it('trägt die Farben und Schriften des Entwurfs', () => {
    expect(nachtlaufTheme.palette.background.default).toBe(NACHTLAUF_FARBEN.ground)
    expect(nachtlaufTheme.palette.background.paper).toBe(NACHTLAUF_FARBEN.surface)
    expect(nachtlaufTheme.palette.text.primary).toBe(NACHTLAUF_FARBEN.ink)
    expect(nachtlaufTheme.palette.text.secondary).toBe(NACHTLAUF_FARBEN.ink2)
    expect(nachtlaufTheme.palette.primary.main).toBe(NACHTLAUF_FARBEN.akzent)
    expect(nachtlaufTheme.palette.divider).toBe(NACHTLAUF_FARBEN.line)
    expect(nachtlaufTheme.typography.fontFamily).toBe(NACHTLAUF_SCHRIFTEN.body)
  })

  it('erbt vom Leitstand-Theme, statt neben ihm zu stehen', () => {
    // Ein verschachtelter ThemeProvider ersetzt das äußere Theme vollständig. Ohne das
    // Leitstand-Theme als Basis fielen alle Komponenten-Vorgaben auf die MUI-Vorgabe zurück
    // (blaues Primary, eigene Radien) — sichtbar an jedem Dialog, den die Seite öffnet.
    expect(nachtlaufTheme.components?.MuiTable?.styleOverrides?.root).toBeDefined()
  })

  it('behält die eigene Palette des Leitstands für die Altbestand-Lauf-Arten', () => {
    // `palette.nightRun` bleibt unverändert (E10): Es trägt die beiden Lauf-Arten, die
    // weiterhin in der Designsprache „Panel" dargestellt werden.
    expect(nachtlaufTheme.palette.nightRun.red).toBe('#FF0000')
  })
})
