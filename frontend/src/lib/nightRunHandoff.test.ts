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
