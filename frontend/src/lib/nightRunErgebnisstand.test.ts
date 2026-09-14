import { describe, expect, it } from 'vitest'
import { NIGHT_RUN_EXCERPT_MAX, parseNightRunLog } from './nightRunLog'
import type { NightRun, NightRunItem } from './nightRunLog'
import { parseNightRunErgebnisstand } from './nightRunErgebnisstand'
import type { NightRunErgebnisstandResult } from './nightRunErgebnisstand'
import echterLauf from './__fixtures__/night-run-2026-09-07-085229.json'
import echterNachtplanHarterStopp from './__fixtures__/night-run-2026-09-09-141506.json'
import echterNachtplanRegulaer from './__fixtures__/night-run-2026-09-09-125621.json'
import echterPrueflauf from './__fixtures__/night-run-2026-09-11-103116.json'
import echteKette from './__fixtures__/night-run-2026-09-14-131200.json'

/**
 * Die Fixtures dieser Datei sind — anders als in `nightRunLog.test.ts` — nicht
 * anonymisiert: Der Ergebnisstand traegt keine Pfade, keine Sitzungs-IDs und keinen
 * Quelltext, sondern Kartennummern, Titel und Kennzahlen des eigenen Projekts. Der
 * echte Lauf vom 2026-09-07 liegt deshalb unveraendert unter `__fixtures__/` (Issue
 * #773): Ein Test gegen selbstgebaute Daten prueft die eigene Annahme, nicht das
 * Format.
 */

/** Ein Ergebnisstand der Fassung 1; jedes Feld ist einzeln ueberschreibbar. */
const stand = (felder: Record<string, unknown> = {}): string =>
  JSON.stringify({
    schemaFassung: 1,
    erzeugtVon: '1.47.0',
    start: '2026-09-07T08:52:29.532Z',
    art: 'implementierung',
    modell: 'claude-opus-5',
    max: 10,
    label: null,
    einheiten: [],
    abschluss: 'regulaer',
    ...felder,
  })

/** Eine Einheit, wie `einheitAnlegen` in `night.mjs` sie schreibt. */
const einheit = (felder: Record<string, unknown>): Record<string, unknown> => ({
  id: '100',
  titel: 'Paket 1',
  ...felder,
})

/** Ein Ergebnisstand mit genau einer Einheit — der Regelfall dieser Tests. */
const mitEinheit = (felder: Record<string, unknown>, laufFelder: Record<string, unknown> = {}): string =>
  stand({ einheiten: [einheit(felder)], ...laufFelder })

/** Dieselbe Einheit in einem Pruef-Lauf — `art`/`stufe`, wie `night.mjs --review` sie schreibt. */
const imPrueflauf = (felder: Record<string, unknown>, laufFelder: Record<string, unknown> = {}): string =>
  mitEinheit(felder, { art: 'review', stufe: 'plan', ...laufFelder })

/**
 * Dieselbe Einheit in einem Ketten-Lauf. `stufe: null` schreibt `night.mjs --kette` in jeden
 * Ketten-Stand — die Stufen liegen dort innerhalb der Einheit, nicht am Lauf.
 */
const inKette = (felder: Record<string, unknown>, laufFelder: Record<string, unknown> = {}): string =>
  mitEinheit(felder, { art: 'kette', stufe: null, ...laufFelder })

/** Den Lauf holen und dabei sicherstellen, dass die Deutung ueberhaupt gelang. */
function lauf(text: string): NightRun {
  const ergebnis = parseNightRunErgebnisstand(text)
  if (!ergebnis.ok) throw new Error(`unerwartet nicht deutbar: ${ergebnis.grund}`)
  return ergebnis.run
}

/** Das einzige Arbeitspaket eines Laufs. */
const einziges = (text: string): NightRunItem => lauf(text).items[0]

/**
 * Die Ablehnung eines Stands — wirft, wenn er wider Erwarten deutbar war.
 *
 * <p>Seit Issue #857 traegt eine Ablehnung neben dem Grund auch das nicht gedeutete Wort
 * und die erzeugende Ausgabe des Nachtlaufs. Die Bestandsfaelle pruefen deshalb `.grund`
 * statt der vollstaendigen Form mit `toEqual`: Ihre Aussage ist unveraendert die, welcher
 * der drei Gruende zutrifft.
 */
function ablehnung(text: string): Extract<NightRunErgebnisstandResult, { ok: false }> {
  const ergebnis = parseNightRunErgebnisstand(text)
  if (ergebnis.ok) throw new Error('unerwartet deutbar')
  return ergebnis
}

describe('parseNightRunErgebnisstand — Ablehnungen', () => {
  it('lehnt eine Datei ab, die kein JSON ist', () => {
    expect(ablehnung('[2026-09-07T08:52:29.532Z] Nacht-Runner startet').grund).toBe('kein-json')
  })

  it('lehnt `null` ab — gueltiges JSON, aber kein Objekt', () => {
    expect(ablehnung('null').grund).toBe('kein-json')
  })

  it('lehnt ein Array ab', () => {
    expect(ablehnung('[]').grund).toBe('kein-json')
  })

  it('lehnt eine blosse Zahl ab', () => {
    expect(ablehnung('42').grund).toBe('kein-json')
  })

  it('lehnt ein Objekt ohne schemaFassung ab — es ist kein Ergebnisstand', () => {
    expect(ablehnung('{"art":"implementierung"}').grund).toBe('kein-json')
  })

  it('meldet eine unbekannte Fassung getrennt vom Formfehler', () => {
    expect(ablehnung(stand({ schemaFassung: 2 })).grund).toBe('unbekannte-fassung')
  })

  it('lehnt einen Pruef-Lauf mit unbekannter Stufe ab', () => {
    expect(ablehnung(stand({ art: 'review', stufe: 'sonstwas' })).grund).toBe('nicht-unterstuetzt')
  })

  it.each([
    ['mitBefund'],
    ['ohneBefund'],
    ['schaerfungFehlt'],
    ['syntheseOhneBeleg'],
  ])('lehnt den Pruef-Ausgang %s in einem Implementierungs-Lauf ab', (ausgang) => {
    expect(ablehnung(mitEinheit({ ausgang })).grund).toBe('nicht-unterstuetzt')
  })

  it('lehnt einen Pruef-Ausgang in einem Nachtplan-Lauf ab', () => {
    const text = mitEinheit({ ausgang: 'mitBefund' }, { art: 'erzeugung', stufe: 'plan' })
    expect(ablehnung(text).grund).toBe('nicht-unterstuetzt')
  })

  // Die Einheiten tragen jeweils genau das, was den Ausgang in seinem eigenen Modus
  // deutbar machte — sonst schluege schon die alte Pruefung zu und die Ablehnung waere
  // nicht belegt.
  it.each([
    ['verbraucht', {}],
    ['offen', {}],
    ['erfolg', { pruefung: { id: '100', zustand: 'geprueft' } }],
    ['fehlschlag', { pruefung: { id: '100', zustand: 'rot', rotesKommando: 'mvn verify', rotesErgebnis: 'rot' } }],
    ['zurueckgestellt', { grund: 'Nachtlauf: Idee ([Idee]).' }],
  ] as const)('lehnt den Ausgang %s eines anderen Modus in einem Pruef-Lauf ab', (ausgang, felder) => {
    // Ein Pruef-Lauf schreibt sie nie; eine geratene Farbe waere fuer eine kaputte
    // oder fremde Datei die falsche Aussage.
    expect(ablehnung(imPrueflauf({ ausgang, ...felder })).grund).toBe('nicht-unterstuetzt')
  })

  // Wie bei den Pruef-Lagen tragen die Einheiten genau das, was ihren Ausgang im eigenen
  // Modus deutbar machte — sonst schluege schon die alte Pruefung zu.
  it.each([
    ['verbraucht', {}],
    ['offen', {}],
    ['ohneErgebnis', {}],
    ['ohneBefund', {}],
    ['mitBefund', {}],
    ['schaerfungFehlt', {}],
    ['syntheseOhneBeleg', { grund: 'sonnet: „Der Abschnitt fehlt" — steht nicht im Body-Vorschlag.' }],
    ['erfolg', { pruefung: { id: '100', zustand: 'geprueft' } }],
    ['fehlschlag', { pruefung: { id: '100', zustand: 'rot', rotesKommando: 'mvn verify', rotesErgebnis: 'rot' } }],
    ['zurueckgestellt', { grund: 'Nachtlauf: Idee ([Idee]).' }],
    ['harterStopp', {}],
  ] as const)('lehnt den Ausgang %s eines anderen Modus in einem Ketten-Lauf ab', (ausgang, felder) => {
    // `erfolg` steht hier ausdruecklich mit drin: Er fiele sonst auf das modus-unabhaengige
    // Vokabular durch und bekaeme in einer Kette eine geratene Farbe.
    expect(ablehnung(inKette({ ausgang, ...felder })).grund).toBe('nicht-unterstuetzt')
  })

  it.each([['fertig'], ['angehalten'], ['abgebrochen']])(
    'lehnt den Ketten-Ausgang %s in einem Implementierungs-Lauf ab',
    (ausgang) => {
      expect(ablehnung(mitEinheit({ ausgang, grund: 'Kostenbudget: 55.00 $' })).grund).toBe('nicht-unterstuetzt')
    },
  )

  it('lehnt einen Ketten-Stand mit gesetzter Stufe ab — der Runner schreibt dort immer null', () => {
    expect(ablehnung(stand({ art: 'kette', stufe: 'plan' })).grund).toBe('nicht-unterstuetzt')
  })

  it.each([
    ['erzeugung', undefined],
    ['erzeugung', null],
    ['erzeugung', 'issue'],
    [undefined, 'plan'],
    ['implementierung', 'plan'],
  ] as const)('lehnt die Kombination art=%s/stufe=%s ab — nur (erzeugung,plan) ist Nachtplan', (art, stufe) => {
    expect(ablehnung(stand({ art, stufe })).grund).toBe('nicht-unterstuetzt')
  })

  it('lehnt einen Stand ohne Feld `einheiten` ab', () => {
    const ohne = JSON.parse(stand()) as Record<string, unknown>
    delete ohne.einheiten
    expect(ablehnung(JSON.stringify(ohne)).grund).toBe('nicht-unterstuetzt')
  })

  it('lehnt `einheiten` ab, wenn es kein Array ist', () => {
    expect(ablehnung(stand({ einheiten: {} })).grund).toBe('nicht-unterstuetzt')
  })

  it('lehnt einen `abschluss` ab, der weder null noch ein String ist', () => {
    expect(ablehnung(stand({ abschluss: 7 })).grund).toBe('nicht-unterstuetzt')
  })

  it('lehnt einen unbekannten `ausgang` ab', () => {
    expect(ablehnung(mitEinheit({ ausgang: 'halbfertig' })).grund).toBe('nicht-unterstuetzt')
  })

  it('lehnt `erfolg` ohne Pruefblock ab — das Paar ist unvollstaendig', () => {
    expect(ablehnung(mitEinheit({ ausgang: 'erfolg' })).grund).toBe('nicht-unterstuetzt')
  })

  it('lehnt `erfolg` mit unbekanntem Pruefzustand ab', () => {
    const text = mitEinheit({ ausgang: 'erfolg', pruefung: { id: '100', zustand: 'grau' } })
    expect(ablehnung(text).grund).toBe('nicht-unterstuetzt')
  })

  it('lehnt `fehlschlag` mit einem Pruefzustand ab, den die Tabelle dort nicht kennt', () => {
    // `geprueft` ist bei einem Fehlschlag kein Widerspruch, den dieser Parser aufloest —
    // der Runner erzeugt das Paar nicht, und raten waere schlimmer als ablehnen.
    const text = mitEinheit({ ausgang: 'fehlschlag', pruefung: { id: '100', zustand: 'geprueft' } })
    expect(ablehnung(text).grund).toBe('nicht-unterstuetzt')
  })
})

/**
 * AK 10 aus Issue #842: Wer entscheiden will, ob er ein neueres Werkzeug braucht, soll das
 * aus der Meldung erfahren — welches Wort nicht gedeutet werden konnte und welche Ausgabe
 * des Nachtlaufs die Datei geschrieben hat. Beides steht an der Ablehnung selbst; den Satz
 * daraus baut die Seite.
 */
describe('parseNightRunErgebnisstand — Wort und Herkunft einer Ablehnung (Issue #857)', () => {
  it('nennt bei unbekannter Lauf-Art das Paar art/stufe und die erzeugende Ausgabe', () => {
    const ergebnis = ablehnung(stand({ art: 'erfindung', stufe: 'plan' }))
    expect(ergebnis.grund).toBe('nicht-unterstuetzt')
    expect(ergebnis.wort).toBe('art=erfindung/stufe=plan')
    expect(ergebnis.erzeugtVon).toBe('1.47.0')
  })

  it('nennt eine fehlende Stufe im Wort ausdruecklich als „ohne"', () => {
    expect(ablehnung(stand({ art: 'erfindung' })).wort).toBe('art=erfindung/stufe=ohne')
  })

  it('nennt bei einem Ausgang, den eine Kette nie schreibt, genau diesen Ausgang', () => {
    const ergebnis = ablehnung(inKette({ ausgang: 'verbraucht' }))
    expect(ergebnis.grund).toBe('nicht-unterstuetzt')
    expect(ergebnis.wort).toBe('verbraucht')
    expect(ergebnis.erzeugtVon).toBe('1.47.0')
  })

  it('nennt auch den unbekannten Ausgang eines Implementierungs-Laufs', () => {
    expect(ablehnung(mitEinheit({ ausgang: 'halbfertig' })).wort).toBe('halbfertig')
  })

  it('laesst `wort` leer, wo es keines gibt — `einheiten` ist kein Array', () => {
    const ergebnis = ablehnung(stand({ einheiten: {} }))
    expect(ergebnis).not.toHaveProperty('wort')
    expect(ergebnis.erzeugtVon).toBe('1.47.0')
  })

  it('laesst `wort` auch bei falsch getyptem `abschluss` leer', () => {
    expect(ablehnung(stand({ abschluss: 7 }))).not.toHaveProperty('wort')
  })

  it('laesst Wort und Herkunft leer, wenn die Datei gar kein JSON ist', () => {
    const ergebnis = ablehnung('[2026-09-07T08:52:29.532Z] Nacht-Runner startet')
    expect(ergebnis).not.toHaveProperty('wort')
    expect(ergebnis).not.toHaveProperty('erzeugtVon')
  })

  it('laesst die Herkunft leer, wenn der Stand sie nicht als Zeichenkette fuehrt', () => {
    expect(ablehnung(stand({ art: 'erfindung', erzeugtVon: 7 }))).not.toHaveProperty('erzeugtVon')
  })

  /**
   * E11 des Plans #849: AK 10 verlangt die Herkunft woertlich nur fuer „Art oder Vokabular
   * unbekannt". Gerade bei unbekanntem **Aufbau** ist die Frage nach dem neueren Werkzeug
   * aber am dringendsten — sie dort als einzige auszulassen waere der eine Fall, in dem der
   * Betreiber doch in die Datei sehen muesste.
   */
  it('nennt die Herkunft auch bei unbekannter Aufbaufassung', () => {
    const ergebnis = ablehnung(stand({ schemaFassung: 2, erzeugtVon: '2.0.0' }))
    expect(ergebnis.grund).toBe('unbekannte-fassung')
    expect(ergebnis.erzeugtVon).toBe('2.0.0')
    expect(ergebnis).not.toHaveProperty('wort')
  })
})

describe('parseNightRunErgebnisstand — Zustand je Arbeitspaket', () => {
  it('macht `erfolg` mit gepruefter Runde gruen', () => {
    const item = einziges(mitEinheit({ ausgang: 'erfolg', pruefung: { id: '100', zustand: 'geprueft' } }))
    expect(item.state).toBe('GREEN')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('geprüft')
  })

  it('macht `erfolg` mit leerem Paket gruen', () => {
    const item = einziges(mitEinheit({ ausgang: 'erfolg', pruefung: { id: '100', zustand: 'leeresPaket' } }))
    expect(item.state).toBe('GREEN')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('leeres Paket')
  })

  it('macht `erfolg` ohne gefahrene Pruefung gelb mit CHECKS_NOT_STARTED', () => {
    const item = einziges(mitEinheit({ ausgang: 'erfolg', pruefung: { id: '100', zustand: 'ungeprueft' } }))
    expect(item.state).toBe('YELLOW')
    expect(item.errorClass).toBe('CHECKS_NOT_STARTED')
    expect(item.excerpt).toBe('Nachweis fehlt — die Session hat keine Prüfung gefahren')
  })

  it('macht `erfolg` mit unlesbarem Nachweis gelb mit CHECKS_NOT_STARTED', () => {
    const text = mitEinheit({
      ausgang: 'erfolg',
      pruefung: { id: '100', zustand: 'unlesbar', fehler: 'Unexpected token }' },
    })
    const item = einziges(text)
    expect(item.state).toBe('YELLOW')
    expect(item.errorClass).toBe('CHECKS_NOT_STARTED')
    expect(item.excerpt).toBe('Nachweis unlesbar (Unexpected token })')
  })

  it('macht `erfolg` mit rotem Nachweis gelb mit CHECKS_RED', () => {
    const text = mitEinheit({
      ausgang: 'erfolg',
      pruefung: { id: '100', zustand: 'rot', rotesKommando: 'mvn verify', rotesErgebnis: 'rot' },
    })
    const item = einziges(text)
    expect(item.state).toBe('YELLOW')
    expect(item.errorClass).toBe('CHECKS_RED')
    expect(item.excerpt).toBe('Nachweis rot — mvn verify endete rot')
  })

  it('macht `fehlschlag` mit rotem Nachweis rot mit CHECKS_RED', () => {
    const text = mitEinheit({
      ausgang: 'fehlschlag',
      pruefung: { id: '100', zustand: 'rot', rotesKommando: 'mvn verify', rotesErgebnis: 'rot' },
    })
    const item = einziges(text)
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('CHECKS_RED')
    expect(item.excerpt).toBe('Nachweis rot — mvn verify endete rot')
  })

  it('macht `fehlschlag` ohne gefahrene Pruefung rot mit CHECKS_NOT_STARTED', () => {
    const item = einziges(mitEinheit({ ausgang: 'fehlschlag', pruefung: { id: '100', zustand: 'ungeprueft' } }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('CHECKS_NOT_STARTED')
  })

  it('macht `fehlschlag` mit unlesbarem Nachweis rot mit CHECKS_NOT_STARTED', () => {
    const text = mitEinheit({
      ausgang: 'fehlschlag',
      pruefung: { id: '100', zustand: 'unlesbar', fehler: 'ENOENT' },
    })
    const item = einziges(text)
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('CHECKS_NOT_STARTED')
    expect(item.excerpt).toBe('Nachweis unlesbar (ENOENT)')
  })

  it('macht ein wegen unerfuellter Abhaengigkeit zurueckgestelltes Paket grau mit DEPENDENCY_UNMET', () => {
    const grund = 'Nachtlauf: Abhaengigkeit #99 nicht erfuellt (nicht in In review/Done) — Issue zurueckgestellt.'
    const item = einziges(mitEinheit({ ausgang: 'zurueckgestellt', grund }))
    expect(item.state).toBe('GREY')
    expect(item.errorClass).toBe('DEPENDENCY_UNMET')
    expect(item.excerpt).toBe(grund)
  })

  it('macht ein wegen kit:klaeren zurueckgestelltes Paket rot mit AWAITING_DECISION', () => {
    const grund = 'Nachtlauf: Traegt kit:klaeren — eine offene Entscheidung wartet auf einen Menschen, wird nicht implementiert.'
    const item = einziges(mitEinheit({ ausgang: 'zurueckgestellt', grund }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('AWAITING_DECISION')
    expect(item.excerpt).toBe(grund)
  })

  it('macht eine Session ohne In-review-Ergebnis rot mit UNEXPECTED_STATE', () => {
    const grund =
      'Session ohne In-review-Ergebnis beendet — Issue zurueckgestellt, Lauf ging mit dem naechsten Issue weiter.'
    const item = einziges(mitEinheit({ ausgang: 'zurueckgestellt', grund }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('UNEXPECTED_STATE')
  })

  it('macht jedes andere zurueckgestellte Paket grau ohne Fehlerklasse', () => {
    const grund = 'Nachtlauf: Plan-Dokument — wird nicht implementiert, bitte per /issues #100 in Arbeitspakete ueberfuehren.'
    const item = einziges(mitEinheit({ ausgang: 'zurueckgestellt', grund }))
    expect(item.state).toBe('GREY')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe(grund)
  })

  it('behandelt ein zurueckgestelltes Paket ohne Grund als den generischen Fall', () => {
    const item = einziges(mitEinheit({ ausgang: 'zurueckgestellt' }))
    expect(item.state).toBe('GREY')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('')
  })

  it('haelt die Reihenfolge der Sonderfaelle ein — der Grundtext ist nicht exklusiv', () => {
    // Eine Abhaengigkeit auf ein Ticket, dessen Titel `kit:klaeren` nennt: Die erste
    // Zeile der Tabelle gewinnt, sonst kippte ein grauer Fall auf rot.
    const grund = 'Nachtlauf: Abhaengigkeit #99 nicht erfuellt — dort haengt kit:klaeren.'
    const item = einziges(mitEinheit({ ausgang: 'zurueckgestellt', grund }))
    expect(item.state).toBe('GREY')
    expect(item.errorClass).toBe('DEPENDENCY_UNMET')
  })

  it('macht ein Paket mit unbekanntem Ausgang rot mit HARD_ABORT', () => {
    const item = einziges(mitEinheit({ ausgang: 'unbekannt' }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('HARD_ABORT')
    expect(item.excerpt).toBe('Lauf mitten in der Runde abgebrochen — kein Ausgang')
  })

  it('macht ein Paket mit hartem Stopp rot mit HARD_ABORT', () => {
    const item = einziges(mitEinheit({ ausgang: 'harterStopp' }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('HARD_ABORT')
    expect(item.excerpt).toBe('Harter Stopp')
  })

  it('macht `verbraucht` gruen ohne Fehlerklasse', () => {
    const item = einziges(mitEinheit({ ausgang: 'verbraucht' }, { art: 'erzeugung', stufe: 'plan' }))
    expect(item.state).toBe('GREEN')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('Dokument(e) erzeugt und geprüft — Label entfernt')
  })

  it('macht `liegengeblieben` grau ohne Fehlerklasse', () => {
    const item = einziges(mitEinheit({ ausgang: 'liegengeblieben' }, { art: 'erzeugung', stufe: 'plan' }))
    expect(item.state).toBe('GREY')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('Über die Obergrenze (--max) hinaus — bleibt liegen')
  })

  // `offen` kommt in keinem der beiden echten Fixtures vor (Plan #803, #805) — dieser
  // Test ist bewusst synthetisch, nicht gegen echte Daten belegt.
  it('macht das synthetische `offen` grau ohne Fehlerklasse', () => {
    const item = einziges(mitEinheit({ ausgang: 'offen' }, { art: 'erzeugung', stufe: 'plan' }))
    expect(item.state).toBe('GREY')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('Noch nicht jedes erzeugte Dokument hat einen Endzustand — Label bleibt stehen')
  })

  it('macht `ohneErgebnis` rot ohne Fehlerklasse', () => {
    const item = einziges(mitEinheit({ ausgang: 'ohneErgebnis' }, { art: 'erzeugung', stufe: 'plan' }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('Keine verwertbare Erzeugung — Session ohne Dokument oder Prüfrunde ohne Anker')
  })

  it('macht `uebersprungen` grau mit Freitext aus grund, unabhaengig vom Lauf-Modus', () => {
    const item = einziges(mitEinheit({ ausgang: 'uebersprungen', grund: "kein Label 'kit:nightplan'" }))
    expect(item.state).toBe('GREY')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe("kein Label 'kit:nightplan'")
  })

  it('macht `uebersprungen` ohne grund grau mit leerem Auszug', () => {
    const item = einziges(mitEinheit({ ausgang: 'uebersprungen' }))
    expect(item.state).toBe('GREY')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('')
  })

  it('macht `uebersprungen` mit nicht-string grund grau mit leerem Auszug', () => {
    const item = einziges(mitEinheit({ ausgang: 'uebersprungen', grund: 7 }))
    expect(item.state).toBe('GREY')
    expect(item.excerpt).toBe('')
  })

  it('kuerzt einen ueberlangen Auszug auf die gemeinsame Obergrenze', () => {
    const grund = 'x'.repeat(NIGHT_RUN_EXCERPT_MAX + 100)
    expect(einziges(mitEinheit({ ausgang: 'zurueckgestellt', grund })).excerpt).toHaveLength(
      NIGHT_RUN_EXCERPT_MAX,
    )
  })
})

describe('parseNightRunErgebnisstand — Pruef-Lauf (Issue #816)', () => {
  it.each([['fachlich'], ['plan'], ['issue']])('deutet art=review/stufe=%s als REVIEW', (stufe) => {
    const r = lauf(imPrueflauf({ ausgang: 'ohneBefund' }, { stufe }))
    expect(r.mode).toBe('REVIEW')
    expect(r.stage).toBe(stufe)
  })

  it.each([
    ['stufe: null', { stufe: null }],
    ['fehlende Stufe (Bestand vor 1.51.0)', { stufe: undefined }],
  ])('setzt bei %s die wirksame Stufe "issue" — so laeuft runReviewLoop', (_name, laufFelder) => {
    const r = lauf(imPrueflauf({ ausgang: 'ohneBefund' }, laufFelder))
    expect(r.mode).toBe('REVIEW')
    expect(r.stage).toBe('issue')
  })

  it('laesst die Stufe in den uebrigen Modi unbesetzt — wie der Text-Parser', () => {
    expect(lauf(stand())).not.toHaveProperty('stage')
    expect(lauf(mitEinheit({ ausgang: 'verbraucht' }, { art: 'erzeugung', stufe: 'plan' }))).not.toHaveProperty('stage')
  })

  it('macht `ohneBefund` gruen ohne Fehlerklasse', () => {
    const item = einziges(imPrueflauf({ ausgang: 'ohneBefund' }))
    expect(item.state).toBe('GREEN')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('geprüft ohne Befund — Marker gesetzt')
  })

  it('macht `mitBefund` gruen ohne Fehlerklasse — der Review hat sich gelohnt', () => {
    const item = einziges(imPrueflauf({ ausgang: 'mitBefund' }))
    expect(item.state).toBe('GREEN')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('geprüft mit Befund — kein Marker, wartet auf dich')
  })

  it('macht `schaerfungFehlt` gelb mit CHECKS_NOT_STARTED', () => {
    const item = einziges(imPrueflauf({ ausgang: 'schaerfungFehlt' }))
    expect(item.state).toBe('YELLOW')
    expect(item.errorClass).toBe('CHECKS_NOT_STARTED')
    expect(item.excerpt).toBe('Befunde vorhanden, aber kein Body-Vorschlag — Schärfung fehlt')
  })

  it('macht `syntheseOhneBeleg` rot mit AWAITING_DECISION und uebernimmt den grund', () => {
    const grund = 'sonnet: „Der Abschnitt fehlt" — steht nicht im Body-Vorschlag.'
    const item = einziges(imPrueflauf({ ausgang: 'syntheseOhneBeleg', grund }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('AWAITING_DECISION')
    expect(item.excerpt).toBe(grund)
  })

  it('kuerzt einen ueberlangen syntheseOhneBeleg-Grund auf die gemeinsame Obergrenze', () => {
    const grund = 'x'.repeat(NIGHT_RUN_EXCERPT_MAX + 100)
    expect(einziges(imPrueflauf({ ausgang: 'syntheseOhneBeleg', grund })).excerpt).toHaveLength(
      NIGHT_RUN_EXCERPT_MAX,
    )
  })

  it.each([
    ['ohne grund', {}],
    ['mit nicht-string grund', { grund: 7 }],
  ])('laesst den Auszug von `syntheseOhneBeleg` %s leer', (_name, felder) => {
    const item = einziges(imPrueflauf({ ausgang: 'syntheseOhneBeleg', ...felder }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('AWAITING_DECISION')
    expect(item.excerpt).toBe('')
  })

  // Derselbe Ausgang, zwei Bedeutungen: Im Pruef-Lauf hat die Session nichts
  // hinterlassen, im Nachtplan-Lauf ist nichts Verwertbares entstanden.
  it('macht `ohneErgebnis` im Pruef-Lauf rot mit CHECKS_NOT_STARTED', () => {
    const item = einziges(imPrueflauf({ ausgang: 'ohneErgebnis' }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('CHECKS_NOT_STARTED')
    expect(item.excerpt).toBe('Die Review-Session hat nichts hinterlassen — weder Marker noch Befunde')
  })

  it('laesst `ohneErgebnis` im Nachtplan-Lauf unveraendert rot ohne Fehlerklasse', () => {
    const item = einziges(mitEinheit({ ausgang: 'ohneErgebnis' }, { art: 'erzeugung', stufe: 'plan' }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('Keine verwertbare Erzeugung — Session ohne Dokument oder Prüfrunde ohne Anker')
  })

  it.each([
    ['uebersprungen', 'GREY' as const, "kein Label 'review:offen'"],
    ['liegengeblieben', 'GREY' as const, 'Über die Obergrenze (--max) hinaus — bleibt liegen'],
    ['unbekannt', 'RED' as const, 'Lauf mitten in der Runde abgebrochen — kein Ausgang'],
  ])('deutet den modus-unabhaengigen Ausgang %s auch im Pruef-Lauf', (ausgang, state, auszug) => {
    const item = einziges(imPrueflauf({ ausgang, grund: auszug }))
    expect(item.state).toBe(state)
    expect(item.excerpt).toBe(auszug)
  })
})

describe('parseNightRunErgebnisstand — Ketten-Lauf (Issue #854)', () => {
  /** Der Grund eines Zeitbudget-Abbruchs, wie `ketteSession` ihn schreibt. */
  const ZEITBUDGET = 'Zeitbudget review: die Session wurde nach 15.0 min am Limit beendet'

  it('deutet art=kette/stufe=null als CHAIN und laesst die Stufe unbesetzt', () => {
    const r = lauf(inKette({ ausgang: 'fertig' }))
    expect(r.mode).toBe('CHAIN')
    expect(r).not.toHaveProperty('stage')
  })

  it('macht `fertig` gruen ohne Fehlerklasse', () => {
    const item = einziges(inKette({ ausgang: 'fertig' }))
    expect(item.state).toBe('GREEN')
    expect(item.errorClass).toBeUndefined()
    expect(item.excerpt).toBe('Kette vollständig durchlaufen — Plan, Prüfung, Pakete, Abdeckung')
  })

  it('macht `angehalten` rot mit AWAITING_DECISION — eine Stopp-Frage wartet auf einen Menschen', () => {
    const item = einziges(inKette({ ausgang: 'angehalten', grund: 'Stopp-Frage im Plan #849' }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('AWAITING_DECISION')
    expect(item.excerpt).toBe('Stopp-Frage am Fachplan — die Kette wartet auf eine Entscheidung')
  })

  it('macht einen Zeitbudget-Abbruch ohne erzeugtes Dokument rot mit TIME_BUDGET_EXCEEDED', () => {
    const item = einziges(inKette({ ausgang: 'abgebrochen', grund: ZEITBUDGET }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('TIME_BUDGET_EXCEEDED')
    expect(item.excerpt).toBe(ZEITBUDGET)
  })

  // Die Stufen-Blöcke entstehen VOR ihrem Ergebnis (`stufePlan` legt `{ id: null, … }` an,
  // bevor die Session laeuft). Deshalb entscheidet `plan.id`, nicht die blosse Anwesenheit
  // des Blocks, und ein leeres `pakete.ids` zaehlt nicht als Dokument.
  it.each([
    ['ohne Stufen-Block', {}, 'RED' as const],
    ['mit begonnener, aber ergebnisloser Plan-Stufe', { stufen: { plan: { id: null } } }, 'RED' as const],
    ['mit leerem Stufen-Block', { stufen: {} }, 'RED' as const],
    ['mit leerer Paket-Liste', { stufen: { pakete: { ids: [] } } }, 'RED' as const],
    ['mit erzeugtem Plan', { stufen: { plan: { id: '849' } } }, 'YELLOW' as const],
    ['mit erzeugten Paketen', { stufen: { pakete: { ids: ['845'] } } }, 'YELLOW' as const],
  ])('faerbt einen Zeitbudget-Abbruch %s als %s', (_name, felder, state) => {
    const item = einziges(inKette({ ausgang: 'abgebrochen', grund: ZEITBUDGET, ...felder }))
    expect(item.state).toBe(state)
    expect(item.errorClass).toBe('TIME_BUDGET_EXCEEDED')
  })

  // E16: Kostenbudget und technischer Fehler sind harte Abbrueche — ein nebenbei erzeugtes
  // Dokument macht sie nicht gelb, anders als beim Zeitbudget.
  it.each([
    ['Kostenbudget: 55.00 $ von 50 $ nach der Stufe review'],
    ['technischer Fehler: die Session der Stufe plan endete mit exit 1'],
    ['kein Plan entstanden — die Session hat kein [Plan]-Dokument mit der Herkunftszeile angelegt'],
  ])('macht den Abbruch "%s" rot mit HARD_ABORT, auch mit erzeugtem Dokument', (grund) => {
    const item = einziges(
      inKette({ ausgang: 'abgebrochen', grund, stufen: { plan: { id: '849' } } }),
    )
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('HARD_ABORT')
    // Erste Zeile: Seit Issue #855 folgt dem Ausgang der Stufenblock der Einheit.
    expect(item.excerpt.split('\n')[0]).toBe(grund)
  })

  it.each([
    ['ohne grund', {}],
    ['mit nicht-string grund', { grund: 7 }],
  ])('behandelt einen Abbruch %s als harten Abbruch mit leerem Auszug', (_name, felder) => {
    const item = einziges(inKette({ ausgang: 'abgebrochen', ...felder }))
    expect(item.state).toBe('RED')
    expect(item.errorClass).toBe('HARD_ABORT')
    expect(item.excerpt).toBe('')
  })

  it('kuerzt einen ueberlangen Abbruch-Grund auf die gemeinsame Obergrenze', () => {
    const grund = 'x'.repeat(NIGHT_RUN_EXCERPT_MAX + 100)
    expect(einziges(inKette({ ausgang: 'abgebrochen', grund })).excerpt).toHaveLength(
      NIGHT_RUN_EXCERPT_MAX,
    )
  })

  it.each([
    ['uebersprungen', 'GREY' as const, "kein Label 'kit:night'"],
    ['liegengeblieben', 'GREY' as const, 'Über die Obergrenze (--max) hinaus — bleibt liegen'],
    ['unbekannt', 'RED' as const, 'Lauf mitten in der Runde abgebrochen — kein Ausgang'],
  ])('deutet den modus-unabhaengigen Ausgang %s auch im Ketten-Lauf', (ausgang, state, auszug) => {
    const item = einziges(inKette({ ausgang, grund: auszug }))
    expect(item.state).toBe(state)
    expect(item.excerpt).toBe(auszug)
  })
})

describe('parseNightRunErgebnisstand — Stufen und Dauer eines Ketten-Vorgangs (Issue #855)', () => {
  /**
   * Die Ausnahme aus E3: `stufeAbdeckung` laesst die Kette auch bei Zeitbudget, Fehlstart
   * oder fehlendem Text `fertig` enden und legt den Grund an der Stufe ab. Ohne diese
   * Ausnahme hiesse eine gescheiterte Abdeckung „gelungen" — genau die Falschaussage, die
   * der Leitstand nicht machen darf. Konstruiert, weil der erste echte Ketten-Lauf keine
   * Abdeckung mit `grund` enthaelt.
   */
  it('zeigt bei der Abdeckung ihren `grund` statt „gelungen", auch wenn die Einheit fertig ist', () => {
    const item = einziges(
      inKette({
        ausgang: 'fertig',
        stufen: {
          plan: { id: '849' },
          review: {},
          pakete: { ids: ['855'] },
          abdeckung: { grund: 'die Abdeckungs-Session lieferte keinen Text' },
        },
      }),
    )
    expect(item.excerpt.split('\n')).toEqual([
      'Kette vollständig durchlaufen — Plan, Prüfung, Pakete, Abdeckung',
      'plan #849: gelungen',
      'review: gelungen',
      'pakete #855: gelungen',
      'abdeckung: die Abdeckungs-Session lieferte keinen Text',
    ])
  })

  it('nennt nur die Stufen, die der Vorgang erreicht hat, und haengt den Ausgang an die letzte', () => {
    const item = einziges(
      inKette({
        ausgang: 'angehalten',
        grund: 'Stopp-Frage im Plan #849',
        stufen: { plan: { id: '849' }, review: {} },
      }),
    )
    expect(item.excerpt.split('\n')).toEqual([
      'Stopp-Frage am Fachplan — die Kette wartet auf eine Entscheidung',
      'plan #849: gelungen',
      'review: angehalten — Stopp-Frage im Plan #849',
    ])
  })

  // Denselben Rueckfall kennt die Ausgangs-Deutung (`grund`-Fallback `''`): Der Ausgang
  // steht auch ohne Grund fest, und die Stufenzeile nennt dann eben nur ihn.
  it('nennt an der letzten Stufe nur den Ausgang, wenn die Einheit keinen grund traegt', () => {
    const item = einziges(inKette({ ausgang: 'abgebrochen', stufen: { plan: { id: '849' } } }))
    expect(item.excerpt.split('\n')[1]).toBe('plan #849: abgebrochen')
  })

  it('nennt eine begonnene Plan-Stufe ohne Dokument ohne Nummer', () => {
    const item = einziges(inKette({ ausgang: 'fertig', stufen: { plan: { id: null } } }))
    expect(item.excerpt.split('\n')[1]).toBe('plan: gelungen')
  })

  it('nennt jede Paketnummer der Paket-Stufe', () => {
    const item = einziges(
      inKette({ ausgang: 'fertig', stufen: { pakete: { ids: ['847', '848'] } } }),
    )
    expect(item.excerpt.split('\n')[1]).toBe('pakete #847, #848: gelungen')
  })

  it('laesst den Auszug einer Einheit ohne Stufen-Block unveraendert', () => {
    expect(einziges(inKette({ ausgang: 'fertig' })).excerpt).toBe(
      'Kette vollständig durchlaufen — Plan, Prüfung, Pakete, Abdeckung',
    )
  })

  it('kuerzt auch den Stufen-Text auf die gemeinsame Obergrenze', () => {
    const grund = 'x'.repeat(NIGHT_RUN_EXCERPT_MAX)
    const item = einziges(
      inKette({ ausgang: 'fertig', stufen: { abdeckung: { grund } } }),
    )
    expect(item.excerpt).toHaveLength(NIGHT_RUN_EXCERPT_MAX)
  })

  it('summiert die Dauer eines Ketten-Vorgangs aus seinen Stufen', () => {
    const item = einziges(
      inKette({
        ausgang: 'fertig',
        stufen: { plan: { id: '849', dauerMs: 1000 }, review: { dauerMs: 2000 } },
      }),
    )
    expect(item.durationMs).toBe(3000)
  })

  it('bevorzugt ein vorhandenes dauerMs der Einheit vor der Summe ihrer Stufen', () => {
    const item = einziges(
      inKette({ ausgang: 'fertig', dauerMs: 42, stufen: { plan: { id: '849', dauerMs: 1000 } } }),
    )
    expect(item.durationMs).toBe(42)
  })

  /**
   * AK 9 aus #842: Ein zurueckgestelltes oder uebersprungenes Paket ohne `dauerMs` und ohne
   * Stufen zeigt heute **keine** Dauer — eine leere Summe duerfte daraus kein „0s" machen.
   */
  it('erzeugt ohne dauerMs und ohne Stufen-Block keine Dauer am Arbeitspaket', () => {
    const item = einziges(inKette({ ausgang: 'uebersprungen', grund: "kein Label 'kit:night'" }))
    expect(item).not.toHaveProperty('durationMs')
  })
})

describe('parseNightRunErgebnisstand — uebernommene Felder', () => {
  const vollstaendig = mitEinheit({
    id: '767',
    titel: 'Sammelzugriff fuer Zustaendige',
    ausgang: 'erfolg',
    dauerMs: 244427,
    commit: '917b6a3',
    endStatus: 'in_review',
    pruefung: { id: '767', zustand: 'geprueft' },
    kennzahlen: { kostenUsd: 2.13, apiDauerMs: 180563, zuege: 38 },
  })

  it('uebernimmt Startzeit, Kartennummer, Titel, Dauer und Commit', () => {
    const item = einziges(vollstaendig)
    expect(lauf(vollstaendig).startedAt).toBe('2026-09-07T08:52:29.532Z')
    expect(item.cardNumber).toBe(767)
    expect(item.title).toBe('Sammelzugriff fuer Zustaendige')
    expect(item.durationMs).toBe(244427)
    expect(item.commit).toBe('917b6a3')
  })

  it('laesst den Commit weg, wenn der Stand ihn als null fuehrt', () => {
    const item = einziges(mitEinheit({ ausgang: 'unbekannt', commit: null }))
    expect(item).not.toHaveProperty('commit')
  })

  it('laesst die Dauer weg, wenn die Einheit keine traegt', () => {
    const item = einziges(mitEinheit({ ausgang: 'zurueckgestellt', grund: 'Nachtlauf: Idee ([Idee]).' }))
    expect(item).not.toHaveProperty('durationMs')
  })

  it('haelt kein Rohprotokoll vor — der Ergebnisstand traegt keines', () => {
    expect(einziges(vollstaendig).rawLines).toEqual([])
  })

  it('nummeriert die Arbeitspakete in der Reihenfolge der Einheiten', () => {
    const text = stand({
      einheiten: [
        einheit({ id: '100', ausgang: 'unbekannt' }),
        einheit({ id: '101', ausgang: 'unbekannt' }),
      ],
    })
    expect(lauf(text).items.map((i) => [i.cardNumber, i.position])).toEqual([
      [100, 0],
      [101, 1],
    ])
  })

  it('deutet einen Implementierungs-Lauf als IMPLEMENTATION', () => {
    expect(lauf(stand()).mode).toBe('IMPLEMENTATION')
  })

  it('deutet einen Implementierungs-Lauf mit stufe:null (Kit ab 1.51.0) weiterhin als IMPLEMENTATION', () => {
    expect(lauf(stand({ stufe: null })).mode).toBe('IMPLEMENTATION')
  })

  it('deutet einen Bestand ganz ohne art-Feld (aelter als 1.47.0) als IMPLEMENTATION', () => {
    expect(lauf(stand({ art: undefined })).mode).toBe('IMPLEMENTATION')
  })

  it('deutet art=erzeugung/stufe=plan als NIGHTPLAN', () => {
    const text = mitEinheit(
      { ausgang: 'uebersprungen', grund: "kein Label 'kit:nightplan'" },
      { art: 'erzeugung', stufe: 'plan' },
    )
    expect(lauf(text).mode).toBe('NIGHTPLAN')
  })

  it('zaehlt bearbeitete und uebergangene Pakete getrennt', () => {
    const text = stand({
      einheiten: [
        einheit({ id: '100', ausgang: 'unbekannt' }),
        einheit({ id: '101', ausgang: 'zurueckgestellt', grund: 'Nachtlauf: Idee ([Idee]).' }),
      ],
    })
    const r = lauf(text)
    expect(r.processedCount).toBe(1)
    expect(r.skippedCount).toBe(1)
  })

  it('kennt keine ungedeuteten Zeilen — der Stand ist strukturiert', () => {
    const r = lauf(stand())
    expect(r.unparsedCount).toBe(0)
    expect(r.unparsedSample).toEqual([])
  })

  it('summiert die Laufdauer aus den Einheiten und zaehlt fehlende Dauern als null', () => {
    const text = stand({
      einheiten: [
        einheit({ id: '100', ausgang: 'unbekannt', dauerMs: 1000 }),
        einheit({ id: '101', ausgang: 'unbekannt', dauerMs: 2000 }),
        einheit({ id: '102', ausgang: 'zurueckgestellt', grund: 'Nachtlauf: Idee ([Idee]).' }),
      ],
    })
    expect(lauf(text).durationMs).toBe(3000)
  })

  it('meldet einen Lauf ohne Abschluss als unvollstaendig', () => {
    expect(lauf(stand({ abschluss: null })).incomplete).toBe(true)
  })

  it('meldet einen regulaer beendeten Lauf als vollstaendig', () => {
    expect(lauf(stand()).incomplete).toBe(false)
  })
})

describe('parseNightRunErgebnisstand — harter Stopp auf Lauf-Ebene', () => {
  it('setzt Zustand und Fehlerklasse des Laufs und uebernimmt den Fehlertext', () => {
    const r = lauf(
      stand({
        abschluss: 'harterStopp',
        fehlerklasse: 'zustand',
        fehlerText: 'Working Tree ist nicht sauber. Bitte committen oder aufraeumen, dann neu starten.',
      }),
    )
    expect(r.runState).toBe('RED')
    expect(r.runErrorClass).toBe('HARD_ABORT')
    expect(r.runExcerpt).toBe(
      'Working Tree ist nicht sauber. Bitte committen oder aufraeumen, dann neu starten.',
    )
  })

  it('nennt ohne Fehlertext wenigstens die Fehlerklasse', () => {
    const r = lauf(stand({ abschluss: 'harterStopp', fehlerklasse: 'umgebung' }))
    expect(r.runExcerpt).toBe('Harter Stopp (umgebung)')
  })

  it('meldet ohne Fehlertext und ohne Fehlerklasse den blossen harten Stopp', () => {
    expect(lauf(stand({ abschluss: 'harterStopp' })).runExcerpt).toBe('Harter Stopp')
  })

  it('laesst den Lauf-Zustand bei regulaerem Abschluss unbesetzt', () => {
    const r = lauf(stand())
    expect(r).not.toHaveProperty('runState')
    expect(r).not.toHaveProperty('runErrorClass')
    expect(r).not.toHaveProperty('runExcerpt')
  })
})

describe('parseNightRunErgebnisstand — echter Lauf vom 2026-09-07', () => {
  const r = lauf(JSON.stringify(echterLauf))
  const nach = (nummer: number) => r.items.find((i) => i.cardNumber === nummer)

  it('deutet die beiden erfolgreichen, aber ungeprueften Pakete als gelb', () => {
    for (const nummer of [767, 770]) {
      expect(nach(nummer)?.state).toBe('YELLOW')
      expect(nach(nummer)?.errorClass).toBe('CHECKS_NOT_STARTED')
    }
  })

  it('deutet die drei Pakete mit rotem Nachweis als rot', () => {
    for (const nummer of [768, 769, 771]) {
      expect(nach(nummer)?.state).toBe('RED')
      expect(nach(nummer)?.errorClass).toBe('CHECKS_RED')
    }
  })

  it('summiert die Laufdauer aus den fuenf Einheiten', () => {
    expect(r.durationMs).toBe(244427 + 769668 + 980483 + 167015 + 926467)
  })

  it('zaehlt fuenf bearbeitete Pakete, keines uebergangen, nichts ungedeutet', () => {
    expect(r.processedCount).toBe(5)
    expect(r.skippedCount).toBe(0)
    expect(r.unparsedCount).toBe(0)
  })
})

describe('parseNightRunErgebnisstand — echter Nachtplan-Lauf, hart gestoppt (2026-09-09-141506)', () => {
  const r = lauf(JSON.stringify(echterNachtplanHarterStopp))
  const nach = (nummer: number) => r.items.find((i) => i.cardNumber === nummer)

  it('deutet den Lauf als NIGHTPLAN, unvollstaendig-Flag false trotz Hart-Stopp', () => {
    expect(r.mode).toBe('NIGHTPLAN')
    expect(r.incomplete).toBe(false)
  })

  it('zaehlt 2 bearbeitete und 33 uebergangene Pakete', () => {
    expect(r.processedCount).toBe(2)
    expect(r.skippedCount).toBe(33)
  })

  it('setzt Lauf-Zustand und -Fehlerklasse auf den harten Stopp', () => {
    expect(r.runState).toBe('RED')
    expect(r.runErrorClass).toBe('HARD_ABORT')
    expect(r.runExcerpt).toBe('Harter Stopp (harterStopp)')
  })

  it('deutet die beiden abgebrochenen Pakete #479 und #549 rot mit HARD_ABORT', () => {
    for (const nummer of [479, 549]) {
      expect(nach(nummer)?.state).toBe('RED')
      expect(nach(nummer)?.errorClass).toBe('HARD_ABORT')
    }
  })
})

describe('parseNightRunErgebnisstand — echter Nachtplan-Lauf, regulaer beendet (2026-09-09-125621)', () => {
  const r = lauf(JSON.stringify(echterNachtplanRegulaer))
  const nach = (nummer: number) => r.items.find((i) => i.cardNumber === nummer)

  it('deutet den Lauf als NIGHTPLAN, vollstaendig, ohne Lauf-Zustand', () => {
    expect(r.mode).toBe('NIGHTPLAN')
    expect(r.incomplete).toBe(false)
    expect(r).not.toHaveProperty('runState')
  })

  it('zaehlt 3 bearbeitete und 35 uebergangene Pakete', () => {
    expect(r.processedCount).toBe(3)
    expect(r.skippedCount).toBe(35)
  })

  it('summiert die Laufdauer aus den Einheiten mit dauerMs', () => {
    expect(r.durationMs).toBe(1858420 + 1976663 + 0)
  })

  it('deutet die beiden `verbraucht`-Pakete #533 und #535 gruen ohne Fehlerklasse', () => {
    for (const nummer of [533, 535]) {
      expect(nach(nummer)?.state).toBe('GREEN')
      expect(nach(nummer)?.errorClass).toBeUndefined()
    }
  })

  it('deutet das `ohneErgebnis`-Paket #479 rot ohne Fehlerklasse', () => {
    expect(nach(479)?.state).toBe('RED')
    expect(nach(479)?.errorClass).toBeUndefined()
  })

  it('deutet die `liegengeblieben`-Pakete #541/#549/#551 grau ohne Fehlerklasse', () => {
    for (const nummer of [541, 549, 551]) {
      expect(nach(nummer)?.state).toBe('GREY')
      expect(nach(nummer)?.errorClass).toBeUndefined()
    }
  })
})

describe('parseNightRunErgebnisstand — echter Pruef-Lauf (2026-09-11-103116)', () => {
  const r = lauf(JSON.stringify(echterPrueflauf))
  const nach = (nummer: number) => r.items.find((i) => i.cardNumber === nummer)

  it('deutet den Lauf als REVIEW auf Stufe plan, vollstaendig, ohne Lauf-Zustand', () => {
    expect(r.mode).toBe('REVIEW')
    expect(r.stage).toBe('plan')
    expect(r.incomplete).toBe(false)
    expect(r).not.toHaveProperty('runState')
  })

  it('zaehlt ein bearbeitetes und 34 uebergangene Pakete, nichts ungedeutet', () => {
    expect(r.processedCount).toBe(1)
    expect(r.skippedCount).toBe(34)
    expect(r.unparsedCount).toBe(0)
  })

  it('summiert die Laufdauer aus der einzigen Einheit mit dauerMs', () => {
    expect(r.durationMs).toBe(879763)
  })

  it('deutet das `mitBefund`-Paket #782 gruen ohne Fehlerklasse', () => {
    expect(nach(782)?.state).toBe('GREEN')
    expect(nach(782)?.errorClass).toBeUndefined()
  })
})

/**
 * Der erste echte Ketten-Lauf (Issue #854). Er traegt genau die drei Lagen, um die es geht:
 * zwei vollstaendig durchlaufene Ketten und einen Zeitbudget-Abbruch, bei dem trotzdem ein
 * Plan-Dokument entstanden ist — der Gelb-Fall. Ein Test gegen selbstgebaute Daten prueft
 * die eigene Annahme, nicht das Format.
 */
describe('parseNightRunErgebnisstand — echter Ketten-Lauf (2026-09-14-131200)', () => {
  const r = lauf(JSON.stringify(echteKette))
  const nach = (nummer: number) => r.items.find((i) => i.cardNumber === nummer)

  it('deutet den Lauf als CHAIN, vollstaendig, ohne Lauf-Zustand und ohne Stufe', () => {
    expect(r.mode).toBe('CHAIN')
    expect(r.incomplete).toBe(false)
    expect(r).not.toHaveProperty('runState')
    expect(r).not.toHaveProperty('stage')
  })

  it('zaehlt drei bearbeitete Vorgaenge, keinen uebergangen, nichts ungedeutet', () => {
    expect(r.processedCount).toBe(3)
    expect(r.skippedCount).toBe(0)
    expect(r.unparsedCount).toBe(0)
  })

  it('deutet die beiden fertigen Ketten #791 und #814 gruen ohne Fehlerklasse', () => {
    for (const nummer of [791, 814]) {
      expect(nach(nummer)?.state).toBe('GREEN')
      expect(nach(nummer)?.errorClass).toBeUndefined()
    }
  })

  it('deutet den Zeitbudget-Abbruch #842 gelb — der Plan #849 ist trotzdem entstanden', () => {
    expect(nach(842)?.state).toBe('YELLOW')
    expect(nach(842)?.errorClass).toBe('TIME_BUDGET_EXCEEDED')
    expect(nach(842)?.excerpt.split('\n')[0]).toBe(
      'Zeitbudget review: die Session wurde nach 15.0 min am Limit beendet',
    )
  })

  it('nennt im Auszug der fertigen Kette #791 alle vier Stufen mit ihren Dokumenten (AK 2)', () => {
    expect(nach(791)?.excerpt.split('\n')).toEqual([
      'Kette vollständig durchlaufen — Plan, Prüfung, Pakete, Abdeckung',
      'plan #844: gelungen',
      'review: gelungen',
      'pakete #845: gelungen',
      'abdeckung: gelungen',
    ])
  })

  it('nennt im Auszug des Abbruchs #842 nur die erreichten Stufen — Plan gelungen, Review am Limit (AK 2)', () => {
    expect(nach(842)?.excerpt.split('\n')).toEqual([
      'Zeitbudget review: die Session wurde nach 15.0 min am Limit beendet',
      'plan #849: gelungen',
      'review: abgebrochen — Zeitbudget review: die Session wurde nach 15.0 min am Limit beendet',
    ])
  })

  it('summiert die Dauer des Vorgangs #842 aus seinen beiden Stufen (AK 6)', () => {
    expect(nach(842)?.durationMs).toBe(439741 + 900484)
  })

  it('summiert die Laufdauer ueber alle drei Vorgaenge (AK 6)', () => {
    expect(r.durationMs).toBe(1533322 + 1274784 + 1340225)
  })
})

/**
 * Beide Wege muessen dieselbe Lage gleich benennen — sonst hiesse derselbe Lauf im
 * Leitstand je nach Quelle anders. Verglichen wird nur, was der Ergebnisstand
 * tatsaechlich erzeugen kann.
 *
 * <p>Der `kit:klaeren`-Fall haengt an der **Dry-Run-Zeile**: Die Echtlauf-Zeile
 * (`#100 uebersprungen: traegt kit:klaeren …`) faellt im Textparser auf das generische
 * `uebersprungen`-Muster und damit auf GREY ohne Fehlerklasse. Der Unterschied ist
 * bekannt und nicht Gegenstand von Issue #773.
 */
describe('parseNightRunErgebnisstand gegen parseNightRunLog', () => {
  const z = (minute: number, text: string) =>
    `[2026-09-01T22:${String(minute).padStart(2, '0')}:00.000Z] ${text}`

  const protokoll = (zeile: string) =>
    [
      z(0, 'Nacht-Runner startet (Modus Implementierung, max 5 Sessions, Modell claude-opus-5, Label none)'),
      z(1, zeile),
      z(9, 'Nacht-Runner beendet: 0 erfolgreich, 1 zurueckgestellt, 1 Session(s) gestartet.'),
    ].join('\n')

  const lagen: ReadonlyArray<{ name: string; zeile: string; einheit: Record<string, unknown> }> = [
    {
      name: 'unerfuellte Abhaengigkeit',
      zeile: '#100 zurueckgestellt: Abhaengigkeit #99 nicht erfuellt.',
      einheit: {
        ausgang: 'zurueckgestellt',
        grund: 'Nachtlauf: Abhaengigkeit #99 nicht erfuellt (nicht in In review/Done) — Issue zurueckgestellt.',
      },
    },
    {
      name: 'kit:klaeren',
      zeile: '  #100 Paket 1 -> uebersprungen (kit:klaeren, offene Entscheidung)',
      einheit: {
        ausgang: 'zurueckgestellt',
        grund:
          'Nachtlauf: Traegt kit:klaeren — eine offene Entscheidung wartet auf einen Menschen, wird nicht implementiert.',
      },
    },
    {
      name: 'Session ohne In-review-Ergebnis',
      zeile: '  Fehlschlag nach 5 min: Issue #100 nicht in In review, Tree sauber — Issue ins Backlog, weiter.',
      einheit: {
        ausgang: 'zurueckgestellt',
        grund:
          'Session ohne In-review-Ergebnis beendet — Issue zurueckgestellt, Lauf ging mit dem naechsten Issue weiter.',
      },
    },
    {
      name: 'Praefix-Gate',
      zeile: '#100 uebersprungen: fachliches Issue ([Fachlich]), wird nicht implementiert.',
      einheit: {
        ausgang: 'zurueckgestellt',
        grund:
          'Nachtlauf: Fachliches Issue — wird nicht implementiert, bitte per /plan #100 in technische Issues ueberfuehren.',
      },
    },
    {
      name: 'Nachtplan ohne Label',
      zeile: "#100 uebersprungen: kein Label 'kit:nightplan'.",
      einheit: {
        ausgang: 'uebersprungen',
        grund: "kein Label 'kit:nightplan'",
      },
    },
  ]

  /**
   * Derselbe Vergleich fuer den Pruef-Lauf (Issue #816). Eigener Kopf und eigener
   * Abschluss, weil der Text-Parser Modus und Stufe genau aus diesen beiden Zeilen
   * liest; die Ausgangszeilen stehen woertlich wie in `werteReviewSession`.
   */
  const pruefProtokoll = (zeile: string) =>
    [
      z(0, 'Nacht-Runner startet (Modus Review, Stufe plan, max 5 Sessions, Modell claude-opus-5, Label review:offen)'),
      z(1, 'Review-Session 1/5: Issue #100 — Paket 1'),
      z(4, zeile),
      z(
        9,
        'Nacht-Review beendet (Stufe plan): 1 ohne Befund, 0 mit Befund, 0 Schaerfung fehlt, '
          + '0 Synthese ohne Beleg, 0 uebersprungen, 0 ohne Ergebnis, 1 Session(s) gestartet.',
      ),
    ].join('\n')

  const prueflagen: ReadonlyArray<{ name: string; zeile: string; einheit: Record<string, unknown> }> = [
    {
      name: 'ohneBefund',
      zeile: '  Erfolg nach 3 min: Issue #100 geprueft ohne Befund, Marker gesetzt.',
      einheit: { ausgang: 'ohneBefund' },
    },
    {
      name: 'mitBefund',
      zeile: '  Erfolg nach 3 min: Issue #100 geprueft mit Befund — kein Marker, wartet auf dich.',
      einheit: { ausgang: 'mitBefund' },
    },
    {
      name: 'schaerfungFehlt',
      zeile: '  Nach 3 min: Issue #100 — Befunde vorhanden, aber kein Body-Vorschlag — Schaerfung fehlt.',
      einheit: { ausgang: 'schaerfungFehlt' },
    },
    {
      name: 'syntheseOhneBeleg',
      zeile:
        '  Nach 3 min: Issue #100 — Synthese ohne Beleg: 2 als uebernommen bezeichnete Funde stehen nicht im Body-Vorschlag.',
      einheit: {
        ausgang: 'syntheseOhneBeleg',
        grund: 'sonnet: „Der Abschnitt fehlt" — steht nicht im Body-Vorschlag.',
      },
    },
    {
      name: 'ohneErgebnis',
      zeile: '  Fehlschlag nach 3 min: Issue #100 — die Session hat nichts hinterlassen, weiter mit dem naechsten.',
      einheit: { ausgang: 'ohneErgebnis' },
    },
  ]

  it.each(prueflagen)('deutet $name im Pruef-Lauf aus beiden Quellen gleich', ({ zeile, einheit: felder }) => {
    const protokollLauf = parseNightRunLog(pruefProtokoll(zeile)).runs[0]
    const standLauf = lauf(imPrueflauf(felder))
    expect([standLauf.mode, standLauf.stage]).toEqual([protokollLauf.mode, protokollLauf.stage])
    const ausProtokoll = protokollLauf.items[0]
    const ausStand = standLauf.items[0]
    expect([ausStand.state, ausStand.errorClass]).toEqual([
      ausProtokoll.state,
      ausProtokoll.errorClass,
    ])
  })

  it.each(lagen)('deutet $name aus beiden Quellen gleich', ({ zeile, einheit: felder }) => {
    const ausProtokoll = parseNightRunLog(protokoll(zeile)).runs[0].items[0]
    const ausStand = einziges(mitEinheit(felder))
    expect([ausStand.state, ausStand.errorClass]).toEqual([
      ausProtokoll.state,
      ausProtokoll.errorClass,
    ])
  })
})
