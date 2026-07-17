import { describe, expect, it } from 'vitest'
import { normalizeTaskLists, toggleTaskAt } from './markdownTasks'

describe('normalizeTaskLists', () => {
  it('wandelt nackte [ ] am Zeilenanfang in Task-Items um', () => {
    expect(normalizeTaskLists('[ ] Aufgabe eins')).toBe('- [ ] Aufgabe eins')
    expect(normalizeTaskLists('[x] erledigt')).toBe('- [x] erledigt')
  })

  it('lässt bereits vorhandene Listenmarker unverändert', () => {
    expect(normalizeTaskLists('- [ ] schon Liste')).toBe('- [ ] schon Liste')
    expect(normalizeTaskLists('1. [ ] nummeriert')).toBe('1. [ ] nummeriert')
  })

  it('erhält die Einrückung', () => {
    expect(normalizeTaskLists('  [ ] eingerückt')).toBe('  - [ ] eingerückt')
  })

  it('lässt [ ] mitten im Fließtext unangetastet', () => {
    expect(normalizeTaskLists('Text mit [ ] darin')).toBe('Text mit [ ] darin')
  })

  it('rührt Marker in Code-Fences nicht an', () => {
    const md = '```\n[ ] kein Task\n```'
    expect(normalizeTaskLists(md)).toBe(md)
  })

  it('kanonisiert Marker mit abweichender Leerzeichen-Zahl bei Listen-Items', () => {
    expect(normalizeTaskLists('- [  ] doppelt')).toBe('- [ ] doppelt')
    expect(normalizeTaskLists('- [] leer')).toBe('- [ ] leer')
    expect(normalizeTaskLists('- [ x ] umrahmt')).toBe('- [x] umrahmt')
    expect(normalizeTaskLists('- [X] gross')).toBe('- [x] gross')
    expect(normalizeTaskLists('- [X]y')).toBe('- [x] y')
  })

  it('kanonisiert auch nackte Marker mit Varianten', () => {
    expect(normalizeTaskLists('[  ] doppelt')).toBe('- [ ] doppelt')
    expect(normalizeTaskLists('[] leer')).toBe('- [ ] leer')
  })

  it('rendert benachbarte Zeilen mit unterschiedlicher Leerzeichen-Zahl beide als Task', () => {
    expect(normalizeTaskLists('- [ ] a\n- [  ] b')).toBe('- [ ] a\n- [ ] b')
  })

  it('lässt nackte Klammern ohne folgenden Whitespace (Fließtext) unangetastet', () => {
    expect(normalizeTaskLists('[x]foo bar')).toBe('[x]foo bar')
  })

  // Regression S8786: `[` + sehr viele Leerzeichen ohne schließendes `]` löste beim alten
  // Marker-Muster `\[\s*[xX]?\s*\]` super-lineares Backtracking aus (zwei benachbarte `\s*`).
  // Mit dem verankerten Muster `\[\s*(?:[xX]\s*)?\]` läuft es linear; die Zeile bleibt (kein
  // gültiger Marker) unverändert. Timeout des Tests wäre der Regressionsindikator.
  it('verarbeitet pathologische Marker-Eingaben ohne katastrophales Backtracking', () => {
    const nakedish = `[${' '.repeat(50_000)}`
    expect(normalizeTaskLists(nakedish)).toBe(nakedish)

    const listedish = `-  [${' '.repeat(50_000)}`
    expect(normalizeTaskLists(listedish)).toBe(listedish)

    // Gegenprobe: ein gültiger Marker mit vielen Leerzeichen wird weiterhin korrekt kanonisiert.
    expect(normalizeTaskLists(`[${' '.repeat(500)}] tief eingerückt`)).toBe('- [ ] tief eingerückt')
  })
})

describe('toggleTaskAt', () => {
  it('schaltet die n-te Checkbox um', () => {
    const md = '[ ] eins\n[ ] zwei'
    expect(toggleTaskAt(md, 0)).toBe('[x] eins\n[ ] zwei')
    expect(toggleTaskAt(md, 1)).toBe('[ ] eins\n[x] zwei')
  })

  it('schaltet [x] zurück auf [ ]', () => {
    expect(toggleTaskAt('- [x] fertig', 0)).toBe('- [ ] fertig')
  })

  it('zählt gemischte Marker (mit/ohne Liste) in Dokumentreihenfolge', () => {
    const md = '- [ ] a\n1. [ ] b\n[ ] c'
    expect(toggleTaskAt(md, 2)).toBe('- [ ] a\n1. [ ] b\n[x] c')
  })

  it('flippt nur die Checkbox, nicht Klammern im Task-Text', () => {
    expect(toggleTaskAt('- [ ] siehe [x] oben', 0)).toBe('- [x] siehe [x] oben')
  })

  it('überspringt Checkboxen in Code-Fences', () => {
    const md = '```\n[ ] ignoriert\n```\n[ ] echt'
    expect(toggleTaskAt(md, 0)).toBe('```\n[ ] ignoriert\n```\n[x] echt')
  })

  it('lässt den Text bei ungültigem Index unverändert', () => {
    expect(toggleTaskAt('[ ] eins', 5)).toBe('[ ] eins')
  })

  it('zählt und flippt Marker mit abweichender Leerzeichen-Zahl konsistent zum Rendern', () => {
    // Gerendert (normalizeTaskLists) sind beides Checkboxen an Index 0 und 1; ein Klick auf die
    // zweite (Index 1) muss genau die zweite Zeile treffen, obwohl sie zwei Leerzeichen hat.
    const md = '- [ ] a\n- [  ] b'
    expect(toggleTaskAt(md, 1)).toBe('- [ ] a\n- [x] b')
  })

  it('flippt leere und umrahmte Marker kanonisch', () => {
    expect(toggleTaskAt('- [] x', 0)).toBe('- [x] x')
    expect(toggleTaskAt('- [ x ] y', 0)).toBe('- [ ] y')
  })
})
