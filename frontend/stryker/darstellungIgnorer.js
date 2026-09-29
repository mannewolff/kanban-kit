import { declareClassPlugin, PluginKind } from '@stryker-mutator/api/plugin'

// Stryker-Ignorer für Darstellungsmutanten (Issue #1277, Plan #1270 E8). Stilwerte — ein `sx`-
// oder `style`-Attribut, das Argument eines `styled(...)`, eine benannte Stilkonstante — sind durch
// Verhaltenstests nicht zu töten. Der Ignorer sortiert sie beim Instrumentieren aus: Stryker meldet
// sie als `Ignored`, und der Treiber zählt sie nicht in der Quote. Ein Filter erst im Treiber ließe
// sie dagegen laufen und bezahlte sie mit Laufzeit.
//
// Reines JavaScript, weil Stryker das Plugin-Modul selbst importiert und keinen TypeScript-Loader
// mitbringt.

/**
 * Die Schlüssel, die in einer Stilkonstante stehen dürfen. Nur ein Objekt, dessen Schlüssel
 * sämtlich hierher gehören, gilt als Stil — der Ignorer läuft über alle mutierten Dateien, und ein
 * `{ color: … }` oder `{ width: …, height: … }` aus der Logik (`epicTiles.ts`, `designGuard.ts`) darf
 * nicht still aus der Quote fallen.
 */
export const STIL_SCHLUESSEL = new Set([
  'color',
  'bgcolor',
  'backgroundColor',
  'p',
  'px',
  'py',
  'pt',
  'pb',
  'pl',
  'pr',
  'm',
  'mx',
  'my',
  'mt',
  'mb',
  'ml',
  'mr',
  'gap',
  'borderRadius',
  'boxShadow',
  'fontSize',
  'fontWeight',
  'letterSpacing',
  'opacity',
  'zIndex',
  'width',
  'height',
  'minWidth',
  'maxWidth',
  'flex',
  'display',
  'alignItems',
  'justifyContent',
])

/** Die JSX-Attribute, deren Wert reiner Stil ist. */
const STIL_ATTRIBUTE = new Set(['sx', 'style'])

/** Bezeichner einer Stilkonstante: `kopfSx`, `KACHEL_SX`. */
const STIL_BEZEICHNER = /(?:Sx|_SX)$/

/** Hüllen, die den Wert eines Ausdrucks nicht ändern: `{…} as const`, `{…} satisfies T`, `(…)`. */
const HUELLEN = new Set(['TSAsExpression', 'TSSatisfiesExpression', 'TSTypeAssertion', 'ParenthesizedExpression'])

const istStilAttribut = (knoten) =>
  knoten.type === 'JSXAttribute' && knoten.name.type === 'JSXIdentifier' && STIL_ATTRIBUTE.has(knoten.name.name)

/** `styled(…)` selbst oder der Aufruf seines Ergebnisses, `styled(Box)(…)`. */
const istStyled = (callee) =>
  (callee.type === 'Identifier' && callee.name === 'styled') ||
  (callee.type === 'CallExpression' && istStyled(callee.callee))

const istStyledArgument = (path) =>
  path.listKey === 'arguments' && path.parent.type === 'CallExpression' && istStyled(path.parent.callee)

const schluesselName = (eigenschaft) => {
  if (eigenschaft.type !== 'ObjectProperty' || eigenschaft.computed) return undefined
  if (eigenschaft.key.type === 'Identifier') return eigenschaft.key.name
  return eigenschaft.key.type === 'StringLiteral' ? eigenschaft.key.value : undefined
}

const nurStilSchluessel = (objekt) =>
  objekt.properties.every((eigenschaft) => STIL_SCHLUESSEL.has(schluesselName(eigenschaft)))

/** Der Bezeichner, dem das Objekt zugewiesen ist — durch wertneutrale Hüllen hindurch. */
const zugewiesenerBezeichner = (path) => {
  let aktuell = path
  while (HUELLEN.has(aktuell.parent.type)) aktuell = aktuell.parentPath
  const { parent } = aktuell
  return parent.type === 'VariableDeclarator' && parent.init === aktuell.node && parent.id.type === 'Identifier'
    ? parent.id.name
    : undefined
}

const istStilKonstante = (path) =>
  path.node.type === 'ObjectExpression' &&
  STIL_BEZEICHNER.test(zugewiesenerBezeichner(path) ?? '') &&
  nurStilSchluessel(path.node)

export class DarstellungIgnorer {
  /** Ein Grund, wenn der Teilbaum unter `path` reiner Stil ist; sonst `undefined`. */
  shouldIgnore(path) {
    if (istStilAttribut(path.node)) return 'Darstellung: Stilwert im sx- oder style-Attribut'
    if (istStyledArgument(path)) return 'Darstellung: Argument eines styled(...)-Aufrufs'
    if (istStilKonstante(path)) return 'Darstellung: Stilkonstante mit reinen Stilschlüsseln'
    return undefined
  }
}

export const strykerPlugins = [declareClassPlugin(PluginKind.Ignore, 'darstellung', DarstellungIgnorer)]
