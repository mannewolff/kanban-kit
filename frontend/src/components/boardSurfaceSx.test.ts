import { describe, expect, it } from 'vitest'
import { EPIC_EDGE_WIDTH, STATUS_EDGE_WIDTH, SURFACE_HOVER_SHADOW } from '../theme'
import { edgeSurfaceSx } from './boardSurfaceSx'

describe('edgeSurfaceSx', () => {
  it('trägt den Status an der Oberkante, in der Breite aus dem Theme', () => {
    expect(edgeSurfaceSx({ statusColor: '#2F8C97' }).borderTop).toBe(`${STATUS_EDGE_WIDTH}px solid #2F8C97`)
  })

  it('trägt die Zugehörigkeit an der linken Kante, in der Breite aus dem Theme', () => {
    expect(edgeSurfaceSx({ statusColor: '#2F8C97', epicColor: '#534AB7' }).borderLeft)
      .toBe(`${EPIC_EDGE_WIDTH}px solid #534AB7`)
  })

  it('lässt die linke Kante weg, wo es keine Zugehörigkeit gibt', () => {
    expect(edgeSurfaceSx({ statusColor: '#2F8C97' })).not.toHaveProperty('borderLeft')
  })

  // Regressionsschutz E10 (#649): Der Hover überschrieb bisher alle vier Ränder und färbte die
  // Vorhaben-Kante kurzzeitig teal. Tiefe entsteht nur über den Schatten, nie über die Kanten.
  it('hebt beim Hover nur die Fläche an und lässt jede Kante in Ruhe', () => {
    const hover = edgeSurfaceSx({ statusColor: '#2F8C97', epicColor: '#534AB7' })['&:hover'] as Record<string, unknown>

    expect(hover).toEqual({ boxShadow: SURFACE_HOVER_SHADOW })
    expect(Object.keys(hover).filter((key) => key.startsWith('border'))).toEqual([])
  })

  it('erlaubt eine abweichende Farbe der Haarlinie, ohne die Status-Oberkante zu verlieren', () => {
    const sx = edgeSurfaceSx({ statusColor: '#2F8C97', hairlineColor: 'primary.main' })

    expect(sx.borderColor).toBe('primary.main')
    // Die Oberkante steht nach der Haarlinie und behält deshalb ihre eigene Farbe.
    expect(Object.keys(sx).indexOf('borderColor')).toBeLessThan(Object.keys(sx).indexOf('borderTop'))
  })
})
