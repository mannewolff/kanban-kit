import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi, type Card, type CardDetail } from '../api/cards'
import { ApiError } from '../api/client'
import { dashboardApi, type BoardDashboardKpis } from '../api/dashboard'
import { nightRunsApi, type NightRunItemView, type NightRunView } from '../api/nightRuns'
import { projectsApi } from '../api/projects'
import { cssRegel } from '../test/cssRegel'
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
vi.mock('../api/cards', () => ({ cardsApi: { get: vi.fn(), list: vi.fn() } }))
vi.mock('../api/dashboard', () => ({ dashboardApi: { get: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/nightRuns', () => ({ nightRunsApi: { list: vi.fn() } }))

// Toast-Weg: useSnackbar liefert im Test einen Spy (statt des No-op-Defaults ohne Provider).
const mNotify = vi.fn()
vi.mock('../components/SnackbarProvider', () => ({ useSnackbar: () => mNotify }))

// Das Detail-Modal ist eigenständig getestet; hier zählt nur, mit welcher Karte es geöffnet wird.
vi.mock('../components/CardDetailModal', () => ({
  CardDetailModal: ({
    card,
    canEdit,
    columnName,
    onClose,
  }: Readonly<{
    card: CardDetail
    canEdit: boolean
    columnName?: string
    onClose: () => void
  }>) => (
    <div data-testid="card-detail">
      <span data-testid="detail-title">{card.title}</span>
      <span data-testid="detail-column">{columnName}</span>
      <span data-testid="detail-can-edit">{String(canEdit)}</span>
      <button type="button" onClick={onClose}>
        Detail schließen
      </button>
    </div>
  ),
}))

const mBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as {
  get: ReturnType<typeof vi.fn>
  list: ReturnType<typeof vi.fn>
}
const mDashboard = dashboardApi as unknown as { get: ReturnType<typeof vi.fn> }
const mProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mNightRuns = nightRunsApi as unknown as { list: ReturnType<typeof vi.fn> }

const outlierCard: Card = {
  derivedFrom: null,
  id: 9,
  boardId: 1,
  columnId: 5,
  number: 42,
  title: 'Hängt fest',
  description: null,
  excerpt: null,
  positionInColumn: 0,
  archived: false,
  ideaStored: false,
  movedToDoneAt: null,
  dependencies: [],
  type: 'CARD',
  parentId: null,
  shortcode: null,
  assignees: [],
  dueDate: null,
  labels: [],
}

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
  avgCycleTimeSeconds: 3600,
  cycleTimeSampleCount: 4,
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
    mCards.get.mockResolvedValue(outlierCard)
    mNightRuns.list.mockResolvedValue([])
  })

  it('zeigt den Breadcrumb-Pfad ab Projekte', async () => {
    renderPage()
    expect(await screen.findByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/')
  })

  it('zeigt Lead und Cycle Time als lesbare Dauern', async () => {
    renderPage()
    expect(await screen.findByText('2 T 3 Std')).toBeInTheDocument()
    expect(screen.getByText('1 Std 0 Min')).toBeInTheDocument()
    // Einmal „n. v.“: nur die Spalte „Done“ ohne Messung.
    expect(screen.getAllByText('n. v.')).toHaveLength(1)
  })

  it('nennt die Datenbasis der Cycle Time neben ihrem Wert', async () => {
    renderPage()
    expect(await screen.findByText('4 Messungen')).toBeInTheDocument()
  })

  it('nimmt die Cycle Time ohne Datenbasis zurück wie eine Kachel ohne Messung', async () => {
    mDashboard.get.mockResolvedValue({ ...kpis, avgCycleTimeSeconds: null, cycleTimeSampleCount: 0 })
    renderPage()
    // Die Hero-Zahl steht weiter, die Cycle Time sagt ausdrücklich, dass ihr die Messung fehlt —
    // dieselbe Aussage wie bei den Spalten-Kacheln, nicht bloß ein zweites „n. v.“.
    expect(await screen.findByTestId('hero-metric')).toHaveTextContent('2 T 3 Std')
    expect(screen.getAllByText('keine Messung')).toHaveLength(2)
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
      cycleTimeSampleCount: 0,
    })
    renderPage()
    expect(await screen.findByText(/Noch keine abgeschlossene Karte —/)).toBeInTheDocument()
    expect(screen.queryByTestId('hero-metric')).not.toBeInTheDocument()
    // Nur die Spalte „Done“ darf jetzt noch „n. v.“ zeigen — keine hervorgehobene Leerangabe.
    expect(screen.getAllByText('n. v.')).toHaveLength(1)
  })

  it('blendet eine vorhandene Cycle Time nicht hinter dem Leer-Hinweis aus', async () => {
    // Nur die Lead Time fehlt: der Hinweis würde sonst eine gemessene Zahl still verschlucken.
    mDashboard.get.mockResolvedValue({
      ...kpis,
      avgLeadTimeSeconds: null,
      leadTimeSampleCount: 0,
    })
    renderPage()
    expect(await screen.findByText('1 Std 0 Min')).toBeInTheDocument()
    expect(screen.queryByText(/Noch keine abgeschlossene Karte —/)).not.toBeInTheDocument()
    // Die leere Hero-Zahl steht zurückgenommen da und behauptet keinen Durchschnitt aus 0 Karten.
    expect(screen.getByTestId('hero-metric')).toHaveTextContent('n. v.')
    expect(screen.getByText(/noch keine abgeschlossene Karte\./)).toBeInTheDocument()
    expect(screen.queryByText(/Durchschnitt aus 0/)).not.toBeInTheDocument()
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

  it('markiert keine Spalte, solange nur eine einzige Spalte gemessen wurde', async () => {
    mDashboard.get.mockResolvedValue({
      ...kpis,
      columnDwell: [
        { columnId: 1, columnName: 'Ready', avgDwellSeconds: 7200, sampleCount: 3 },
        { columnId: 2, columnName: 'Done', avgDwellSeconds: null, sampleCount: 0 },
      ],
    })
    renderPage()
    expect(await screen.findByText('keine Messung')).toBeInTheDocument()
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

  it('blendet die Wert-Labels für Screenreader aus — die Tabelle trägt dieselben Zahlen', async () => {
    renderPage()
    const labels = await screen.findAllByTestId('throughput-value')
    labels.forEach((label) => expect(label).toHaveAttribute('aria-hidden', 'true'))
  })

  it('setzt das Label des Maximums unter den Punkt, die übrigen darüber', async () => {
    mDashboard.get.mockResolvedValue({
      ...kpis,
      throughput: [
        { weekStart: '2026-06-01T09:00:00Z', doneCount: 2 },
        { weekStart: '2026-06-08T09:00:00Z', doneCount: 7 },
        { weekStart: '2026-06-15T09:00:00Z', doneCount: 3 },
      ],
    })
    renderPage()
    const labels = await screen.findAllByTestId('throughput-value')
    // Das Maximum (7) liegt am oberen Plotrand — sein Label weicht nach unten aus. So ragt es
    // nicht aus dem Plot und kollidiert nicht mit dem Label eines benachbarten Randpunkts.
    expect(labels.map((l) => l.textContent)).toEqual(['2', '7', '3'])
    expect(labels[0]).toHaveAttribute('dy', '-10')
    expect(labels[1]).toHaveAttribute('dy', '20')
    expect(labels[2]).toHaveAttribute('dy', '-10')
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

  it('öffnet per Klick auf eine Ausreißer-Zeile die Karte über den Einzelabruf', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Karte 42 öffnen: Hängt fest' }))
    const detail = await screen.findByTestId('card-detail')
    // Genau ein Einzelabruf — die Board-Kartenliste wird nicht mehr geladen (#515).
    expect(mCards.get).toHaveBeenCalledTimes(1)
    expect(mCards.get).toHaveBeenCalledWith(9)
    expect(mCards.list).not.toHaveBeenCalled()
    expect(within(detail).getByTestId('detail-title')).toHaveTextContent('Hängt fest')
    expect(within(detail).getByTestId('detail-column')).toHaveTextContent('Review')
    expect(within(detail).getByTestId('detail-can-edit')).toHaveTextContent('false')
  })

  it('öffnet die Karte auch per Tastatur', async () => {
    const user = userEvent.setup()
    renderPage()
    const trigger = await screen.findByRole('button', { name: 'Karte 42 öffnen: Hängt fest' })
    // `focus()` löst im MUI-Link ein State-Update aus (focus-visible) — deshalb in act().
    act(() => trigger.focus())
    await user.keyboard('{Enter}')
    expect(await screen.findByTestId('card-detail')).toBeInTheDocument()
    // Der gebubbelte Klick des Auslösers darf die Zeile nicht ein zweites Mal auslösen.
    expect(mCards.get).toHaveBeenCalledTimes(1)
  })

  it('öffnet die Karte auch bei einem Klick neben den Auslöser in dieselbe Zeile', async () => {
    const user = userEvent.setup()
    renderPage()
    const table = await screen.findByRole('table', { name: 'Ausreißer-Karten' })
    await user.click(within(table).getByText('Review'))
    expect(await screen.findByTestId('card-detail')).toBeInTheDocument()
  })

  it('meldet eine nicht mehr vorhandene Karte, statt ein leeres Modal zu zeigen', async () => {
    mCards.get.mockRejectedValue(new ApiError(404, 'Not found'))
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Karte 42 öffnen: Hängt fest' }))
    await vi.waitFor(() =>
      expect(mNotify).toHaveBeenCalledWith(expect.stringContaining('Karte 42'), 'warning'),
    )
    expect(screen.queryByTestId('card-detail')).not.toBeInTheDocument()
  })

  it('meldet einen Fehler, wenn die Karte nicht geladen werden kann', async () => {
    mCards.get.mockRejectedValue(new Error('offline'))
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Karte 42 öffnen: Hängt fest' }))
    await vi.waitFor(() =>
      expect(mNotify).toHaveBeenCalledWith('Karte konnte nicht geladen werden.', 'error'),
    )
    expect(screen.queryByTestId('card-detail')).not.toBeInTheDocument()
  })

  it('zeigt während des Ladens ein Zeichen in der geklickten Zeile', async () => {
    let release: (card: Card) => void = () => {}
    mCards.get.mockReturnValue(
      new Promise<Card>((resolve) => {
        release = resolve
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Karte 42 öffnen: Hängt fest' }))
    const table = screen.getByRole('table', { name: 'Ausreißer-Karten' })
    const row = within(table).getAllByRole('row')[1]
    expect(row).toHaveAttribute('aria-busy', 'true')
    expect(within(row).getByRole('progressbar')).toBeInTheDocument()
    release(outlierCard)
    expect(await screen.findByTestId('card-detail')).toBeInTheDocument()
    expect(row).toHaveAttribute('aria-busy', 'false')
  })

  it('ignoriert weitere Klicks, solange eine Karte lädt — kein Doppel-Request', async () => {
    let release: (card: Card) => void = () => {}
    mCards.get.mockReturnValue(
      new Promise<Card>((resolve) => {
        release = resolve
      }),
    )
    const user = userEvent.setup()
    renderPage()
    const trigger = await screen.findByRole('button', { name: 'Karte 42 öffnen: Hängt fest' })
    await user.click(trigger)
    await user.click(trigger)
    expect(mCards.get).toHaveBeenCalledTimes(1)
    release(outlierCard)
    expect(await screen.findByTestId('card-detail')).toBeInTheDocument()
  })

  it('lädt die Karte beim zweiten Öffnen erneut — der Listen-Cache ist entfallen', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Karte 42 öffnen: Hängt fest' }))
    await user.click(await screen.findByRole('button', { name: 'Detail schließen' }))
    await user.click(screen.getByRole('button', { name: 'Karte 42 öffnen: Hängt fest' }))
    expect(await screen.findByTestId('card-detail')).toBeInTheDocument()
    expect(mCards.get).toHaveBeenCalledTimes(2)
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

  // AK 11 (#959): Jede Kennzahl nennt ihren Zeitraum; eine Zahl ohne Datenbasis erscheint nicht als Null.
  it('nennt an jeder Kachel den Zeitraum, auf den sie sich bezieht', async () => {
    renderPage()
    await screen.findByText('Ready')
    // Drei Spalten plus die Cycle Time: vier Kacheln, jede mit ihrem Zeitraum.
    expect(screen.getAllByText('gesamter Verlauf')).toHaveLength(4)
  })

  it('nennt am Durchsatz den Zeitraum von zwölf Wochen', async () => {
    renderPage()
    expect(await screen.findByText(/letzte 12 Wochen/)).toBeInTheDocument()
  })

  it('zeigt bei zwölf Wochen ohne fertige Karte den Hinweis statt einer Nulllinie', async () => {
    // Das Backend liefert immer zwölf Wochen, auch wenn alle leer sind — eine Linie auf null wäre
    // eine Zahl ohne Datenbasis.
    const leer = Array.from({ length: 12 }, (_, i) => ({ weekStart: `2026-06-${String(i + 1).padStart(2, '0')}T09:00:00Z`, doneCount: 0 }))
    mDashboard.get.mockResolvedValue({ ...kpis, throughput: leer })
    renderPage()
    expect(await screen.findByText('Noch keine abgeschlossene Karte in den letzten Wochen.')).toBeInTheDocument()
    expect(screen.queryByTestId('line-chart')).not.toBeInTheDocument()
  })

  it('unterscheidet bei den Ausreißern „keine Datenbasis" von „keine Ausreißer"', async () => {
    mDashboard.get.mockResolvedValue({
      ...kpis,
      outliers: [],
      leadTimeSampleCount: 0,
      avgLeadTimeSeconds: null,
      columnDwell: kpis.columnDwell.map((c) => ({ ...c, avgDwellSeconds: null, sampleCount: 0 })),
    })
    renderPage()
    expect(await screen.findByText('Keine Datenbasis — auf diesem Board wurde noch keine Verweildauer gemessen.')).toBeInTheDocument()
    expect(screen.queryByText('Keine Ausreißer.')).not.toBeInTheDocument()
  })

  it('lässt die große Einzelzahl ohne Tabellenziffern', async () => {
    renderPage()
    expect(cssRegel(await screen.findByTestId('hero-metric'))).not.toContain('tabular-nums')
  })
})

describe('DashboardPage — Stand der letzten Nacht (AK 9, E22, #959)', () => {
  const paket = (cardNumber: number, title: string, state: NightRunItemView['state'], errorClass: NightRunItemView['errorClass'] = null): NightRunItemView => ({
    id: cardNumber, cardNumber, title, state, errorClass, durationMs: 60_000, commitHash: null, excerpt: null,
    usage: { costUsd: 4.2, inputTokens: null, outputTokens: null, cachedInputTokens: null },
  })
  const lauf = (startedAt: string, items: NightRunItemView[], complete = true): NightRunView => ({
    id: Number(startedAt.slice(8, 10)), startedAt, mode: 'CHAIN', durationMs: 3_600_000, processedCount: items.length,
    skippedCount: 0, unparsedCount: 0, unparsedSample: null, createdAt: startedAt, origin: 'TOKEN', tokenName: 'kette',
    complete, updatedAt: null, usage: { costUsd: 12.5, inputTokens: null, outputTokens: null, cachedInputTokens: null }, items,
  })

  beforeEach(() => {
    vi.clearAllMocks()
    mBoards.get.mockResolvedValue({ id: 1, projectId: 9, name: 'B', createdAt: '', columns: [] })
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
    mDashboard.get.mockResolvedValue(kpis)
    mNightRuns.list.mockResolvedValue([])
  })

  it('steht ganz oben, vor den Kennzahlen', async () => {
    mNightRuns.list.mockResolvedValue([lauf('2026-09-16T01:00:00Z', [paket(1, 'A', 'GREEN')])])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    await screen.findByTestId('hero-metric')
    // Vor der Hero-Zahl im Dokument: ohne Rollen sichtbar.
    expect(stand.compareDocumentPosition(screen.getByTestId('hero-metric')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(mNightRuns.list).toHaveBeenCalledWith(9)
  })

  it('nennt einen Abbruch samt Ort: Vorgang, Titel und Fehlerklasse', async () => {
    mNightRuns.list.mockResolvedValue([
      lauf('2026-09-16T01:00:00Z', [
        paket(840, 'Grün', 'GREEN'),
        paket(842, 'Theme trägt zwei Erscheinungsbilder', 'RED', 'HARD_ABORT'),
      ]),
    ])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    expect(await within(stand).findByText(/Abgebrochen/)).toBeInTheDocument()
    const ort = within(stand).getByRole('listitem')
    expect(ort).toHaveTextContent('#842')
    expect(ort).toHaveTextContent('Theme trägt zwei Erscheinungsbilder')
    expect(ort).toHaveTextContent('Harter Abbruch')
    expect(within(stand).queryByText(/#840/)).not.toBeInTheDocument()
  })

  it('nennt auch einen Vorgang mit roter Prüfung als Befund', async () => {
    mNightRuns.list.mockResolvedValue([lauf('2026-09-16T01:00:00Z', [paket(7, 'Gelb', 'YELLOW', 'CHECKS_RED')])])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    expect(await within(stand).findByText(/Abgebrochen/)).toBeInTheDocument()
    expect(within(stand).getByRole('listitem')).toHaveTextContent('#7')
  })

  it('meldet eine durchgelaufene Nacht als durchgelaufen', async () => {
    mNightRuns.list.mockResolvedValue([lauf('2026-09-16T01:00:00Z', [paket(1, 'A', 'GREEN'), paket(2, 'B', 'GREEN'), paket(3, 'C', 'GREY')])])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    expect(await within(stand).findByText('Durchgelaufen — 2 Vorgänge ohne Abbruch.')).toBeInTheDocument()
  })

  it('liest den jüngsten Lauf, nicht den ersten der Liste', async () => {
    mNightRuns.list.mockResolvedValue([
      lauf('2026-09-14T01:00:00Z', [paket(5, 'Alt', 'RED', 'HARD_ABORT')]),
      lauf('2026-09-16T01:00:00Z', [paket(1, 'A', 'GREEN')]),
    ])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    expect(await within(stand).findByText('Durchgelaufen — 1 Vorgang ohne Abbruch.')).toBeInTheDocument()
  })

  it('liest den jüngsten Lauf auch dann, wenn er schon vorne steht', async () => {
    mNightRuns.list.mockResolvedValue([
      lauf('2026-09-16T01:00:00Z', [paket(1, 'A', 'GREEN')]),
      lauf('2026-09-14T01:00:00Z', [paket(5, 'Alt', 'RED', 'HARD_ABORT')]),
    ])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    expect(await within(stand).findByText('Durchgelaufen — 1 Vorgang ohne Abbruch.')).toBeInTheDocument()
  })

  it('nennt einen Befund ohne Fehlerklasse ohne leere Klammer', async () => {
    mNightRuns.list.mockResolvedValue([lauf('2026-09-16T01:00:00Z', [paket(9, 'Ohne Klasse', 'RED')])])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    const ort = await within(stand).findByRole('listitem')
    expect(ort).toHaveTextContent('#9 Ohne Klasse — gescheitert')
    expect(ort).not.toHaveTextContent('(')
  })

  it('sagt ausdrücklich, dass ein Lauf noch nicht abgeschlossen gemeldet ist', async () => {
    mNightRuns.list.mockResolvedValue([lauf('2026-09-16T01:00:00Z', [paket(1, 'A', 'GREEN')], false)])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    expect(await within(stand).findByText('Noch nicht abgeschlossen gemeldet — bisher 1 Vorgang ohne Abbruch.')).toBeInTheDocument()
  })

  it('zeigt ohne aufbewahrten Lauf eine eigene Aussage statt einer Null', async () => {
    mNightRuns.list.mockResolvedValue([])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    expect(await within(stand).findByText('Kein Nachtlauf aufbewahrt.')).toBeInTheDocument()
    expect(within(stand).queryByText(/\b0\b/)).not.toBeInTheDocument()
  })

  it('zeigt keine Kosten', async () => {
    mNightRuns.list.mockResolvedValue([lauf('2026-09-16T01:00:00Z', [paket(842, 'X', 'RED', 'HARD_ABORT')])])
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    await within(stand).findByText(/Abgebrochen/)
    expect(stand).not.toHaveTextContent(/Kosten|\$|USD|12[,.]5|4[,.]2/)
  })

  it('blendet den Stand ohne Recht auf die Läufe aus, statt einen Fehler zu zeigen', async () => {
    mNightRuns.list.mockRejectedValue(new ApiError(403, 'Forbidden'))
    renderPage()
    await screen.findByTestId('hero-metric')
    await waitFor(() => expect(mNightRuns.list).toHaveBeenCalled())
    // Während des Ladens steht der Bereich kurz da; nach der Abweisung verschwindet er ganz.
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Letzte Nacht' })).not.toBeInTheDocument())
    expect(screen.queryByText(/konnte nicht geladen werden/)).not.toBeInTheDocument()
  })

  it('meldet einen anderen Fehler beim Laden des Stands', async () => {
    mNightRuns.list.mockRejectedValue(new TypeError('Failed to fetch'))
    renderPage()
    const stand = await screen.findByRole('region', { name: 'Letzte Nacht' })
    expect(await within(stand).findByText('Stand der letzten Nacht konnte nicht geladen werden.')).toBeInTheDocument()
  })

  it('zeigt den Stand erst nach dem Laden des Boards — ohne Projekt gibt es keine Läufe', () => {
    mBoards.get.mockReturnValue(new Promise(() => {}))
    renderPage()
    expect(mNightRuns.list).not.toHaveBeenCalled()
  })
})
