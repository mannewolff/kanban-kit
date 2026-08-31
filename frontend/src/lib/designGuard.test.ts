import { describe, expect, it } from 'vitest'
import { deadAllowlistEntries, scanSource, unmatchedViolations, type DesignAllowance } from './designGuard'

const regeln = (source: string) => scanSource('components/Fixture.tsx', source).map((v) => v.rule)

describe('scanSource: was gemeldet wird', () => {
  it('meldet Hex-Farben, positive elevation und numerischen boxShadow', () => {
    const fixture = [
      "<Paper elevation={2} sx={{ color: '#fff' }}>",
      '  <Box sx={{ boxShadow: 3 }} />',
      '</Paper>',
    ].join('\n')

    expect(regeln(fixture).sort()).toEqual(['boxShadow', 'elevation', 'hex'])
  })

  it('meldet eine Hex-Farbe auch in einem mehrzeiligen sx-Block', () => {
    expect(regeln(['<Box\n  sx={{', "    color: '#123456',", '  }}\n/>'].join('\n'))).toEqual(['hex'])
  })

  // Befund 3 des Reviews: Genau die Objekte, in die dieses Redesign die Styles zentralisiert hat,
  // waren fuer die Leitplanke kein Style-Kontext.
  it('meldet Hex-Farben in benannten Style-Konstanten', () => {
    expect(regeln("const cardSx = {\n  color: '#ff0000',\n}")).toEqual(['hex'])
    expect(regeln("export const dialogTitleSx = { borderBottom: '1px solid #D8ECEE' }")).toEqual(['hex'])
    expect(regeln("const markdownBodySx = {\n  '& code': { backgroundColor: '#f4f5f7' },\n}")).toEqual(['hex'])
  })

  // Befund 3: nur Zahlenliterale wurden erkannt.
  it('meldet eine elevation aus einer Variablen ebenso wie aus einer Zahl', () => {
    expect(regeln('<Paper elevation={depth} />')).toEqual(['elevation'])
    expect(regeln('<Paper elevation={6} />')).toEqual(['elevation'])
    expect(regeln('<Paper elevation={0} />')).toEqual([])
  })

  // Befund 3: ein schwarzer Schatten als String kam an der Regel vorbei.
  it('meldet einen schwarzen Schatten auch als Zeichenkette', () => {
    expect(regeln("<Box sx={{ boxShadow: '0 2px 8px rgba(0,0,0,0.4)' }} />")).toEqual(['boxShadow'])
    // Der Hex-Schatten verletzt beide Regeln zugleich; die Meldung nennt deshalb auch beide.
    expect(regeln("<Box sx={{ boxShadow: '0 1px 2px #000' }} />").sort()).toEqual(['boxShadow', 'hex'])
    expect(regeln('<Box sx={{ boxShadow: SURFACE_HOVER_SHADOW }} />')).toEqual([])
  })
})

describe('scanSource: was nicht gemeldet wird', () => {
  it('haelt die Kartennummer #345 und Issue-Anker in Kommentaren heraus', () => {
    const fixture = [
      '// Toleranz gegenueber #611 im Kommentar',
      '/* Blockkommentar zu #612 */',
      '<TextField placeholder="#345" />',
      "const DEFAULT_COLOR = '#1976d2'",
      '<Paper elevation={0} sx={{ boxShadow: 0 }} />',
    ].join('\n')

    expect(scanSource('components/Fixture.tsx', fixture)).toEqual([])
  })

  // Befund 4 des Reviews: verifizierter Falschtreffer. Die Klammerbilanz lief ueber den
  // sx-Ausdruck hinaus und erklaerte den ganzen Handler-Rumpf zum Style-Kontext.
  it('endet mit dem sx-Ausdruck und nicht erst am Zeilenende', () => {
    const fixture = ['<Box sx={{ p: 1 }} onClick={() => {', "  track('#deadbeef')", '}} />'].join('\n')

    expect(scanSource('components/Fixture.tsx', fixture)).toEqual([])
  })

  // Befund 3, zwei Detailfehler in stripComments.
  it('laesst sich von // und /* innerhalb von Zeichenketten nicht taeuschen', () => {
    expect(regeln("<Box sx={{ backgroundImage: 'url(//cdn/x.png)', color: '#ff0000' }} />")).toEqual(['hex'])
    expect(regeln("const s = '/*'\n<Box sx={{ color: '#ff0000' }} />\nconst t = '*/'")).toEqual(['hex'])
  })

  it('liest ueber maskierte Anfuehrungszeichen hinweg', () => {
    // Ohne Escape-Behandlung endete die Zeichenkette am maskierten Apostroph, und alles danach
    // gaelte wieder als Quelltext — inklusive eines `//`, das dann den Zeilenrest verschluckt.
    expect(regeln("const s = 'a\\'b' // '#ff0000'\n<Box sx={{ color: '#123456' }} />")).toEqual(['hex'])
  })

  it('meldet Hex-Werte ausserhalb jedes Style-Kontexts nicht', () => {
    expect(regeln("const EPIC_PALETTE = ['#534AB7', '#1D9E75']")).toEqual([])
  })
})

describe('Ausnahmeliste', () => {
  const violations = [
    { file: 'components/Alt.tsx', line: 5, rule: 'hex' as const, text: "color: '#fff'" },
    { file: 'components/Neu.tsx', line: 9, rule: 'elevation' as const, text: 'elevation={2}' },
  ]

  it('laesst genau die gedeckten Verstoesse durch', () => {
    const allowlist: DesignAllowance[] = [{ file: 'components/Alt.tsx', rule: 'hex', resolvedIn: 'Paket X' }]

    expect(unmatchedViolations(violations, allowlist)).toEqual([violations[1]])
  })

  it('meldet einen Eintrag, der keinen Verstoss mehr deckt', () => {
    const allowlist: DesignAllowance[] = [
      { file: 'components/Alt.tsx', rule: 'hex', resolvedIn: 'Paket X' },
      { file: 'components/Weg.tsx', rule: 'boxShadow', resolvedIn: 'Paket Y' },
    ]

    expect(deadAllowlistEntries(violations, allowlist)).toEqual([allowlist[1]])
  })

  it('kommt mit einer leeren Liste in beide Richtungen zurecht', () => {
    expect(unmatchedViolations(violations, [])).toEqual(violations)
    expect(deadAllowlistEntries(violations, [])).toEqual([])
  })
})
