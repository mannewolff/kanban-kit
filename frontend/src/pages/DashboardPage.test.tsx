import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { dashboardApi, type BoardDashboardKpis } from '../api/dashboard'
import { projectsApi } from '../api/projects'
import { DashboardPage } from './DashboardPage'

interface MarkStubProps {
  dataIndex: number
  x: number
  y: number
}

interface LineChartStubProps {
  series: { data: number[]; label?: string }[]
  xAxis?: { data?: string[] }[]
  slots: { mark: (props: MarkStubProps) => React.ReactElement }
  slotProps?: { legend?: { hidden?: boolean } }
}

// Charts als schlanke Stubs — jsdom kann SVG-Größen nicht messen; hier zählt die Seitenlogik.
// Der Stub bildet genau den Vertrag ab, auf den sich die Seite verlässt: Achsenbeschriftungen,
// Serienwerte, Sichtbarkeit der Legende und den `mark`-Slot, den MarkPlot je Datenpunkt mit
// dessen Index rendert.
vi.mock('@mui/x-charts/LineChart', () => ({
  LineChart: ({ series, xAxis, slots, slotProps }: LineChartStubProps) => {
    const Mark = slots.mark
    return (
      <div data-testid="line-chart">
        <span data-testid="x-labels">{(xAxis?.[0]?.data ?? []).join(' ')}</span>
        <span data-testid="series-values">{series[0].data.join(',')}</span>
        {slotProps?.legend?.hidden !== true && <span>{series[0].label}</span>}
        <svg>
          {series[0].data.map((_, index) => (
            <Mark key={index} dataIndex={index} x={index * 20} y={40} />
          ))}
        </svg>
      </div>
    )
  },
  MarkElement: ({ x, y }: MarkStubProps) => <circle cx={x} cy={y} r={3} />,
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/dashboard', () => ({ dashboardApi: { get: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))

const mBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const mDashboard = dashboardApi as unknown as { get: ReturnType<typeof vi.fn> }
const mProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }

const kpis: BoardDashboardKpis = {
  columnDwell: [
    { columnId: 1, columnName: 'Ready', avgDwellSeconds: 7200, sampleCount: 3 },
    { columnId: 3, columnName: 'In progress', avgDwellSeconds: 480, sampleCount: 12 },
    { columnId: 2, columnName: 'Done', avgDwellSeconds: null, sampleCount: 0 },
  ],
  // Wochenbeginn bewusst mittags: `weekStart` ist ein Instant, die Anzeige rechnet in die lokale
  // Zone um. Mit 09:00 UTC fällt der Kalendertag in jeder realistischen Testumgebung gleich aus.
  throughput: [
    { weekStart: '2026-06-01T09:00:00Z', doneCount: 2 },
    { weekStart: '2026-06-08T09:00:00Z', doneCount: 5 },
  ],
  avgLeadTimeSeconds: 2 * 86_400 + 3 * 3600,
  leadTimeSampleCount: 5,
  avgCycleTimeSeconds: null,
  outliers: [{ cardId: 9, number: 42, title: 'Hängt fest', columnName: 'Review', dwellSeconds: 700_000 }],
}

function renderPage(path = '/boards/1/dashboard') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/boards/:boardId/dashboard" element={<DashboardPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mBoards.get.mockResolvedValue({ id: 1, projectId: 9, name: 'B', createdAt: '', columns: [] })
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'VIEWER', createdAt: '' }])
    mDashboard.get.mockResolvedValue(kpis)
  })

  it('zeigt den Breadcrumb-Pfad ab Projekte', async () => {
    renderPage()
    expect(await screen.findByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/')
  })

  it('zeigt Lead/Cycle-Time (Cycle null als n. v.)', async () => {
    renderPage()
    expect(await screen.findByText('2 T 3 Std')).toBeInTheDocument()
    // Zweimal „n. v.“: die Cycle Time ohne Datenbasis und die Spalte „Done“ ohne Messung.
    expect(screen.getAllByText('n. v.')).toHaveLength(2)
  })

  it('führt mit der Ø Lead Time als einziger Hero-Zahl', async () => {
    renderPage()
    expect(await screen.findByTestId('hero-metric')).toHaveTextContent('2 T 3 Std')
    expect(screen.getAllByTestId('hero-metric')).toHaveLength(1)
  })

  it('nennt unter der Hero-Zahl ihre Bedeutung und ihre Datenbasis', async () => {
    renderPage()
    expect(await screen.findByText(/Durchschnitt aus 5 abgeschlossenen Karten/)).toBeInTheDocument()
  })

  it('nennt die Datenbasis im Singular, wenn nur eine Karte fertig ist', async () => {
    mDashboard.get.mockResolvedValue({ ...kpis, leadTimeSampleCount: 1 })
    renderPage()
    expect(await screen.findByText(/Durchschnitt aus 1 abgeschlossenen Karte\./)).toBeInTheDocument()
  })

  it('zeigt die Cycle Time als zweite Kennzahl neben der Hero-Zahl, nicht als Hero-Zahl', async () => {
    renderPage()
    const hero = await screen.findByTestId('hero-metric')
    expect(screen.getByText('Ø Cycle Time')).toBeInTheDocument()
    expect(hero).not.toHaveTextContent('Ø Cycle Time')
  })

  it('fällt ohne abgeschlossene Karte auf einen ruhigen Hinweis statt einer Hero-Zahl zurück', async () => {
    mDashboard.get.mockResolvedValue({
      ...kpis,
      avgLeadTimeSeconds: null,
      leadTimeSampleCount: 0,
      avgCycleTimeSeconds: null,
    })
    renderPage()
    expect(await screen.findByText(/Noch keine abgeschlossene Karte/)).toBeInTheDocument()
    expect(screen.queryByTestId('hero-metric')).not.toBeInTheDocument()
    // Nur die Spalte „Done“ darf jetzt noch „n. v.“ zeigen — keine hervorgehobene Leerangabe.
    expect(screen.getAllByText('n. v.')).toHaveLength(1)
  })

  it('zeigt die Verweildauer je Spalte als lesbare Dauer statt als Dezimalstunden', async () => {
    renderPage()
    expect(await screen.findByText('2 Std 0 Min')).toBeInTheDocument()
    expect(screen.getByText('8 Min')).toBeInTheDocument()
    expect(screen.queryByText('2,0')).not.toBeInTheDocument()
  })

  it('zeigt für eine Spalte ohne Datenbasis „keine Messung“ und keine Null', async () => {
    renderPage()
    expect(await screen.findByText('keine Messung')).toBeInTheDocument()
    expect(screen.queryByText('0')).not.toBeInTheDocument()
    expect(screen.queryByText('0 Messungen')).not.toBeInTheDocument()
  })

  it('nennt die Stichprobengröße je Spalte', async () => {
    renderPage()
    expect(await screen.findByText('3 Messungen')).toBeInTheDocument()
    expect(screen.getByText('12 Messungen')).toBeInTheDocument()
  })

  it('markiert die Spalte mit der längsten Verweildauer genau einmal', async () => {
    renderPage()
    expect(await screen.findAllByText('längste Spalte')).toHaveLength(1)
  })

  it('markiert keine Spalte, wenn nirgends gemessen wurde', async () => {
    mDashboard.get.mockResolvedValue({
      ...kpis,
      columnDwell: [
        { columnId: 1, columnName: 'Ready', avgDwellSeconds: null, sampleCount: 0 },
        { columnId: 2, columnName: 'Done', avgDwellSeconds: null, sampleCount: 0 },
      ],
    })
    renderPage()
    expect(await screen.findAllByText('keine Messung')).toHaveLength(2)
    expect(screen.queryByText('längste Spalte')).not.toBeInTheDocument()
  })

  it('rendert das Durchsatz-Liniendiagramm', async () => {
    renderPage()
    expect(await screen.findByTestId('series-values')).toHaveTextContent('2,5')
  })

  it('zeigt die Durchsatzwerte ohne Hover direkt am Diagramm', async () => {
    renderPage()
    const labels = await screen.findAllByTestId('throughput-value')
    expect(labels.map((l) => l.textContent)).toEqual(['2', '5'])
  })

  it('beschriftet bei vielen Wochen nur Anfang, Maximum und Ende', async () => {
    mDashboard.get.mockResolvedValue({
      ...kpis,
      throughput: [
        { weekStart: '2026-06-01T09:00:00Z', doneCount: 2 },
        { weekStart: '2026-06-08T09:00:00Z', doneCount: 1 },
        { weekStart: '2026-06-15T09:00:00Z', doneCount: 7 },
        { weekStart: '2026-06-22T09:00:00Z', doneCount: 3 },
      ],
    })
    renderPage()
    const labels = await screen.findAllByTestId('throughput-value')
    // Die zweite Woche (1) bleibt unbeschriftet: weder Rand noch Maximum.
    expect(labels.map((l) => l.textContent)).toEqual(['2', '7', '3'])
  })

  it('nennt im Wochenlabel auch das Jahr', async () => {
    renderPage()
    const labels = await screen.findByTestId('x-labels')
    expect(labels).toHaveTextContent(/^\d{2}\.\d{2}\.\d{2} \d{2}\.\d{2}\.\d{2}$/)
    expect(labels).toHaveTextContent('01.06.26')
  })

  it('zeigt keine Legende für die einzige Serie', async () => {
    renderPage()
    await screen.findByTestId('line-chart')
    expect(screen.queryByText('Fertig')).not.toBeInTheDocument()
  })

  it('listet den Durchsatz zusätzlich als Tabelle', async () => {
    renderPage()
    const table = await screen.findByRole('table', { name: 'Durchsatz je Woche' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3) // Kopfzeile + zwei Wochen
    expect(within(table).getByText('08.06.26')).toBeInTheDocument()
    expect(within(rows[2]).getByText('5')).toBeInTheDocument()
  })

  it('zeigt ohne Durchsatzdaten einen Hinweis statt eines leeren Achsenkreuzes', async () => {
    mDashboard.get.mockResolvedValue({ ...kpis, throughput: [] })
    renderPage()
    expect(
      await screen.findByText('Noch keine abgeschlossene Karte in den letzten Wochen.'),
    ).toBeInTheDocument()
    expect(screen.queryByTestId('line-chart')).not.toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Durchsatz je Woche' })).not.toBeInTheDocument()
  })

  it('listet Ausreißer-Karten mit formatierter Verweildauer', async () => {
    renderPage()
    expect(await screen.findByText('Hängt fest')).toBeInTheDocument()
    expect(screen.getByText('Review')).toBeInTheDocument()
    expect(screen.getByText('8 T 2 Std')).toBeInTheDocument() // 700000 s
  })

  it('zeigt „Keine Ausreißer." bei leerer Liste', async () => {
    mDashboard.get.mockResolvedValue({ ...kpis, outliers: [] })
    renderPage()
    expect(await screen.findByText('Keine Ausreißer.')).toBeInTheDocument()
  })

  it('zeigt den Projektnamen in der Kopfzeile', async () => {
    renderPage()
    expect(await screen.findByText(/Projekt/)).toBeInTheDocument()
  })

  it('lehnt ungültige Board-IDs ab', () => {
    renderPage('/boards/abc/dashboard')
    expect(screen.getByText('Ungültige Board-ID.')).toBeInTheDocument()
  })

  it('behandelt einen fehlenden Board-Parameter als ungültig (boardId undefined)', () => {
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route path="/dashboard" element={<DashboardPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByText('Ungültige Board-ID.')).toBeInTheDocument()
  })
})
