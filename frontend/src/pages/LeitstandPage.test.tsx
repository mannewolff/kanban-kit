import { GRUND_UNBEKANNT, serverBefund } from '../test/befund'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { ApiError } from '../api/client'
import { dashboardApi, type BoardDashboardKpis } from '../api/dashboard'
import { epicsApi, type Epic } from '../api/epics'
import { nightRunsApi, type NightRunItemView, type NightRunView } from '../api/nightRuns'
import { nightRunUsageApi, type VerbrauchGesamt, type VerbrauchKennzahlen, type VerbrauchZeitraum } from '../api/nightRunUsage'
import { LeitstandPage } from './LeitstandPage'

vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/cards', () => ({ cardsApi: { byNumber: vi.fn() } }))
vi.mock('../api/dashboard', () => ({ dashboardApi: { get: vi.fn() } }))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn() } }))
vi.mock('../api/nightRuns', () => ({ nightRunsApi: { list: vi.fn(), errorClassCounts: vi.fn() } }))
vi.mock('../api/nightRunUsage', async (original) => ({
  ...(await original<typeof import('../api/nightRunUsage')>()),
  nightRunUsageApi: { period: vi.fn(), total: vi.fn() },
}))

const mNotify = vi.fn()
vi.mock('../components/SnackbarProvider', () => ({ useSnackbar: () => mNotify }))

// Der Kartendialog ist eigenständig getestet; hier zählt, mit welcher Karte er geöffnet wird.
vi.mock('../components/CardDetailModal', () => ({
  CardDetailModal: ({ card, onClose }: Readonly<{ card: Card; onClose: () => void }>) => (
    <div data-testid="karten-detail">
      <span>{card.title}</span>
      <button type="button" onClick={onClose}>
        Detail schließen
      </button>
    </div>
  ),
}))

const m = {
  board: boardsApi.get as ReturnType<typeof vi.fn>,
  kpis: dashboardApi.get as ReturnType<typeof vi.fn>,
  epics: epicsApi.list as ReturnType<typeof vi.fn>,
  laeufe: nightRunsApi.list as ReturnType<typeof vi.fn>,
  klassen: nightRunsApi.errorClassCounts as ReturnType<typeof vi.fn>,
  karteNachNummer: cardsApi.byNumber as ReturnType<typeof vi.fn>,
  verbrauch: nightRunUsageApi.period as ReturnType<typeof vi.fn>,
  lebenszeit: nightRunUsageApi.total as ReturnType<typeof vi.fn>,
}

const karte = (title: string): Card => ({
  derivedFrom: null,
  id: 9,
  boardId: 1,
  columnId: 5,
  number: 42,
  title,
  description: null,
  excerpt: null,
  positionInColumn: 0,
  archived: false,
  movedToDoneAt: null,
  dependencies: [],
  type: 'CARD',
  parentId: null,
  shortcode: null,
  assignees: [],
  dueDate: null,
  labels: [],
})

const kpis = (extra: Partial<BoardDashboardKpis> = {}): BoardDashboardKpis => ({
  columnDwell: [{ columnId: 1, columnName: 'Ready', avgDwellSeconds: 7200, sampleCount: 3 }],
  throughput: [
    { weekStart: '2026-06-01T09:00:00Z', doneCount: 10 },
    { weekStart: '2026-06-08T09:00:00Z', doneCount: 12 },
  ],
  avgLeadTimeSeconds: 276_480,
  leadTimeSampleCount: 86,
  avgImplementationSeconds: 53_280,
  implementationSampleCount: 61,
  outliers: [{ cardId: 9, number: 846, title: 'Kartenverlauf als Zeitstrahl', columnName: 'Review', dwellSeconds: 14 * 86_400 }],
  ...extra,
})

const paket = (nummer: number, state: NightRunItemView['state'], extra: Partial<NightRunItemView> = {}): NightRunItemView => ({
  id: nummer,
  cardNumber: nummer,
  title: `Paket ${nummer}`,
  state,
  errorClass: null,
  durationMs: 1_122_000,
  commitHash: null,
  excerpt: null,
  usage: null,
  stages: [],
  ...extra,
})

const lauf = (extra: Partial<NightRunView> = {}): NightRunView => {
  const basis: NightRunView = {
  id: 3,
  startedAt: '2026-09-14T21:10:00Z',
  mode: 'CHAIN',
  durationMs: 15_120_000,
  processedCount: 3,
  skippedCount: 0,
  unparsedCount: 0,
  unparsedSample: null,
  createdAt: '2026-09-15T01:22:00Z',
  origin: 'TOKEN',
  tokenName: 'kette',
  complete: true,
  updatedAt: null,
  usage: { costUsd: 12.4, inputTokens: null, outputTokens: null, cachedInputTokens: null, modelDurationMs: null, turns: null },
  noWorkReason: null,
  abortReason: null,
  budget: null,
  items: [
    paket(917, 'GREEN', { commitHash: '9489421abcdef' }),
    paket(922, 'RED', { errorClass: 'CHECKS_RED', excerpt: '2 Tests rot in BoardViewTest' }),
    paket(925, 'YELLOW', { errorClass: 'AWAITING_DECISION' }),
    paket(930, 'GREY', { durationMs: null }),
  ],
    outcome: { abortReason: null, verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null },
    ...extra,
  }
  // Der Befund kommt aus dem Szenario, nicht aus der Vorgabe: Ein Lauf mit rotem Paket traegt sonst
  // einen Befund, der etwas anderes sagt als seine eigenen Pakete (Issue #1081).
  return { ...basis, outcome: extra.outcome ?? serverBefund(basis) }
}

const epic = (id: number, title: string, done: number, total: number, memberNumbers: number[] = []): Epic => ({
  id,
  number: id,
  title,
  description: null,
  shortcode: null,
  done,
  total,
  memberNumbers,
  rootNumbers: [],
  requirementCardNumber: null,
})

const angaben = (costUsd: number | null, inputTokens: number | null = 4_820_000, outputTokens: number | null = 186_000, cachedInputTokens: number | null = 3_660_000) => ({
  costUsd,
  inputTokens,
  outputTokens,
  cachedInputTokens,
  cachedInputSharePercent: null,
})

/**
 * Der Gattungs-Split der Antwort (Issue #1016). Der Leitstand liest weiterhin die Gesamtsumme; die
 * Fixtures führen den Split, weil die Antwort ihn trägt — der Nachtlauf-Anteil ist hier die ganze
 * Summe, der Sitzungs-Anteil leer.
 */
const jeGattung = (gesamt: ReturnType<typeof angaben>) => ({
  night: { total: gesamt, cardShare: angaben(null), remainder: angaben(null) },
  interactive: { total: angaben(null), cardShare: angaben(null), remainder: angaben(null) },
})

const kennzahlen = (extra: Partial<VerbrauchKennzahlen> = {}): VerbrauchKennzahlen => ({
  type: 'DAY',
  firstDay: '2026-09-14',
  lastDay: '2026-09-14',
  from: '',
  to: '',
  coverage: 'COMPLETE',
  noRuns: false,
  runCount: 1,
  nightRunCount: 1,
  interactiveRunCount: 0,
  durationMs: 1,
  cardCount: 9,
  usage: { total: angaben(12.4), cardShare: angaben(null), remainder: angaben(null) },
  usageByKind: jeGattung(angaben(12.4)),
  interactiveUsageSince: null,
  ...extra,
})

const nacht = (night: string, outputTokens: number | null) => ({
  night,
  runCount: 1,
  cardCount: 9,
  usage: { total: angaben(10, 1, outputTokens, 1), cardShare: angaben(null), remainder: angaben(null) },
  usageByKind: jeGattung(angaben(10, 1, outputTokens, 1)),
  aborted: false,
})

const zeitraum = (extra: Partial<VerbrauchZeitraum> = {}): VerbrauchZeitraum => ({
  current: kennzahlen(),
  previous: kennzahlen({
    usage: { total: angaben(14.1), cardShare: angaben(null), remainder: angaben(null) },
    usageByKind: jeGattung(angaben(14.1)),
  }),
  nights: [nacht('2026-09-14', 186_000)],
  epics: [],
  withoutEpic: { epicId: null, shortcode: null, title: null, cardCount: 0, usage: angaben(null) },
  epicsOverlap: false,
  stages: [],
  ...extra,
})

/** Die Lebenszeit-Summe der Verbrauchs-Kacheln (Issue #1014, #1017). */
const lebenszeit = (): VerbrauchGesamt => ({
  runCount: 12,
  nightRunCount: 9,
  interactiveRunCount: 3,
  cardCount: 30,
  usage: { total: angaben(150), cardShare: angaben(null), remainder: angaben(null) },
  usageByKind: jeGattung(angaben(150)),
  oldestRetainedRunStart: '2026-08-01T00:00:00Z',
  interactiveUsageSince: null,
})

function renderPage(pfad = '/boards/1/leitstand') {
  return render(
    <MemoryRouter initialEntries={[pfad]}>
      <Routes>
        <Route path="/boards/:boardId/leitstand" element={<LeitstandPage />} />
        <Route path="/boards/leitstand" element={<LeitstandPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  m.board.mockResolvedValue({ id: 1, name: 'Entwicklung', projectId: 5, columns: [] })
  m.kpis.mockResolvedValue(kpis())
  m.epics.mockResolvedValue([epic(1, 'Nachtlauf-Auswertung', 7, 9, [917]), epic(2, 'Erledigt', 3, 3)])
  m.laeufe.mockResolvedValue([lauf({ id: 1, startedAt: '2026-09-10T21:00:00Z', items: [paket(1, 'GREEN')] }), lauf()])
  m.klassen.mockResolvedValue({ CHECKS_RED: 11, AWAITING_DECISION: 7 })
  m.verbrauch.mockResolvedValue(zeitraum())
  m.lebenszeit.mockResolvedValue(lebenszeit())
})

describe('LeitstandPage (#979)', () => {
  it('lehnt eine ungültige Board-ID ab', () => {
    renderPage('/boards/abc/leitstand')
    expect(screen.getByRole('alert')).toHaveTextContent('Ungültige Board-ID.')
  })

  it('behandelt einen fehlenden Board-Parameter als ungültig', () => {
    renderPage('/boards/leitstand')
    expect(screen.getByRole('alert')).toHaveTextContent('Ungültige Board-ID.')
  })

  it('zeigt im Laufband den jüngsten Lauf mit Titel, letztem Vorgang, Zeit und Kosten', async () => {
    renderPage()
    const band = await screen.findByRole('region', { name: 'Jüngster Run' })
    expect(within(band).getByTestId('laufband-titel')).toHaveTextContent('Kette abgeschlossen — 4 Vorgänge')
    expect(band).toHaveTextContent('#930 Paket 930 · Beginn')
    expect(band).toHaveTextContent('252 min')
    expect(band).toHaveTextContent('12,40 $')
    expect(within(band).getAllByTestId('led-zinnob')).toHaveLength(1)
  })

  it('lässt im Laufband die Kosten weg, wenn sie nicht gemessen wurden, und pulsiert bei laufender Kette', async () => {
    m.laeufe.mockResolvedValue([lauf({ usage: null, complete: false, items: [] })])
    renderPage()
    const band = await screen.findByRole('region', { name: 'Jüngster Run' })
    expect(band).toHaveTextContent('Kette läuft')
    expect(band).not.toHaveTextContent('Kosten')
    expect(within(band).getByTestId('led-stahl')).toBeInTheDocument()
  })

  it('zeigt die Kennzahl-Kacheln mit Wert, Einheit, Delta und Datenbasis', async () => {
    renderPage()
    const durchsatz = await screen.findByRole('article', { name: 'Durchsatz · Woche' })
    expect(durchsatz).toHaveTextContent('12Karten')
    expect(durchsatz).toHaveTextContent('▲ 20 %')
    expect(durchsatz).toHaveTextContent('2 Wochen')
    expect(within(durchsatz).getByTestId('funke')).toBeInTheDocument()
    expect(screen.getByRole('article', { name: 'Durchlaufzeit' })).toHaveTextContent('3,2Tage')
    expect(screen.getByRole('article', { name: 'Implementierungszeit' })).toHaveTextContent('14,8Stunden')
    expect(screen.getByRole('article', { name: 'Implementierungszeit' })).toHaveTextContent('61 Karten')
    // Genau diese vier Kacheln — die abgelöste Kennzahl steht nicht mehr daneben. Die
    // Nachtlauf-Kachel hängt an der längeren Kette Board → Läufe und kommt nach den Kennzahlen;
    // ohne das Warten hinge die Vollständigkeitsprüfung am Zufall der Auflösungsreihenfolge.
    await waitFor(() =>
      expect(screen.getAllByRole('article').map((k) => k.getAttribute('aria-label'))).toEqual([
        'Durchsatz · Woche',
        'Durchlaufzeit',
        'Implementierungszeit',
        'Run · grün',
      ]),
    )
  })

  it('zeigt ohne Datenbasis einen Leerwert statt einer Null', async () => {
    m.kpis.mockResolvedValue(
      kpis({
        avgLeadTimeSeconds: null,
        leadTimeSampleCount: 0,
        avgImplementationSeconds: null,
        implementationSampleCount: 0,
        throughput: [{ weekStart: '2026-06-01T09:00:00Z', doneCount: 0 }],
      }),
    )
    renderPage()
    const lead = await screen.findByRole('article', { name: 'Durchlaufzeit' })
    expect(lead).toHaveTextContent('—keine Datenbasis')
    expect(screen.getByRole('article', { name: 'Implementierungszeit' })).toHaveTextContent('—keine Datenbasis')
    expect(screen.getByRole('article', { name: 'Durchsatz · Woche' })).toHaveTextContent('—keine Datenbasis')
    expect(screen.getByRole('region', { name: 'Durchsatz' })).toHaveTextContent('Noch keine abgeschlossene Karte in den letzten Wochen.')
  })

  it('meldet Laden und Fehler der Kennzahlen', async () => {
    let ablehnen: (e: unknown) => void = () => undefined
    m.kpis.mockReturnValue(new Promise((_, reject) => (ablehnen = reject)))
    renderPage()
    expect(screen.getByText('Kennzahlen werden geladen …')).toBeInTheDocument()
    ablehnen(new Error('kaputt'))
    expect(await screen.findByText('Kennzahlen konnten nicht geladen werden.')).toBeInTheDocument()
  })

  it('rechnet die Nachtlauf-Kachel über alle aufbewahrten Pakete ohne graue', async () => {
    renderPage()
    const kachel = await screen.findByRole('article', { name: 'Run · grün' })
    expect(kachel).toHaveTextContent('50%')
    expect(within(kachel).getByRole('img', { name: '2 grün, 1 gelb, 1 rot' })).toBeInTheDocument()
    expect(kachel).toHaveTextContent('letzte 4 Pakete')
  })

  it('nennt ein einzelnes Paket in der Nachtlauf-Kachel und zeigt ohne bewertetes Paket keinen Balken', async () => {
    m.laeufe.mockResolvedValue([lauf({ items: [paket(1, 'GREEN')] })])
    const { unmount } = renderPage()
    expect(await screen.findByRole('article', { name: 'Run · grün' })).toHaveTextContent('letztes Paket')
    unmount()

    m.laeufe.mockResolvedValue([lauf({ items: [paket(1, 'GREY')] })])
    renderPage()
    const kachel = await screen.findByRole('article', { name: 'Run · grün' })
    expect(kachel).toHaveTextContent('keine Datenbasis')
    expect(within(kachel).queryByRole('img')).not.toBeInTheDocument()
  })

  it('lässt ohne Recht auf die Läufe alles Lauf-Bezogene still weg und behält die Board-Kennzahlen', async () => {
    m.laeufe.mockRejectedValue(new ApiError(403, 'Forbidden'))
    m.klassen.mockRejectedValue(new ApiError(403, 'Forbidden'))
    renderPage()
    expect(await screen.findByRole('article', { name: 'Durchlaufzeit' })).toBeInTheDocument()
    await waitFor(() => expect(m.laeufe).toHaveBeenCalled())
    expect(screen.queryByRole('region', { name: 'Jüngster Run' })).not.toBeInTheDocument()
    expect(screen.queryByRole('article', { name: 'Run · grün' })).not.toBeInTheDocument()
    expect(screen.queryByText('Verbrauch')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Abbruchgründe' })).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(m.verbrauch).not.toHaveBeenCalled()
  })

  it('lässt auch bei einem anderen Fehler der Läufe die Lauf-Bereiche weg', async () => {
    m.laeufe.mockRejectedValue(new Error('Netz'))
    renderPage()
    expect(await screen.findByRole('article', { name: 'Durchlaufzeit' })).toBeInTheDocument()
    await waitFor(() => expect(m.laeufe).toHaveBeenCalled())
    expect(screen.queryByRole('region', { name: /Letzter Run/ })).not.toBeInTheDocument()
  })

  it('zeigt ohne aufbewahrten Run weder Laufband noch Letzten Lauf', async () => {
    m.laeufe.mockResolvedValue([])
    renderPage()
    expect(await screen.findByRole('article', { name: 'Run · grün' })).toHaveTextContent('keine Datenbasis')
    expect(screen.queryByRole('region', { name: 'Jüngster Run' })).not.toBeInTheDocument()
  })

  it('verarbeitet eine verspätete Antwort nach dem Verlassen der Seite nicht mehr', async () => {
    let liefern: (wert: BoardDashboardKpis) => void = () => undefined
    m.kpis.mockReturnValue(new Promise((resolve) => (liefern = resolve)))
    const { unmount } = renderPage()
    unmount()
    liefern(kpis())
    await Promise.resolve()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })

  it('nennt den Board-Namen in der unsichtbaren Überschrift', async () => {
    renderPage()
    expect(screen.getByRole('heading', { level: 1, name: 'Leitstand' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { level: 1, name: 'Leitstand Entwicklung' })).toBeInTheDocument()
  })
})

describe('LeitstandPage — Herkunft der Einlieferung', () => {
  it('nennt eine von der Kette gemeldete Einlieferung samt Token und Zeilen', async () => {
    renderPage()
    expect(await screen.findByText(/^Eingeliefert von der Kette um \d\d:\d\d — automatisch/)).toBeInTheDocument()
    expect(screen.getByText('Token kette')).toBeInTheDocument()
    expect(screen.getByText('4 Vorgänge · 0 ungedeutete Zeilen')).toBeInTheDocument()
  })

  it('nennt einen hochgeladenen Lauf ohne Token, einen einzelnen Vorgang und laufend mit stahlblauer LED', async () => {
    m.laeufe.mockResolvedValue([lauf({ origin: 'UPLOAD', tokenName: null, updatedAt: '2026-09-15T02:00:00Z', complete: false, items: [paket(1, 'GREEN')] })])
    renderPage()
    await screen.findByText(/^Im Browser hochgeladen um/)
    expect(screen.queryByText(/^Token /)).not.toBeInTheDocument()
    expect(screen.getByText('1 Vorgang · 0 ungedeutete Zeilen')).toBeInTheDocument()
    // Laufband, Letzter Lauf und Herkunftszeile melden den laufenden Lauf stahlblau.
    expect(screen.getAllByTestId('led-stahl')).toHaveLength(3)
    // Issue #1136: Laufband und „Letzter Lauf" zeigen dabei den Wechselblinker aus zwei Lampen.
    const band = screen.getByRole('region', { name: 'Jüngster Run' })
    expect(within(within(band).getByTestId('led-stahl')).getAllByTestId('blinker-lampe')).toHaveLength(2)
    const platte = screen.getByRole('region', { name: 'Letzter Run · Kette' })
    expect(within(platte).getAllByTestId('blinker-lampe')).toHaveLength(2)
  })
})

describe('LeitstandPage — Letzter Run', () => {
  const platte = () => screen.findByRole('region', { name: 'Letzter Run · Kette' })

  it('listet die Arbeitspakete mit Zustand, Vorhaben, Fehlerklasse, Dauer und kurzem Hash', async () => {
    renderPage()
    const letzter = await platte()
    const zeilen = within(letzter).getAllByRole('listitem')
    expect(zeilen).toHaveLength(4)
    expect(zeilen[0]).toHaveTextContent('#917Paket 917Nachtlauf-Auswertung18:429489421')
    expect(zeilen[1]).toHaveTextContent('CHECKS_RED2 Tests rot in BoardViewTest')
    expect(zeilen[2]).toHaveTextContent('AWAITING_DECISION')
    expect(zeilen[3]).toHaveTextContent('#930Paket 930—')
    expect(letzter).toHaveTextContent('4 Pakete')
  })

  it('filtert auf Abbrüche und zurück', async () => {
    renderPage()
    const letzter = await platte()
    fireEvent.click(within(letzter).getByRole('button', { name: 'Nur Abbrüche' }))
    expect(within(letzter).getAllByRole('listitem')).toHaveLength(2)
    expect(within(letzter).getByRole('button', { name: 'Nur Abbrüche' })).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(within(letzter).getByRole('button', { name: 'Alle' }))
    expect(within(letzter).getAllByRole('listitem')).toHaveLength(4)
  })

  it('sagt es, wenn der Filter nichts findet oder der Lauf noch kein Paket gemeldet hat', async () => {
    m.laeufe.mockResolvedValue([lauf({ items: [paket(1, 'GREEN')] })])
    const { unmount } = renderPage()
    const letzter = await platte()
    fireEvent.click(within(letzter).getByRole('button', { name: 'Nur Abbrüche' }))
    expect(letzter).toHaveTextContent('Kein Abbruch in diesem Run.')
    unmount()

    m.laeufe.mockResolvedValue([lauf({ items: [], complete: false })])
    renderPage()
    expect(await platte()).toHaveTextContent('Der Run hat noch kein Arbeitspaket gemeldet.')
  })

  it('öffnet die Karte über ihre Nummer im Projekt', async () => {
    m.karteNachNummer.mockResolvedValue(karte('Paket 917 im Detail'))
    renderPage()
    const letzter = await platte()
    fireEvent.click(within(letzter).getByRole('button', { name: 'Karte #917 öffnen: Paket 917' }))
    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Paket 917 im Detail')
    expect(m.karteNachNummer).toHaveBeenCalledWith(5, 917)
    fireEvent.click(screen.getByRole('button', { name: 'Detail schließen' }))
    expect(screen.queryByTestId('karten-detail')).not.toBeInTheDocument()
  })

  it('meldet eine nicht gefundene Karte als Warnung und einen anderen Fehler als Fehler', async () => {
    m.karteNachNummer.mockRejectedValueOnce(new ApiError(404, 'Not Found')).mockRejectedValueOnce(new Error('Netz'))
    renderPage()
    const letzter = await platte()
    fireEvent.click(within(letzter).getByRole('button', { name: 'Karte #917 öffnen: Paket 917' }))
    await waitFor(() => expect(mNotify).toHaveBeenCalledWith('Karte #917 nicht gefunden — gelöscht oder kein Zugriff.', 'warning'))
    fireEvent.click(within(letzter).getByRole('button', { name: 'Karte #922 öffnen: Paket 922' }))
    await waitFor(() => expect(mNotify).toHaveBeenCalledWith('Karte konnte nicht geladen werden.', 'error'))
  })
})

describe('LeitstandPage — Platten des Rumpfs', () => {
  it('zeigt den Durchsatz als Balkenwerk mit Kalenderwochen, eine leere Woche ohne Balken', async () => {
    m.kpis.mockResolvedValue(
      kpis({
        throughput: [
          { weekStart: '2026-06-01T09:00:00Z', doneCount: 10 },
          { weekStart: '2026-06-08T09:00:00Z', doneCount: 0 },
          { weekStart: '2026-06-15T09:00:00Z', doneCount: 12 },
        ],
      }),
    )
    renderPage()
    const durchsatz = await screen.findByRole('region', { name: 'Durchsatz' })
    expect(within(durchsatz).getByRole('img')).toHaveAccessibleName('Durchsatz der letzten 3 Wochen: 10, 0, 12 Karten, zuletzt 12')
    expect(within(durchsatz).getAllByTestId(/^balken-/).map((b) => b.dataset.testid)).toEqual(['balken-83', 'balken-0', 'balken-100'])
    expect(durchsatz).toHaveTextContent('232425')
  })

  it('sortiert die Abbruchgründe nach Häufigkeit und nennt die Zahl der Runs', async () => {
    renderPage()
    const gruende = await screen.findByRole('region', { name: 'Abbruchgründe' })
    expect(within(gruende).getAllByRole('listitem').map((z) => z.textContent)).toEqual(['CHECKS_RED11', 'AWAITING_DECISION7'])
    expect(gruende).toHaveTextContent('2 Runs')
  })

  it('sagt ohne Abbruch, dass es keinen gab, und nennt einen einzelnen Lauf im Singular', async () => {
    m.klassen.mockResolvedValue({})
    m.laeufe.mockResolvedValue([lauf()])
    renderPage()
    const gruende = await screen.findByRole('region', { name: 'Abbruchgründe' })
    expect(gruende).toHaveTextContent('Kein Abbruch in den aufbewahrten Runs.')
    expect(gruende).toHaveTextContent('1 Run')
  })

  it('zeigt keine Platte „Liegengeblieben", auch wenn die Antwort Ausreißer führt (#983)', async () => {
    // `outliers` bleibt im Backend; im Leitstand wird die Kennzahl nicht mehr dargestellt.
    renderPage()
    await screen.findByRole('region', { name: 'Abbruchgründe' })
    expect(screen.queryByRole('region', { name: 'Liegengeblieben' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Kartenverlauf als Zeitstrahl/)).not.toBeInTheDocument()
  })

  it('zeigt nur offene Vorhaben mit Fortschritt', async () => {
    renderPage()
    const vorhaben = await screen.findByRole('region', { name: 'Vorhaben' })
    expect(within(vorhaben).getAllByRole('listitem').map((z) => z.textContent)).toEqual(['Nachtlauf-Auswertung7/9'])
    expect(within(vorhaben).getByTestId('fuellung-78')).toBeInTheDocument()
  })

  it('sagt ohne offenes Vorhaben, dass es keins gibt', async () => {
    m.epics.mockResolvedValue([epic(2, 'Erledigt', 3, 3)])
    renderPage()
    expect(await screen.findByRole('region', { name: 'Vorhaben' })).toHaveTextContent('Kein offenes Vorhaben.')
  })
})

describe('LeitstandPage — Verbrauch', () => {
  const kachel = (name: string) => screen.findByRole('article', { name })

  it('zeigt die Nacht mit Eingabe-Aufteilung, Ausgabe und Kosten samt Vergleich', async () => {
    renderPage()
    const eingabe = await kachel('Eingabe-Token')
    expect(eingabe).toHaveTextContent('4,82Mio')
    expect(within(eingabe).getByRole('img')).toHaveAccessibleName('Aufteilung der Eingabe: 76 Prozent aus dem Cache gelesen, 24 Prozent frisch')
    expect(eingabe).toHaveTextContent('Cache gelesen 3,66 Mio')
    expect(eingabe).toHaveTextContent('frisch 1,16 Mio')
    expect(await kachel('Ausgabe-Token')).toHaveTextContent('186Tsd')
    expect(await kachel('Ausgabe-Token')).toHaveTextContent('21 Tsd je Vorgang')
    expect(await kachel('Ausgabe-Token')).toHaveTextContent('1 Schicht')
    const kosten = await kachel('Kosten')
    expect(kosten).toHaveTextContent('12,40$')
    expect(within(kosten).getByTestId('delta-gut')).toHaveTextContent('▼ 1,70 $')
    expect(kosten).toHaveTextContent('1,38 $ je Vorgang')
    expect(screen.getByTestId('verbrauch-umfang')).toHaveTextContent(
      'Schicht vom 14.09.2026 auf den 15.09.2026 · 1 Run · 0 Sitzungen',
    )
    expect(m.verbrauch).toHaveBeenCalledWith(5, 'DAY', 0)
  })

  it('wechselt den Zeitraum und zeigt dort den Verlauf der Ausgabe', async () => {
    renderPage()
    await kachel('Kosten')
    m.verbrauch.mockResolvedValue(
      zeitraum({
        current: kennzahlen({ type: 'WEEK', lastDay: '2026-09-20', runCount: 3, nightRunCount: 3, usage: { total: angaben(20), cardShare: angaben(null), remainder: angaben(null) }, usageByKind: jeGattung(angaben(20)) }),
        nights: [nacht('2026-09-14', 100), nacht('2026-09-15', null), nacht('2026-09-16', 300)],
      }),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Woche' }))
    expect(screen.getByRole('button', { name: 'Woche' })).toHaveAttribute('aria-pressed', 'true')
    await waitFor(() =>
      expect(screen.getByTestId('verbrauch-umfang')).toHaveTextContent('3 Runs · 0 Sitzungen'),
    )
    expect(m.verbrauch).toHaveBeenLastCalledWith(5, 'WEEK', 0)
    const ausgabe = await kachel('Ausgabe-Token')
    expect(within(ausgabe).getByTestId('funke')).toBeInTheDocument()
    expect(ausgabe).toHaveTextContent('3 Schichten')
    expect(within(await kachel('Kosten')).getByTestId('delta-schlecht')).toHaveTextContent('▲ 5,90 $')
  })

  it('zeigt ohne Messung Leerwerte, keinen Stapel, keinen Vergleich und den Hinweis zum Zeitraum', async () => {
    m.verbrauch.mockResolvedValue(
      zeitraum({
        current: kennzahlen({ noRuns: true, runCount: 0, nightRunCount: 0, cardCount: 0, usage: { total: angaben(null, null, null, null), cardShare: angaben(null), remainder: angaben(null) }, usageByKind: jeGattung(angaben(null, null, null, null)) }),
        nights: [],
      }),
    )
    renderPage()
    const eingabe = await kachel('Eingabe-Token')
    expect(eingabe).toHaveTextContent('keine Datenbasis')
    expect(within(eingabe).queryByRole('img')).not.toBeInTheDocument()
    expect(await kachel('Kosten')).not.toHaveTextContent('je Vorgang')
    expect(await kachel('Ausgabe-Token')).toHaveTextContent('0 Schichten')
    expect(
      screen.getByText('In diesem Zeitraum hat weder ein Run noch eine Sitzung stattgefunden.'),
    ).toBeInTheDocument()
  })

  it('meldet Laden und Fehler des Verbrauchs im Bereich', async () => {
    let ablehnen: (e: unknown) => void = () => undefined
    m.verbrauch.mockReturnValue(new Promise((_, reject) => (ablehnen = reject)))
    renderPage()
    expect(await screen.findByText('Der Verbrauch wird geladen …')).toBeInTheDocument()
    ablehnen(new Error('Netz'))
    expect(await screen.findByText('Der Verbrauch konnte nicht geladen werden.')).toBeInTheDocument()
  })

  it('verarbeitet eine verspätete Verbrauchsantwort nach einem Zeitraumwechsel nicht mehr', async () => {
    let erste: (z: VerbrauchZeitraum) => void = () => undefined
    let fehler: (e: unknown) => void = () => undefined
    m.verbrauch
      .mockReturnValueOnce(new Promise((resolve) => (erste = resolve)))
      .mockReturnValueOnce(new Promise((_, reject) => (fehler = reject)))
      .mockResolvedValue(
        zeitraum({ current: kennzahlen({ type: 'MONTH', runCount: 2, nightRunCount: 2 }) }),
      )
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Woche' }))
    fireEvent.click(screen.getByRole('button', { name: 'Monat' }))
    erste(zeitraum())
    fehler(new Error('spät'))
    await waitFor(() =>
      expect(screen.getByTestId('verbrauch-umfang')).toHaveTextContent(
        'September 2026 · 2 Runs · 0 Sitzungen',
      ),
    )
    expect(screen.queryByText('Der Verbrauch konnte nicht geladen werden.')).not.toBeInTheDocument()
  })
})

describe('LeitstandPage — Lauf ohne Arbeit (#1069)', () => {
  const GRUND = 'Kein Eintrag trug das Label kit:nightrun'
  const ohneArbeit = () => lauf({ noWorkReason: GRUND, processedCount: 0, items: [] })

  it('zeigt den juengsten Lauf ohne Arbeit in Laufband und „Letzter Run" grau mit seinem Text', async () => {
    m.klassen.mockResolvedValue({})
    m.laeufe.mockResolvedValue([ohneArbeit()])
    renderPage()

    const band = await screen.findByRole('region', { name: 'Jüngster Run' })
    expect(band).toHaveTextContent(GRUND)
    const platte = await screen.findByRole('region', { name: 'Letzter Run · Kette' })
    expect(platte).toHaveTextContent(GRUND)
    // Seit #1121 melden beide grau statt rot: Der Lauf fand nichts zu tun, und das ist kein Mangel.
    // Die Herkunftszeile bleibt davon unberuehrt (E10) und meldet weiter gruen.
    expect(within(band).getByTestId('led-grau')).toBeInTheDocument()
    expect(within(platte).getByTestId('led-grau')).toBeInTheDocument()
    expect(screen.queryAllByTestId('led-zinnob')).toHaveLength(0)
  })

  /**
   * Der Rueckfall des Servers („Grund unbekannt") ist seit #1185 derselbe ruhige Lauf, nur mit
   * blasserer Auskunft: grau statt rot. Die Auskunft steht in der Notizzeile des Plattenkopfs, die
   * Ueberschrift bleibt „Letzter Run · <Modus>" (Plan #1181 E7).
   */
  it('meldet den Lauf ohne Arbeit mit unbekanntem Grund grau und nennt ihn in der Notiz', async () => {
    m.klassen.mockResolvedValue({})
    m.laeufe.mockResolvedValue([lauf({ noWorkReason: GRUND_UNBEKANNT, processedCount: 0, items: [] })])
    renderPage()

    const platte = await screen.findByRole('region', { name: 'Letzter Run · Kette' })
    expect(platte).toHaveTextContent(GRUND_UNBEKANNT)
    expect(within(platte).getByTestId('led-grau')).toBeInTheDocument()
    expect(within(platte).queryAllByTestId('led-zinnob')).toHaveLength(0)
  })

  /**
   * Ein Lauf, der alle Pakete zurueckstellte: Er meldet keinen Grund, der Server faellt auf den
   * Rueckfalltext zurueck — und trotzdem hat er nicht nichts gefunden. Notiz und Laufband nennen
   * deshalb keinen Grund (Plan #1181 E11).
   */
  it('nennt am Lauf mit zurueckgestellten Paketen keinen Grund in Notiz und Laufband', async () => {
    m.klassen.mockResolvedValue({})
    m.laeufe.mockResolvedValue([
      lauf({
        noWorkReason: GRUND_UNBEKANNT,
        processedCount: 0,
        items: [paket(917, 'GREY', { errorClass: 'DEPENDENCY_UNMET' })],
      }),
    ])
    renderPage()

    const platte = await screen.findByRole('region', { name: 'Letzter Run · Kette' })
    expect(platte).not.toHaveTextContent(GRUND_UNBEKANNT)
    const band = await screen.findByRole('region', { name: 'Jüngster Run' })
    expect(band).not.toHaveTextContent(GRUND_UNBEKANNT)
    expect(band).toHaveTextContent('Kette abgeschlossen — 1 Vorgang')
  })

  // E12: Er zaehlt mit, taucht aber in keiner Zeile der Klassenliste auf -- er hat keine Klasse.
  it('zaehlt den Lauf ohne Arbeit bei den Abbruchgruenden mit, fuehrt ihn aber in keiner Zeile', async () => {
    m.klassen.mockResolvedValue({})
    m.laeufe.mockResolvedValue([ohneArbeit()])
    renderPage()

    const gruende = await screen.findByRole('region', { name: 'Abbruchgründe' })
    expect(gruende).toHaveTextContent('1 Run')
    expect(gruende).toHaveTextContent('Kein Abbruch in den aufbewahrten Runs.')
    expect(within(gruende).queryAllByRole('listitem')).toHaveLength(0)
  })

  /**
   * Festgehaltenes Verhalten (E10, Nicht-Ziel): Die LED der Herkunftszeile bewertet die
   * Einlieferung, nicht den Ausgang. Ein Lauf mit rotem Paket bleibt dort gruen — wer sie auf
   * `laufMelder` umstellte, faerbte jeden solchen Lauf um.
   */
  it('laesst die LED der Herkunftszeile an einem Lauf mit rotem Paket unveraendert', async () => {
    m.laeufe.mockResolvedValue([lauf({ noWorkReason: null })])
    renderPage()

    await screen.findByRole('region', { name: 'Letzter Run · Kette' })
    expect(screen.getAllByTestId('led-gruen').length).toBeGreaterThanOrEqual(1)
  })
})

describe('LeitstandPage — Der verstummte Lauf (#1092)', () => {
  // Ein Lauf, dessen Runner abgeschossen wurde: `complete` bleibt `false`, der Server nennt ihn
  // seit #1091 trotzdem FAILED. Die Anzeige liest den Befund, nicht `complete`.
  const verstummt = () =>
    lauf({ complete: false, items: [], outcome: serverBefund({ complete: false, verstummt: true, items: [] }) })

  it('meldet den verstummten Lauf zinnober und ohne Puls', async () => {
    m.laeufe.mockResolvedValue([verstummt()])
    renderPage()

    const letzter = await screen.findByRole('region', { name: 'Letzter Run · Kette' })
    expect(within(letzter).getByTestId('led-zinnob')).toHaveAttribute('data-puls', 'aus')
  })

  it('nimmt dem verstummten Lauf im Laufband den Puls und nennt den Beginn', async () => {
    m.laeufe.mockResolvedValue([verstummt()])
    renderPage()

    const band = await screen.findByRole('region', { name: 'Jüngster Run' })
    expect(within(band).getByTestId('led-zinnob')).toHaveAttribute('data-puls', 'aus')
    expect(band).toHaveTextContent('Beginn')
  })

  it('laesst den laufenden Lauf weiter stahlblau pulsieren', async () => {
    m.laeufe.mockResolvedValue([lauf({ complete: false, items: [] })])
    renderPage()

    const band = await screen.findByRole('region', { name: 'Jüngster Run' })
    expect(within(band).getByTestId('led-stahl')).toHaveAttribute('data-puls', 'an')
  })
})
