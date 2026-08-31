import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi } from '../api/cards'
import { epicsApi } from '../api/epics'
import { labelsApi } from '../api/labels'
import { projectsApi } from '../api/projects'
import { EpicsPage } from './EpicsPage'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, memberships: [{ projectId: 9, role: 'OWNER' }] } }),
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/cards', () => ({
  cardsApi: {
    list: vi.fn(),
    epicTree: vi.fn(),
    getActivity: vi.fn().mockResolvedValue([]),
    update: vi.fn(),
    setAssignees: vi.fn(),
    setLabels: vi.fn(),
    restore: vi.fn(),
  },
}))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn(), assign: vi.fn(), create: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/labels', () => ({ labelsApi: { list: vi.fn() } }))
vi.mock('../api/comments', () => ({
  commentsApi: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('../api/attachments', () => ({
  attachmentsApi: { list: vi.fn().mockResolvedValue([]), upload: vi.fn(), remove: vi.fn(), fetchBlob: vi.fn() },
}))

const mBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mEpics = epicsApi as unknown as { list: ReturnType<typeof vi.fn>; create: ReturnType<typeof vi.fn> }
const mProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mLabels = labelsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mEpicTree = cardsApi as unknown as { epicTree: ReturnType<typeof vi.fn> }


function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/boards/1/vorhaben']}>
      <Routes>
        <Route path="/boards/:boardId/vorhaben" element={<EpicsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('EpicsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mBoards.get.mockResolvedValue({ id: 1, projectId: 9, name: 'B', createdAt: '', columns: [] })
    mCards.list.mockResolvedValue([])
    mEpics.create.mockResolvedValue({})
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
    mEpicTree.epicTree.mockResolvedValue([])
    mLabels.list.mockResolvedValue([])
  })

  it('zeigt den Breadcrumb-Pfad ab Projekte', async () => {
    mEpics.list.mockResolvedValue([])
    renderPage()
    expect(await screen.findByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/')
  })

  it('listet Epics mit Kürzel und Fortschritt', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    renderPage()

    expect(await screen.findByText('Auth')).toBeInTheDocument()
    expect(screen.getByText('AUT')).toBeInTheDocument()
    expect(screen.getByText('1 von 2 fertig')).toBeInTheDocument()
    expect(await screen.findByLabelText('Fortschritt Auth')).toBeInTheDocument()
  })

  // Die Gestalt ist hier keine Geschmacksfrage, sondern eine ausdrückliche Nutzerentscheidung
  // (#656): Vorhaben sind quadratische Kacheln in einem umbrechenden Raster, keine Zeilen über die
  // volle Breite. Sie war zuvor zweimal verloren gegangen, weil kein Test sie festgehalten hat.
  it('stellt die Vorhaben als quadratische Kacheln in einem Raster dar', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    renderPage()
    await screen.findByText('Auth')

    const raster = screen.getByTestId('vorhaben-raster')
    expect(raster).toHaveStyle({ display: 'grid' })
    // `auto-fill` bricht um und füllt die Breite; `auto-fit` würde eine einzelne Kachel über die
    // ganze Zeile strecken und damit das Quadrat aufgeben.
    expect(raster).toHaveStyle({
      gridTemplateColumns: 'repeat(auto-fill, minmax(min(240px, 100%), 1fr))',
    })

    const kachel = screen.getByTestId('vorhaben-kachel-9')
    expect(kachel).toHaveStyle({ aspectRatio: '1' })
    expect(kachel).toContainElement(screen.getByLabelText('Fortschritt Auth'))
  })

  it('legt über „Neues Epic" ein Epic an', async () => {
    mEpics.list.mockResolvedValue([])
    renderPage()
    await screen.findByText('Vorhaben')

    fireEvent.click(screen.getByRole('button', { name: 'Neues Vorhaben' }))
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Auth-Epic' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(mEpics.create).toHaveBeenCalledWith(1, 'Auth-Epic', expect.any(String), null))
  })

  it('zeigt bei ungültiger Board-ID einen Fehler und ruft keine API auf', async () => {
    render(
      <MemoryRouter initialEntries={['/boards/abc/vorhaben']}>
        <Routes>
          <Route path="/boards/:boardId/vorhaben" element={<EpicsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Ungültige Board-ID.')).toBeInTheDocument()
    expect(mBoards.get).not.toHaveBeenCalled()
    expect(mEpics.list).not.toHaveBeenCalled()
    expect(mCards.list).not.toHaveBeenCalled()
  })

  it('öffnet ein Epic per Klick im Detail-Modal mit seinen Kind-Karten', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: 'Text', shortcode: 'AUT', done: 1, total: 2, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    mCards.list.mockResolvedValue([
      {
        id: 30, boardId: 1, columnId: 10, number: 3, title: 'Kind', description: null,
        positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
        type: 'CARD', parentId: 9, shortcode: null, assignees: [], dueDate: null, labels: [],
      },
    ])
    mEpicTree.epicTree.mockResolvedValue([
      {
        number: 3, title: 'Kind', type: 'CARD', derivedFrom: null, depth: 0, done: false,
        blocked: false, dependencies: [], externalDependencies: [], externalOrigin: false,
        broken: false, labels: [],
      },
    ])
    renderPage()

    fireEvent.click(await screen.findByText('Auth'))

    expect(await screen.findByRole('tree')).toBeInTheDocument()
    expect(screen.getByText('Kind')).toBeInTheDocument()
  })

  it('zeigt 0 % Fortschritt für ein Epic ohne Stories und schließt das Detail-Modal', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Leer', description: 'X', shortcode: 'LEE', done: 0, total: 0, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    renderPage()

    const progress = await screen.findByLabelText('Fortschritt Leer')
    expect(progress).toHaveAttribute('aria-valuenow', '0')

    fireEvent.click(screen.getByText('Leer'))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('lädt die Rolle nach, wenn sie nicht in den Memberships steht', async () => {
    mBoards.get.mockResolvedValue({ id: 1, projectId: 42, name: 'B', createdAt: '', columns: [] })
    mEpics.list.mockResolvedValue([])
    mProjects.list.mockResolvedValue([{ id: 42, name: 'Fremd', role: 'VIEWER', createdAt: '' }])
    renderPage()

    await screen.findByText('Vorhaben')
    await waitFor(() => expect(mProjects.list).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: 'Neues Vorhaben' })).not.toBeInTheDocument()
  })

  it('behandelt einen fehlenden Board-Parameter als ungültig (boardId undefined)', () => {
    render(
      <MemoryRouter initialEntries={['/epics']}>
        <Routes>
          <Route path="/epics" element={<EpicsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByText('Ungültige Board-ID.')).toBeInTheDocument()
  })

  /**
   * Die Aufloesung einer Nummer geht erst gegen `epics`, dann gegen `cards`: `cardsApi.list`
   * filtert serverseitig auf `type == CARD`, ein Vorhaben steht dort also nicht. Seit #644 ist
   * dieser Weg ueber den Anforderungs-Verweis der Kachel erreichbar statt ueber den Baum.
   */
  it('öffnet über den Verweis ein Vorhaben, das nur über epics auflösbar ist', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 0, memberNumbers: [], rootNumbers: [], requirementCardNumber: 4 },
      { id: 11, number: 4, title: 'Grosses Vorhaben', description: null, shortcode: 'GRO', done: 0, total: 0, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    mCards.list.mockResolvedValue([])
    renderPage()
    await screen.findByText('Auth')

    // Der Titel-Rueckfall greift: `titelZuNummer` sucht nur in der Kartenliste, und ein Vorhaben
    // steht dort nicht. Die Nummer bleibt sichtbar, und der Klick loest sie ueber `epics` auf —
    // die Aufloesung ist also unabhaengig davon, ob der Titel angezeigt werden konnte.
    fireEvent.click(screen.getByRole('button', { name: '#4 · noch nicht geladen' }))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('nennt auf der gerenderten Seite nirgends „Epic"', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    renderPage()
    await screen.findByText('Auth')

    expect(screen.queryByText(/epic/i)).toBeNull()
  })

  it('trägt keine Reiter mehr — die Seite zeigt nur noch Kacheln', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    renderPage()
    await screen.findByText('Auth')

    expect(screen.queryAllByRole('tab')).toHaveLength(0)
  })

  it('trägt keine Aufklapp-Schaltfläche mehr', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2, memberNumbers: [7], rootNumbers: [7], requirementCardNumber: null },
    ])
    renderPage()
    await screen.findByText('Auth')

    // Der Baum steht seit #644 im Detail-Dialog; die Liste auf der Kachel entfaellt (Plan #637, E7).
    expect(screen.queryByRole('button', { name: /karten von/i })).toBeNull()
  })

  // --- Anforderung auf der Kachel (Issue #641) ------------------------------

  /** Ein Vorhaben mit Anforderung, dazu die passende Karte in der Kartenliste. */
  function mitAnforderung(nummer: number | null) {
    mEpics.list.mockResolvedValue([
      {
        id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2,
        memberNumbers: [], rootNumbers: [], requirementCardNumber: nummer,
      },
    ])
    mCards.list.mockResolvedValue([
      {
        id: 30, boardId: 1, columnId: 10, number: 7, title: 'Anforderungskarte', description: null,
        positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
        type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
      },
    ])
  }

  const anforderungsVerweis = () => screen.getByRole('button', { name: '#7 · Anforderungskarte' })

  it('nennt auf der Kachel die Anforderung mit Nummer und Titel', async () => {
    mitAnforderung(7)
    renderPage()

    expect(await screen.findByText('Anforderung:')).toBeInTheDocument()
    expect(anforderungsVerweis()).toBeInTheDocument()
  })

  /**
   * Revidiert Plan #637 (E6) bewusst: Dort wollte man keinen Platzhalter, hier gewinnt die
   * Unterscheidbarkeit — eine leere Stelle waere nicht von einem Ladefehler zu unterscheiden.
   */
  it('sagt ausdrücklich, wenn ein Vorhaben keine Anforderung trägt', async () => {
    mitAnforderung(null)
    renderPage()
    await screen.findByText('Auth')

    expect(screen.getByText('Keine Anforderung hinterlegt.')).toBeInTheDocument()
    // Der Verweis selbst bleibt aus: Es gibt nichts zu oeffnen.
    expect(screen.queryByText('Anforderung:')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^#\d+ · / })).not.toBeInTheDocument()
  })

  it('öffnet die Anforderungskarte per Klick auf den Verweis', async () => {
    mitAnforderung(7)
    renderPage()
    await screen.findByText('Auth')

    fireEvent.click(anforderungsVerweis())

    expect(await screen.findByRole('heading', { name: /Anforderungskarte/ })).toBeInTheDocument()
  })

  /**
   * Der Verweis muss per Tastatur bedienbar sein. Ein Test, der nur klickt, belegt das nicht:
   * jsx-a11y prueft ausschliesslich DOM-Elemente in Kleinschreibung, ein onClick auf einer
   * MUI-Anzeigekomponente kaeme also durch alle Gates und waere trotzdem unerreichbar.
   */
  it('öffnet die Anforderungskarte auch per Tastatur (Enter)', async () => {
    mitAnforderung(7)
    renderPage()
    await screen.findByText('Auth')

    anforderungsVerweis().focus()
    expect(anforderungsVerweis()).toHaveFocus()
    await userEvent.keyboard('{Enter}')

    expect(await screen.findByRole('heading', { name: /Anforderungskarte/ })).toBeInTheDocument()
  })

  it('öffnet beim Klick auf den Verweis nicht zusätzlich das Vorhaben-Detail', async () => {
    mitAnforderung(7)
    renderPage()
    await screen.findByText('Auth')

    fireEvent.click(anforderungsVerweis())

    await screen.findByRole('heading', { name: /Anforderungskarte/ })
    // Das Vorhaben-Detail zeigt seine zugeordneten Karten an — ohne stopPropagation stuende hier
    // der Dialog des Vorhabens statt der Anforderung.
    expect(screen.queryByText(/^Karten \(/)).not.toBeInTheDocument()
  })

  it('öffnet beim Klick auf die Kachelfläche weiterhin das Vorhaben-Detail', async () => {
    mitAnforderung(7)
    renderPage()

    fireEvent.click(await screen.findByText('Auth'))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('zeigt eine nicht auflösbare Anforderungsnummer an und öffnet beim Klick nichts', async () => {
    // Dauerzustand, kein Ladezustand: `cardsApi.list` filtert auf `type == CARD` und liefert
    // archivierte Karten nicht.
    mEpics.list.mockResolvedValue([
      {
        id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 0,
        memberNumbers: [], rootNumbers: [], requirementCardNumber: 42,
      },
    ])
    mCards.list.mockResolvedValue([])
    renderPage()

    const verweis = await screen.findByRole('button', { name: '#42 · noch nicht geladen' })
    fireEvent.click(verweis)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  // --- Zusammensetzung, Zustaende und Leer-Aussagen (Issue #663) -------------

  function karte(number: number, title: string, labels: number[] = []) {
    return {
      id: number * 10, boardId: 1, columnId: 10, number, title, description: null,
      positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null,
      dependencies: [], type: 'CARD', parentId: null, shortcode: null, assignees: [],
      dueDate: null, labels,
    }
  }

  it('zeigt die Zusammensetzung nach Art getrennt', async () => {
    mEpics.list.mockResolvedValue([
      {
        id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 8,
        memberNumbers: [1, 2, 3, 4, 5, 6, 7, 8], rootNumbers: [], requirementCardNumber: null,
      },
    ])
    mCards.list.mockResolvedValue([
      karte(1, '[Fachlich] Anforderung'),
      karte(2, '[Plan] Weg A'),
      karte(3, '[Plan] Weg B'),
      ...[4, 5, 6, 7, 8].map((n) => karte(n, `Paket ${n}`)),
    ])
    renderPage()

    expect(await screen.findByText('1 Anforderung')).toBeInTheDocument()
    expect(screen.getByText('2 Pläne')).toBeInTheDocument()
    expect(screen.getByText('5 Arbeitspakete')).toBeInTheDocument()
  })

  it('zeigt je gezähltem Label eine Marke mit Anzahl als Text', async () => {
    mEpics.list.mockResolvedValue([
      {
        id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 2,
        memberNumbers: [1, 2], rootNumbers: [], requirementCardNumber: null,
      },
    ])
    mCards.list.mockResolvedValue([karte(1, 'A', [70]), karte(2, 'B', [70, 71])])
    mLabels.list.mockResolvedValue([
      { id: 70, boardId: 1, name: 'stockt', color: '#f00', countOnEpicTile: true },
      { id: 71, boardId: 1, name: 'intern', color: '#ccc', countOnEpicTile: false },
    ])
    renderPage()

    expect(await screen.findByText('stockt 2')).toBeInTheDocument()
    // Beide Seiten: Das nicht gezaehlte Label erscheint nicht.
    expect(screen.queryByText(/intern/)).not.toBeInTheDocument()
  })

  it('sagt bei einem leeren Vorhaben, dass es leer ist — und zeigt trotzdem den Balken', async () => {
    mEpics.list.mockResolvedValue([
      {
        id: 9, number: 2, title: 'Leer', description: null, shortcode: 'LEE', done: 0, total: 0,
        memberNumbers: [], rootNumbers: [], requirementCardNumber: null,
      },
    ])
    renderPage()

    expect(await screen.findByText('Noch keine Karten zugeordnet.')).toBeInTheDocument()
    // Der Balken steht an JEDER Kachel (#656, Frage 4) — auch an der leeren.
    expect(screen.getByLabelText('Fortschritt Leer')).toBeInTheDocument()
    expect(screen.getByText('0 von 0 fertig')).toBeInTheDocument()
  })

  /**
   * `total` zaehlt ALLE Mitglieder. Sobald dieselbe Kachel "1 Anforderung, 2 Plaene, 5
   * Arbeitspakete" ausweist, waere "8 Arbeitspakete fertig" falsch. Die Beschriftung ist deshalb
   * IMMER neutral, auch bei sortenreinen Vorhaben — eine nur bedingte Umbenennung haette die
   * Bestandstests gruen gelassen und waere kein Fortschritt gewesen.
   */
  it('nennt in der Fortschrittszeile nie „Arbeitspakete", auch nicht bei sortenreinen Vorhaben', async () => {
    mEpics.list.mockResolvedValue([
      {
        id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2,
        memberNumbers: [1, 2], rootNumbers: [], requirementCardNumber: null,
      },
    ])
    mCards.list.mockResolvedValue([karte(1, 'Paket A'), karte(2, 'Paket B')])
    renderPage()

    await screen.findByText('Auth')
    expect(screen.getByText('1 von 2 fertig')).toBeInTheDocument()
    // Die Zusammensetzung nennt die Art sehr wohl -- die Fortschrittszeile nicht.
    expect(screen.getByText('2 Arbeitspakete')).toBeInTheDocument()
    expect(screen.queryByText(/\d+ von \d+ Arbeitspakete/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Arbeitspakete fertig/)).not.toBeInTheDocument()
  })

  it('ordnet die Kacheln nach Handlungsbedarf, leere ans Ende', async () => {
    mEpics.list.mockResolvedValue([
      {
        id: 1, number: 1, title: 'Leer', description: null, shortcode: 'LEE', done: 0, total: 0,
        memberNumbers: [], rootNumbers: [], requirementCardNumber: null,
      },
      {
        id: 2, number: 2, title: 'Wenig', description: null, shortcode: 'WEN', done: 0, total: 1,
        memberNumbers: [1], rootNumbers: [], requirementCardNumber: null,
      },
      {
        id: 3, number: 3, title: 'Viel', description: null, shortcode: 'VIE', done: 0, total: 2,
        memberNumbers: [2, 3], rootNumbers: [], requirementCardNumber: null,
      },
    ])
    mCards.list.mockResolvedValue([karte(1, 'A', [70]), karte(2, 'B', [70]), karte(3, 'C', [70])])
    mLabels.list.mockResolvedValue([
      { id: 70, boardId: 1, name: 'stockt', color: '#f00', countOnEpicTile: true },
    ])
    renderPage()

    await screen.findByText('Viel')
    // `getAllByTestId` liefert in DOM-Reihenfolge -- also genau der Reihenfolge auf dem Raster.
    expect(screen.getAllByTestId(/^vorhaben-kachel-/).map((k) => k.dataset.testid)).toEqual([
      'vorhaben-kachel-3',
      'vorhaben-kachel-2',
      'vorhaben-kachel-1',
    ])
  })
})
