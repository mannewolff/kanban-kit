import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MetricTile } from './MetricTile'
import { EPIC_EDGE_WIDTH } from '../theme'

describe('MetricTile', () => {
  it('zeigt Beschriftung und bereits formatierten Wert', () => {
    render(<MetricTile label="Review" value="2 T 6 Std" />)
    expect(screen.getByText('Review')).toBeInTheDocument()
    expect(screen.getByText('2 T 6 Std')).toBeInTheDocument()
  })

  it('nennt die Stichprobengröße im Plural', () => {
    render(<MetricTile label="Review" value="8 Min" sample={34} />)
    expect(screen.getByText('34 Messungen')).toBeInTheDocument()
  })

  it('nennt eine einzelne Messung im Singular', () => {
    render(<MetricTile label="Review" value="8 Min" sample={1} />)
    expect(screen.getByText('1 Messung')).toBeInTheDocument()
  })

  it('zeigt ohne Stichprobenangabe keine Messungszeile', () => {
    render(<MetricTile label="Review" value="8 Min" />)
    expect(screen.queryByText(/Messung/)).not.toBeInTheDocument()
  })

  it('weist einen Wert ohne Datenbasis als „keine Messung“ aus statt als Zahl', () => {
    render(<MetricTile label="Done" value="n. v." sample={0} />)
    expect(screen.getByText('n. v.')).toBeInTheDocument()
    expect(screen.getByText('keine Messung')).toBeInTheDocument()
    expect(screen.queryByText('0 Messungen')).not.toBeInTheDocument()
  })

  it('nimmt einen Wert ohne Datenbasis auch optisch zurück', () => {
    render(<MetricTile label="Done" value="n. v." sample={0} />)
    expect(screen.getByText('n. v.')).toHaveStyle({ fontWeight: '400' })
  })

  it('zeigt einen gemessenen Wert hervorgehoben', () => {
    render(<MetricTile label="Review" value="8 Min" sample={4} />)
    expect(screen.getByText('8 Min')).toHaveStyle({ fontWeight: '700' })
  })

  it('nennt den Grund der Hervorhebung im Text, nicht nur in der Farbe', () => {
    render(<MetricTile label="Review" value="2 T 6 Std" sample={4} emphasis="längste Spalte" />)
    expect(screen.getByText('längste Spalte')).toBeInTheDocument()
  })

  it('hebt über die linke Kante hervor statt über eine getönte Fläche', () => {
    // E3/E7 (#651): Die Fläche bleibt weiß, die Betonung sitzt an der Kante.
    render(<MetricTile label="Review" value="2 T 6 Std" sample={4} emphasis="längste Spalte" />)

    const kachel = screen.getByText((_content, element) =>
      element?.classList.contains('MuiPaper-root') === true)
    expect(kachel).toHaveStyle({ borderLeftWidth: `${EPIC_EDGE_WIDTH}px` })
    // action.hover des Default-Themes; die Kachel darf keine Flächentönung mehr tragen.
    expect(kachel).not.toHaveStyle({ backgroundColor: 'rgba(0, 0, 0, 0.04)' })
  })

  it('lässt die linke Kante ohne Hervorhebung eine gewöhnliche Haarlinie', () => {
    render(<MetricTile label="Review" value="8 Min" sample={4} />)

    const kachel = screen.getByText((_content, element) =>
      element?.classList.contains('MuiPaper-root') === true)
    expect(kachel).not.toHaveStyle({ borderLeftWidth: `${EPIC_EDGE_WIDTH}px` })
  })
})
