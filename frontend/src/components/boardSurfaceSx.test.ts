import { describe, expect, it } from 'vitest'
import { AUSWAHL, CARD_LIFT, CARD_RADIUS, CARD_SHADOW, CARD_SHADOW_HOVER, KUPFER_SCHIMMER, RAND, SCHATTEN_NUTE } from '../theme'
import { ablageflaecheSx, karteDichteSx, karteSx, PLATZHALTER_SX } from './boardSurfaceSx'

describe('karteSx (#980)', () => {
  it('legt die Karte als Platte mit Haarlinie, Verlauf und Schatten an', () => {
    const sx = karteSx()
    expect(sx.border).toBe(`1px solid ${RAND}`)
    expect(sx.background).toContain('linear-gradient(180deg')
    expect(sx.boxShadow).toBe(CARD_SHADOW)
    expect(sx.borderRadius).toBe(`${CARD_RADIUS}px`)
  })

  it('trägt keine farbige Status-Kante mehr', () => {
    const sx = karteSx()
    expect(sx).not.toHaveProperty('borderLeft')
    expect(sx).not.toHaveProperty('borderTop')
  })

  it('hebt die Karte unter dem Zeiger an und öffnet den Schatten, ohne die Kanten anzufassen', () => {
    const hover = karteSx()['&:hover'] as Record<string, unknown>
    expect(hover).toEqual({ boxShadow: CARD_SHADOW_HOVER, transform: `translateY(${CARD_LIFT}px)` })
  })

  it('tönt eine gewählte Karte kupfern', () => {
    const sx = karteSx({ gewaehlt: true })
    expect(sx.background).toBe(AUSWAHL)
    expect(sx.border).toContain('color-mix')
  })

  it('führt keinen eigenen Bewegungsvorbehalt neben der zentralen Regel', () => {
    expect(Object.keys(karteSx()).filter((key) => key.includes('prefers-reduced-motion'))).toEqual([])
  })

  it('lässt an der Stelle einer gezogenen Karte die Vertiefung stehen', () => {
    const sx = karteSx({ bewegt: true })
    expect(sx).toMatchObject(PLATZHALTER_SX)
    expect(sx['& > *']).toEqual({ visibility: 'hidden' })
    expect(sx.boxShadow).toBe(SCHATTEN_NUTE)
    expect(String(sx.border)).toContain('dashed')
    expect(karteSx()).not.toHaveProperty('& > *')
  })
})

describe('karteDichteSx (#1056)', () => {
  it('gibt der normalen Dichte die weiten Abstände der Platte', () => {
    expect(karteDichteSx('normal')).toEqual({ gap: '7px', px: '11px', py: '10px' })
  })

  it('rückt die kompakte Karte in jedem Maß enger zusammen', () => {
    expect(karteDichteSx('kompakt')).toEqual({ gap: '4px', px: '9px', py: '6px' })
  })

  it('bleibt kompakt in jedem Maß kleiner als normal — die Dichte wirkt in eine Richtung', () => {
    const normal = karteDichteSx('normal')
    const kompakt = karteDichteSx('kompakt')
    const px = (wert: string) => Number.parseFloat(wert)
    expect(px(kompakt.gap)).toBeLessThan(px(normal.gap))
    expect(px(kompakt.px)).toBeLessThan(px(normal.px))
    expect(px(kompakt.py)).toBeLessThan(px(normal.py))
  })
})

describe('ablageflaecheSx', () => {
  it('zeigt die Ablagefläche als gestrichelten Kupferrahmen auf Schimmer', () => {
    expect(ablageflaecheSx(true)).toEqual({
      outline: '2px dashed',
      outlineColor: 'primary.main',
      outlineOffset: '-4px',
      bgcolor: KUPFER_SCHIMMER,
      borderRadius: `${CARD_RADIUS}px`,
    })
  })

  it('trägt außerhalb eines Ziehvorgangs nichts', () => {
    expect(ablageflaecheSx(false)).toEqual({})
  })
})
