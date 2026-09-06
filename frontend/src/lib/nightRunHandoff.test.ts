import { describe, expect, it, vi } from 'vitest'
import { buildHandoffText, type NightRunHandoffItem } from './nightRunHandoff'

/**
 * Tests des Uebernahmetexts (Issue #727). Sie kommen ohne Oberflaeche aus — genau dafuer ist die
 * Erzeugung eine reine Funktion und keine Methode der Seite.
 *
 * Fixtures anonymisiert wie in `nightRunLog.test.ts`: Titel nach dem Schema `Paket N`, keine Pfade,
 * keine Sitzungs-IDs.
 */

const AUSZUG_ROT = '  HARTER STOPP: erfolgreiche Runde zu Issue #700 hinterlaesst einen dirty Tree'
const AUSZUG_GELB = '  Issue #700: gelaufen: npm test -> rot (Frontend) | ausgelassen: keine'

/**
 * Rohzeilen wie der Parser sie liefert (`lib/nightRunLog.ts`): mit Zeitstempel-Praefix, mit
 * Einrueckung, dazwischen eine Zeile ohne Zeitstempel. Genau so muessen sie im Text stehen.
 */
const ROHZEILEN = [
  '[00:01:12] Session 1/5: Issue #700 — Paket A',
  '[00:03:40]   gelaufen: npm test -> rot (Frontend) | ausgelassen: keine',
  '      Fortsetzung ohne Zeitstempel, bewusst eingerueckt',
  `[00:05:02] ${AUSZUG_ROT.trim()}`,
]

const paket = (partial: Partial<NightRunHandoffItem> = {}): NightRunHandoffItem => ({
  cardNumber: 700,
  title: 'Paket A',
  state: 'RED',
  errorClass: 'HARD_ABORT',
  excerpt: AUSZUG_ROT,
  ...partial,
})

describe('buildHandoffText — gelbe und rote Arbeitspakete', () => {
  it('nennt zu einem roten Arbeitspaket Kartennummer, Zustand, Fehlerklasse und Auszug', () => {
    const text = buildHandoffText(paket())

    expect(text).toContain('#700')
    expect(text).toContain('Paket A')
    expect(text).toContain('gescheitert')
    expect(text).toContain('Harter Abbruch')
    expect(text).toContain(AUSZUG_ROT)
  })

  it('nennt zu einem gelben Arbeitspaket Kartennummer, Zustand, Fehlerklasse und Auszug', () => {
    const text = buildHandoffText(
      paket({ state: 'YELLOW', errorClass: 'CHECKS_RED', excerpt: AUSZUG_GELB }),
    )

    expect(text).toContain('#700')
    expect(text).toContain('Erfolg, Prüfung rot')
    expect(text).toContain('Prüfungen rot')
    expect(text).toContain(AUSZUG_GELB)
  })

  it('nennt die Karte ohne nachlaufendes Leerzeichen, wenn das Protokoll keinen Titel trug', () => {
    // Der Parser setzt `title: ''`, wenn die Zeile den Titel nicht nennt (`lib/nightRunLog.ts`).
    // Geprueft wird die Kopfzeile, nicht der ganze Text: Der Auszug nennt die Nummer ebenfalls.
    const text = buildHandoffText(paket({ title: '' }))

    expect(text?.split('\n')[0]).toMatch(/#700$/)
  })
})

describe('buildHandoffText — kein Befund, kein Text', () => {
  it('erzeugt zu einem gruenen Arbeitspaket keinen Text', () => {
    expect(buildHandoffText(paket({ state: 'GREEN', errorClass: undefined }))).toBeNull()
  })

  it('erzeugt zu einem grauen Arbeitspaket keinen Text', () => {
    // Grau traegt eine Fehlerklasse (offene Abhaengigkeit) und ist trotzdem kein Befund.
    expect(buildHandoffText(paket({ state: 'GREY', errorClass: 'DEPENDENCY_UNMET' }))).toBeNull()
  })
})

describe('buildHandoffText — fehlende Angaben', () => {
  it('erzeugt den Text auch ohne Auszug, ohne `undefined` und ohne leeren Block', () => {
    const text = buildHandoffText(paket({ excerpt: undefined }))

    expect(text).toContain('#700')
    expect(text).toContain('gescheitert')
    expect(text).toContain('Harter Abbruch')
    expect(text).not.toContain('undefined')
    expect(text?.split('\n').filter((zeile) => zeile.trim() === '')).toHaveLength(0)
  })

  it('laesst die Fehlerklasse weg, wenn der Lauf keine nennt', () => {
    const text = buildHandoffText(paket({ errorClass: undefined }))

    expect(text).toContain('gescheitert')
    expect(text).not.toContain('Fehlerklasse')
    expect(text).not.toContain('undefined')
  })
})

describe('buildHandoffText — Rohprotokoll', () => {
  it('haengt an ein rotes Arbeitspaket den Abschnitt samt Rohzeilen an', () => {
    const text = buildHandoffText(paket({ rawLines: ROHZEILEN }))

    expect(text).toContain('Rohprotokoll:')
    for (const roh of ROHZEILEN) {
      expect(text).toContain(roh)
    }
  })

  it('haengt den Abschnitt auch an ein gelbes Arbeitspaket an', () => {
    const text = buildHandoffText(
      paket({ state: 'YELLOW', errorClass: 'CHECKS_RED', excerpt: AUSZUG_GELB, rawLines: ROHZEILEN }),
    )

    expect(text).toContain('Rohprotokoll:')
    expect(text).toContain(ROHZEILEN[0])
  })

  it('setzt den Abschnitt ohne Leerzeile hinter die letzte bisherige Zeile ans Ende', () => {
    const ohne = buildHandoffText(paket())
    const zeilen = buildHandoffText(paket({ rawLines: ROHZEILEN }))?.split('\n') ?? []
    const grenze = ohne?.split('\n').length ?? 0

    expect(zeilen.slice(0, grenze)).toEqual(ohne?.split('\n'))
    expect(zeilen[grenze]).toBe('Rohprotokoll:')
    expect(zeilen.slice(grenze + 1)).toEqual(ROHZEILEN)
  })

  it('uebernimmt die Rohzeilen woertlich, ohne Trim und ohne Filterung', () => {
    const roh = ['      eingerueckt und ungetrimmt', '', '   noch eine Zeile   ']

    const zeilen = buildHandoffText(paket({ rawLines: roh }))?.split('\n') ?? []

    expect(zeilen.slice(zeilen.indexOf('Rohprotokoll:') + 1)).toEqual(roh)
  })

  it('erzeugt keinen Abschnitt, wenn die Rohzeilen leer sind', () => {
    expect(buildHandoffText(paket({ rawLines: [] }))).toBe(buildHandoffText(paket()))
  })

  it('erzeugt keinen Abschnitt, wenn das Arbeitspaket keine Rohzeilen traegt', () => {
    expect(buildHandoffText(paket({ rawLines: undefined }))).not.toContain('Rohprotokoll:')
  })

  it('liefert zu einem gruenen Arbeitspaket auch mit Rohzeilen keinen Text', () => {
    expect(
      buildHandoffText(paket({ state: 'GREEN', errorClass: undefined, rawLines: ROHZEILEN })),
    ).toBeNull()
  })

  it('liefert zu einem grauen Arbeitspaket auch mit Rohzeilen keinen Text', () => {
    expect(
      buildHandoffText(paket({ state: 'GREY', errorClass: 'DEPENDENCY_UNMET', rawLines: ROHZEILEN })),
    ).toBeNull()
  })
})

describe('buildHandoffText — Reinheit', () => {
  it('liefert zu gleicher Eingabe denselben Text', () => {
    expect(buildHandoffText(paket())).toBe(buildHandoffText(paket()))
  })

  it('greift weder auf das Datum noch auf den Zufall zu', () => {
    const jetzt = vi.spyOn(Date, 'now')
    const zufall = vi.spyOn(Math, 'random')

    buildHandoffText(paket())

    expect(jetzt).not.toHaveBeenCalled()
    expect(zufall).not.toHaveBeenCalled()
    jetzt.mockRestore()
    zufall.mockRestore()
  })
})
