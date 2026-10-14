import { describe, expect, it } from 'vitest'
import { de } from '../i18n/de'
import { en } from '../i18n/en'
import { stufenname, verstummtSatz } from './runnerJetzt'

/** Stufennamen und der Satz für „verstummt“ (Issue #1729, #1730). */
describe('runnerJetzt', () => {
  it('übersetzt eine bekannte Stufe und zeigt eine unbekannte wörtlich', () => {
    expect(stufenname(de.RUNNER_JETZT, 'VORBEREITUNG')).toBe('Vorbereitung')
    expect(stufenname(en.RUNNER_JETZT, 'UMSETZUNG')).toBe('Implementation')
    expect(stufenname(de.RUNNER_JETZT, 'GANZ_NEU')).toBe('GANZ_NEU')
  })

  it('bildet den Satz für „verstummt“ mit und ohne Stufe', () => {
    expect(verstummtSatz(de.RUNNER_JETZT, '14.10. 01:05', 'UMSETZUNG')).toBe(
      'Runner verstummt seit 14.10. 01:05 (letzte Stufe: Umsetzung), vermutlich abgebrochen; die Umsetzung gilt womöglich noch als belegt.',
    )
    expect(verstummtSatz(de.RUNNER_JETZT, '14.10. 01:05', null)).toBe(
      'Runner verstummt seit 14.10. 01:05 (letzte Stufe: nicht gemeldet), vermutlich abgebrochen; die Umsetzung gilt womöglich noch als belegt.',
    )
  })
})
