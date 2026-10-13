import { parse } from '@babel/parser'
import type { Node, SourceLocation, TemplateLiteral } from '@babel/types'

/**
 * Waechter gegen fest eingebaute Texte (Plan #1638, A9): Bei Englisch darf auf keiner Seite ein
 * deutsches Wort stehen, das nicht aus Daten stammt. Texte kommen ueber `useT` aus dem Woerterbuch;
 * dieser Waechter meldet, was noch im Quelltext steht.
 *
 * Nach dem Muster von `designGuard.ts`: Die Heuristik liegt hier und nicht in der Testdatei, damit
 * das Coverage-Gate sie erreicht. Das Einsammeln der Quelltexte bleibt in `src/sprachwaechter.test.ts`,
 * weil es Vite braucht.
 *
 * Zwei Regeln:
 * - `jsx` — JSX-Text, String-Literale in Textattributen und das erste Argument von `notify(…)`;
 * - `literal` — String- und Template-Literale mit Umlaut oder `ß` oder mit einem Leerzeichen
 *   zwischen zwei Woertern, wie sie Texttabellen tragen.
 */

export type SprachRegel = 'jsx' | 'literal'

export interface SprachFund {
  file: string
  line: number
  rule: SprachRegel
  text: string
}

/** Ein geduldeter Fund, geschluesselt auf Datei plus Regel — nie auf eine Zeilennummer. */
export interface SprachAllowance {
  file: string
  rule: SprachRegel
  /** Das Paket, das den Eintrag aufloest, oder `kein Anzeigetext`. */
  resolvedIn: string
}

const TEXT_ATTRIBUTES = new Set(['label', 'title', 'placeholder', 'aria-label', 'helperText', 'alt', 'tooltip'])

/** Woerterbuch, Server-Anbindung und die Kit-Wortformen tragen ihre Texte per Auftrag. */
const EXCLUDED = [/\.test\.tsx?$/, /^src\/test\//, /^src\/i18n\//, /^src\/api\//, /^src\/lib\/kitWortformen\.ts$/]

const LETTER = /\p{L}/u
const UMLAUT = /[äöüÄÖÜß]/
const WORD_SPACE_WORD = /\p{L}\s+\p{L}/u

/** Ob eine Datei vom Waechter ausgenommen ist; Pfade relativ zum Frontend (`src/…`). */
export function isExcluded(file: string): boolean {
  return EXCLUDED.some((pattern) => pattern.test(file))
}

/**
 * Text eines Template-Literals. Die Stuecke werden mit einem Leerzeichen verbunden: Aus
 * `${vorname} ${nachname}` wird so kein Wort, aus `${n} Karten offen` bleibt der Satz. Ein Stueck
 * ohne gekochten Wert (ungueltige Escape-Folge in einem getaggten Template wie `String.raw`) ist
 * kein Anzeigetext und zaehlt leer.
 */
const templateText = (node: TemplateLiteral) => node.quasis.map((quasi) => quasi.value.cooked ?? '').join(' ')

/** Text eines String- oder Template-Literals, sonst `null`. */
function literalText(node: Node | null | undefined): string | null {
  if (node?.type === 'StringLiteral') return node.value
  if (node?.type === 'TemplateLiteral') return templateText(node)
  return null
}

/** Ein Literal, das in einem Textattribut, als JSX-Text oder in `notify` steht. */
function jsxText(node: Node): string | null {
  if (node.type === 'JSXText') return node.value.trim()
  if (node.type === 'JSXAttribute' && node.name.type === 'JSXIdentifier' && TEXT_ATTRIBUTES.has(node.name.name)) {
    const value = node.value?.type === 'JSXExpressionContainer' ? node.value.expression : node.value
    return literalText(value as Node | null | undefined)
  }
  if (node.type === 'CallExpression' && node.callee.type === 'Identifier' && node.callee.name === 'notify') {
    return literalText(node.arguments[0] as Node | undefined)
  }
  return null
}

/** Knoten, deren Literale kein Anzeigetext sind: Modulpfade und Typen. */
const SKIPPED = new Set([
  'ImportDeclaration',
  'ExportAllDeclaration',
  'TSLiteralType',
  'TSImportType',
  'TSExternalModuleReference',
])

/** Die Kindknoten eines Knotens, ohne Positions- und Kommentarangaben. */
function children(node: Node): Node[] {
  return Object.entries(node)
    .filter(([key]) => key !== 'loc' && !key.endsWith('Comments'))
    .flatMap(([, value]) => (Array.isArray(value) ? value : [value]))
    .filter((value): value is Node => typeof value?.type === 'string')
}

/** Funde einer Quelldatei; ausgenommene Dateien liefern keine. */
export function scanSource(file: string, source: string): SprachFund[] {
  if (isExcluded(file)) return []

  // `.ts` ohne JSX-Plugin: dort ist `<T>wert` eine Typumwandlung, kein Element.
  const plugins: Array<'typescript' | 'jsx'> = file.endsWith('.tsx') ? ['typescript', 'jsx'] : ['typescript']
  const ast = parse(source, { sourceType: 'module', plugins })
  const funde: SprachFund[] = []

  const visit = (node: Node) => {
    if (SKIPPED.has(node.type) || (node.type === 'ExportNamedDeclaration' && node.source)) return
    // Babel setzt `loc` an jedem Knoten; der Typ laesst es nur fuer selbst gebaute Knoten offen.
    const push = (rule: SprachRegel, text: string) =>
      funde.push({ file, line: (node.loc as SourceLocation).start.line, rule, text })

    const jsx = jsxText(node)
    if (jsx !== null && LETTER.test(jsx)) push('jsx', jsx)

    const literal = literalText(node)
    if (literal !== null && (UMLAUT.test(literal) || WORD_SPACE_WORD.test(literal))) push('literal', literal)

    children(node).forEach(visit)
  }

  visit(ast.program)
  return funde
}

const covers = (fund: SprachFund, entry: SprachAllowance) => entry.file === fund.file && entry.rule === fund.rule

/** Funde, die keine Ausnahme deckt — sie machen den Waechter rot. */
export function unmatchedFindings(
  funde: readonly SprachFund[],
  allowlist: readonly SprachAllowance[],
): SprachFund[] {
  return funde.filter((fund) => !allowlist.some((entry) => covers(fund, entry)))
}

/**
 * Ausnahmen, die keinen Fund mehr decken. Sie machen den Waechter ebenfalls rot: sonst koennte ein
 * Paket seine Texte umstellen und den Eintrag stehenlassen, und die Liste waere am Ende nicht leer,
 * sondern nur unwahr.
 */
export function deadAllowlistEntries(
  funde: readonly SprachFund[],
  allowlist: readonly SprachAllowance[],
): SprachAllowance[] {
  return allowlist.filter((entry) => !funde.some((fund) => covers(fund, entry)))
}
