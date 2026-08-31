import { describe, expect, it } from 'vitest'
import { CARD_LIFT, CARD_RADIUS, CARD_SHADOW, CARD_SHADOW_HOVER, STATUS_EDGE_WIDTH } from '../theme'
import { edgeSurfaceSx } from './boardSurfaceSx'

describe('edgeSurfaceSx', () => {
  it('trägt den Status an der linken Kante, in der Breite aus dem Theme', () => {
    expect(edgeSurfaceSx({ statusColor: '#2F8C97' }).borderLeft).toBe(`${STATUS_EDGE_WIDTH}px solid #2F8C97`)
  })

  // Der Status stand bis 2026-08-31 oben (Variante „Kante"); der Entwurf, aus dem die
  // Designsprache stammt, trug ihn links. Ohne diese Zusicherung wandert er beim nächsten Umbau
  // stillschweigend zurück.
  it('belegt die Oberkante der Karte nicht — die gehört dem Panel', () => {
    expect(edgeSurfaceSx({ statusColor: '#2F8C97' })).not.toHaveProperty('borderTop')
  })

  it('trägt Tiefe schon im Ruhezustand, nicht erst unter dem Zeiger', () => {
    const sx = edgeSurfaceSx({ statusColor: '#2F8C97' })

    expect(sx.boxShadow).toBe(CARD_SHADOW)
    expect(sx.borderRadius).toBe(`${CARD_RADIUS}px`)
  })

  it('öffnet den Schatten unter dem Zeiger und hebt die Fläche an', () => {
    const hover = edgeSurfaceSx({ statusColor: '#2F8C97' })['&:hover'] as Record<string, unknown>

    expect(hover).toEqual({ boxShadow: CARD_SHADOW_HOVER, transform: `translateY(${CARD_LIFT}px)` })
    // Tiefe entsteht über den Schatten, nie über die Kanten: Ein Hover, der `border*` anfasst,
    // färbte die Status-Kante kurzzeitig um.
    expect(Object.keys(hover).filter((key) => key.startsWith('border'))).toEqual([])
  })

  it('behält bei abgestellter Bewegung die Tiefe und lässt nur das Anheben weg', () => {
    const sx = edgeSurfaceSx({ statusColor: '#2F8C97' })
    const reduced = sx['@media (prefers-reduced-motion: reduce)'] as Record<string, unknown>

    expect(reduced.transition).toBe('none')
    expect(reduced['&:hover']).toEqual({ boxShadow: CARD_SHADOW_HOVER, transform: 'none' })
  })

  it('erlaubt eine abweichende Farbe der Haarlinie, ohne die Status-Kante zu verlieren', () => {
    const sx = edgeSurfaceSx({ statusColor: '#2F8C97', hairlineColor: 'primary.main' })

    expect(sx.borderColor).toBe('primary.main')
    // Die Status-Kante steht nach der Haarlinie und behält deshalb ihre eigene Farbe.
    expect(Object.keys(sx).indexOf('borderColor')).toBeLessThan(Object.keys(sx).indexOf('borderLeft'))
  })
})
