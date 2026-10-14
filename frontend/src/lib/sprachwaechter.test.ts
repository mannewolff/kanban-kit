import { describe, expect, it } from 'vitest'
import {
  deadAllowlistEntries,
  isExcluded,
  scanSource,
  unmatchedFindings,
  type SprachAllowance,
  type SprachFund,
} from './sprachwaechter'

const regeln = (source: string, file = 'src/components/Fixture.tsx') => scanSource(file, source).map((f) => f.rule)

describe('scanSource: Regel jsx', () => {
  it('meldet ein Textattribut mit festem Wort', () => {
    expect(regeln('const x = <Button title="Speichern" />')).toEqual(['jsx'])
  })

  it('meldet jedes der Textattribute, auch als Ausdruck oder Template-Literal', () => {
    for (const name of ['label', 'title', 'placeholder', 'aria-label', 'helperText', 'alt', 'tooltip']) {
      expect(regeln(`const x = <A ${name}="Wort" />`)).toEqual(['jsx'])
      expect(regeln(`const x = <A ${name}={'Wort'} />`)).toEqual(['jsx'])
      expect(regeln(`const x = <A ${name}={\`Wort\`} />`)).toEqual(['jsx'])
    }
  })

  it('meldet JSX-Text', () => {
    expect(regeln('const x = <p>Hallo</p>')).toEqual(['jsx'])
  })

  it('meldet das erste Argument von notify', () => {
    expect(regeln("notify('Gespeichert', 'success')")).toEqual(['jsx'])
    expect(regeln('notify(`Gespeichert`)')).toEqual(['jsx'])
  })

  it('meldet keine Textattribute aus Variablen, keine anderen Attribute und keine Satzzeichen', () => {
    expect(regeln('const x = <A title={t.speichern} label={name} />')).toEqual([])
    expect(regeln('const x = <A variant="outlined" data-testid="knopf" />')).toEqual([])
    expect(regeln('const x = <A title="" />')).toEqual([])
    expect(regeln("const x = <p>\n  {name} · {' '}\n</p>")).toEqual([])
    expect(regeln('const x = <A title />')).toEqual([])
  })

  it('meldet bei notify nur ein Literal als erstes Argument und keine andere Funktion', () => {
    expect(regeln('notify(t.gespeichert, "success")')).toEqual([])
    expect(regeln("melde('Gespeichert')")).toEqual([])
    expect(regeln("api.notify('Gespeichert')")).toEqual([])
    expect(regeln('notify()')).toEqual([])
  })

  it('meldet kein Textattribut mit Namensraum', () => {
    expect(regeln('const x = <A xlink:title="Wort" />')).toEqual([])
  })

  it('liest .tsx mit TypeScript- und JSX-Plugin', () => {
    expect(regeln('const x: string = <p>Hallo</p>')).toEqual(['jsx'])
  })
})

describe('scanSource: Regel literal', () => {
  it('meldet ein Literal mit Leerzeichen zwischen zwei Wörtern', () => {
    expect(regeln("const HINWEIS = 'Am Board fehlt'", 'src/lib/fixture.ts')).toEqual(['literal'])
  })

  it('meldet ein Literal mit mehreren Leerzeichen zwischen zwei Wörtern', () => {
    expect(regeln("const HINWEIS = 'Am  Board'", 'src/lib/fixture.ts')).toEqual(['literal'])
  })

  it('meldet Literale auch in einer Export-Anweisung ohne Modulpfad', () => {
    expect(regeln("export const HINWEIS = 'Am Board fehlt'", 'src/lib/fixture.ts')).toEqual(['literal'])
  })

  it('meldet ein Literal mit Umlaut oder ß', () => {
    expect(regeln("const a = 'Größe'", 'src/lib/fixture.ts')).toEqual(['literal'])
    expect(regeln("const a = 'Fuß'", 'src/lib/fixture.ts')).toEqual(['literal'])
    expect(regeln("const a = 'Ärger'", 'src/lib/fixture.ts')).toEqual(['literal'])
  })

  it('meldet Template-Literale über alle ihre Stücke', () => {
    expect(regeln('const a = `${n} Karten offen`', 'src/lib/fixture.ts')).toEqual(['literal'])
    expect(regeln('const a = `${n} Stück`', 'src/lib/fixture.ts')).toEqual(['literal'])
  })

  it('zählt ein getaggtes Template mit ungültiger Escape-Folge leer', () => {
    expect(regeln('const a = String.raw`C:\\unicode Am Board`', 'src/lib/fixture.ts')).toEqual([])
  })

  it('meldet keine Bezeichner, Pfade, Einheiten und Platzhalter-Ketten', () => {
    expect(regeln("const a = 'in_progress'", 'src/lib/fixture.ts')).toEqual([])
    expect(regeln("const a = '/api/cards'", 'src/lib/fixture.ts')).toEqual([])
    expect(regeln("const a = '12 px'", 'src/lib/fixture.ts')).toEqual([])
    expect(regeln('const a = `${vorname} ${nachname}`', 'src/lib/fixture.ts')).toEqual([])
  })

  it('meldet keine Modulpfade und keine Literale in Typen', () => {
    expect(regeln("import x from './a b'\nexport { y } from './c d'\nexport * from './e f'", 'src/lib/f.ts')).toEqual([])
    expect(regeln("type Zustand = 'In progress' | 'In review'", 'src/lib/fixture.ts')).toEqual([])
    expect(regeln("type T = import('./a b').X", 'src/lib/fixture.ts')).toEqual([])
    expect(regeln("import x = require('./a b')", 'src/lib/fixture.ts')).toEqual([])
  })

  it('meldet ein Textattribut mit Satz unter beiden Regeln', () => {
    expect(regeln('const x = <A title="Am Board fehlt" />').sort()).toEqual(['jsx', 'literal'])
  })

  it('liest .ts ohne JSX-Plugin, damit Typumwandlungen in spitzen Klammern parsen', () => {
    expect(regeln("const a = <string>wert\nconst b = 'Am Board fehlt'", 'src/lib/fixture.ts')).toEqual(['literal'])
  })
})

describe('scanSource: Fundangaben', () => {
  it('nennt Datei, Zeile, Regel und Text', () => {
    const funde = scanSource('src/lib/fixture.ts', "const a = 1\nconst b = 'Am Board fehlt'")
    expect(funde).toEqual<SprachFund[]>([
      { file: 'src/lib/fixture.ts', line: 2, rule: 'literal', text: 'Am Board fehlt' },
    ])
  })

  it('nennt JSX-Text ohne umgebenden Leerraum', () => {
    expect(scanSource('src/components/Fixture.tsx', 'const x = <p> Hallo </p>').map((f) => f.text)).toEqual(['Hallo'])
  })

  it('verbindet die Stücke eines Template-Literals mit einem Leerzeichen', () => {
    expect(scanSource('src/lib/fixture.ts', 'const a = `Karten ${n} offen`').map((f) => f.text)).toEqual([
      'Karten   offen',
    ])
  })
})

describe('isExcluded: ausgenommene Pfade', () => {
  it('nimmt Tests, Wörterbuch, Server-Anbindung und Kit-Wortformen aus', () => {
    expect(isExcluded('src/components/A.test.tsx')).toBe(true)
    expect(isExcluded('src/lib/a.test.ts')).toBe(true)
    expect(isExcluded('src/test/setup.ts')).toBe(true)
    expect(isExcluded('src/i18n/de.ts')).toBe(true)
    expect(isExcluded('src/api/client.ts')).toBe(true)
    expect(isExcluded('src/lib/kitWortformen.ts')).toBe(true)
  })

  it('prüft alle übrigen Quellen', () => {
    expect(isExcluded('src/components/A.tsx')).toBe(false)
    expect(isExcluded('src/lib/kitWortformen.tsx')).toBe(false)
    expect(isExcluded('src/pages/api/A.tsx')).toBe(false)
    expect(isExcluded('src/lib/a.test.tsHelfer.ts')).toBe(false)
    expect(isExcluded('src/pages/src/test/A.tsx')).toBe(false)
    expect(isExcluded('src/pages/src/i18n/A.tsx')).toBe(false)
    expect(isExcluded('src/pages/src/api/A.tsx')).toBe(false)
    expect(isExcluded('src/pages/src/lib/kitWortformen.ts')).toBe(false)
  })

  it('meldet in ausgenommenen Dateien nichts', () => {
    const satz = "const a = 'Am Board fehlt'"
    expect(scanSource('src/i18n/de.ts', satz)).toEqual([])
    expect(scanSource('src/lib/kitWortformen.ts', satz)).toEqual([])
    expect(scanSource('src/api/client.ts', satz)).toEqual([])
    expect(scanSource('src/lib/a.test.ts', satz)).toEqual([])
  })
})

describe('Ausnahmeliste', () => {
  const fund: SprachFund = { file: 'src/lib/a.ts', line: 1, rule: 'literal', text: 'Am Board' }
  const eintrag: SprachAllowance = { file: 'src/lib/a.ts', rule: 'literal', resolvedIn: 'Paket' }

  it('deckt einen Fund über Datei und Regel', () => {
    expect(unmatchedFindings([fund], [eintrag])).toEqual([])
    expect(unmatchedFindings([fund], [{ ...eintrag, rule: 'jsx' }])).toEqual([fund])
    expect(unmatchedFindings([fund], [{ ...eintrag, file: 'src/lib/b.ts' }])).toEqual([fund])
    expect(unmatchedFindings([fund], [{ ...eintrag, file: 'src/lib/b.ts' }, eintrag])).toEqual([])
  })

  it('meldet eine tote Ausnahme: Datei ohne Fund dieser Regel', () => {
    expect(deadAllowlistEntries([fund], [eintrag])).toEqual([])
    expect(deadAllowlistEntries([], [eintrag])).toEqual([eintrag])
    expect(deadAllowlistEntries([{ ...fund, rule: 'jsx' }], [eintrag])).toEqual([eintrag])
  })
})
