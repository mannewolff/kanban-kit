import { describe, expect, it } from 'vitest'
import { normalizeTaskLists, toggleTaskAt } from './markdownTasks'

describe('normalizeTaskLists', () => {
  it('wandelt nackte [ ] am Zeilenanfang in Task-Items um', () => {
    expect(normalizeTaskLists('[ ] Aufgabe eins')).toBe('- [ ] Aufgabe eins')
    expect(normalizeTaskLists('[x] erledigt')).toBe('- [x] erledigt')
  })

  it('behält Marker ohne nachfolgenden Text ohne angehängtes Leerzeichen', () => {
    // Leerer Body -> Suffix '' (kein „- [ ] " mit Trailing-Space).
    expect(normalizeTaskLists('- [x]')).toBe('- [x]')
    // NAKED verlangt Whitespace nach dem Marker; leerer Body danach -> normalisiert ohne Trailing.
    expect(normalizeTaskLists('[ ] ')).toBe('- [ ]')
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

  // Ab hier: Randfälle der Muster FENCE, LISTED und NAKED (Issue #1340).

  it('öffnet keinen Fence, wenn vor den Backticks Text steht', () => {
    expect(normalizeTaskLists('Text ``` mitten\n[ ] a')).toBe('Text ``` mitten\n- [ ] a')
  })

  it('erkennt eine eingerückte Fence-Zeile als Fence', () => {
    const md = '  ```\n[ ] im Code\n  ```'
    expect(normalizeTaskLists(md)).toBe(md)
  })

  it('kanonisiert eingerückte Listen-Items', () => {
    expect(normalizeTaskLists('  - [  ] a')).toBe('  - [ ] a')
  })

  it('kanonisiert mehrstellig und mit Klammer nummerierte Listen-Items', () => {
    expect(normalizeTaskLists('12. [  ] a')).toBe('12. [ ] a')
    expect(normalizeTaskLists('3) [  ] a')).toBe('3) [ ] a')
  })

  it('erhält mehrere Leerzeichen zwischen Listenmarker und Task-Marker', () => {
    expect(normalizeTaskLists('-   [  ] a')).toBe('-   [ ] a')
  })

  it('lässt Zeilen mit Text vor dem Listenmarker unangetastet', () => {
    expect(normalizeTaskLists('x- [  ] a')).toBe('x- [  ] a')
  })

  it('verliert keinen Text hinter einem Unicode-Zeilentrenner', () => {
    expect(normalizeTaskLists('- [ ] a b')).toContain(' b')
    expect(normalizeTaskLists('[ ] a b')).toContain(' b')
  })

  it('lässt Klammern mit weiteren Zeichen nach dem x unangetastet', () => {
    expect(normalizeTaskLists('[xy] a')).toBe('[xy] a')
  })

  it('reduziert mehrere Leerzeichen nach dem Marker auf eines', () => {
    expect(normalizeTaskLists('- [ ]   a')).toBe('- [ ] a')
    expect(normalizeTaskLists('[ ]   a')).toBe('- [ ] a')
  })

  // Zeilen mit CRLF-Ende (Issue #1341): Das `\r` bleibt erhalten, der Marker wird trotzdem kanonisiert.

  it('kanonisiert Listen-Items in Zeilen mit CRLF-Ende', () => {
    expect(normalizeTaskLists('- [  ] a\r\n- [ ] b')).toBe('- [ ] a\r\n- [ ] b')
    expect(normalizeTaskLists('- [] a\r\n')).toBe('- [ ] a\r\n')
  })

  it('kanonisiert nackte Marker in Zeilen mit CRLF-Ende', () => {
    expect(normalizeTaskLists('[ ] a\r\n[x] b')).toBe('- [ ] a\r\n- [x] b')
  })

  it('kanonisiert einen Marker ohne Text in einer Zeile mit CRLF-Ende', () => {
    expect(normalizeTaskLists('- [  ]\r\nx')).toBe('- [ ]\r\nx')
  })

  it('rührt Marker in Code-Fences mit CRLF-Ende nicht an', () => {
    const md = '```\r\n[ ] kein Task\r\n```'
    expect(normalizeTaskLists(md)).toBe(md)
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

  // Ab hier: Zählweise wie der Renderer (GFM-Parser, Issue #1327) — was als Checkbox erscheint,
  // zählt; was als Code oder Fließtext erscheint, nicht.

  it('überspringt eingerückte Code-Blöcke vor und nach echten Aufgaben', () => {
    const md = 'Vorher\n\n    - [ ] code eins\n\n- [ ] a\n- [ ] b\n\nText\n\n    - [ ] code zwei'
    expect(toggleTaskAt(md, 0)).toBe(
      'Vorher\n\n    - [ ] code eins\n\n- [x] a\n- [ ] b\n\nText\n\n    - [ ] code zwei',
    )
    expect(toggleTaskAt(md, 1)).toBe(
      'Vorher\n\n    - [ ] code eins\n\n- [ ] a\n- [x] b\n\nText\n\n    - [ ] code zwei',
    )
    expect(toggleTaskAt(md, 2)).toBe(md)
  })

  it('zählt und flippt Aufgaben in Zitaten', () => {
    expect(toggleTaskAt('> - [ ] a', 0)).toBe('> - [x] a')
    expect(toggleTaskAt('> > - [x] a', 0)).toBe('> > - [ ] a')
    const md = '- [ ] x\n\n> - [ ] a\n\n> > - [ ] b'
    expect(toggleTaskAt(md, 1)).toBe('- [ ] x\n\n> - [x] a\n\n> > - [ ] b')
    expect(toggleTaskAt(md, 2)).toBe('- [ ] x\n\n> - [ ] a\n\n> > - [x] b')
  })

  it('zählt verschachtelte Listen mit zwei und vier Leerzeichen Einrückung als Aufgaben', () => {
    const md = '- [ ] a\n  - [ ] b\n    - [ ] c\n- [ ] d\n    - [ ] e'
    expect(toggleTaskAt(md, 1)).toBe('- [ ] a\n  - [x] b\n    - [ ] c\n- [ ] d\n    - [ ] e')
    expect(toggleTaskAt(md, 2)).toBe('- [ ] a\n  - [ ] b\n    - [x] c\n- [ ] d\n    - [ ] e')
    expect(toggleTaskAt(md, 4)).toBe('- [ ] a\n  - [ ] b\n    - [ ] c\n- [ ] d\n    - [x] e')
  })

  it('paart Fences nur mit gleichem Zeichen', () => {
    const backticks = '```\n~~~\n- [ ] im Code\n```\n- [ ] echt'
    expect(toggleTaskAt(backticks, 0)).toBe('```\n~~~\n- [ ] im Code\n```\n- [x] echt')
    expect(toggleTaskAt(backticks, 1)).toBe(backticks)
    const tilden = '~~~\n```\n- [ ] im Code\n~~~\n- [ ] echt'
    expect(toggleTaskAt(tilden, 0)).toBe('~~~\n```\n- [ ] im Code\n~~~\n- [x] echt')
  })

  it('flippt nackte Marker an ihrer Startspalte und lässt den Rest der Zeile stehen', () => {
    expect(toggleTaskAt('[ ] siehe [x]', 0)).toBe('[x] siehe [x]')
    expect(toggleTaskAt('  [  ] eingerückt', 0)).toBe('  [x] eingerückt')
    expect(toggleTaskAt('- [ ] a\n- [ ] b\n[ ] c [ ]', 2)).toBe('- [ ] a\n- [ ] b\n[x] c [ ]')
  })

  it('behandelt Tab-Einrückung wie der Renderer', () => {
    expect(toggleTaskAt('\t- [ ] a', 0)).toBe('\t- [ ] a')
    expect(toggleTaskAt('- [ ] a\n\t- [ ] b', 1)).toBe('- [ ] a\n\t- [x] b')
  })

  it('zählt und flippt Marker in Zeilen mit CRLF-Ende und erhält die Zeilenenden', () => {
    const md = '- [  ] a\r\n- [ ] b'
    expect(toggleTaskAt(md, 0)).toBe('- [x] a\r\n- [ ] b')
    expect(toggleTaskAt(md, 1)).toBe('- [  ] a\r\n- [x] b')
  })

  it('zählt Listeneinträge ohne Checkbox nicht mit', () => {
    expect(toggleTaskAt('- normal\n- [ ] a', 0)).toBe('- normal\n- [x] a')
  })

  it('lässt den Text bei einem Index außerhalb des Bereichs unverändert', () => {
    const md = '- [ ] a\n\n> - [ ] b'
    expect(toggleTaskAt(md, 2)).toBe(md)
    expect(toggleTaskAt(md, -1)).toBe(md)
  })
})
