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
  'border',
  'borderTop',
  'borderBottom',
  'borderLeft',
  'borderRight',
  'borderColor',
  'background',
  'overflow',
  'transition',
  'transform',
  'fontFamily',
  'fontStretch',
  'fontVariantNumeric',
  'textTransform',
  'textAlign',
  'lineHeight',
  'whiteSpace',
  'flexDirection',
  'flexWrap',
  'gridTemplateColumns',
  'gridColumn',
  'perspective',
  'cursor',
  'animation',
  'animationDelay',
])

/** Die JSX-Attribute, deren Wert reiner Stil ist. */
const STIL_ATTRIBUTE = new Set(['sx', 'style'])

/** Bezeichner einer Stilkonstante: `kopfSx`, `KACHEL_SX`. */
const STIL_BEZEICHNER = /(?:Sx|_SX)$/

/** Die Tags der Stiltexte aus Emotion und MUI: ``keyframes`…` `` und ``css`…` ``. */
const STIL_TAGS = new Set(['keyframes', 'css'])

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

/**
 * Ob ein Schlüssel samt Wert Stil ist: ein Schlüssel der Liste, eine CSS-Variable (`--…`) oder ein
 * Selektor bzw. eine Media-Query (`&…`, `@…`), deren Wert wieder ein reines Stilobjekt ist.
 */
const istStilEigenschaft = (eigenschaft) => {
  const name = schluesselName(eigenschaft)
  if (name === undefined) return false
  if (STIL_SCHLUESSEL.has(name) || name.startsWith('--')) return true
  return (
    (name.startsWith('&') || name.startsWith('@')) &&
    eigenschaft.value.type === 'ObjectExpression' &&
    nurStilSchluessel(eigenschaft.value)
  )
}

const nurStilSchluessel = (objekt) => objekt.properties.every(istStilEigenschaft)

/** Der äußerste Pfad um `path`, der nur aus wertneutralen Hüllen besteht. */
const ohneHuellen = (path) => {
  let aktuell = path
  while (HUELLEN.has(aktuell.parent.type)) aktuell = aktuell.parentPath
  return aktuell
}

/** Der Bezeichner, dem der Wert zugewiesen ist — durch wertneutrale Hüllen hindurch. */
const zugewiesenerBezeichner = (path) => {
  const aktuell = ohneHuellen(path)
  const { parent } = aktuell
  return parent.type === 'VariableDeclarator' && parent.init === aktuell.node && parent.id.type === 'Identifier'
    ? parent.id.name
    : undefined
}

const traegtStilNamen = (path) => STIL_BEZEICHNER.test(zugewiesenerBezeichner(path) ?? '')

/**
 * Die Funktion, deren Rückgabewert der Wert ist: als Ausdruckskörper einer Pfeilfunktion oder als
 * Argument eines `return`, jeweils durch wertneutrale Hüllen hindurch.
 */
const zurueckgebendeFunktion = (path) => {
  const aktuell = ohneHuellen(path)
  const { parent } = aktuell
  if (parent.type === 'ArrowFunctionExpression' && parent.body === aktuell.node) return aktuell.parentPath
  return parent.type === 'ReturnStatement' ? aktuell.parentPath.getFunctionParent() : null
}

/** Ein Stilobjekt, das eine Funktion mit Stilnamen zurückgibt: `lampeSx = (a) => ({ … })`. */
const istStilRueckgabe = (path) => {
  const funktion = zurueckgebendeFunktion(path)
  return funktion !== null && traegtStilNamen(funktion)
}

const istStilKonstante = (path) =>
  path.node.type === 'ObjectExpression' &&
  (traegtStilNamen(path) || istStilRueckgabe(path)) &&
  nurStilSchluessel(path.node)

const istStilTemplate = (knoten) =>
  knoten.type === 'TaggedTemplateExpression' && knoten.tag.type === 'Identifier' && STIL_TAGS.has(knoten.tag.name)

const istStilText = (path) =>
  (path.node.type === 'StringLiteral' || path.node.type === 'TemplateLiteral') && traegtStilNamen(path)

export class DarstellungIgnorer {
  /** Ein Grund, wenn der Teilbaum unter `path` reiner Stil ist; sonst `undefined`. */
  shouldIgnore(path) {
    if (istStilAttribut(path.node)) return 'Darstellung: Stilwert im sx- oder style-Attribut'
    if (istStyledArgument(path)) return 'Darstellung: Argument eines styled(...)-Aufrufs'
    if (istStilKonstante(path)) return 'Darstellung: Stilkonstante mit reinen Stilschlüsseln'
    if (istStilTemplate(path.node)) return 'Darstellung: Stiltext in keyframes`…` oder css`…`'
    if (istStilText(path)) return 'Darstellung: Stiltext in einer Stilkonstante'
    return undefined
  }
}

export const strykerPlugins = [declareClassPlugin(PluginKind.Ignore, 'darstellung', DarstellungIgnorer)]
