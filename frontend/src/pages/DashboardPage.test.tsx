import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { dashboardApi, type BoardDashboardKpis } from '../api/dashboard'
import { projectsApi } from '../api/projects'
import { DashboardPage } from './DashboardPage'

// Charts als schlanke Stubs — jsdom kann SVG-Größen nicht messen; hier zählt die Seitenlogik.
vi.mock('@mui/x-charts/BarChart', () => ({
  BarChart: ({ series }: { series: { data: number[] }[] }) => (
    <div data-testid="bar-chart">{series[0].data.join(',')}</div>
  ),
}))
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
    expect(screen.getByText('n. v.')).toBeInTheDocument()
  })

  it('rendert das Balkendiagramm mit Verweildauer in Stunden (null -> 0)', async () => {
    renderPage()
    // 7200 s = 2.0 Std; null -> 0
    expect(await screen.findByTestId('bar-chart')).toHaveTextContent('2,0')
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
})
