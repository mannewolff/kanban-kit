import { describe, expect, it } from 'vitest'
import { stufenplan, testMusterFuer } from '../../mutationTestUmfang'

// Der Vollständigkeitsnachweis aus Issue #1275 (Plan #1270, AK 2 / E12): Der Stufenplan ist die
// einzige Quelle des Frontend-Prüfbereichs — also muss jede Quelldatei unter `src` dort genau
// einmal vorkommen, entweder in einem Ausschnitt oder unter den Ausnahmen. Eine neue Datei, die
// niemand einordnet, fällt hier im Pflicht-Gate auf und nicht erst nachts im Mutationslauf, wo
// sie stillschweigend ungemessen bliebe. Und weil der Vollauf den Kandidaten mitmisst (E6), muss
// auch jeder noch nicht aufgenommene Ausschnitt einen erreichbaren Test haben.

const SONDERZEICHEN = /[.+?^$()|[\]\\]/
const TOKEN = /\*\*\/|\*\*|\*|\{[^}]*\}|[\s\S]/g

const tokenZuRegex = (token: string): string => {
  if (token === '**/') return '(?:.*/)?'
  if (token === '**') return '.*'
  if (token === '*') return '[^/]*'
  if (token.startsWith('{')) return `(?:${token.slice(1, -1).split(',').join('|')})`
  return SONDERZEICHEN.test(token) ? `\\${token}` : token
}

/**
 * Minimal-Glob für Pfadmuster: `*` innerhalb eines Segments, `**` über Segmentgrenzen,
 * `{a,b}` als Alternative. Ein `**` samt folgendem Trenner darf ganz verschwinden, damit
 * `src/lib/**\/*.ts` auch `src/lib/a.ts` trifft — dieselbe Auslegung, die Stryker seinen
 * `mutate`-Mustern gibt (vgl. `globZuRegex` in `scripts/mutationspruefung.mjs`).
 */
const globZuRegex = (muster: string): RegExp =>
  new RegExp(`^${[...muster.matchAll(TOKEN)].map((treffer) => tokenZuRegex(treffer[0])).join('')}$`)

const trifft = (muster: readonly string[], pfad: string): boolean =>
  muster.some((einzeln) => globZuRegex(einzeln).test(pfad))

// Rohliste statt `node:fs`: `import.meta.glob` ist das etablierte Muster im Projekt (siehe
// `designQuelle.test.ts`), und die Namen genügen — die Module werden nicht geladen.
const ALLE: Record<string, unknown> = import.meta.glob('../**/*.{ts,tsx}')

// Vite gibt die Schlüssel relativ zu dieser Datei aus und kürzt dabei (`./boardOps.ts` statt
// `../lib/boardOps.ts`) — deshalb gegen das eigene Verzeichnis auflösen statt das `../` zu ersetzen.
const normalisiere = (schluessel: string): string => {
  const teile = ['src', 'lib']
  for (const segment of schluessel.split('/')) {
    if (segment === '..') teile.pop()
    else if (segment !== '.') teile.push(segment)
  }
  return teile.join('/')
}

const pfade = Object.keys(ALLE).map(normalisiere)
const quellen = pfade.filter((pfad) => !/\.test\.tsx?$/.test(pfad))
const tests = pfade.filter((pfad) => /\.test\.tsx?$/.test(pfad))

describe('mutationsstufen.json: der Stufenplan ist vollständig', () => {
  it('hat den Quellbaum überhaupt eingesammelt', () => {
    expect(quellen.length).toBeGreaterThan(100)
    expect(tests.length).toBeGreaterThan(100)
  })

  it.each(quellen)('%s ist genau einem Ausschnitt oder den Ausnahmen zugeordnet', (pfad) => {
    const treffer = stufenplan.ausschnitte
      .filter((ausschnitt) => trifft(ausschnitt.muster, pfad))
      .map((ausschnitt) => ausschnitt.name)
    const ausgenommen = trifft(stufenplan.ausnahmen, pfad)

    expect(
      ausgenommen || treffer.length === 1,
      ausgenommen
        ? `"${pfad}" steht unter den Ausnahmen.`
        : `"${pfad}" gehört zu ${treffer.length} Ausschnitten (${treffer.join(', ') || 'keinem'}) — ` +
            'ohne Ausschnitt bleibt die Datei ungemessen, in zweien zählt sie doppelt. ' +
            'Trag sie in `frontend/mutationsstufen.json` ein.',
    ).toBe(true)
  })

  it('ordnet keine Datei zwei Ausschnitten zu', () => {
    const doppelte = quellen.filter(
      (pfad) =>
        stufenplan.ausschnitte.filter((ausschnitt) => trifft(ausschnitt.muster, pfad)).length > 1,
    )

    expect(doppelte).toEqual([])
  })
})

describe('mutationsstufen.json: jeder Ausschnitt ist benutzbar', () => {
  it.each(stufenplan.ausschnitte.map((ausschnitt) => [ausschnitt.name, ausschnitt] as const))(
    '%s nennt Muster und hat einen erreichbaren Test',
    (name, ausschnitt) => {
      expect(ausschnitt.muster.length, `"${name}" nennt kein Muster.`).toBeGreaterThan(0)
      expect(ausschnitt.begruendung.length, `"${name}" nennt keine Begründung.`).toBeGreaterThan(0)

      const erreichbar = tests.filter((pfad) => trifft(testMusterFuer(ausschnitt), pfad))

      expect(
        erreichbar.length,
        `Zu "${name}" findet der Mutationslauf keine Testdatei — jeder seiner Mutanten endete ` +
          'als NoCoverage und seine Quote wäre null. Testmuster prüfen (Feld `testMuster`).',
      ).toBeGreaterThan(0)
    },
  )

  it('trifft mit jedem Ausschnitt mindestens eine Quelldatei', () => {
    const leer = stufenplan.ausschnitte
      .filter((ausschnitt) => !quellen.some((pfad) => trifft(ausschnitt.muster, pfad)))
      .map((ausschnitt) => ausschnitt.name)

    expect(leer).toEqual([])
  })
})

describe('mutationsstufen.json: die Reihenfolge ist Daten, keine Absprache', () => {
  const nachReihenfolge = [...stufenplan.ausschnitte].sort(
    (einer, anderer) => einer.reihenfolge - anderer.reihenfolge,
  )

  it('ist lückenlos 1..n und eindeutig', () => {
    expect(nachReihenfolge.map((ausschnitt) => ausschnitt.reihenfolge)).toEqual(
      stufenplan.ausschnitte.map((_, index) => index + 1),
    )
  })

  it('trägt eindeutige Namen', () => {
    const namen = stufenplan.ausschnitte.map((ausschnitt) => ausschnitt.name)

    expect(new Set(namen).size).toBe(namen.length)
  })

  it('lässt die Phase über die Reihenfolge nicht fallen', () => {
    const phasen = nachReihenfolge.map((ausschnitt) => ausschnitt.phase)

    expect(phasen).toEqual([...phasen].sort((einer, anderer) => einer - anderer))
  })

  it('führt `aufgenommen` als Datum oder als false', () => {
    for (const ausschnitt of stufenplan.ausschnitte) {
      expect(
        ausschnitt.aufgenommen === false || /^\d{4}-\d{2}-\d{2}$/.test(ausschnitt.aufgenommen),
        `"${ausschnitt.name}" trägt ein unbrauchbares \`aufgenommen\`.`,
      ).toBe(true)
    }
  })
})
