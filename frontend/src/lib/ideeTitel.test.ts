import { describe, expect, it } from 'vitest'
import type { BoardColumn } from '../api/boards'
import { IDEE_TEXT_GRENZE, ideeTitel, ideeZuLang, ideenSpalte } from './ideeTitel'

const spalte = (id: number, name: string, position: number): BoardColumn => ({ id, name, position, wipLimit: null })

describe('ideeTitel', () => {
  it('setzt das Präfix [Idea] vor den Text und schneidet Leerraum ab', () => {
    expect(ideeTitel('  Kaffee für alle \n')).toBe('[Idea] Kaffee für alle')
  })
})

describe('ideeZuLang', () => {
  it('lässt genau 293 Zeichen Text zu, damit der Titel samt Präfix bei 300 bleibt', () => {
    expect(IDEE_TEXT_GRENZE).toBe(293)
    expect(ideeZuLang('x'.repeat(293))).toBe(false)
    expect(ideeTitel('x'.repeat(293))).toHaveLength(300)
  })

  it('meldet 294 Zeichen als zu lang', () => {
    expect(ideeZuLang('x'.repeat(294))).toBe(true)
  })

  it('zählt Leerraum am Rand nicht mit', () => {
    expect(ideeZuLang(`  ${'x'.repeat(293)}  `)).toBe(false)
  })
})

describe('ideenSpalte', () => {
  it('wählt die Spalte, die nach ihrem Namen Backlog ist, unabhängig von der Reihenfolge', () => {
    const spalten = [spalte(1, 'Ready', 1), spalte(2, 'Back-Log', 2), spalte(3, 'Eingang', 0)]
    expect(ideenSpalte(spalten)?.id).toBe(2)
  })

  it('fällt ohne Backlog-Spalte auf die erste Spalte nach Position zurück', () => {
    const spalten = [spalte(1, 'Ready', 1), spalte(3, 'Eingang', 0)]
    expect(ideenSpalte(spalten)?.id).toBe(3)
  })

  it('liefert ohne Spalten nichts', () => {
    expect(ideenSpalte([])).toBeUndefined()
  })
})
