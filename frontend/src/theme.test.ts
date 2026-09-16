import { describe, expect, it } from 'vitest'
import { EPIC_FARBWERTE, epicColor, epicTint } from './lib/epicMeta'
import { kontrast } from './lib/kontrast'
import { ARCHIVED_STATUS_COLOR, STATUS_FARBWERTE, statusColors } from './lib/statusColors'
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
  TABELLENZIFFERN,
  theme,
  type PanelPalette,
} from './theme'

/**
 * Seit #951 trägt das Theme zwei Erscheinungsbilder als MUI-CSS-Variablen. Die exportierten Tokens
 * sind deshalb keine Hexwerte mehr, sondern `var(--mb-…)`-Verweise; geprüft wird zweierlei: dass
 * der Verweis auf die Variable zeigt, die MUI tatsächlich erzeugt, und welcher Wert je
 * Erscheinungsbild dahinter liegt. jsdom löst `var()` nicht auf — die Werte liest der Test
 * deshalb aus `theme.colorSchemes`, nicht aus einem gerenderten Stil.
 */
const hell = theme.colorSchemes.light!.palette
const dunkel = theme.colorSchemes.dark!.palette
const schemata = [
  ['hell', hell],
  ['dunkel', dunkel],
] as const

// Seit #951 ein festes Objekt: Die Overrides lesen keine Theme-Funktion mehr (siehe theme.ts).
const appBarStil = (): Record<string, unknown> =>
  theme.components?.MuiAppBar?.styleOverrides?.root as Record<string, unknown>

describe('theme Erscheinungsbilder', () => {
  it('folgt der Einstellung des Betriebssystems und bietet keinen Schalter an', () => {
    // `media` erzeugt die Dunkelwerte unter `@media (prefers-color-scheme: dark)`. Die Selektoren
    // `class` oder `data` setzten einen Zustand voraus, den jemand umschalten müsste — genau das
    // schließt der Fachplan aus („kein Einstellen des Erscheinungsbildes").
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

describe('theme Panel-Tokens als Variablen', () => {
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
    // Die Konstante bleibt eine Zeichenkette, damit ihre Fundstellen außerhalb von theme.ts
    // unberührt bleiben; sie trägt aber den Verweis statt eines Werts.
    expect(token.startsWith(`var(--mb-palette-panel-${name}`)).toBe(true)
    expect(token).toBe(theme.vars.palette.panel[name])
  })

  it.each(tokens)('%s hat in beiden Erscheinungsbildern einen eigenen Wert', (name) => {
    expect(hell.panel[name]).toBeTruthy()
    expect(dunkel.panel[name]).toBeTruthy()
    expect(dunkel.panel[name]).not.toBe(hell.panel[name])
    expect(hell.panel[name]).not.toContain('var(')
    expect(dunkel.panel[name]).not.toContain('var(')
  })
})

describe('theme Zebra-Streifen', () => {
  it('streift nur gerade Datenzeilen im TableBody, nicht den Header', () => {
    const root = theme.components?.MuiTable?.styleOverrides?.root as Record<string, unknown>
    expect(root).toBeDefined()

    const zebra = root['& .MuiTableBody-root .MuiTableRow-root:nth-of-type(even)']
    // Nur Body-Zeilen: der Selektor ist auf TableBody eingeschränkt (kein TableHead).
    expect(zebra).toMatchObject({ backgroundColor: SURFACE_TINT })
    expect(JSON.stringify(root)).not.toContain('MuiTableHead')
  })
})

describe('theme Design-Tokens (Panel)', () => {
  // `src/theme.ts` ist in vite.config.ts von der Coverage ausgenommen. Ohne diese
  // Zusicherungen erzwingt kein Gate die Existenz und die Werte der Tokens.
  it('legt die Kanten-Stärken und die hellen Flächentöne mit den festgeschriebenen Werten fest', () => {
    expect(STATUS_EDGE_WIDTH).toBe(3)
    expect(EPIC_EDGE_WIDTH).toBe(4)
    expect(hell.panel.surfaceTint).toBe('#F6FAFB')
    expect(hell.panel.codeBg).toBe('#f4f5f7')
  })

  it('führt die Flächentöne beider Erscheinungsbilder als Hexwert', () => {
    for (const [, palette] of schemata) {
      expect(palette.panel.surfaceTint).toMatch(/^#[0-9A-Fa-f]{6}$/)
      expect(palette.panel.headerBg).toMatch(/^#[0-9A-Fa-f]{6}$/)
      expect(palette.panel.codeBg).toMatch(/^#[0-9A-Fa-f]{6}$/)
      expect(palette.panel.ice).toMatch(/^#[0-9A-Fa-f]{6}$/)
    }
  })

  it.each(schemata)('hebt einfache Flächen %s mit einem Teal-Schatten an, ohne schwarzen Farbanteil', (_, palette) => {
    const shadow = palette.panel.surfaceHoverShadow.toLowerCase()
    expect(shadow).toMatch(/rgba\((47,140,151|92,188,199)/)
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
  it('gibt der Karte hell eine weiße Lichtkante und zwei Schattenebenen, im Ruhezustand wie beim Hover', () => {
    for (const shadow of [hell.panel.cardShadow, hell.panel.cardShadowHover]) {
      expect(shadow).toContain('inset 0 1px 0 #FFFFFF')
      // Zwei abgesetzte Ebenen: eine harte Kontaktschattierung, eine weiche Streuung.
      expect(shadow.match(/rgba\(36,53,57/g)).toHaveLength(2)
    }
    // Der Hover öffnet weiter, als der Ruhezustand steht.
    expect(hell.panel.cardShadowHover).toContain('30px')
  })

  it('gibt der Karte auch dunkel eine Lichtkante — gedämpft, weil volles Weiß dort grell wäre', () => {
    for (const shadow of [dunkel.panel.cardShadow, dunkel.panel.cardShadowHover]) {
      expect(shadow).toMatch(/^inset 0 1px 0 rgba\(255,255,255,0\.\d+\)/)
      expect(shadow.match(/rgba\(4,14,16/g)).toHaveLength(2)
    }
    expect(dunkel.panel.cardShadowHover).toContain('30px')
  })

  it.each(schemata)('führt alle Flächen-Schatten %s in einer Tinte, nie in Schwarz', (name, palette) => {
    // Hell ist es die Marken-Tinte, dunkel eine fast schwarze Teal-Tinte: Auch auf dunklem Grund
    // wirkt ein Schatten in der Grundfarbe wie Licht und ein schwarzer wie Schmutz.
    const tinte = name === 'hell' ? 'rgba(36,53,57' : 'rgba(4,14,16'
    for (const shadow of [palette.panel.cardShadow, palette.panel.cardShadowHover, palette.panel.panelShadow]) {
      const lower = shadow.toLowerCase()
      expect(lower).toContain(tinte)
      expect(lower).not.toContain('rgba(0,0,0')
      expect(lower).not.toContain('rgba(0, 0, 0')
      expect(lower).not.toContain('#000')
      expect(lower).not.toContain('black')
    }
  })

  it.each(schemata)('lässt den Panel-Kopf %s auf die Papierfläche auslaufen, damit er kein eigener Kasten wird', (_, palette) => {
    expect(palette.panel.panelHeadGradient).toContain('linear-gradient')
    expect(palette.panel.panelHeadGradient).toContain(palette.panel.ice)
    expect(palette.panel.panelHeadGradient).toContain(palette.background.paper)
  })

  it('hebt die Karte nach oben an, nicht nach unten', () => {
    expect(CARD_LIFT).toBeLessThan(0)
  })
})

describe('theme Kopfleiste', () => {
  // Die Leiste war vor #653 mittleres Teal mit weißer Schrift (3,85:1, AA verfehlt) und danach
  // weiß. Diese Zusicherung hält den Weg zurück zur Farbe offen, ohne den alten Fehler zu
  // wiederholen: Farbe ja, aber nur mit einem Kontrast, der die AA-Schwelle hält.
  it.each(schemata)('hält %s mit der Textfarbe die AA-Schwelle für normalen Text', (_, palette) => {
    expect(kontrast(palette.panel.headerBg, palette.text.primary)).toBeGreaterThanOrEqual(4.5)
  })

  it('wäre hell mit weißer Schrift schlechter — deshalb trägt die Leiste dunkle', () => {
    expect(kontrast(hell.panel.headerBg, '#FFFFFF')).toBeLessThan(4.5)
  })

  it('trägt hell den hellen und dunkel den dunklen Teal der Palette', () => {
    expect(hell.panel.headerBg).toBe('#5BABB5')
    expect(hell.panel.headerBg).toBe(hell.primary.light)
    expect(dunkel.panel.headerBg).toBe('#1E5F68')
  })
})

describe('theme AppBar-Override', () => {
  it('gibt der Kopfleiste ihre eigene Fläche, nicht die Papierfläche und nicht die Primärfarbe', () => {
    const style = appBarStil()

    expect(style.backgroundColor).toBe(HEADER_BG)
    // Weiß war der Zustand zwischen #653 und dem 2026-08-31; die Primärfarbe war der davor und
    // verfehlte mit weißer Schrift die AA-Schwelle.
    for (const [, palette] of schemata) {
      expect(palette.panel.headerBg).not.toBe(palette.background.paper)
      expect(palette.panel.headerBg).not.toBe(palette.primary.main)
    }
  })

  it('gibt der Kopfleiste auch ihre Textfarbe, als Variable', () => {
    // Geerbtes Weiss aus primary.contrastText waere auf dem hellen Teal schlechter lesbar. Als
    // Variable, damit die Schrift im dunklen Erscheinungsbild mitschaltet.
    expect(appBarStil().color).toBe(theme.vars.palette.text.primary)
  })

  it('trennt die Kopfleiste mit einer Haarlinie statt mit einer Elevation', () => {
    expect(appBarStil().borderBottom).toBe(`1px solid ${theme.vars.palette.divider}`)
    // MUI setzt am AppBar eine eigene Elevation von 4; der MuiPaper-Default 0 greift dort nicht.
    expect(theme.components?.MuiAppBar?.defaultProps?.elevation).toBe(0)
  })
})

describe('theme Overrides schalten mit dem Erscheinungsbild', () => {
  it('führt in den Komponenten-Overrides keinen festen Hexwert', () => {
    // Ein Hexwert in einem Override bliebe im dunklen Erscheinungsbild hell. Die Overrides tragen
    // deshalb ausschließlich Variablen; die Werte stehen in `colorSchemes`.
    // MUI hängt an jede Variable den hellen Wert als Rückfall an (`var(--mb-…, #F6FAFB)`); der
    // gehört zur Variable und wird deshalb samt Klammerinhalt ausgeblendet — mit Klammertiefe,
    // weil Rückfallwerte selbst `rgba(…)` tragen.
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
    // Ausgenommen ist allein der Druckblock: Er setzt die hellen Werte absichtlich fest, damit der
    // Ausdruck auch bei dunklem System hell bleibt (#953) — dort ist ein fester Wert der Zweck.
    const alle = JSON.stringify(theme.components, (schluessel, wert: unknown) =>
      schluessel === '@media print' ? undefined : typeof wert === 'string' ? ohneVariablen(wert) : wert,
    )
    expect(alle).not.toMatch(/#[0-9A-Fa-f]{3,8}\b/)
    // Gegenprobe: Die Ausblendung frisst keinen Hexwert außerhalb einer Variable.
    expect(ohneVariablen('var(--a, rgba(1,2,3,0)) #ABCDEF')).toBe(' #ABCDEF')
  })
})

describe('theme Grund der Anwendung', () => {
  it('tönt den hellen Grund aus dem Eis der Palette, ohne eine neue Hexfarbe einzuführen', () => {
    // Zwei radiale Verläufe, beide aus ICE — der Grund trägt keinen Ton, den die Palette nicht kennt.
    expect(hell.panel.appBackground.match(/#EDF5F6/g)).toHaveLength(2)
    expect(hell.panel.appBackground.match(/radial-gradient/g)).toHaveLength(2)
  })

  it.each(schemata)('baut den Grund %s aus den eigenen Flächentönen', (_, palette) => {
    const grund = palette.panel.appBackground
    expect(grund.split(palette.panel.ice)).toHaveLength(3)
    expect(grund.match(/radial-gradient/g)).toHaveLength(2)
  })

  it.each(schemata)('legt %s eine getönte Grundfläche unter die Verläufe, nicht die Papierfläche', (_, palette) => {
    // Der tragende Teil der Tönung. Mit `#FFFFFF` als Grundfläche war der Grund nur dort getönt,
    // wo die Verläufe reichten — bei 1920px Breite blieben Mitte, unterer Bereich und beide
    // unteren Ecken reines Weiß, die Tönung war auf einem breiten Bildschirm unsichtbar.
    expect(palette.panel.appBackground.endsWith(palette.panel.surfaceTint)).toBe(true)
    expect(palette.panel.appBackground).not.toContain(palette.background.paper)
  })

  it.each(schemata)('lässt die Verläufe %s weit genug auslaufen, um Fläche zu tragen', (_, palette) => {
    // Die erste Fassung lief bei 55 % von 1200px aus und deckte damit ab etwa 660px nichts mehr.
    // Ein Verlauf, der auf einem Drittel der Fläche endet, setzt einen Akzent statt einen Grund.
    const radien = [...palette.panel.appBackground.matchAll(/(\d+)px (\d+)px at/g)].map((m) => Number(m[1]))
    expect(radien).toHaveLength(2)
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
    for (const [, palette] of schemata) {
      expect(palette.panel.appBackground).not.toContain('background-attachment')
    }
    expect(JSON.stringify(theme.components?.MuiCssBaseline?.styleOverrides)).not.toContain(
      'attachment',
    )
  })

  it.each(schemata)('hält background.default %s als Farbwert, nicht als Verlaufsstring', (_, palette) => {
    // MUI leitet aus diesem Feld Kontraste ab; ein Verlauf bräche die Komponenten, die das tun.
    expect(palette.background.default).toMatch(/^#[0-9A-Fa-f]{6}$/)
    expect(palette.background.paper).toMatch(/^#[0-9A-Fa-f]{6}$/)
  })
})

/**
 * Die tatsächlichen Flächen eines Erscheinungsbilds, auf denen Text und Zustandsfarben stehen:
 * Papier (Karten, Panels, Dialoge), getönter Grund, Eis der Panel-Köpfe und der Code-Grund. Die
 * Kopfleiste steht gesondert, weil auf ihr nur die Textfarbe steht.
 */
const flaechenVon = (palette: typeof hell) =>
  [
    ['Papier', palette.background.paper],
    ['Grund', palette.panel.surfaceTint],
    ['Eis', palette.panel.ice],
    ['Code', palette.panel.codeBg],
  ] as const

const tabelle = <T,>(bauen: (name: string, palette: typeof hell) => T[]): T[] =>
  schemata.flatMap(([name, palette]) => bauen(name, palette))

describe('theme Kontrasttabelle beider Erscheinungsbilder (AK 14)', () => {
  // Kontrast wird gegen die Fläche gerechnet, auf der der Text steht (CLAUDE-design.md) — und seit
  // #951 in beiden Erscheinungsbildern. Weiß allein genügt als Nachweis nicht mehr.
  const textPaare = tabelle((name, palette) =>
    flaechenVon(palette).flatMap(([flaeche, wert]) => [
      [name, 'Fließtext', flaeche, wert, palette.text.primary] as const,
      [name, 'Sekundärtext', flaeche, wert, palette.text.secondary] as const,
    ]),
  )

  it.each(textPaare)('%s: %s auf %s hält 4,5:1', (_, __, ___, flaeche, farbe) => {
    expect(kontrast(flaeche, farbe)).toBeGreaterThanOrEqual(4.5)
  })

  const flaechenPaare = tabelle((name, palette) =>
    // Die Primärfarbe trägt Fokuslinie, Auswahl und Akzente — Grafikelemente, kein Fließtext.
    flaechenVon(palette).map(([flaeche, wert]) => [name, 'Primärfarbe', flaeche, wert, palette.primary.main] as const),
  )

  it.each(flaechenPaare)('%s: %s auf %s hält 3:1 als bedeutungstragendes Element', (_, __, ___, flaeche, farbe) => {
    expect(kontrast(flaeche, farbe)).toBeGreaterThanOrEqual(3)
  })

  it('hält dunkel die Schrift auf einer gefüllten Primärfläche mit 4,5:1', () => {
    // Hell liegt Weiß auf #2F8C97 bei 3,95:1 — ein Bestandswert, den dieses Paket nicht ändert.
    // Dunkel wird der Ton neu gewählt und muss die Schwelle deshalb von Anfang an halten.
    expect(kontrast(dunkel.primary.main, dunkel.primary.contrastText)).toBeGreaterThanOrEqual(4.5)
  })
})

describe('theme Nachtlauf-Zustandsfarben (Plan #718, A15)', () => {
  // Die Ampel-Flächen (#738) der Nachtlauf-Auswertung stehen auf Papier, Grund und Eis. Alle vier
  // Zustände dienen ausschließlich als ausgefüllte Fläche, nicht als Text — deshalb gilt hier nur
  // die 3:1-Schwelle für bedeutungstragende Grafikelemente, nicht die 4,5:1 für Text.
  const zustaende = ['green', 'yellow', 'red', 'grey'] as const
  const paare = tabelle((name, palette) =>
    zustaende.flatMap((zustand) =>
      flaechenVon(palette)
        .filter(([flaeche]) => flaeche !== 'Code')
        .map(([flaeche, wert]) => [name, zustand, flaeche, wert, palette.nightRun[zustand]] as const),
    ),
  )

  it.each(schemata)('legt %s für alle vier Zustände einen eigenen Palette-Eintrag als Hexwert an', (_, palette) => {
    for (const zustand of zustaende) {
      expect(palette.nightRun[zustand]).toMatch(/^#[0-9A-Fa-f]{6}$/)
    }
  })

  it('gibt jedem Zustand dunkel einen eigenen Ton', () => {
    for (const zustand of zustaende) {
      expect(dunkel.nightRun[zustand]).not.toBe(hell.nightRun[zustand])
    }
  })

  it.each(schemata)('führt Grau %s auf den Sekundärtext zurück, nicht auf text.disabled', (_, palette) => {
    // `text.disabled` ist in theme.ts gar nicht gesetzt; es gälte der MUI-Default
    // rgba(0,0,0,0.38) mit rund 2,8:1. Grau ist hier aber ein bedeutungstragender Zustand
    // („vom Lauf nicht bearbeitet"), kein deaktiviertes Bedienelement.
    expect(palette.nightRun.grey).toBe(palette.text.secondary)
  })

  it('bleibt über theme.palette beim hellen Wert, den die Nachtlauf-Auswertung bisher liest', () => {
    expect(theme.palette.nightRun).toEqual(hell.nightRun)
  })

  it.each(paare)('%s: %s auf %s hält die Schwelle für das Farbfeld (3:1)', (_, __, ___, flaeche, farbe) => {
    expect(kontrast(flaeche, farbe)).toBeGreaterThanOrEqual(3)
  })

  it.each(schemata)('lässt die MUI-Semantikfarben %s unberührt', (_, palette) => {
    // `success`/`warning`/`error` sind im Frontend an Dutzenden Nicht-Test-Stellen in Gebrauch
    // (Lösch-Buttons, Alert-`severity`, Feldfehler). Sie umzudefinieren färbte all das mit um.
    expect(palette.nightRun.green).not.toBe(palette.success.main)
    expect(palette.nightRun.yellow).not.toBe(palette.warning.main)
    expect(palette.nightRun.red).not.toBe(palette.error.main)
  })
})

describe('theme Status- und Vorhaben-Farben beider Erscheinungsbilder (#952)', () => {
  // Die Werte stehen in `lib/statusColors.ts` und `lib/epicMeta.ts` (Plan #932 E10/E11), das Theme
  // legt sie als Variablen an. Die beiden Module liefern Verweise; hier wird geprüft, dass diese
  // Verweise auf Variablen zeigen, die MUI tatsächlich erzeugt, und welche Werte dahinter liegen.
  const statusSets = Object.keys(STATUS_FARBWERTE.light) as Array<keyof typeof STATUS_FARBWERTE.light>
  const felder = ['bg', 'text', 'dot'] as const

  it.each(schemata)('trägt %s die Statusfarben aus lib/statusColors', (name, palette) => {
    expect(palette.status).toEqual(name === 'hell' ? STATUS_FARBWERTE.light : STATUS_FARBWERTE.dark)
  })

  it.each(schemata)('trägt %s die Vorhaben-Farben aus lib/epicMeta', (name, palette) => {
    expect(palette.epic).toEqual(name === 'hell' ? EPIC_FARBWERTE.light : EPIC_FARBWERTE.dark)
  })

  it('erzeugt für jeden Status-Verweis aus lib/statusColors die passende Variable', () => {
    for (const set of statusSets) {
      for (const feld of felder) {
        expect(theme.vars.palette.status[set][feld]).toMatch(new RegExp(`^var\\(--mb-palette-status-${set}-${feld},`))
      }
    }
    // Stichprobe über die öffentliche Funktion: derselbe Name, nur ohne Rückfallwert.
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

  // Die dunklen Werte sind neu gewählt und halten AA von Anfang an. Die hellen Werte sind
  // unveränderter Bestand; ihre Lücken hält der Abschlussbericht zu #952 fest.
  const dunkleFlaechen = flaechenVon(dunkel).filter(([flaeche]) => flaeche !== 'Code')

  it.each(statusSets)('dunkel: Statustext %s hält 4,5:1 auf seiner Fläche', (set) => {
    expect(kontrast(dunkel.status[set].bg, dunkel.status[set].text)).toBeGreaterThanOrEqual(4.5)
  })

  it.each(statusSets.flatMap((set) => dunkleFlaechen.map(([flaeche, wert]) => [set, flaeche, wert] as const)))(
    'dunkel: Statuspunkt %s hält auf %s 3:1',
    (set, _, wert) => {
      expect(kontrast(wert, dunkel.status[set].dot)).toBeGreaterThanOrEqual(3)
    },
  )

  it.each(EPIC_FARBWERTE.dark.flatMap((platz, i) => dunkleFlaechen.map(([flaeche, wert]) => [i, flaeche, wert, platz] as const)))(
    'dunkel: Vorhaben-Platz %s hält auf %s 3:1 als Kante und 4,5:1 als Kürzel auf seinem Tint',
    (_, __, wert, platz) => {
      expect(kontrast(wert, platz.hue)).toBeGreaterThanOrEqual(3)
      expect(kontrast(ueber(platz.tint, wert), platz.hue)).toBeGreaterThanOrEqual(4.5)
    },
  )
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
