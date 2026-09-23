import { describe, expect, it, vi } from 'vitest'
import type { Verdict } from '../api/nightRuns'
import { NIGHT_RUN_ERROR_CLASSES, type NightRunState } from './nightRunLog'
import {
  buildHandoffText,
  kurzGrund,
  KURZ_GRUND_MAX,
  nightRunZustandsText,
  NIGHT_RUN_STATE_TEXT,
  NIGHT_RUN_VERDICT_TEXT,
  type NightRunHandoffItem,
} from './nightRunHandoff'

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

describe('nightRunZustandsText — Zeitbudget (Issue #856)', () => {
  const GRUND_ZEITBUDGET = 'Zeitbudget review: die Session wurde nach 15.0 min am Limit beendet'

  it('nennt am gelben Vorgang das Zeitbudget als Grund statt „Erfolg, Prüfung rot"', () => {
    expect(nightRunZustandsText('YELLOW', 'TIME_BUDGET_EXCEEDED')).toBe(
      'Am Zeitbudget beendet, Ergebnis liegt vor',
    )
    expect(nightRunZustandsText('YELLOW', 'TIME_BUDGET_EXCEEDED')).not.toContain('Prüfung rot')
  })

  it('nennt am roten Vorgang das Zeitbudget und das fehlende Ergebnis', () => {
    expect(nightRunZustandsText('RED', 'TIME_BUDGET_EXCEEDED')).toBe(
      'Am Zeitbudget beendet, ohne Ergebnis',
    )
  })

  it('traegt den Zeitbudget-Zustandstext in den Uebernahmetext eines gelben Vorgangs', () => {
    const text = buildHandoffText(
      paket({ state: 'YELLOW', errorClass: 'TIME_BUDGET_EXCEEDED', excerpt: GRUND_ZEITBUDGET }),
    )

    expect(text).toContain('Zustand: Am Zeitbudget beendet, Ergebnis liegt vor')
    expect(text).toContain('Fehlerklasse: Zeitbudget erschöpft')
    expect(text).not.toContain('Prüfungen nicht gelaufen')
    expect(text).not.toContain('Erfolg, Prüfung rot')
  })

  it('traegt den Zeitbudget-Zustandstext in den Uebernahmetext eines roten Vorgangs', () => {
    const text = buildHandoffText(
      paket({ state: 'RED', errorClass: 'TIME_BUDGET_EXCEEDED', excerpt: GRUND_ZEITBUDGET }),
    )

    expect(text).toContain('Zustand: Am Zeitbudget beendet, ohne Ergebnis')
    expect(text).toContain('Fehlerklasse: Zeitbudget erschöpft')
    expect(text).not.toContain('Prüfungen nicht gelaufen')
  })
})

describe('nightRunZustandsText — Rueckfall wertgleich (AK 9 aus #842)', () => {
  const ZUSTAENDE: readonly NightRunState[] = ['GREEN', 'YELLOW', 'RED', 'GREY']

  it('liefert ohne Fehlerklasse den Text der Zustandstabelle', () => {
    for (const state of ZUSTAENDE) {
      expect(nightRunZustandsText(state, undefined)).toBe(NIGHT_RUN_STATE_TEXT[state])
    }
  })

  it('liefert fuer jede bestehende Kombination denselben Text wie die Zustandstabelle', () => {
    // Genau ein Paar ist neu; jede andere der 32 Kombinationen muss unveraendert bleiben — sonst
    // saehe ein Bestandslauf nach dieser Aenderung anders aus als vorher.
    for (const state of ZUSTAENDE) {
      for (const errorClass of NIGHT_RUN_ERROR_CLASSES) {
        const neu =
          errorClass === 'TIME_BUDGET_EXCEEDED' && (state === 'YELLOW' || state === 'RED')
        if (neu) continue
        expect(nightRunZustandsText(state, errorClass)).toBe(NIGHT_RUN_STATE_TEXT[state])
      }
    }
  })

  it('laesst den gruenen und den grauen Zeitbudget-Fall auf die Zustandstabelle fallen', () => {
    // Die neue Tabelle traegt nur Gelb und Rot: Ein Vorgang, der am Zeitbudget endete, ist nie
    // gruen, und grau ist er nur als uebergangener Kandidat — dort sagt „nicht bearbeitet" mehr.
    expect(nightRunZustandsText('GREEN', 'TIME_BUDGET_EXCEEDED')).toBe('Erfolg')
    expect(nightRunZustandsText('GREY', 'TIME_BUDGET_EXCEEDED')).toBe('nicht bearbeitet')
  })
})

describe('NIGHT_RUN_VERDICT_TEXT — die Woerter der fuenf Ausgaenge (#1096, #1121)', () => {
  const VERDICTS = ['SUCCEEDED', 'FAILED', 'WAITING', 'RUNNING', 'NO_WORK'] as const satisfies readonly Verdict[]

  it('traegt zu jedem der fuenf Ausgaenge einen nicht leeren Text', () => {
    // Ueber die Schluessel der Tabelle selbst: Ein sechster Eintrag faellt hier auf, statt still
    // mitzulaufen. Ein fehlender bricht schon `tsc` am `Record`.
    expect(Object.keys(NIGHT_RUN_VERDICT_TEXT).toSorted()).toEqual([...VERDICTS].toSorted())
    for (const verdict of VERDICTS) {
      expect(NIGHT_RUN_VERDICT_TEXT[verdict].trim()).not.toBe('')
    }
  })

  // Woertlich, weil Kriterium 11 der fachlichen Quelle (#1086) genau diese Woerter verlangt: Der
  // Ausgang muss ohne Farbwahrnehmung zu lesen sein. `NO_WORK` kam mit Issue #1121 dazu — sein
  // Melder ist dasselbe Grau wie das eines uebergangenen Pakets, den Sinn traegt allein das Wort.
  it('ordnet die Ausgaenge ihren Woertern woertlich zu', () => {
    expect(NIGHT_RUN_VERDICT_TEXT).toEqual({
      SUCCEEDED: 'gelungen',
      FAILED: 'nicht gelungen',
      WAITING: 'mit Vorbehalt',
      RUNNING: 'läuft',
      NO_WORK: 'nichts zu tun',
    })
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

/**
 * Die Kuerzung eines Abbruchgrunds (Issue #1144, Plan #1139 E8). Sie steht in diesem Modul, weil
 * die Stoerzeile des Plattform-Leitstands und die durchgefuehrte Zeile daneben denselben Text
 * kuerzen — zwei Kuerzungen liefen beim naechsten Feinschliff auseinander.
 */
describe('kurzGrund — der Abbruchgrund in einer Zeile (#1144)', () => {
  it('nimmt die erste nicht leere Zeile eines mehrzeiligen Textes', () => {
    expect(kurzGrund('\n   \nHarter Stopp (dirty-tree)\nnaehere Angaben\nund mehr')).toBe(
      'Harter Stopp (dirty-tree)',
    )
  })

  it('laesst einen Text unterhalb der Grenze unveraendert', () => {
    const grund = 'Harter Stopp (dirty-tree)'

    expect(grund.length).toBeLessThan(KURZ_GRUND_MAX)
    expect(kurzGrund(grund)).toBe(grund)
  })

  it('laesst den Text genau auf der Grenze ungekuerzt', () => {
    const grund = 'x'.repeat(KURZ_GRUND_MAX)

    expect(kurzGrund(grund)).toBe(grund)
  })

  it('setzt das Auslassungszeichen genau bei Ueberschreitung', () => {
    const gekuerzt = kurzGrund('x'.repeat(KURZ_GRUND_MAX + 1))

    expect(gekuerzt).toBe(`${'x'.repeat(KURZ_GRUND_MAX - 1)}…`)
    expect(gekuerzt).toHaveLength(KURZ_GRUND_MAX)
  })

  it('gibt zu leerem Text und zu lauter leeren Zeilen einen leeren Text', () => {
    expect(kurzGrund('')).toBe('')
    expect(kurzGrund('\n  \n\t\n')).toBe('')
  })
})
