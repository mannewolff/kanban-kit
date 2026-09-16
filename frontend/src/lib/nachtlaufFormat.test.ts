import { describe, expect, it } from 'vitest'
import { betrag, kosten, menge } from './nachtlaufFormat'

/**
 * Die drei Formatierer der Nachtlauf-Anzeigen (Issue #968). Sie stehen an einer Stelle, weil
 * Auswertung und Karten-Detail dieselben Wörter führen — ein zweites Paar liefe auseinander.
 */
/** `Intl` setzt vor das Währungszeichen ein geschütztes Leerzeichen; verglichen wird der Wortlaut. */
const lesbar = (text: string) => text.replaceAll('\u00a0', ' ')

describe('nachtlaufFormat', () => {
  it('betrag schreibt US-Dollar in deutscher Schreibweise und sagt, wenn nichts angegeben ist', () => {
    expect(lesbar(betrag(25.983293))).toBe('25,98 $')
    expect(betrag(undefined)).toBe('nicht angegeben')
  })

  it('kosten nennt eine fehlende Angabe „nicht gemessen", eine gemessene Null bleibt ein Betrag', () => {
    expect(lesbar(kosten(0.94))).toBe('0,94 $')
    expect(lesbar(kosten(0))).toBe('0,00 $')
    expect(kosten(undefined)).toBe('nicht gemessen')
  })

  it('menge nennt ihre Einheit', () => {
    expect(menge(8_883_160)).toBe('8.883.160 Token')
    expect(menge(0)).toBe('0 Token')
    expect(menge(undefined)).toBe('nicht gemessen')
  })
})
