import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { dashboardApi, type BoardDashboardKpis } from '../api/dashboard'
import { projectsApi } from '../api/projects'
import { DashboardPage } from './DashboardPage'

// Charts als schlanke Stubs — jsdom kann SVG-Größen nicht messen; hier zählt die Seitenlogik.
vi.mock('@mui/x-charts/LineChart', () => ({
  LineChart: ({ series }: { series: { data: number[] }[] }) => (
    <div data-testid="line-chart">{series[0].data.join(',')}</div>
  ),
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
  throughput: [
    { weekStart: '2026-06-01T00:00:00Z', doneCount: 2 },
    { weekStart: '2026-06-08T00:00:00Z', doneCount: 5 },
  ],
  avgLeadTimeSeconds: 2 * 86_400 + 3 * 3600,
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

  it('zeigt Lead/Cycle-Time-Kacheln (Cycle null als n. v.)', async () => {
    renderPage()
    expect(await screen.findByText('2 T 3 Std')).toBeInTheDocument()
    // Zweimal „n. v.“: die Cycle Time ohne Datenbasis und die Spalte „Done“ ohne Messung.
    expect(screen.getAllByText('n. v.')).toHaveLength(2)
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
    expect(await screen.findByTestId('line-chart')).toHaveTextContent('2,5')
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
