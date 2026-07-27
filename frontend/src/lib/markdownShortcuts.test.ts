import { describe, expect, it } from 'vitest'
import { expandCheckboxShortcut } from './markdownShortcuts'

describe('expandCheckboxShortcut', () => {
  it('ersetzt Slash plus Leerzeichen am Zeilenanfang durch eine leere Checkbox', () => {
    expect(expandCheckboxShortcut('/ ', 2)).toEqual({ value: '- [ ] ', caret: 6, from: 2 })
  })

  it('ersetzt /x plus Leerzeichen durch eine abgehakte Checkbox', () => {
    expect(expandCheckboxShortcut('/x ', 3)).toEqual({ value: '- [x] ', caret: 6, from: 3 })
  })

  it('behandelt großes X wie kleines x', () => {
    expect(expandCheckboxShortcut('/X ', 3)).toEqual({ value: '- [x] ', caret: 6, from: 3 })
  })

  it('erhält die Einrückung', () => {
    expect(expandCheckboxShortcut('  / ', 4)).toEqual({ value: '  - [ ] ', caret: 8, from: 4 })
  })

  it('greift auch in einer späteren Zeile und lässt die vorigen Zeilen unberührt', () => {
    expect(expandCheckboxShortcut('Text\n/ ', 7)).toEqual({ value: 'Text\n- [ ] ', caret: 11, from: 7 })
  })

  it('erhält den Text hinter dem Cursor', () => {
    expect(expandCheckboxShortcut('/ Rest', 2)).toEqual({ value: '- [ ] Rest', caret: 6, from: 2 })
  })

  it('greift nicht ohne folgendes Leerzeichen', () => {
    expect(expandCheckboxShortcut('/xyz', 4)).toBeNull()
  })

  it('greift nicht bei einem Pfad am Zeilenanfang', () => {
    expect(expandCheckboxShortcut('/api/cards ', 11)).toBeNull()
  })

  it('greift nicht mitten in der Zeile', () => {
    expect(expandCheckboxShortcut('und / ', 6)).toBeNull()
  })

  it('greift nicht bei zwei Leerzeichen hinter dem Slash', () => {
    expect(expandCheckboxShortcut('/  ', 3)).toBeNull()
  })

  it('greift nicht bei Cursor an Position 0', () => {
    expect(expandCheckboxShortcut('/ ', 0)).toBeNull()
  })

  it('greift nicht bei leerem Text', () => {
    expect(expandCheckboxShortcut('', 0)).toBeNull()
  })

  it('greift nicht, wenn die Cursor-Position unbekannt ist', () => {
    expect(expandCheckboxShortcut('/ ', null)).toBeNull()
  })

  it('greift nicht, wenn der Cursor vor dem Leerzeichen steht', () => {
    expect(expandCheckboxShortcut('/ ', 1)).toBeNull()
  })
})
