import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MetricTile } from './MetricTile'

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
})
