import { describe, expect, it } from 'vitest'
import { CARD_LIFT, CARD_RADIUS, CARD_SHADOW, CARD_SHADOW_HOVER, STATUS_EDGE_WIDTH } from '../theme'
import { ablageflaecheSx, edgeSurfaceSx } from './boardSurfaceSx'

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

  // Seit #953 steht der Vorbehalt zentral im Theme (`MuiCssBaseline`, Plan #932 E13). Eine eigene
  // Regel hier liefe neben der zentralen her und könnte ihr unbemerkt widersprechen.
  it('führt keinen eigenen Bewegungsvorbehalt neben der zentralen Regel', () => {
    const sx = edgeSurfaceSx({ statusColor: '#2F8C97' })

    expect(Object.keys(sx).filter((key) => key.includes('prefers-reduced-motion'))).toEqual([])
    expect(sx.transition).toBe('box-shadow .2s ease, transform .2s ease')
  })

  it('erlaubt eine abweichende Farbe der Haarlinie, ohne die Status-Kante zu verlieren', () => {
    const sx = edgeSurfaceSx({ statusColor: '#2F8C97', hairlineColor: 'primary.main' })

    expect(sx.borderColor).toBe('primary.main')
    // Die Status-Kante steht nach der Haarlinie und behält deshalb ihre eigene Farbe.
    expect(Object.keys(sx).indexOf('borderColor')).toBeLessThan(Object.keys(sx).indexOf('borderLeft'))
  })
})

// Ziehen (AK 7, AK 8, Plan #932 E15, #956): Board und Ideen-Board sprechen dieselbe Sprache, weil
// beide diese Bausteine nutzen — keine eigene Variante je Ansicht.
describe('edgeSurfaceSx im Ziehzustand', () => {
  const bewegt = () => edgeSurfaceSx({ statusColor: '#2F8C97', bewegt: true })

  it('nimmt die bewegte Karte zurück und zeichnet ihre Kante gestrichelt', () => {
    const sx = bewegt()
    expect(sx.opacity).toBeLessThan(1)
    expect(sx.borderStyle).toBe('dashed')
    expect(sx.boxShadow).toBe('none')
  })

  it('lässt an ihrer Stelle einen Platzhalter derselben Höhe stehen', () => {
    // Der Inhalt wird unsichtbar, nicht entfernt: `visibility` behält die Höhe, `display: none` nicht.
    // Das Element selbst bleibt im Dokument — ein natives Ziehen bräche ab, verschwände seine Quelle.
    expect(bewegt()['& > *']).toEqual({ visibility: 'hidden' })
  })

  it('behält die Status-Kante durchgezogen: die Strichelung gilt der Haarlinie', () => {
    const sx = bewegt()
    expect(Object.keys(sx).indexOf('borderStyle')).toBeLessThan(Object.keys(sx).indexOf('borderLeft'))
    expect(sx.borderLeft).toBe(`${STATUS_EDGE_WIDTH}px solid #2F8C97`)
  })

  it('hebt den Platzhalter unter dem Zeiger nicht an', () => {
    expect(bewegt()['&:hover']).toEqual({ boxShadow: 'none', transform: 'none' })
  })

  it('trägt ohne Ziehen keinen dieser Zustände', () => {
    const sx = edgeSurfaceSx({ statusColor: '#2F8C97' })
    expect(sx).not.toHaveProperty('opacity')
    expect(sx).not.toHaveProperty('borderStyle')
    expect(sx).not.toHaveProperty('& > *')
  })
})

describe('ablageflaecheSx', () => {
  it('zeigt die Ablagefläche als gestrichelten Rahmen in der Primärfarbe auf getönter Fläche', () => {
    expect(ablageflaecheSx(true)).toEqual({
      outline: '2px dashed',
      outlineColor: 'primary.main',
      outlineOffset: '-4px',
      bgcolor: 'action.hover',
      borderRadius: `${CARD_RADIUS}px`,
    })
  })

  it('trägt außerhalb eines Ziehvorgangs nichts', () => {
    expect(ablageflaecheSx(false)).toEqual({})
  })
})
