import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { de } from '../i18n/de'
import { codeFehlermeldung } from './codeFehlermeldung'

describe('codeFehlermeldung', () => {
  it('nennt bei 429 die Begründung des Servers', () => {
    const e = new ApiError(429, 'Too Many Requests', undefined, 'Bitte in 15 Minuten erneut versuchen.')
    expect(codeFehlermeldung(e, de)).toBe('Bitte in 15 Minuten erneut versuchen.')
  })

  it('fällt bei 429 ohne Begründung auf den allgemeinen Hinweis zurück', () => {
    expect(codeFehlermeldung(new ApiError(429, ''), de)).toBe(de.AUTH.ZU_VIELE_VERSUCHE)
  })

  it('meldet bei 401 den ungültigen Code, auch wenn der Server etwas anderes schreibt', () => {
    expect(codeFehlermeldung(new ApiError(401, 'x', undefined, 'Detail'), de)).toBe(de.AUTH.CODE_UNGUELTIG)
  })

  it('nennt bei 403 die konkrete Meldung des Servers', () => {
    expect(codeFehlermeldung(new ApiError(403, 'x', undefined, 'Konto gesperrt'), de)).toBe('Konto gesperrt')
  })

  it('fällt bei anderen Fehlern auf die allgemeine Meldung zurück', () => {
    expect(codeFehlermeldung(new Error('network down'), de)).toBe(de.AUTH.ANMELDUNG_FEHLGESCHLAGEN)
  })
})
