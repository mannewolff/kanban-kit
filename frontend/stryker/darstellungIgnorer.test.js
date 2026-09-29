import { parse } from '@babel/parser'
import traverseModule from '@babel/traverse'
import { PluginKind } from '@stryker-mutator/api/plugin'
import { describe, expect, it } from 'vitest'
import { DarstellungIgnorer, STIL_SCHLUESSEL, strykerPlugins } from './darstellungIgnorer.js'

// `@babel/traverse` ist CommonJS; je nach Interop liegt die Funktion am Modul oder an `default`.
const traverse = traverseModule.default ?? traverseModule

/**
 * Durchläuft die Quelle wie Strykers `IgnorerBookkeeper`: Liefert der Ignorer für einen Knoten
 * einen Grund, gilt der ganze Teilbaum darunter als ausgenommen, bis der Knoten wieder verlassen
 * wird. Zurück kommt die Menge der ausgenommenen Knoten.
 */
function ausgenommeneKnoten(quelle) {
  const ignorer = new DarstellungIgnorer()
  const ast = parse(quelle, { sourceType: 'module', plugins: ['typescript', 'jsx'] })
  const ausgenommen = new Set()
  let aktiv
  traverse(ast, {
    enter(path) {
      if (!aktiv && ignorer.shouldIgnore(path)) aktiv = path.node
      if (aktiv) ausgenommen.add(path.node)
    },
    exit(path) {
      if (aktiv === path.node) aktiv = undefined
    },
  })
  return { ast, ausgenommen }
}

/** Der erste Knoten, dessen Quelltext genau `ausschnitt` ist — dort setzt ein Mutant an. */
function knotenMitText(quelle, ast, ausschnitt) {
  let gefunden
  traverse(ast, {
    enter(path) {
      if (!gefunden && quelle.slice(path.node.start, path.node.end) === ausschnitt) gefunden = path.node
    },
  })
  if (!gefunden) throw new Error(`Kein Knoten mit dem Text ${ausschnitt}`)
  return gefunden
}

/** Ob der Knoten mit dem Quelltext `ausschnitt` ausgenommen ist. */
function istAusgenommen(quelle, ausschnitt) {
  const { ast, ausgenommen } = ausgenommeneKnoten(quelle)
  return ausgenommen.has(knotenMitText(quelle, ast, ausschnitt))
}

describe('DarstellungIgnorer', () => {
  describe('nimmt Stilwerte aus', () => {
    it('im sx-Attribut samt ganzem Teilbaum', () => {
      const quelle = `const k = <Box sx={{ color: aktiv ? 'red' : 'blue', p: 2 }} />`
      expect(istAusgenommen(quelle, `'red'`)).toBe(true)
      expect(istAusgenommen(quelle, `aktiv ? 'red' : 'blue'`)).toBe(true)
      expect(istAusgenommen(quelle, '2')).toBe(true)
    })

    it('im style-Attribut', () => {
      const quelle = `const k = <div style={{ width: breite > 3 ? 10 : 20 }} />`
      expect(istAusgenommen(quelle, 'breite > 3')).toBe(true)
    })

    it('in den Argumenten eines styled(...)-Aufrufs', () => {
      const quelle = `const Kopf = styled('div')({ color: 'red' })\nconst Fuss = styled(Box)(({ theme }) => ({ gap: theme.gap > 1 ? 2 : 1 }))`
      expect(istAusgenommen(quelle, `'div'`)).toBe(true)
      expect(istAusgenommen(quelle, `'red'`)).toBe(true)
      expect(istAusgenommen(quelle, 'theme.gap > 1')).toBe(true)
    })

    it('in einem *_SX-Objektliteral mit reinen Stilschlüsseln, auch unter as const', () => {
      const quelle = `export const KACHEL_SX = { display: 'flex', 'gap': '10px', pt: '15px' } as const`
      expect(istAusgenommen(quelle, `'flex'`)).toBe(true)
      expect(istAusgenommen(quelle, `'15px'`)).toBe(true)
    })

    it('in einem *Sx-Objektliteral mit Typangabe', () => {
      const quelle = `const kopfSx: SxProps = { fontWeight: 600, letterSpacing: '.1em' }`
      expect(istAusgenommen(quelle, '600')).toBe(true)
    })

    it('in einer Stilkonstante mit Rahmen, Verlauf, Übergang, Selektor und Media-Query', () => {
      const quelle = [
        'export const KACHEL_SX = {',
        `  flexDirection: 'column',`,
        '  border: `1px solid ${RAND}`,',
        '  background: `linear-gradient(180deg, ${A}, ${B})`,',
        `  transition: 'transform .22s ease',`,
        `  '&:hover': { transform: 'translateY(-3px)', boxShadow: HOCH },`,
        `  '@media (prefers-reduced-motion: reduce)': { '&:hover': { transform: 'none' } },`,
        '} as const',
      ].join('\n')
      expect(istAusgenommen(quelle, `'column'`)).toBe(true)
      expect(istAusgenommen(quelle, '`1px solid ${RAND}`')).toBe(true)
      expect(istAusgenommen(quelle, `'translateY(-3px)'`)).toBe(true)
      expect(istAusgenommen(quelle, `'none'`)).toBe(true)
    })

    it('in einer Stilkonstante mit CSS-Variablen', () => {
      const quelle = `const lampeSx = { '--blinker-hell': HELL, width: 9 }`
      expect(istAusgenommen(quelle, 'HELL')).toBe(true)
    })

    it('im Rückgabeobjekt einer *Sx-Pfeilfunktion mit Ausdruckskörper, auch unter Hüllen', () => {
      const quelle = `const lampeSx = (a) => ({ '--x': a, borderRadius: '50%', bgcolor: a ? 'rot' : 'grau' })\nconst B_SX = (a) => ({ p: a > 1 ? 1 : 2 } as const)`
      expect(istAusgenommen(quelle, `'50%'`)).toBe(true)
      expect(istAusgenommen(quelle, `a ? 'rot' : 'grau'`)).toBe(true)
      expect(istAusgenommen(quelle, 'a > 1')).toBe(true)
    })

    it('im return-Objekt einer *Sx-Funktion mit Block', () => {
      const quelle = `const kopfSx = (a) => { return { p: a > 1 ? 1 : 2 } }\nconst fussSx = function (b) { if (b) return { m: 1 }; return { m: b - 1 } }`
      expect(istAusgenommen(quelle, 'a > 1')).toBe(true)
      expect(istAusgenommen(quelle, 'b - 1')).toBe(true)
    })

    it('in den Tagged Templates keyframes und css', () => {
      const quelle = 'const wechsel = keyframes`0% { opacity: ${a > 1 ? 1 : 0}; }`\nconst k = css`color: ${b + 1};`'
      expect(istAusgenommen(quelle, 'a > 1')).toBe(true)
      expect(istAusgenommen(quelle, 'b + 1')).toBe(true)
    })

    it('in einem Text, der einem *Sx/*_SX-Bezeichner zugewiesen ist', () => {
      const quelle = 'export const ZEILE_SX = `color-mix(in srgb, ${a} 75%, ${b})`\nconst randSx = \'1px solid\''
      expect(istAusgenommen(quelle, '`color-mix(in srgb, ${a} 75%, ${b})`')).toBe(true)
      expect(istAusgenommen(quelle, `'1px solid'`)).toBe(true)
    })
  })

  describe('lässt Logik stehen', () => {
    it('neben einem sx-Attribut', () => {
      const quelle = `const k = <Box sx={{ p: 1 }} onClick={() => setOffen(!offen)}>{anzahl > 0 ? 'viele' : 'keine'}</Box>`
      expect(istAusgenommen(quelle, '!offen')).toBe(false)
      expect(istAusgenommen(quelle, 'anzahl > 0')).toBe(false)
      expect(istAusgenommen(quelle, `'viele'`)).toBe(false)
    })

    it('in einem anderen Attribut, auch wenn es ein Objekt mit Stilschlüsseln trägt', () => {
      const quelle = `const k = <Kachel werte={{ color: farbe ?? 'grau' }} />`
      expect(istAusgenommen(quelle, `farbe ?? 'grau'`)).toBe(false)
    })

    it('in einem Objekt mit Stilschlüsseln, das keinem *Sx/*_SX-Bezeichner zugewiesen ist', () => {
      const quelle = `const marke = { color: label.color || 'grau' }\nconst groesse = { width: breite - 1, height: hoehe + 1 }\nfn({ color: 'rot' })`
      expect(istAusgenommen(quelle, `label.color || 'grau'`)).toBe(false)
      expect(istAusgenommen(quelle, 'breite - 1')).toBe(false)
      expect(istAusgenommen(quelle, `'rot'`)).toBe(false)
    })

    it('in einem *_SX-Objekt mit einem Schlüssel außerhalb der Stil-Liste', () => {
      const quelle = `const ZEILE_SX = { display: 'flex', anzahl: n > 1 ? 2 : 1 }`
      expect(istAusgenommen(quelle, `'flex'`)).toBe(false)
      expect(istAusgenommen(quelle, 'n > 1')).toBe(false)
    })

    it('in einem *_SX-Objekt mit Spread, berechnetem Schlüssel oder Methode', () => {
      const quelle = `const A_SX = { ...basis, p: 1 }\nconst B_SX = { [schluessel]: 'x' }\nconst C_SX = { color() { return 'rot' } }`
      expect(istAusgenommen(quelle, `'x'`)).toBe(false)
      expect(istAusgenommen(quelle, `'rot'`)).toBe(false)
    })

    it('in einer Funktion ohne *Sx/*_SX-Namen, die ein Stilobjekt zurückgibt', () => {
      const quelle = `const lampe = (a) => ({ '--x': a, borderRadius: '50%' })\nfunction chip(aktiv) { return { color: aktiv ? 'rot' : 'grau' } }\nconst kopf = function () { return { p: 1 > 0 ? 2 : 3 } }`
      expect(istAusgenommen(quelle, `'50%'`)).toBe(false)
      expect(istAusgenommen(quelle, `aktiv ? 'rot' : 'grau'`)).toBe(false)
      expect(istAusgenommen(quelle, '1 > 0')).toBe(false)
    })

    it('in einer *Sx-Funktion, deren Objekt einen Logikschlüssel trägt, und außerhalb ihres Rückgabeobjekts', () => {
      const quelle = `const zeileSx = (n) => ({ p: 1, anzahl: n > 1 ? 2 : 1 })\nconst kopfSx = (n) => { const m = n + 1; return { p: m } }\nconst fussSx = (n) => { return tuWas({ p: n - 1 }) }`
      expect(istAusgenommen(quelle, 'n > 1')).toBe(false)
      expect(istAusgenommen(quelle, 'n + 1')).toBe(false)
      expect(istAusgenommen(quelle, 'n - 1')).toBe(false)
    })

    it('in einem return, das nicht zur *Sx-Funktion selbst gehört', () => {
      const quelle = `const kopfSx = () => { const f = () => { return { p: a > 1 ? 1 : 2 } }; return f() }`
      expect(istAusgenommen(quelle, 'a > 1')).toBe(false)
    })

    it('in einem verschachtelten &- oder @-Schlüssel mit Logikschlüssel oder ohne Objektwert', () => {
      const quelle = `const A_SX = { p: 1, '&:hover': { anzahl: n > 1 ? 2 : 1 } }\nconst B_SX = { p: 2, '@media x': m > 1 ? 'a' : 'b' }`
      expect(istAusgenommen(quelle, 'n > 1')).toBe(false)
      expect(istAusgenommen(quelle, 'm > 1')).toBe(false)
    })

    it('in einem anderen Tagged Template als keyframes oder css', () => {
      const quelle = 'const q = sql`SELECT ${a > 1 ? 1 : 2}`\nconst r = x.keyframes`0% { top: ${b + 1}px }`'
      expect(istAusgenommen(quelle, 'a > 1')).toBe(false)
      expect(istAusgenommen(quelle, 'b + 1')).toBe(false)
    })

    it('in einem Text, der keinem *Sx/*_SX-Bezeichner zugewiesen ist', () => {
      const quelle = 'const ZEILE = `color-mix(in srgb, ${a} 75%, ${b})`\nconst TITEL = \'Leitstand\'\nconst WERT_SX = rechne(`x${c}`)'
      expect(istAusgenommen(quelle, '`color-mix(in srgb, ${a} 75%, ${b})`')).toBe(false)
      expect(istAusgenommen(quelle, `'Leitstand'`)).toBe(false)
      expect(istAusgenommen(quelle, '`x${c}`')).toBe(false)
    })

    it('in einem Bezeichner, der nur sx heißt oder Sx nicht am Ende trägt', () => {
      const quelle = `const sx = { color: 'rot' }\nconst SxWerte = { color: 'blau' }`
      expect(istAusgenommen(quelle, `'rot'`)).toBe(false)
      expect(istAusgenommen(quelle, `'blau'`)).toBe(false)
    })

    it('in den Argumenten eines Aufrufs, der nicht styled heißt', () => {
      const quelle = `const x = gestylt('div')({ color: 'red' })\nconst y = objekt.styled({ color: 'blau' })`
      expect(istAusgenommen(quelle, `'red'`)).toBe(false)
      expect(istAusgenommen(quelle, `'blau'`)).toBe(false)
    })
  })

  it('nennt einen Grund, wenn er ausnimmt, und sonst undefined', () => {
    const ignorer = new DarstellungIgnorer()
    const ast = parse(`const k = <Box sx={{ p: 1 }} title="t" />`, { sourceType: 'module', plugins: ['jsx'] })
    const gruende = []
    traverse(ast, {
      JSXAttribute(path) {
        gruende.push(ignorer.shouldIgnore(path))
      },
    })
    expect(gruende[0]).toMatch(/sx/)
    expect(gruende[1]).toBeUndefined()
  })

  it('führt die Stil-Liste aus dem Issue', () => {
    expect([...STIL_SCHLUESSEL]).toEqual(
      expect.arrayContaining(['color', 'bgcolor', 'p', 'mx', 'gap', 'zIndex', 'justifyContent']),
    )
    expect([...STIL_SCHLUESSEL]).toEqual(
      expect.arrayContaining([
        'border', 'borderTop', 'borderBottom', 'borderLeft', 'borderRight', 'borderColor', 'background',
        'overflow', 'transition', 'transform', 'fontFamily', 'fontStretch', 'fontVariantNumeric',
        'textTransform', 'textAlign', 'lineHeight', 'whiteSpace', 'flexDirection', 'flexWrap',
        'gridTemplateColumns', 'gridColumn', 'perspective', 'cursor', 'animation', 'animationDelay',
      ]),
    )
    expect(STIL_SCHLUESSEL.has('count')).toBe(false)
  })

  it('meldet sich bei Stryker als Ignore-Plugin „darstellung“ an', () => {
    expect(strykerPlugins).toEqual([
      expect.objectContaining({ kind: PluginKind.Ignore, name: 'darstellung', injectableClass: DarstellungIgnorer }),
    ])
  })
})
