import { describe, expect, it } from 'vitest'

/**
 * Struktur-Leitplanke des Redesigns „Kante" (#648, Plandokument #617).
 *
 * Sie verbietet in `src/components/` und `src/pages/` drei Muster, die der Designsprache
 * widersprechen: Farb-Hex-Literale (Farben kommen aus dem Theme), `elevation={n}` mit n > 0 und
 * numerischen `boxShadow` (Haarlinien statt Schatten; für angehobene Flächen `SURFACE_HOVER_SHADOW`).
 *
 * Bewusst ein Quelltext-Scanner und keine ESLint-Regel: Die Ausnahmeliste soll als **eine**
 * dokumentierte Tabelle an **einem** Ort stehen. Verstreute `eslint-disable`-Kommentare erfüllen
 * das nicht, und ihre Zuordnung zu dem Paket, das sie auflöst, wäre nicht ablesbar.
 *
 * Die Liste ist ein **Ratchet**: Sie greift ab sofort für jede neue Stelle, und jedes Folgepaket
 * streicht seine Einträge. Nach Paket 6 ist sie leer. Ein Eintrag, der keinen realen Verstoß mehr
 * deckt, macht den Test rot — so kann ein Paket seine Stelle nicht auflösen, ohne sie auszutragen.
 */

type Rule = 'hex' | 'elevation' | 'boxShadow'

interface Violation {
  file: string
  line: number
  rule: Rule
  text: string
}

/**
 * Ausnahmeliste. Schlüssel ist **Datei plus Verstoßmuster**, nicht die Zeilennummer — jedes
 * Folgepaket verschiebt Zeilen. Die Zeilenangaben im Kommentar sind Fundstellen zum Stand
 * 2026-08-30 und dienen nur dem Wiederfinden.
 */
const ALLOWLIST: ReadonlyArray<{ file: string; rule: Rule; resolvedIn: string }> = [
  { file: 'components/AuthCard.tsx', rule: 'elevation', resolvedIn: 'Paket 6, #653' }, // 9
]

/**
 * `components/LabelManagerDialog.tsx:23` (`DEFAULT_COLOR = '#1976d2'`) steht bewusst **nicht** in
 * der Liste: Der Wert ist kein Design-Token, sondern der Vorgabewert einer benutzerdefinierten
 * Labelfarbe, und er steht außerhalb jedes Style-Kontexts. Der Scanner schlägt dort nicht an.
 */

/** Entfernt Kommentare, damit Issue-Anker der Form `#611` nicht als Farbe gelesen werden. */
function stripComments(source: string): string {
  const withoutBlocks = source.replace(/\/\*[\s\S]*?\*\//g, (match) => match.replace(/[^\n]/g, ' '))
  return withoutBlocks
    .split('\n')
    .map((line) => line.replace(/(^|[^:])\/\/.*$/, '$1'))
    .join('\n')
}

/** Öffnende minus schließende Klammern ab `from` — hält mehrzeilige `sx`-Blöcke offen. */
function bracketBalance(line: string, from: number): number {
  let balance = 0
  for (const char of line.slice(from)) {
    if (char === '{' || char === '(') balance += 1
    if (char === '}' || char === ')') balance -= 1
  }
  return balance
}

const STYLE_ENTRY = /\b(sx|style)\s*[=:]|\bstyled\(/

/**
 * Verstöße einer Quelldatei. Hex-Literale zählen nur im Style-Kontext (`sx`, `style`, `styled`) —
 * sonst schlüge `placeholder="#345"` an, eine Kartennummer und keine Farbe.
 */
export function scanSource(file: string, source: string): Violation[] {
  const violations: Violation[] = []
  let openStyle = 0

  stripComments(source)
    .split('\n')
    .forEach((line, index) => {
      const entry = STYLE_ENTRY.exec(line)
      const inStyle = openStyle > 0 || entry !== null
      openStyle = Math.max(0, openStyle + bracketBalance(line, openStyle > 0 ? 0 : (entry?.index ?? line.length)))

      const push = (rule: Rule) => violations.push({ file, line: index + 1, rule, text: line.trim() })

      if (inStyle && /#[0-9a-fA-F]{3,8}(?![0-9a-fA-F])/.test(line)) push('hex')
      if (/elevation=\{\s*[1-9]\d*\s*\}/.test(line)) push('elevation')
      if (/boxShadow\s*:\s*[1-9]\d*/.test(line)) push('boxShadow')
    })

  return violations
}

/**
 * Quelltexte der beiden Bereiche. Über `import.meta.glob` und nicht über `node:fs`: Vite liefert
 * den Rohtext ohne eine zusätzliche Abhängigkeit (`@types/node` ist im Frontend nicht installiert)
 * und ohne Annahme über das Arbeitsverzeichnis des Testlaufs.
 */
const SOURCES: Record<string, string> = {
  ...import.meta.glob('./components/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true }),
  ...import.meta.glob('./pages/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true }),
}

/** Verstöße des gesamten Produktivcodes; Testdateien sind keiner. */
function scanTree(): Violation[] {
  return Object.entries(SOURCES)
    .filter(([path]) => !/\.test\.tsx?$/.test(path))
    .flatMap(([path, source]) => scanSource(path.replace('./', ''), source))
}

const isAllowed = (violation: Violation) =>
  ALLOWLIST.some((entry) => entry.file === violation.file && entry.rule === violation.rule)

describe('Design-Leitplanke (Scanner)', () => {
  it('meldet Hex-Farben, positive elevation und numerischen boxShadow im Style-Kontext', () => {
    const fixture = [
      "<Paper elevation={2} sx={{ color: '#fff' }}>",
      "  <Box sx={{ boxShadow: 3 }} />",
      '</Paper>',
    ].join('\n')

    expect(scanSource('components/Fixture.tsx', fixture).map((v) => v.rule).sort()).toEqual([
      'boxShadow',
      'elevation',
      'hex',
    ])
  })

  it('meldet eine Hex-Farbe auch in einem mehrzeiligen sx-Block', () => {
    const fixture = ['<Box\n  sx={{', "    color: '#123456',", '  }}\n/>'].join('\n')

    expect(scanSource('components/Fixture.tsx', fixture).map((v) => v.rule)).toEqual(['hex'])
  })

  it('schlägt bei der Kartennummer #345 und bei Issue-Ankern in Kommentaren nicht an', () => {
    const fixture = [
      '// Toleranz gegenüber #611 im Kommentar',
      '/* Blockkommentar zu #612 */',
      '<TextField placeholder="#345" />',
      "const DEFAULT_COLOR = '#1976d2'",
      '<Paper elevation={0} sx={{ boxShadow: 0 }} />',
    ].join('\n')

    expect(scanSource('components/Fixture.tsx', fixture)).toEqual([])
  })

  it('hält den Bestand frei von neuen Verstößen', () => {
    expect(scanTree().filter((v) => !isAllowed(v))).toEqual([])
  })

  it('führt keine Ausnahme, die kein Verstoß mehr deckt', () => {
    const violations = scanTree()
    const dead = ALLOWLIST.filter(
      (entry) => !violations.some((v) => v.file === entry.file && v.rule === entry.rule),
    )

    expect(dead).toEqual([])
  })
})
