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
 * Wo der Leser gerade steht. `quote` traegt nur im Modus `code` einen Wert — ein Kommentar beginnt
 * nie innerhalb einer Zeichenkette, also ist die Kombination `line`/`block` plus offenes
 * Anfuehrungszeichen unerreichbar.
 */
interface StripState {
  mode: 'code' | 'line' | 'block'
  quote: string | null
}

/** Ein Schritt des Lesers: was ausgegeben wird, wie viele Zeichen er verbraucht, wie es weitergeht. */
interface StripStep {
  emit: string
  width: number
  state: StripState
}

const QUOTES = new Set(["'", '"', '`'])

/** Welchen Kommentar ein `/` eroeffnet — abhaengig vom Zeichen danach. */
const COMMENT_MODE = new Map<string, StripState['mode']>([
  ['/', 'line'],
  ['*', 'block'],
])

/** Im Zeilenkommentar: alles wird geleert, das Zeilenende beendet ihn. */
const inLineComment = (char: string, blank: string): StripStep => ({
  emit: blank,
  width: 1,
  state: { mode: char === '\n' ? 'code' : 'line', quote: null },
})

/** Im Blockkommentar: der Sternschraegstrich beendet ihn und wird selbst mit geleert. */
const inBlockComment = (char: string, next: string, blank: string): StripStep =>
  char === '*' && next === '/'
    ? { emit: '  ', width: 2, state: { mode: 'code', quote: null } }
    : { emit: blank, width: 1, state: { mode: 'block', quote: null } }

/** In einer Zeichenkette: nichts wird geleert, nur das passende Anfuehrungszeichen beendet sie. */
const inString = (char: string, source: string, index: number, quote: string): StripStep =>
  char === '\\'
    ? // `slice` statt `source[index + 1]`: am Dateiende gibt es kein naechstes Zeichen, und ein
      // `?? ''` waere ein Zweig, den kein gueltiger Quelltext je nimmt.
      { emit: char + source.slice(index + 1, index + 2), width: 2, state: { mode: 'code', quote } }
    : { emit: char, width: 1, state: { mode: 'code', quote: char === quote ? null : quote } }

/** Im Quelltext: ein Anfuehrungszeichen oeffnet eine Zeichenkette, `//` und `/*` einen Kommentar. */
const inCode = (char: string, next: string): StripStep => {
  if (QUOTES.has(char)) return { emit: char, width: 1, state: { mode: 'code', quote: char } }

  const comment = char === '/' ? COMMENT_MODE.get(next) : undefined
  if (comment !== undefined) return { emit: '  ', width: 2, state: { mode: comment, quote: null } }

  return { emit: char, width: 1, state: { mode: 'code', quote: null } }
}

const stepAt = (source: string, index: number, state: StripState): StripStep => {
  const char = source[index]
  const next = source[index + 1]
  const blank = char === '\n' ? '\n' : ' '

  if (state.mode === 'line') return inLineComment(char, blank)
  if (state.mode === 'block') return inBlockComment(char, next, blank)
  if (state.quote !== null) return inString(char, source, index, state.quote)
  return inCode(char, next)
}

/**
 * Ersetzt Kommentare durch Leerzeichen, ohne Zeilen zu verschieben. Zeichenweise statt per Regex,
 * weil `//` und `/*` auch in Zeichenketten vorkommen (`'url(//cdn/x.png)'`): ein Regex-Ansatz
 * frisst dort den Zeilenrest oder loescht alles bis zum naechsten Sternschraegstrich.
 *
 * Die vier Modi liegen als eigene Funktionen daneben statt als Zweige in einer Schleife: jede ist
 * fuer sich lesbar, und der Rumpf hier sagt nur noch, dass Schritt an Schritt gereiht wird.
 */
export function stripComments(source: string): string {
  const out: string[] = []
  let state: StripState = { mode: 'code', quote: null }

  for (let index = 0; index < source.length; index++) {
    const step = stepAt(source, index, state)
    out.push(step.emit)
    index += step.width - 1
    state = step.state
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
