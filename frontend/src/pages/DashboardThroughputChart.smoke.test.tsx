import { render, screen } from '@testing-library/react'
import { LineChart } from '@mui/x-charts/LineChart'
import { describe, expect, it, vi } from 'vitest'
import { makeThroughputMark } from './DashboardPage'

// Smoke-Test gegen die ECHTE Chart-Bibliothek (Issue #514, H-478.1): Die übrigen Dashboard-Tests
// stubben @mui/x-charts komplett — ein Major-Upgrade, das den mark-Slot oder die Legenden-Config
// umbaut, machte sie nicht rot, sondern änderte still das Verhalten. Dieser Test rendert die
// LineChart mit exakt den Props der ThroughputSection und bricht, wenn die Bibliothek den
// mark-Slot (und damit Punkte und Wert-Labels) nicht mehr wie vereinbart rendert.

// jsdom hat keinen ResizeObserver; mit fester width/height misst die Chart nichts nach,
// braucht den Observer aber trotzdem beim Mount.
class ResizeObserverStub {
  observe(): void {
    // bewusst leer: feste width/height, es gibt nichts zu beobachten.
  }
  unobserve(): void {
    // bewusst leer, siehe observe().
  }
  disconnect(): void {
    // bewusst leer, siehe observe().
  }
}
vi.stubGlobal('ResizeObserver', ResizeObserverStub)

describe('Durchsatz-Chart gegen die echte @mui/x-charts-LineChart', () => {
  const counts = [2, 7, 3]

  function renderChart(): void {
    render(
      <LineChart
        width={600}
        height={260}
        skipAnimation
        xAxis={[{ scaleType: 'point', data: ['01.06.26', '08.06.26', '15.06.26'] }]}
        series={[{ data: [...counts], label: 'Fertig' }]}
        slots={{ mark: makeThroughputMark(counts) }}
        slotProps={{ legend: { hidden: true } }}
      />,
    )
  }

  it('rendert die Wert-Labels der beschrifteten Wochen ins SVG', () => {
    renderChart()
    const labels = screen.getAllByTestId('throughput-value')
    expect(labels.map((l) => l.textContent)).toEqual(['2', '7', '3'])
  })

  it('ruft den mark-Slot mit dem Datenpunkt-Index auf (Punkte liegen im DOM)', () => {
    renderChart()
    // Ein Label je Datenpunkt beweist: MarkPlot reicht dataIndex und Koordinaten an den Slot.
    expect(screen.getAllByTestId('throughput-value')).toHaveLength(counts.length)
  })

  it('zeigt keine Legende, wenn sie per slotProps versteckt ist', () => {
    renderChart()
    expect(screen.queryByText('Fertig')).not.toBeInTheDocument()
  })
})
