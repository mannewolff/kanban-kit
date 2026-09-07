import { describe, expect, it } from 'vitest'
import { NIGHT_RUN_EXCERPT_MAX, parseNightRunLog } from './nightRunLog'
import type { NightRun, NightRunItem } from './nightRunLog'
import { parseNightRunErgebnisstand } from './nightRunErgebnisstand'
import echterLauf from './__fixtures__/night-run-2026-09-07-085229.json'

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

/** Den Lauf holen und dabei sicherstellen, dass die Deutung ueberhaupt gelang. */
function lauf(text: string): NightRun {
  const ergebnis = parseNightRunErgebnisstand(text)
  if (!ergebnis.ok) throw new Error(`unerwartet nicht deutbar: ${ergebnis.grund}`)
  return ergebnis.run
}

/** Das einzige Arbeitspaket eines Laufs. */
const einziges = (text: string): NightRunItem => lauf(text).items[0]

describe('parseNightRunErgebnisstand — Ablehnungen', () => {
  it('lehnt eine Datei ab, die kein JSON ist', () => {
    expect(parseNightRunErgebnisstand('[2026-09-07T08:52:29.532Z] Nacht-Runner startet')).toEqual({
      ok: false,
      grund: 'kein-json',
    })
  })

  it('lehnt `null` ab — gueltiges JSON, aber kein Objekt', () => {
    expect(parseNightRunErgebnisstand('null')).toEqual({ ok: false, grund: 'kein-json' })
  })

  it('lehnt ein Array ab', () => {
    expect(parseNightRunErgebnisstand('[]')).toEqual({ ok: false, grund: 'kein-json' })
  })

  it('lehnt eine blosse Zahl ab', () => {
    expect(parseNightRunErgebnisstand('42')).toEqual({ ok: false, grund: 'kein-json' })
  })

  it('lehnt ein Objekt ohne schemaFassung ab — es ist kein Ergebnisstand', () => {
    expect(parseNightRunErgebnisstand('{"art":"implementierung"}')).toEqual({
      ok: false,
      grund: 'kein-json',
    })
  })

  it('meldet eine unbekannte Fassung getrennt vom Formfehler', () => {
    expect(parseNightRunErgebnisstand(stand({ schemaFassung: 2 }))).toEqual({
      ok: false,
      grund: 'unbekannte-fassung',
    })
  })

  it('lehnt einen Pruef-Lauf als nicht unterstuetzt ab', () => {
    expect(parseNightRunErgebnisstand(stand({ art: 'review' }))).toEqual({
      ok: false,
      grund: 'nicht-unterstuetzt',
    })
  })

  it('lehnt einen Stand ohne Feld `einheiten` ab', () => {
    const ohne = JSON.parse(stand()) as Record<string, unknown>
    delete ohne.einheiten
    expect(parseNightRunErgebnisstand(JSON.stringify(ohne))).toEqual({
      ok: false,
      grund: 'nicht-unterstuetzt',
    })
  })

  it('lehnt `einheiten` ab, wenn es kein Array ist', () => {
    expect(parseNightRunErgebnisstand(stand({ einheiten: {} }))).toEqual({
      ok: false,
      grund: 'nicht-unterstuetzt',
    })
  })

  it('lehnt einen `abschluss` ab, der weder null noch ein String ist', () => {
    expect(parseNightRunErgebnisstand(stand({ abschluss: 7 }))).toEqual({
      ok: false,
      grund: 'nicht-unterstuetzt',
    })
  })

  it('lehnt einen unbekannten `ausgang` ab', () => {
    expect(parseNightRunErgebnisstand(mitEinheit({ ausgang: 'halbfertig' }))).toEqual({
      ok: false,
      grund: 'nicht-unterstuetzt',
    })
  })

  it('lehnt `erfolg` ohne Pruefblock ab — das Paar ist unvollstaendig', () => {
    expect(parseNightRunErgebnisstand(mitEinheit({ ausgang: 'erfolg' }))).toEqual({
      ok: false,
      grund: 'nicht-unterstuetzt',
    })
  })

  it('lehnt `erfolg` mit unbekanntem Pruefzustand ab', () => {
    const text = mitEinheit({ ausgang: 'erfolg', pruefung: { id: '100', zustand: 'grau' } })
    expect(parseNightRunErgebnisstand(text)).toEqual({ ok: false, grund: 'nicht-unterstuetzt' })
  })

  it('lehnt `fehlschlag` mit einem Pruefzustand ab, den die Tabelle dort nicht kennt', () => {
    // `geprueft` ist bei einem Fehlschlag kein Widerspruch, den dieser Parser aufloest —
    // der Runner erzeugt das Paar nicht, und raten waere schlimmer als ablehnen.
    const text = mitEinheit({ ausgang: 'fehlschlag', pruefung: { id: '100', zustand: 'geprueft' } })
    expect(parseNightRunErgebnisstand(text)).toEqual({ ok: false, grund: 'nicht-unterstuetzt' })
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

  it('kuerzt einen ueberlangen Auszug auf die gemeinsame Obergrenze', () => {
    const grund = 'x'.repeat(NIGHT_RUN_EXCERPT_MAX + 100)
    expect(einziges(mitEinheit({ ausgang: 'zurueckgestellt', grund })).excerpt).toHaveLength(
      NIGHT_RUN_EXCERPT_MAX,
    )
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
  ]

  it.each(lagen)('deutet $name aus beiden Quellen gleich', ({ zeile, einheit: felder }) => {
    const ausProtokoll = parseNightRunLog(protokoll(zeile)).runs[0].items[0]
    const ausStand = einziges(mitEinheit(felder))
    expect([ausStand.state, ausStand.errorClass]).toEqual([
      ausProtokoll.state,
      ausProtokoll.errorClass,
    ])
  })
})
