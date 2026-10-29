import { describe, expect, it } from 'vitest'
import { kuerzeTitel } from './kuerzeTitel'

describe('kuerzeTitel', () => {
  it('lässt einen Titel mit genau 60 Zeichen unverändert', () => {
    const titel = 'a'.repeat(60)
    expect(kuerzeTitel(titel, 60)).toBe(titel)
  })

  it('lässt einen kürzeren Titel unverändert', () => {
    expect(kuerzeTitel('Kurz', 60)).toBe('Kurz')
  })

  it('lässt einen leeren Titel leer', () => {
    expect(kuerzeTitel('', 60)).toBe('')
  })

  it('schneidet einen längeren Titel nach 60 Zeichen und hängt „…" an', () => {
    const titel = 'b'.repeat(60) + 'c'
    expect(kuerzeTitel(titel, 60)).toBe('b'.repeat(60) + '…')
  })

  it('kürzt einen Titel mit 90 Zeichen auf 60 Zeichen plus „…"', () => {
    const titel = '0123456789'.repeat(9)
    expect(kuerzeTitel(titel, 60)).toBe('0123456789'.repeat(6) + '…')
  })

  it('zählt ein Emoji als ein Zeichen, nicht als zwei', () => {
    // 60 Emoji sind 120 UTF-16-Einheiten, aber 60 Codepunkte — also nicht zu kürzen.
    const titel = '😀'.repeat(60)
    expect(kuerzeTitel(titel, 60)).toBe(titel)
  })

  it('zerteilt an der Schnittstelle kein Ersatzpaar', () => {
    const titel = 'a'.repeat(59) + '😀😀'
    expect(kuerzeTitel(titel, 60)).toBe('a'.repeat(59) + '😀…')
  })

  it('richtet sich nach der übergebenen Höchstlänge', () => {
    expect(kuerzeTitel('abcdef', 3)).toBe('abc…')
  })
})
