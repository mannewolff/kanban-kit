/**
 * Struktur-Leitplanke der Designsprache „Kante" (#648): Sie verbietet Farb-Hex-Literale in
 * Style-Kontexten, `elevation` ungleich 0 und schwarze Schatten.
 *
 * Die Heuristik liegt hier und nicht in der Testdatei, damit das Coverage-Gate sie erreicht — ein
 * Waechter, den niemand prueft, ist kein Waechter. Sie kommt ohne `@mui` aus und haelt damit die
 * Lib-Konvention ein. Das Einsammeln der Quelltexte bleibt in der Testdatei, weil es Vite braucht.
 */

export type DesignRule = 'hex' | 'elevation' | 'boxShadow'

export interface DesignViolation {
  file: string
  line: number
  rule: DesignRule
  text: string
}

/** Ein geduldeter Verstoss, geschluesselt auf Datei plus Regel — nie auf eine Zeilennummer. */
export interface DesignAllowance {
  file: string
  rule: DesignRule
  /** Das Paket, das den Eintrag aufloest. */
  resolvedIn: string
}

/**
 * Ersetzt Kommentare durch Leerzeichen, ohne Zeilen zu verschieben. Zeichenweise statt per Regex,
 * weil `//` und `/*` auch in Zeichenketten vorkommen (`'url(//cdn/x.png)'`): ein Regex-Ansatz
 * frisst dort den Zeilenrest oder loescht alles bis zum naechsten Sternschraegstrich.
 */
export function stripComments(source: string): string {
  const out: string[] = []
  let quote: string | null = null
  let mode: 'code' | 'line' | 'block' = 'code'

  for (let i = 0; i < source.length; i++) {
    const char = source[i]
    const next = source[i + 1]
    const blank = char === '\n' ? '\n' : ' '

    if (mode === 'line') {
      out.push(blank)
      if (char === '\n') mode = 'code'
      continue
    }
    if (mode === 'block') {
      out.push(blank)
      if (char === '*' && next === '/') {
        out.push(' ')
        i++
        mode = 'code'
      }
      continue
    }
    if (quote !== null) {
      out.push(char)
      if (char === '\\') {
        // `slice` statt `source[i + 1]`: am Dateiende gibt es kein naechstes Zeichen, und ein
        // `?? ''` waere ein Zweig, den kein gueltiger Quelltext je nimmt.
        out.push(source.slice(i + 1, i + 2))
        i++
      } else if (char === quote) {
        quote = null
      }
      continue
    }
    if (char === "'" || char === '"' || char === '`') {
      quote = char
      out.push(char)
      continue
    }
    if (char === '/' && next === '/') {
      out.push('  ')
      i++
      mode = 'line'
      continue
    }
    if (char === '/' && next === '*') {
      out.push('  ')
      i++
      mode = 'block'
      continue
    }
    out.push(char)
  }

  return out.join('')
}

/**
 * Beginn eines Style-Ausdrucks: das `sx`/`style`-Attribut, ein `styled(`-Aufruf **und** eine
 * benannte Style-Konstante (`const cardSx = {`). Ohne den letzten Fall ist die Leitplanke blind
 * fuer genau die Objekte, in die zentralisierte Styles wandern.
 */
const STYLE_ENTRY = /\b(?:sx|style)\s*[=:]|\bstyled\(|\b\w*[sS]x\s*(?::[^=]*)?=[^=]/g

/**
 * Die Abschnitte einer Zeile, die zu einem Style-Ausdruck gehoeren, plus die am Zeilenende noch
 * offenen Klammern. Ein Ausdruck endet, sobald seine Klammerbilanz wieder null ist — nicht erst am
 * Zeilenende: `sx={{…}} onClick={() => {` haengt sonst den ganzen Handler-Rumpf an den Style an.
 */
export function styleSpans(line: string, carry: number): { spans: Array<[number, number]>; carry: number } {
  const spans: Array<[number, number]> = []
  let position = 0
  let open = carry

  const consume = (from: number): boolean => {
    // `entered` haelt fest, dass der Ausdruck ueberhaupt begonnen hat: Zwischen dem `sx` und
    // seiner ersten Klammer steht die Bilanz auf null, und ohne diese Unterscheidung endete die
    // Spanne sofort wieder.
    let entered = open > 0
    for (let i = from; i < line.length; i++) {
      const char = line[i]
      if (char === '{' || char === '(') {
        open++
        entered = true
      }
      if (char === '}' || char === ')') open--
      if (entered && open <= 0) {
        spans.push([from, i + 1])
        position = i + 1
        open = 0
        return true
      }
    }
    spans.push([from, line.length])
    position = line.length
    return false
  }

  if (open > 0 && !consume(0)) {
    return { spans, carry: open }
  }

  STYLE_ENTRY.lastIndex = position
  let match = STYLE_ENTRY.exec(line)
  while (match !== null) {
    if (!consume(match.index)) {
      return { spans, carry: open }
    }
    STYLE_ENTRY.lastIndex = position
    match = STYLE_ENTRY.exec(line)
  }

  return { spans, carry: 0 }
}

const HEX = /#[0-9a-fA-F]{3,8}(?![0-9a-fA-F])/
/** `elevation={…}` mit allem ausser einer glatten 0 — auch aus einer Variablen. */
const ELEVATION = /elevation=\{\s*(?!0\s*\})/
const NUMERIC_SHADOW = /boxShadow\s*:\s*[1-9]/
/** Ein Schatten mit schwarzem Anteil, auch als Zeichenkette geschrieben. */
const BLACK_SHADOW = /boxShadow[^,}\n]*(?:rgba?\(\s*0\s*,\s*0\s*,\s*0|#000)/i

/**
 * Verstoesse einer Quelldatei. Hex-Literale zaehlen nur innerhalb eines Style-Ausdrucks — sonst
 * schluege `placeholder="#345"` an, eine Kartennummer und keine Farbe.
 */
export function scanSource(file: string, source: string): DesignViolation[] {
  const violations: DesignViolation[] = []
  let carry = 0

  stripComments(source)
    .split('\n')
    .forEach((line, index) => {
      const { spans, carry: next } = styleSpans(line, carry)
      carry = next

      const push = (rule: DesignRule) => violations.push({ file, line: index + 1, rule, text: line.trim() })
      const styleText = spans.map(([from, to]) => line.slice(from, to)).join(' ')

      if (HEX.test(styleText)) push('hex')
      if (ELEVATION.test(line)) push('elevation')
      if (NUMERIC_SHADOW.test(line) || BLACK_SHADOW.test(line)) push('boxShadow')
    })

  return violations
}

const covers = (violation: DesignViolation, entry: DesignAllowance) =>
  entry.file === violation.file && entry.rule === violation.rule

/** Verstoesse, die keine Ausnahme deckt — sie machen die Leitplanke rot. */
export function unmatchedViolations(
  violations: readonly DesignViolation[],
  allowlist: readonly DesignAllowance[],
): DesignViolation[] {
  return violations.filter((violation) => !allowlist.some((entry) => covers(violation, entry)))
}

/**
 * Ausnahmen, die keinen Verstoss mehr decken. Sie machen die Leitplanke ebenfalls rot: sonst
 * koennte ein Paket seine Stelle aufloesen und den Eintrag stehenlassen, und die Liste waere am
 * Ende nicht leer, sondern nur unwahr.
 */
export function deadAllowlistEntries(
  violations: readonly DesignViolation[],
  allowlist: readonly DesignAllowance[],
): DesignAllowance[] {
  return allowlist.filter((entry) => !violations.some((violation) => covers(violation, entry)))
}
