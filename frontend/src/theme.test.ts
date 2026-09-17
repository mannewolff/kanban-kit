import { describe, expect, it } from 'vitest'
import { EPIC_FARBWERTE, epicColor, epicTint } from './lib/epicMeta'
import { kontrast } from './lib/kontrast'
import { ARCHIVED_STATUS_COLOR, STATUS_FARBWERTE, statusColors } from './lib/statusColors'
import {
  APP_BACKGROUND,
  CARD_LIFT,
  ERSCHEINUNGSBILD_SX,
  CARD_RADIUS,
  CARD_SHADOW,
  CARD_SHADOW_HOVER,
  CODE_BG,
  EPIC_EDGE_WIDTH,
  ETIKETT,
  HEADER_BG,
  KLEIN_RADIUS,
  NUT,
  PANEL_HEAD_GRADIENT,
  PANEL_RADIUS,
  PANEL_SHADOW,
  SCHATTEN_NUTE,
  SCHATTEN_TASTE,
  SCHRIFT_ANZEIGE,
  SCHRIFT_MONO,
  SCHRIFT_TEXT,
  STATUS_EDGE_WIDTH,
  SURFACE_HOVER_SHADOW,
  SURFACE_TINT,
  TABELLENZIFFERN,
  ZAHL,
  theme,
  type PanelPalette,
  type WartePalette,
} from './theme'

/**
 * Seit #951 trägt das Theme zwei Erscheinungsbilder als MUI-CSS-Variablen, seit #978 mit den
 * Werten des Leitstand-Entwurfs `docs/entwurf-leitstand.html` („Kupferwarte"). Geprüft wird
 * zweierlei: dass die exportierten Verweise auf Variablen zeigen, die MUI tatsächlich erzeugt, und
 * welcher Wert je Erscheinungsbild dahinter liegt. jsdom löst `var()` nicht auf — die Werte liest
 * der Test deshalb aus `theme.colorSchemes`.
 */
const hell = theme.colorSchemes.light!.palette
const dunkel = theme.colorSchemes.dark!.palette
const schemata = [
  ['hell', hell],
  ['dunkel', dunkel],
] as const

const appBarStil = (): Record<string, unknown> =>
  theme.components?.MuiAppBar?.styleOverrides?.root as Record<string, unknown>

describe('theme Erscheinungsbilder', () => {
  it('folgt der Einstellung des Betriebssystems und bietet keinen Schalter an', () => {
    expect(theme.colorSchemeSelector).toBe('media')
    const schluessel = theme.generateStyleSheets().flatMap((blatt) => Object.keys(blatt))
    expect(schluessel).toContain('@media (prefers-color-scheme: dark)')
  })

  it('führt genau zwei Erscheinungsbilder, hell als Vorgabe', () => {
    expect(Object.keys(theme.colorSchemes).sort()).toEqual(['dark', 'light'])
    expect(theme.defaultColorScheme).toBe('light')
    expect(hell.mode).toBe('light')
    expect(dunkel.mode).toBe('dark')
  })

  it('legt die Variablen unter dem Präfix mb an', () => {
    expect(theme.cssVarPrefix).toBe('mb')
  })
})

describe('theme Werte des Entwurfs (Z. 17–108, #978)', () => {
  // Die Werte stehen 1:1 wie im Entwurf. Wo sie abweichen, ist die Abweichung hier benannt und an
  // der Konstante in theme.ts begründet (AA-Kontrast).
  const entwurf: Record<'hell' | 'dunkel', Partial<Record<keyof WartePalette, string>>> = {
    hell: {
      grund: '#E7E9ED',
      grundTief: '#D8DBE2',
      nute: '#D5D9E0',
      platte: '#FDFDFE',
      platteFuss: '#F2F4F7',
      platteHoch: '#FFFFFF',
      rand: '#CDD2DA',
      randStark: '#B7BEC9',
      kante: 'rgba(255,255,255,.9)',
      kupferHell: '#C2743C',
      kupferSchimmer: 'rgba(168,95,44,.16)',
    },
    dunkel: {
      grund: '#0D1014',
      grundTief: '#090B0E',
      nute: '#080A0D',
      platte: '#171B22',
      platteFuss: '#12151B',
      platteHoch: '#1E242D',
      rand: '#262C36',
      randStark: '#333B47',
      kante: 'rgba(255,255,255,.075)',
      kupferHell: '#E3A26C',
      kupferSchimmer: 'rgba(208,138,82,.18)',
    },
  }

  it.each(schemata)('übernimmt %s die Grundfarben des Entwurfs unverändert', (name, palette) => {
    expect(palette.warte).toMatchObject(entwurf[name])
  })

  it('setzt Grund, Platte, Rand und Text in die MUI-Rollen', () => {
    for (const [, palette] of schemata) {
      expect(palette.background.default).toBe(palette.warte.grund)
      expect(palette.background.paper).toBe(palette.warte.platte)
      expect(palette.divider).toBe(palette.warte.rand)
    }
    expect(hell.text.primary).toBe('#14181E')
    expect(dunkel.text.primary).toBe('#E7EAEF')
    expect(dunkel.text.secondary).toBe('#98A1AE')
  })

  it('führt Kupfer als Leitfarbe', () => {
    expect(hell.primary.main).toBe('#A85F2C')
    expect(dunkel.primary.main).toBe('#D08A52')
    expect(hell.primary.light).toBe('#C2743C')
    expect(dunkel.primary.light).toBe('#E3A26C')
  })

  it('übernimmt die Melder dunkel unverändert und Zinnober und Stahl hell', () => {
    expect(dunkel.melder).toEqual({ gruen: '#46C46F', bernst: '#E0AE49', zinnob: '#F0575C', stahl: '#5B96F0', grau: '#6E7681' })
    expect(hell.melder.zinnob).toBe('#C8393E')
    expect(hell.melder.stahl).toBe('#2F6FC9')
  })

  // Abweichungen, jede mit dem Entwurfston, der die Schwelle verfehlt.
  const abweichungen = [
    ['hell: Text matt auf der Nut', '#58606C', hell.text.secondary, hell.warte.nute, 4.5],
    ['hell: Text schwach als Schrift auf der Nut', '#868E9B', hell.warte.textSchwach, hell.warte.nute, 4.5],
    ['dunkel: Text schwach als Schrift auf der Platte hoch', '#69717E', dunkel.warte.textSchwach, dunkel.warte.platteHoch, 4.5],
    ['hell: Weiß auf dem oberen Ende der Kupfertaste', '#C2743C', hell.warte.kupferTaste, '#FFFFFF', 4.5],
    ['hell: Melder Grün auf der Nut', '#2F8F4E', hell.melder.gruen, hell.warte.nute, 3],
    ['hell: Melder Bernstein auf der Nut', '#B07C15', hell.melder.bernst, hell.warte.nute, 3],
    ['hell: Melder Grau auf der Nut', '#8A929E', hell.melder.grau, hell.warte.nute, 3],
  ] as const

  it.each(abweichungen)('%s: der Entwurfston verfehlt die Schwelle, der gesetzte hält sie', (_, entwurfston, gesetzt, flaeche, schwelle) => {
    expect(kontrast(entwurfston, flaeche)).toBeLessThan(schwelle)
    expect(kontrast(gesetzt, flaeche)).toBeGreaterThanOrEqual(schwelle)
  })

  it('setzt dunkel die Grundtinte als Schrift auf Kupfer, weil Weiß dort 2,8:1 hielte', () => {
    expect(kontrast('#FFFFFF', dunkel.primary.main)).toBeLessThan(4.5)
    expect(dunkel.primary.contrastText).toBe(dunkel.warte.grund)
    expect(dunkel.warte.aufKupfer).toBe(dunkel.warte.grund)
    expect(kontrast(dunkel.primary.contrastText, dunkel.primary.main)).toBeGreaterThanOrEqual(4.5)
    expect(kontrast(dunkel.warte.aufKupfer, dunkel.warte.kupferTaste)).toBeGreaterThanOrEqual(4.5)
    expect(kontrast(hell.primary.contrastText, hell.primary.main)).toBeGreaterThanOrEqual(4.5)
  })
})

describe('theme Tiefenmodell (Entwurf Z. 38–53, 81–94)', () => {
  it('übernimmt die vier Schatten hell wie im Entwurf, mit der Tinte rgba(18,24,33)', () => {
    expect(hell.warte.schattenPlatte).toBe(
      '0 1px 0 rgba(255,255,255,.9) inset, 0 1px 2px rgba(18,24,33,.10), 0 10px 24px -14px rgba(18,24,33,.35)',
    )
    expect(hell.warte.schattenHoch).toBe(
      '0 1px 0 rgba(255,255,255,.9) inset, 0 2px 4px rgba(18,24,33,.10), 0 18px 34px -16px rgba(18,24,33,.42)',
    )
    expect(hell.warte.schattenNute).toBe('0 2px 5px rgba(18,24,33,.14) inset, 0 -1px 0 rgba(255,255,255,.9) inset')
    expect(hell.warte.schattenTaste).toBe('0 1px 0 rgba(255,255,255,.9) inset, 0 1px 2px rgba(18,24,33,.18)')
  })

  it('übernimmt die vier Schatten dunkel wie im Entwurf', () => {
    expect(dunkel.warte.schattenPlatte).toBe(
      '0 1px 0 rgba(255,255,255,.075) inset, 0 1px 2px rgba(0,0,0,.5), 0 12px 28px -16px rgba(0,0,0,.85)',
    )
    expect(dunkel.warte.schattenHoch).toBe(
      '0 1px 0 rgba(255,255,255,.11) inset, 0 2px 6px rgba(0,0,0,.55), 0 22px 40px -18px rgba(0,0,0,.95)',
    )
    expect(dunkel.warte.schattenNute).toBe('0 3px 7px rgba(0,0,0,.6) inset, 0 -1px 0 rgba(255,255,255,.05) inset')
    expect(dunkel.warte.schattenTaste).toBe('0 1px 0 rgba(255,255,255,.08) inset, 0 1px 2px rgba(0,0,0,.6)')
  })

  it.each(schemata)('leitet die Panel-Tokens %s aus den Rollen der Warte ab', (_, palette) => {
    expect(palette.panel.cardShadow).toBe(palette.warte.schattenPlatte)
    expect(palette.panel.cardShadowHover).toBe(palette.warte.schattenHoch)
    expect(palette.panel.panelShadow).toBe(palette.warte.schattenPlatte)
    expect(palette.panel.surfaceTint).toBe(palette.warte.platteFuss)
    expect(palette.panel.codeBg).toBe(palette.warte.nute)
    expect(palette.panel.headerBg).toBe(palette.warte.kopf)
    expect(palette.panel.panelHeadGradient).toBe(
      `linear-gradient(180deg,${palette.warte.platteHoch} 0%,${palette.warte.platte} 100%)`,
    )
  })

  it('rundet mit den drei Radien des Entwurfs (Z. 55–57)', () => {
    expect(PANEL_RADIUS).toBe(14)
    expect(CARD_RADIUS).toBe(10)
    expect(KLEIN_RADIUS).toBe(6)
    expect(theme.shape.borderRadius).toBe(6)
  })

  it('legt Kanten-Stärken und Anheben fest', () => {
    expect(STATUS_EDGE_WIDTH).toBe(3)
    expect(EPIC_EDGE_WIDTH).toBe(4)
    expect(CARD_LIFT).toBe(-2)
  })

  it('mischt den Kopf aus Grund (86 %) und Platte wie der Entwurf (Z. 288)', () => {
    expect(hell.warte.kopf).toBe('#EAECEF')
    expect(dunkel.warte.kopf).toBe('#0E1216')
  })
})

describe('theme Variablen-Verweise', () => {
  const tokens: ReadonlyArray<readonly [keyof PanelPalette, string]> = [
    ['surfaceTint', SURFACE_TINT],
    ['appBackground', APP_BACKGROUND],
    ['headerBg', HEADER_BG],
    ['panelHeadGradient', PANEL_HEAD_GRADIENT],
    ['cardShadow', CARD_SHADOW],
    ['cardShadowHover', CARD_SHADOW_HOVER],
    ['panelShadow', PANEL_SHADOW],
    ['surfaceHoverShadow', SURFACE_HOVER_SHADOW],
    ['codeBg', CODE_BG],
  ]

  it.each(tokens)('%s zeigt auf die Variable, die MUI erzeugt', (name, token) => {
    expect(token.startsWith(`var(--mb-palette-panel-${name}`)).toBe(true)
    expect(token).toBe(theme.vars.palette.panel[name])
  })

  it.each(tokens)('%s hat in beiden Erscheinungsbildern einen eigenen Wert', (name) => {
    expect(dunkel.panel[name]).not.toBe(hell.panel[name])
    expect(hell.panel[name]).not.toContain('var(')
    expect(dunkel.panel[name]).not.toContain('var(')
  })

  it('führt die Rollen der Warte als Verweise', () => {
    expect(NUT).toBe(theme.vars.palette.warte.nute)
    expect(SCHATTEN_NUTE).toBe(theme.vars.palette.warte.schattenNute)
    expect(SCHATTEN_TASTE).toBe(theme.vars.palette.warte.schattenTaste)
  })
})

describe('theme Schriften (Entwurf Z. 2–4, 162–194)', () => {
  it('setzt Plex Sans als Fließtext mit 14 px und Zeilenhöhe 1,5', () => {
    expect(SCHRIFT_TEXT.startsWith('"IBM Plex Sans"')).toBe(true)
    expect(theme.typography.fontFamily).toBe(SCHRIFT_TEXT)
    expect(theme.typography.body1).toMatchObject({ fontSize: '0.875rem', lineHeight: 1.5 })
  })

  it('setzt Archivo, leicht gestreckt, für die Überschriften', () => {
    expect(SCHRIFT_ANZEIGE.startsWith('"Archivo Variable"')).toBe(true)
    for (const variante of ['h1', 'h2', 'h3', 'h4', 'h5', 'h6'] as const) {
      expect(theme.typography[variante]).toMatchObject({ fontFamily: SCHRIFT_ANZEIGE, fontStretch: '112%' })
    }
  })

  it('setzt Zahlen in Plex Mono mit Tabellenziffern', () => {
    expect(SCHRIFT_MONO.startsWith('"IBM Plex Mono"')).toBe(true)
    expect(ZAHL).toEqual({ fontFamily: SCHRIFT_MONO, fontVariantNumeric: 'tabular-nums' })
  })

  it('führt das Etikett als Baustein wie im Entwurf', () => {
    expect(ETIKETT).toMatchObject({
      fontFamily: SCHRIFT_ANZEIGE,
      fontStretch: '118%',
      fontSize: 10,
      fontWeight: 600,
      letterSpacing: '.14em',
      textTransform: 'uppercase',
      color: theme.vars.palette.warte.textSchwach,
    })
    expect(theme.typography.overline).toMatchObject({ fontStretch: '118%', textTransform: 'uppercase' })
  })

  it('kennt Carlito nicht mehr', () => {
    expect(JSON.stringify(theme.typography)).not.toContain('Carlito')
  })
})

describe('theme Zebra-Streifen', () => {
  it('streift nur gerade Datenzeilen im TableBody, nicht den Header', () => {
    const root = theme.components?.MuiTable?.styleOverrides?.root as Record<string, unknown>
    const zebra = root['& .MuiTableBody-root .MuiTableRow-root:nth-of-type(even)']
    expect(zebra).toMatchObject({ backgroundColor: SURFACE_TINT })
    expect(JSON.stringify(root)).not.toContain('MuiTableHead')
  })
})

describe('theme AppBar-Override', () => {
  it('gibt einer Leiste die Fläche und Schrift des Kopfs und eine Haarlinie', () => {
    expect(appBarStil().backgroundColor).toBe(HEADER_BG)
    expect(appBarStil().color).toBe(theme.vars.palette.text.primary)
    expect(appBarStil().borderBottom).toBe(`1px solid ${theme.vars.palette.divider}`)
    expect(theme.components?.MuiAppBar?.defaultProps?.elevation).toBe(0)
  })

  it.each(schemata)('hält %s die Schrift auf dem Kopf mit 4,5:1', (_, palette) => {
    expect(kontrast(palette.panel.headerBg, palette.text.primary)).toBeGreaterThanOrEqual(4.5)
    expect(kontrast(palette.panel.headerBg, palette.text.secondary)).toBeGreaterThanOrEqual(4.5)
  })
})

describe('theme Overrides schalten mit dem Erscheinungsbild', () => {
  it('führt in den Komponenten-Overrides keinen festen Hexwert', () => {
    // MUI hängt an jede Variable den hellen Wert als Rückfall an; der gehört zur Variable und wird
    // samt Klammerinhalt ausgeblendet — mit Klammertiefe, weil Rückfallwerte `rgba(…)` tragen.
    const ohneVariablen = (text: string): string => {
      let ergebnis = ''
      let tiefe = 0
      for (let i = 0; i < text.length; i++) {
        if (tiefe === 0 && text.startsWith('var(', i)) {
          tiefe = 1
          i += 3
        } else if (tiefe > 0) {
          if (text[i] === '(') tiefe++
          if (text[i] === ')') tiefe--
        } else {
          ergebnis += text[i]
        }
      }
      return ergebnis
    }
    // Ausgenommen ist allein der Druckblock: Er setzt die hellen Werte absichtlich fest (#953).
    const alle = JSON.stringify(theme.components, (schluessel, wert: unknown) =>
      schluessel === '@media print' ? undefined : typeof wert === 'string' ? ohneVariablen(wert) : wert,
    )
    expect(alle).not.toMatch(/#[0-9A-Fa-f]{3,8}\b/)
    expect(ohneVariablen('var(--a, rgba(1,2,3,0)) #ABCDEF')).toBe(' #ABCDEF')
  })
})

describe('theme Grund der Anwendung (Entwurf Z. 152–160)', () => {
  it.each(schemata)('legt %s den Kupfer-Schimmer oben links über den Grundton', (_, palette) => {
    expect(palette.panel.appBackground).toBe(
      `radial-gradient(1100px 600px at 18% -8%, ${palette.warte.kupferSchimmer}, transparent 62%), ${palette.warte.grund}`,
    )
  })

  it('legt den Grund auf eine fixierte eigene Schicht hinter dem Inhalt', () => {
    const vorSatz = theme.components?.MuiCssBaseline?.styleOverrides as Record<string, unknown>
    expect(vorSatz['body::before']).toMatchObject({
      content: '""',
      position: 'fixed',
      inset: 0,
      zIndex: -1,
      background: APP_BACKGROUND,
    })
    expect(JSON.stringify(vorSatz)).not.toContain('attachment')
  })

  it.each(schemata)('hält background.default %s als Farbwert, nicht als Verlaufsstring', (_, palette) => {
    expect(palette.background.default).toMatch(/^#[0-9A-Fa-f]{6}$/)
    expect(palette.background.paper).toMatch(/^#[0-9A-Fa-f]{6}$/)
  })
})

/**
 * Die Flächen der Warte, auf denen Text und Zustandsfarben stehen: Nut (Schiene, Spalte, Suche),
 * Grund, Grund tief (oberes Ende der Schiene), Platte, Platte-Fuß, Platte hoch und der Kopf.
 */
const flaechenVon = (palette: typeof hell) =>
  [
    ['Nut', palette.warte.nute],
    ['Grund', palette.warte.grund],
    ['Grund tief', palette.warte.grundTief],
    ['Platte', palette.warte.platte],
    ['Platte-Fuß', palette.warte.platteFuss],
    ['Platte hoch', palette.warte.platteHoch],
    ['Kopf', palette.warte.kopf],
  ] as const

const tabelle = <T,>(bauen: (name: string, palette: typeof hell) => T[]): T[] =>
  schemata.flatMap(([name, palette]) => bauen(name, palette))

describe('theme Kontrasttabelle beider Erscheinungsbilder (AK 14)', () => {
  const textPaare = tabelle((name, palette) =>
    flaechenVon(palette).flatMap(([flaeche, wert]) => [
      [name, 'Text', flaeche, wert, palette.text.primary] as const,
      [name, 'Text matt', flaeche, wert, palette.text.secondary] as const,
      [name, 'Text schwach', flaeche, wert, palette.warte.textSchwach] as const,
    ]),
  )

  it.each(textPaare)('%s: %s auf %s hält 4,5:1', (_, __, ___, flaeche, farbe) => {
    expect(kontrast(flaeche, farbe)).toBeGreaterThanOrEqual(4.5)
  })

  it.each(schemata)('%s: matter Text auf einer gewählten Zeile hält 4,5:1', (_, palette) => {
    expect(kontrast(palette.warte.auswahl, palette.text.secondary)).toBeGreaterThanOrEqual(4.5)
  })

  const grafikPaare = tabelle((name, palette) =>
    flaechenVon(palette).flatMap(([flaeche, wert]) => [
      // Kupfer trägt Fokusring, aktives Icon und Auswahl — Grafikelemente.
      [name, 'Kupfer', flaeche, wert, palette.primary.main] as const,
      ...(Object.entries(palette.melder) as Array<[string, string]>).map(
        ([melder, ton]) => [name, `Melder ${melder}`, flaeche, wert, ton] as const,
      ),
    ]),
  )

  it.each(grafikPaare)('%s: %s auf %s hält 3:1 als bedeutungstragendes Element', (_, __, ___, flaeche, farbe) => {
    expect(kontrast(flaeche, farbe)).toBeGreaterThanOrEqual(3)
  })

  it.each(schemata)('%s: Schrift auf der Kupfertaste hält 4,5:1 über den ganzen Verlauf', (_, palette) => {
    expect(kontrast(palette.warte.aufKupfer, palette.warte.kupferTaste)).toBeGreaterThanOrEqual(4.5)
    expect(kontrast(palette.warte.aufKupfer, palette.primary.main)).toBeGreaterThanOrEqual(4.5)
  })
})

describe('theme Nachtlauf-Zustandsfarben (Plan #718, A15)', () => {
  const zustaende = ['green', 'yellow', 'red', 'grey'] as const

  it.each(schemata)('spricht %s die Melder des Entwurfs', (_, palette) => {
    expect(palette.nightRun).toEqual({
      green: palette.melder.gruen,
      yellow: palette.melder.bernst,
      red: palette.melder.zinnob,
      grey: palette.text.secondary,
    })
  })

  it('bleibt über theme.palette beim hellen Wert, den die Nachtlauf-Auswertung liest', () => {
    expect(theme.palette.nightRun).toEqual(hell.nightRun)
  })

  const paare = tabelle((name, palette) =>
    zustaende.flatMap((zustand) =>
      flaechenVon(palette).map(([flaeche, wert]) => [name, zustand, flaeche, wert, palette.nightRun[zustand]] as const),
    ),
  )

  it.each(paare)('%s: %s auf %s hält die Schwelle für das Farbfeld (3:1)', (_, __, ___, flaeche, farbe) => {
    expect(kontrast(flaeche, farbe)).toBeGreaterThanOrEqual(3)
  })

  it.each(schemata)('lässt die MUI-Semantikfarben %s unberührt', (_, palette) => {
    expect(palette.nightRun.green).not.toBe(palette.success.main)
    expect(palette.nightRun.yellow).not.toBe(palette.warning.main)
    expect(palette.nightRun.red).not.toBe(palette.error.main)
  })
})

describe('theme Status- und Vorhaben-Farben beider Erscheinungsbilder (#952, #978)', () => {
  const statusSets = Object.keys(STATUS_FARBWERTE.light) as Array<keyof typeof STATUS_FARBWERTE.light>
  const felder = ['bg', 'text', 'dot'] as const

  it.each(schemata)('trägt %s die Statusfarben aus lib/statusColors', (name, palette) => {
    expect(palette.status).toEqual(name === 'hell' ? STATUS_FARBWERTE.light : STATUS_FARBWERTE.dark)
  })

  it.each(schemata)('trägt %s die Vorhaben-Farben aus lib/epicMeta', (name, palette) => {
    expect(palette.epic).toEqual(name === 'hell' ? EPIC_FARBWERTE.light : EPIC_FARBWERTE.dark)
  })

  it.each(schemata)('setzt den Statuspunkt %s auf den Melder: Done grün, Review bernstein, In Arbeit stahl, sonst grau', (_, palette) => {
    expect(palette.status.done.dot).toBe(palette.melder.gruen)
    expect(palette.status.review.dot).toBe(palette.melder.bernst)
    expect(palette.status.progress.dot).toBe(palette.melder.stahl)
    for (const set of ['ready', 'backlog', 'neutral', 'archived'] as const) {
      expect(palette.status[set].dot).toBe(palette.melder.grau)
    }
  })

  it('erzeugt für jeden Status-Verweis aus lib/statusColors die passende Variable', () => {
    for (const set of statusSets) {
      for (const feld of felder) {
        expect(theme.vars.palette.status[set][feld]).toMatch(new RegExp(`^var\\(--mb-palette-status-${set}-${feld},`))
      }
    }
    expect(theme.vars.palette.status.done.dot.startsWith(statusColors('Done').dot.slice(0, -1))).toBe(true)
    expect(theme.vars.palette.status.archived.dot.startsWith(ARCHIVED_STATUS_COLOR.dot.slice(0, -1))).toBe(true)
  })

  it('erzeugt für jeden Vorhaben-Verweis aus lib/epicMeta die passende Variable', () => {
    for (let id = 0; id < 40; id++) {
      const platz = Number(/epic-(\d)-hue/.exec(epicColor(id))![1])
      expect(theme.vars.palette.epic[platz].hue.startsWith(epicColor(id).slice(0, -1))).toBe(true)
      expect(theme.vars.palette.epic[platz].tint.startsWith(epicTint(id).slice(0, -1))).toBe(true)
    }
  })

  /** Deckt eine `rgba(r,g,b,a)`-Tönung über eine Hexfläche und liefert die sichtbare Farbe als Hex. */
  const ueber = (rgba: string, flaeche: string): string => {
    const [r, g, b, a] = rgba.slice(5, -1).split(',').map(Number)
    const kanal = (wert: number, i: number) =>
      Math.round(wert * a + Number.parseInt(flaeche.slice(1 + 2 * i, 3 + 2 * i), 16) * (1 - a))
        .toString(16)
        .padStart(2, '0')
    return `#${[r, g, b].map(kanal).join('')}`
  }

  it('rechnet die Tönung wie der Browser: Deckkraft 0 lässt die Fläche, 1 den Farbton stehen', () => {
    expect(ueber('rgba(255,0,0,0)', '#123456')).toBe('#123456')
    expect(ueber('rgba(255,0,0,1)', '#123456')).toBe('#ff0000')
  })

  const statusPaare = tabelle((name, palette) =>
    statusSets.flatMap((set) => [
      [name, set, 'Schrift auf der Schild-Fläche', palette.status[set].bg, palette.status[set].text, 4.5] as const,
      ...flaechenVon(palette).map(
        ([flaeche, wert]) => [name, set, `Punkt auf ${flaeche}`, wert, palette.status[set].dot, 3] as const,
      ),
    ]),
  )

  it.each(statusPaare)('%s: Status %s, %s hält die Schwelle', (_, __, ___, flaeche, farbe, schwelle) => {
    expect(kontrast(flaeche, farbe)).toBeGreaterThanOrEqual(schwelle)
  })

  // Das Schild eines Vorhabens steht auf Karten, Tabellenzeilen und Platten (Entwurf Z. 780–786).
  const schildFlaechen = (palette: typeof hell) =>
    flaechenVon(palette).filter(([flaeche]) => flaeche.startsWith('Platte'))

  const epicPaare = tabelle((name, palette) =>
    palette.epic.flatMap((platz, i) => schildFlaechen(palette).map(([flaeche, wert]) => [name, i, flaeche, wert, platz] as const)),
  )

  it.each(epicPaare)('%s: Vorhaben-Schild %s auf %s hält 3:1 als Rand und 4,5:1 als Schrift auf seiner Tönung', (_, __, ___, wert, platz) => {
    expect(kontrast(wert, platz.hue)).toBeGreaterThanOrEqual(3)
    expect(kontrast(ueber(platz.tint, wert), platz.hue)).toBeGreaterThanOrEqual(4.5)
  })
})

/** Die `MuiCssBaseline`-Overrides als Objekt — dort liegen die anwendungsweiten Regeln. */
const grundregeln = (): Record<string, Record<string, unknown>> =>
  theme.components?.MuiCssBaseline?.styleOverrides as Record<string, Record<string, unknown>>

describe('theme Bewegung reduzieren (AK 16, #953)', () => {
  // Geprüft wird das Theme-Objekt, nicht das Verhalten: jsdom wertet Media-Queries nicht aus, und
  // `src/test/setup.ts` stubbt kein `matchMedia` — ein Verhaltenstest wäre nur scheinbar grün.
  const regel = () =>
    grundregeln()['@media (prefers-reduced-motion: reduce)']?.['*, *::before, *::after'] as
      | Record<string, string>
      | undefined

  it('setzt bei abgestellter Bewegung Übergänge und Animationen zentral auf null', () => {
    expect(regel()).toBeDefined()
    expect(regel()!.transitionDuration).toMatch(/^0s !important$/)
    expect(regel()!.animationDuration).toMatch(/^0s !important$/)
  })

  it('lässt eine Endlos-Animation dabei nicht endlos mit Dauer null laufen', () => {
    expect(regel()!.animationIterationCount).toBe('1 !important')
  })

  it('lässt theme.transitions unverändert — das Theme kennt keine Media-Query', () => {
    // Eine Neutralisierung dort träfe alle Nutzer, auch die ohne die Einstellung (Plan #932 E13).
    expect(theme.transitions.duration.standard).toBeGreaterThan(0)
    expect(theme.transitions.duration.enteringScreen).toBeGreaterThan(0)
    expect(theme.transitions.duration.leavingScreen).toBeGreaterThan(0)
  })
})

describe('theme Ausdruck (Fachplan-Frage 6, E6, #953)', () => {
  const druck = () => grundregeln()['@media print'] as Record<string, Record<string, unknown>> | undefined

  it('führt eine eigene Regel für den Ausdruck', () => {
    expect(druck()).toBeDefined()
  })

  it('druckt ohne getönten Grund', () => {
    expect(druck()!['body::before']).toEqual({ background: 'none' })
  })

  it('druckt ohne Verläufe und ohne Schatten', () => {
    const alles = druck()!['*, *::before, *::after']
    expect(alles.backgroundImage).toBe('none !important')
    expect(alles.boxShadow).toBe('none !important')
    expect(alles.textShadow).toBe('none !important')
  })

  it('druckt immer im hellen Erscheinungsbild, auch bei dunklem System', () => {
    // Die dunklen Werte hängen an `@media (prefers-color-scheme: dark)` und gälten auch im Druck.
    // Der Druckblock setzt deshalb alle Palette-Variablen auf ihre hellen Werte zurück, mit
    // höherer Spezifität als die Dunkel-Regel.
    const wurzel = druck()![':root:root'] as Record<string, string>
    expect(wurzel.colorScheme).toBe('light')
    expect(wurzel['--mb-palette-background-paper']).toBe(hell.background.paper)
    expect(wurzel['--mb-palette-text-primary']).toBe(hell.text.primary)
    expect(wurzel['--mb-palette-panel-surfaceTint']).toBe(hell.panel.surfaceTint)
    expect(wurzel['--mb-palette-status-done-dot']).toBe(hell.status.done.dot)
    expect(wurzel['--mb-palette-epic-0-tint']).toBe(hell.epic[0].tint)
  })

  it('setzt im Druck jede Variable zurück, die das dunkle Erscheinungsbild ändert', () => {
    const dunkleRegel = theme
      .generateStyleSheets()
      .find((blatt) => '@media (prefers-color-scheme: dark)' in blatt)!['@media (prefers-color-scheme: dark)'] as Record<
      string,
      Record<string, string>
    >
    const dunkleVariablen = Object.keys(dunkleRegel[':root']).filter((name) => name.startsWith('--'))
    const wurzel = druck()![':root:root'] as Record<string, string>
    expect(dunkleVariablen.length).toBeGreaterThan(50)
    expect(dunkleVariablen.filter((name) => !(name in wurzel))).toEqual([])
  })
})

describe('theme Teilbaum mit beiden Erscheinungsbildern (#987)', () => {
  const dunkelBlock = () =>
    ERSCHEINUNGSBILD_SX['@media (prefers-color-scheme: dark)'] as Record<string, string>

  it('setzt die hellen Variablen und schaltet im dunklen System auf die dunklen um', () => {
    // Die Nachtlauf-Seite setzt an ihrem Wurzelknoten die hellen Variablen fest (#954). Ein Bereich
    // darin, der Kupferwarte trägt, kann sie nicht „erben lassen" — er schreibt beide Sätze selbst,
    // mit derselben Media-Query, die auch `:root` benutzt.
    expect(ERSCHEINUNGSBILD_SX['--mb-palette-background-default']).toBe(hell.background.default)
    expect(dunkelBlock()['--mb-palette-background-default']).toBe(dunkel.background.default)
    expect(ERSCHEINUNGSBILD_SX.colorScheme).toBe('light')
    expect(dunkelBlock().colorScheme).toBe('dark')
  })

  it('schaltet jede Variable zurueck, die das dunkle Erscheinungsbild aendert', () => {
    // Bliebe eine hängen, stünde im Dunkeln ein heller Wert mitten in der dunklen Fläche.
    const dunkleRegel = theme
      .generateStyleSheets()
      .find((blatt) => '@media (prefers-color-scheme: dark)' in blatt)!['@media (prefers-color-scheme: dark)'] as Record<
      string,
      Record<string, string>
    >
    const dunkleVariablen = Object.keys(dunkleRegel[':root']).filter((name) => name.startsWith('--'))

    expect(dunkleVariablen.length).toBeGreaterThan(50)
    expect(dunkleVariablen.filter((name) => !(name in dunkelBlock()))).toEqual([])
  })

  it('traegt die Warte-Tokens beider Erscheinungsbilder, aus denen die Bausteine bauen', () => {
    expect(ERSCHEINUNGSBILD_SX['--mb-palette-warte-platte']).toBe(hell.warte.platte)
    expect(dunkelBlock()['--mb-palette-warte-platte']).toBe(dunkel.warte.platte)
  })
})

describe('theme Tastaturfokus (AK 15, #953)', () => {
  it('zeichnet einen sichtbaren Fokusring an jedem per Tastatur fokussierten Element', () => {
    const ring = grundregeln()[':focus-visible'] as Record<string, string>
    expect(ring.outline).toBe(`2px solid ${theme.vars.palette.primary.main}`)
    expect(ring.outlineOffset).toBe('2px')
  })

  it('setzt den Ring auch an MUI-Bedienelementen, die ihren Umriss sonst auf null stellen', () => {
    // ButtonBase setzt `outline: 0` und zeigt Fokus nur über eine blasse Welle.
    const root = theme.components?.MuiButtonBase?.styleOverrides?.root as Record<string, Record<string, string>>
    expect(root['&.Mui-focusVisible'].outline).toBe(`2px solid ${theme.vars.palette.primary.main}`)
  })

  it('unterdrückt den Fokus nirgends ersatzlos', () => {
    // Die einzige Ausnahme sind Eingabefelder: Sie tragen den Fokus bereits an ihrer Rahmenlinie
    // (MuiOutlinedInput, Primärfarbe). Ein zweiter Ring läge doppelt um dasselbe Feld.
    const eingabe = grundregeln()['.MuiInputBase-input:focus-visible'] as Record<string, string>
    expect(eingabe.outline).toBe('none')
    const rahmen = (theme.components?.MuiOutlinedInput?.styleOverrides?.root as Record<string, Record<string, unknown>>)[
      '&.Mui-focused .MuiOutlinedInput-notchedOutline'
    ]
    expect(rahmen.borderColor).toBe(theme.vars.palette.primary.main)
    const ohneAusnahme = JSON.stringify({ ...grundregeln(), '.MuiInputBase-input:focus-visible': undefined })
    expect(ohneAusnahme).not.toMatch(/"outline":"(none|0)"/)
  })
})

describe('theme Tabellen: Dichte und Ziffern (AK 10, AK 12b, #953)', () => {
  it('führt Tabellenziffern als zentralen Baustein', () => {
    expect(TABELLENZIFFERN).toEqual({ fontVariantNumeric: 'tabular-nums' })
  })

  it('setzt rechtsbündige Tabellenzellen in Tabellenziffern', () => {
    const zelle = theme.components?.MuiTableCell?.styleOverrides as Record<string, Record<string, unknown>>
    expect(zelle.alignRight).toMatchObject(TABELLENZIFFERN)
  })

  it('trägt die Dichtestufe an den Tabellenzellen', () => {
    // Die Dichte sitzt in der Zelle: Eine MUI-Tabellenzeile hat weder Höhe noch Polsterung, die
    // ein Override dort ändern könnte (#953, E2 im Abschlussbericht).
    expect(theme.components?.MuiTableCell?.defaultProps?.size).toBe('small')
    const zelle = theme.components?.MuiTableCell?.styleOverrides as Record<string, Record<string, unknown>>
    expect(zelle.sizeSmall).toEqual({ paddingTop: 4, paddingBottom: 4 })
  })
})

describe('theme Meldungen in beiden Erscheinungsbildern (#960)', () => {
  const zustaende = ['error', 'warning', 'info', 'success'] as const

  /** MUI rechnet die hellen Meldungsflächen selbst und liefert sie als `rgb(r, g, b)`. */
  const alsHex = (farbe: string): string => {
    const rgb = /^rgb\((\d+), (\d+), (\d+)\)$/.exec(farbe)
    return rgb ? `#${rgb.slice(1).map((k) => Number(k).toString(16).padStart(2, '0')).join('')}` : farbe
  }

  it.each(schemata.flatMap(([name, palette]) => zustaende.map((z) => [name, z, palette] as const)))(
    '%s: gefüllte Meldung „%s" hält 4,5:1',
    (_, zustand, palette) => {
      // Die Toasts der Anwendung sind gefüllte Meldungen (SnackbarProvider).
      expect(kontrast(palette.Alert[`${zustand}FilledBg`], palette.Alert[`${zustand}FilledColor`])).toBeGreaterThanOrEqual(4.5)
    },
  )

  it.each(schemata.flatMap(([name, palette]) => zustaende.map((z) => [name, z, palette] as const)))(
    '%s: Meldung „%s" auf getönter Fläche hält 4,5:1',
    (_, zustand, palette) => {
      // Formular- und Seitenmeldungen (Anmelden, Dialoge) nutzen die Standardform.
      expect(kontrast(alsHex(palette.Alert[`${zustand}StandardBg`]), alsHex(palette.Alert[`${zustand}Color`]))).toBeGreaterThanOrEqual(4.5)
    },
  )
})
