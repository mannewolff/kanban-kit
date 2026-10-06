import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { cssRegel } from '../test/cssRegel'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi } from '../api/cards'
import { epicsApi } from '../api/epics'
import { labelsApi } from '../api/labels'
import { membersApi } from '../api/members'
import { projectsApi } from '../api/projects'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { hiddenEpicsStorageKey } from '../lib/boardHiddenEpics'
import { EpicsPage } from './EpicsPage'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, memberships: [{ projectId: 9, role: 'OWNER' }] } }),
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/cards', () => ({
  cardsApi: {
    list: vi.fn(),
    // Das CardDetailModal lädt die volle Beschreibung beim Öffnen nach (Issue #769).
    get: vi.fn().mockResolvedValue({ description: null }),
    epicTree: vi.fn(),
    byNumber: vi.fn(),
    getActivity: vi.fn().mockResolvedValue([]),
    update: vi.fn(),
    setAssignees: vi.fn(),
    setLabels: vi.fn(),
    restore: vi.fn(),
  },
}))
vi.mock('../api/members', () => ({ membersApi: { list: vi.fn() } }))
vi.mock('../api/epics', () => ({
  epicsApi: { list: vi.fn(), assign: vi.fn(), create: vi.fn(), remove: vi.fn() },
}))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/labels', () => ({ labelsApi: { list: vi.fn() } }))
vi.mock('../api/comments', () => ({
  commentsApi: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('../api/attachments', () => ({
  attachmentsApi: { list: vi.fn().mockResolvedValue([]), upload: vi.fn(), remove: vi.fn(), fetchBlob: vi.fn() },
}))

const mBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as { list: ReturnType<typeof vi.fn>; byNumber: ReturnType<typeof vi.fn> }
const mMembers = membersApi as unknown as { list: ReturnType<typeof vi.fn> }
const mEpics = epicsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
}
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
    mMembers.list.mockResolvedValue([])
  })

  /**
   * localStorage-Stub über eine echte Map — vorbelegbar und nach dem Test auslesbar. Nötig statt
   * des nativen `localStorage`: Unter Node 26 ist es deaktiviert (siehe `src/test/setup.ts`),
   * ein Test gegen das globale Objekt wäre „grün lokal, rot in CI".
   *
   * Steht auf der äußeren Ebene, weil ihn zwei Blöcke brauchen — das ⋮-Menü und die
   * Vorhaben-Auswahl im Detail-Dialog (Issue #756).
   */
  const stubStore = (entries: [string, string][]) => {
    const store = new Map<string, string>(entries)
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => { store.set(k, v) },
      removeItem: (k: string) => { store.delete(k) },
      clear: () => store.clear(), key: () => null, length: 0,
    })
    return store
  }

  afterEach(() => vi.unstubAllGlobals())

  /**
   * Öffnet das ⋮-Menü genau dieser Zeile. Steht auf der äußeren Ebene, weil ihn mehrere Blöcke
   * brauchen, die eigene Vorhaben-Fixtures führen.
   */
  const oeffneMenue = async (epic: { id: number; title: string }) => {
    const zeile = await screen.findByTestId(`vorhaben-zeile-${epic.id}`)
    fireEvent.click(within(zeile).getByLabelText(`Menü ${epic.title}`))
  }

  /** Die IDs der Zeilen in der Liste, in angezeigter Reihenfolge. */
  const zeilenIds = () =>
    screen.queryAllByTestId(/^vorhaben-zeile-/).map((z) => z.dataset.testid)

  /**
   * Der Umschalter „Ausgeblendete zeigen". Steht auf der äußeren Ebene, weil ihn mehrere Blöcke
   * brauchen — das Ausblenden, die Listenansicht und die Vorhaben-Auswahl im Detail-Dialog.
   */
  const umschalter = () => screen.getByLabelText(/^Ausgeblendete zeigen/)

  it('zeigt den Breadcrumb-Pfad ab Projekte', async () => {
    mEpics.list.mockResolvedValue([])
    renderPage()
    expect(await screen.findByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/projects')
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

  it('setzt den Fortschritt in Tabellenziffern, damit Zeilen vergleichbar bleiben (#960)', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    renderPage()

    expect(cssRegel(await screen.findByText('1 von 2 fertig'))).toContain('font-variant-numeric: tabular-nums')
  })

  // Die Gestalt ist eine ausdrückliche PO-Entscheidung (#1487, AK 1): Es gibt nur noch die Liste,
  // das Kachelraster aus #656 entfällt — auch als wählbare Alternative.
  it('stellt die Vorhaben als Liste dar, auch bei ausgeschaltetem Umschalter, und kennt kein Raster', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    renderPage()
    await screen.findByText('Auth')

    expect(umschalter()).not.toBeChecked()
    expect(screen.getByTestId('vorhaben-liste')).toContainElement(screen.getByTestId('vorhaben-zeile-9'))
    expect(screen.getByTestId('vorhaben-zeile-9')).toContainElement(screen.getByLabelText('Fortschritt Auth'))
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

  // --- Ladezustand und Fehlschlag beim Erstladen (Issue #783) ----------------

  it('zeigt einen Ladeindikator, solange die Ladeanfragen noch nicht beantwortet sind', async () => {
    let resolveEpics: (epics: unknown[]) => void = () => {}
    mEpics.list.mockReturnValue(new Promise((resolve) => { resolveEpics = resolve }))
    renderPage()

    expect(screen.getByRole('progressbar')).toBeInTheDocument()
    expect(screen.queryByText('Noch keine Vorhaben.')).not.toBeInTheDocument()
    expect(screen.queryByTestId('vorhaben-liste')).not.toBeInTheDocument()

    resolveEpics([])
    await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument())
    expect(await screen.findByText('Noch keine Vorhaben.')).toBeInTheDocument()
  })

  /**
   * Vorher: eine unbehandelte Promise-Ablehnung, `epics` blieb dauerhaft bei `[]` — nicht von
   * einem echten leeren Board zu unterscheiden. Welcher der vier Ladeaufrufe scheitert, ist
   * beliebig; hier stellvertretend `epicsApi.list`.
   */
  it('zeigt eine Fehlermeldung, wenn eine der vier Ladeanfragen beim ersten Versuch fehlschlägt', async () => {
    mEpics.list.mockRejectedValue(new Error('kaputt'))
    renderPage()

    expect(await screen.findByText('Vorhaben konnten nicht geladen werden.')).toBeInTheDocument()
    expect(screen.queryByText('Noch keine Vorhaben.')).not.toBeInTheDocument()
  })

  it('öffnet ein Epic per Klick im Detail-Modal mit seinen Kind-Karten', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: 'Text', shortcode: 'AUT', done: 1, total: 2, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ])
    mCards.list.mockResolvedValue([
      {
        id: 30, boardId: 1, columnId: 10, number: 3, title: 'Kind', description: null,
        positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
        type: 'CARD', parentId: 9, shortcode: null, assignees: [], dueDate: null, labels: [], status: null, canSetStatus: false,
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
   * dieser Weg ueber den Anforderungs-Verweis der Zeile erreichbar statt ueber den Baum.
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

  it('trägt keine Reiter — die Seite zeigt nur die Liste', async () => {
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

    // Der Baum steht seit #644 im Detail-Dialog; die Liste an der Zeile entfaellt (Plan #637, E7).
    expect(screen.queryByRole('button', { name: /karten von/i })).toBeNull()
  })

  // --- Anforderung an der Zeile (Issue #641) --------------------------------

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
        positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
        type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [], status: null, canSetStatus: false,
      },
    ])
  }

  const anforderungsVerweis = () => screen.getByRole('button', { name: '#7 · Anforderungskarte' })

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

  it('öffnet beim Klick auf die Zeilenfläche weiterhin das Vorhaben-Detail', async () => {
    mitAnforderung(7)
    renderPage()

    // Die Fläche selbst, nicht der Titel: Der Titel ist seit Issue #1306 ein eigener Knopf.
    fireEvent.click(await screen.findByTestId('vorhaben-zeile-9'))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  describe('Titelknopf der Zeile (Issue #1306)', () => {
    const auth = {
      id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1,
      memberNumbers: [1], rootNumbers: [1], requirementCardNumber: null,
    }
    const titelKnopf = async () =>
      within(await screen.findByTestId('vorhaben-zeile-9')).getByRole('button', { name: 'Auth' })

    it('erreicht den Titel per Tab als Knopf und öffnet mit Enter', async () => {
      mEpics.list.mockResolvedValue([auth])
      const user = userEvent.setup()
      renderPage()
      const knopf = await titelKnopf()

      umschalter().focus()
      await user.tab()
      expect(knopf).toHaveFocus()
      await user.keyboard('{Enter}')

      expect(await screen.findByRole('dialog')).toBeInTheDocument()
    })

    it('öffnet mit der Leertaste', async () => {
      mEpics.list.mockResolvedValue([auth])
      const user = userEvent.setup()
      renderPage()
      ;(await titelKnopf()).focus()

      await user.keyboard(' ')

      expect(await screen.findByRole('dialog')).toBeInTheDocument()
    })

    it('öffnet auch für einen Nur-Leser', async () => {
      mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'VIEWER', createdAt: '' }])
      mEpics.list.mockResolvedValue([auth])
      const user = userEvent.setup()
      renderPage()
      await waitFor(() => expect(mProjects.list).toHaveBeenCalled())
      ;(await titelKnopf()).focus()

      await user.keyboard('{Enter}')

      expect(await screen.findByRole('dialog')).toBeInTheDocument()
    })

    it('steht in der Tab-Reihenfolge vor dem ⋮, und Enter auf dem ⋮ öffnet nur das Menü', async () => {
      mEpics.list.mockResolvedValue([auth])
      const user = userEvent.setup()
      renderPage()
      ;(await titelKnopf()).focus()

      await user.tab()
      expect(screen.getByLabelText('Menü Auth')).toHaveFocus()
      await user.keyboard('{Enter}')

      expect(await screen.findByRole('menuitem', { name: 'Löschen' })).toBeInTheDocument()
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('öffnet per Mausklick genau einmal', async () => {
      mEpics.list.mockResolvedValue([auth])
      renderPage()

      fireEvent.click(await titelKnopf())

      expect(await screen.findAllByRole('dialog')).toHaveLength(1)
      await waitFor(() => expect(mEpicTree.epicTree).toHaveBeenCalledTimes(1))
    })
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
      positionInColumn: 0, archived: false, movedToDoneAt: null,
      dependencies: [], type: 'CARD', parentId: null, shortcode: null, assignees: [],
      dueDate: null, labels,
    }
  }

  it('sagt bei einem leeren Vorhaben, dass es leer ist — und zeigt trotzdem den Balken', async () => {
    mEpics.list.mockResolvedValue([
      {
        id: 9, number: 2, title: 'Leer', description: null, shortcode: 'LEE', done: 0, total: 0,
        memberNumbers: [], rootNumbers: [], requirementCardNumber: null,
      },
    ])
    renderPage()

    expect(await screen.findByText('Noch keine Karten zugeordnet.')).toBeInTheDocument()
    // Der Balken steht an JEDER Zeile (#656, Frage 4) — auch an der leeren.
    expect(screen.getByLabelText('Fortschritt Leer')).toBeInTheDocument()
    expect(screen.getByText('0 von 0 fertig')).toBeInTheDocument()
  })

  /**
   * `total` zaehlt ALLE Mitglieder. Sobald dieselbe Zeile "1 Anforderung, 2 Plaene, 5
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

  it('ordnet die Zeilen nach Handlungsbedarf, leere ans Ende', async () => {
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
    // `zeilenIds` liefert in DOM-Reihenfolge -- also genau der Reihenfolge in der Liste.
    expect(zeilenIds()).toEqual(['vorhaben-zeile-3', 'vorhaben-zeile-2', 'vorhaben-zeile-1'])
  })

  // --- Ausblenden am Schalter der Zeile (Issue #1490, zuvor ⋮-Menü #705/#669) ----

  describe('Ausblenden am Schalter', () => {
    const auth = {
      id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1,
      memberNumbers: [1], rootNumbers: [1], requirementCardNumber: null,
    }
    const zahlung = {
      id: 10, number: 3, title: 'Zahlung', description: null, shortcode: 'ZAH', done: 0, total: 1,
      memberNumbers: [2], rootNumbers: [2], requirementCardNumber: null,
    }

    /** Wechselt den Routen-Parameter, ohne die Seite neu zu montieren. */
    function BoardWechsler() {
      const navigate = useNavigate()
      return <button onClick={() => navigate('/boards/2/vorhaben')}>Board wechseln</button>
    }

    /** Rendert die Seite auf einem beliebigen Board, wahlweise mit Wechsel-Knopf davor. */
    const renderAufBoard = (pfad: string, mitWechsler = false) =>
      render(
        <MemoryRouter initialEntries={[pfad]}>
          {mitWechsler && <BoardWechsler />}
          <Routes>
            <Route path="/boards/:boardId/vorhaben" element={<EpicsPage />} />
          </Routes>
        </MemoryRouter>,
      )

    const schalter = (kuerzel: string, aktion: 'ausblenden' | 'einblenden') =>
      screen.getByLabelText(`Vorhaben ${kuerzel} ${aktion}`)

    it('lässt ein eben ausgeblendetes Vorhaben ausgegraut stehen und zählt es sofort (AK 6)', async () => {
      const store = stubStore([])
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben')

      await screen.findByTestId('vorhaben-zeile-9')
      expect(umschalter()).not.toBeChecked()
      fireEvent.click(schalter('AUT', 'ausblenden'))

      // Board und Zahl wirken sofort — derselbe Schlüssel und dasselbe Format, die das Board liest.
      await waitFor(() =>
        expect(JSON.parse(store.get(hiddenEpicsStorageKey(1)) as string)).toEqual([9]),
      )
      expect(screen.getByLabelText('Ausgeblendete zeigen (1)')).toBeInTheDocument()
      // Die Zeile bleibt bis zum nächsten Laden stehen, als ausgeblendet gekennzeichnet.
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
      expect(within(screen.getByTestId('vorhaben-zeile-9')).getByText('Ausgeblendet')).toBeInTheDocument()
      expect(umschalter()).not.toBeChecked()
    })

    it('nimmt ein versehentliches Ausblenden am selben Schalter zurück', async () => {
      const store = stubStore([])
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben')

      await screen.findByTestId('vorhaben-zeile-9')
      fireEvent.click(schalter('AUT', 'ausblenden'))
      fireEvent.click(await screen.findByLabelText('Vorhaben AUT einblenden'))

      // Die leere Menge löscht den Schlüssel, statt ein aussageloses `[]` zu hinterlassen (Plan #846, E8).
      await waitFor(() => expect(store.has(hiddenEpicsStorageKey(1))).toBe(false))
      expect(screen.getByLabelText('Ausgeblendete zeigen (0)')).toBeInTheDocument()
      expect(within(screen.getByTestId('vorhaben-zeile-9')).queryByText('Ausgeblendet')).toBeNull()
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
    })

    it('lässt die Zeile beim Umlegen von „Ausgeblendete zeigen" hin und zurück stehen', async () => {
      stubStore([])
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben')

      await screen.findByTestId('vorhaben-zeile-9')
      fireEvent.click(schalter('AUT', 'ausblenden'))
      fireEvent.click(umschalter())
      fireEvent.click(umschalter())

      // Erst das nächste Laden der Seite nimmt die Zeile heraus (Plan #1488, E5).
      expect(umschalter()).not.toBeChecked()
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
    })

    it('zeigt ein ausgeblendetes Vorhaben nach dem nächsten Laden der Seite nicht mehr', async () => {
      const store = stubStore([])
      mEpics.list.mockResolvedValue([auth, zahlung])
      const { unmount: verlasseErsteSitzung } = renderAufBoard('/boards/1/vorhaben')

      await screen.findByTestId('vorhaben-zeile-9')
      fireEvent.click(schalter('AUT', 'ausblenden'))
      await waitFor(() => expect(store.has(hiddenEpicsStorageKey(1))).toBe(true))
      verlasseErsteSitzung()

      // Frischer Mount auf demselben Board: der Stand kommt aus localStorage zurück.
      const { unmount: verlasseZweiteSitzung } = renderAufBoard('/boards/1/vorhaben')
      await screen.findByTestId('vorhaben-zeile-10')
      expect(zeilenIds()).toEqual(['vorhaben-zeile-10'])
      verlasseZweiteSitzung()

      // Ein anderes Board hat einen eigenen Schlüssel und ist von der Ausblendung unberührt.
      renderAufBoard('/boards/2/vorhaben')
      await screen.findByTestId('vorhaben-zeile-9')
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
    })

    it('holt Ausgeblendete über den Umschalter in die Liste und blendet sie dort wieder ein', async () => {
      const store = stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([9])]])
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben')

      await screen.findByTestId('vorhaben-zeile-10')
      expect(zeilenIds()).toEqual(['vorhaben-zeile-10'])

      fireEvent.click(umschalter())

      const zeile = await screen.findByTestId('vorhaben-zeile-9')
      // Ein sichtbarer Text, keine bloße Dimmung: Nur so ist der Zustand für Screenreader
      // wahrnehmbar und im Test ohne geratenen Stilwert greifbar.
      expect(within(zeile).getByText('Ausgeblendet')).toBeInTheDocument()

      fireEvent.click(within(zeile).getByLabelText('Vorhaben AUT einblenden'))

      await waitFor(() => expect(store.has(hiddenEpicsStorageKey(1))).toBe(false))
      expect(within(screen.getByTestId('vorhaben-zeile-9')).queryByText('Ausgeblendet')).toBeNull()
      expect(umschalter()).toBeChecked()
    })

    it('zählt am Umschalter keine verwaiste ID mit, zu der kein Vorhaben existiert', async () => {
      // Eine ID im Schlüssel überlebt das Löschen ihres Vorhabens (Issue #704). Zählte sie mit,
      // stünde am Umschalter eine Zahl, hinter der im Zeige-Modus weniger Zeilen erscheinen.
      stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([9, 4711])]])
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben')

      expect(await screen.findByLabelText('Ausgeblendete zeigen (1)')).toBeInTheDocument()
    })

    it('zeigt den Umschalter auch dann, wenn nichts ausgeblendet ist', async () => {
      stubStore([])
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben')

      await screen.findByTestId('vorhaben-zeile-9')
      // Beschriftung und Zahl bleiben wörtlich, auch bei „0" (PO-Entscheidung, 2026-09-14).
      expect(screen.getByLabelText('Ausgeblendete zeigen (0)')).toBeInTheDocument()
    })

    it('filtert ohne gespeicherten Schlüssel keine einzige Zeile', async () => {
      // Die Seiten-Hälfte des Belegs, dass `showAllHidden` am Board (es löscht den Schlüssel)
      // auch die Ausblendung in der Liste aufhebt.
      stubStore([])
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben')

      await screen.findByTestId('vorhaben-zeile-9')
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
    })

    it('liest den Stand beim Board-Wechsel ohne Remount neu und setzt den Umschalter zurück', async () => {
      // Die Route hält die Komponente bei einem reinen Parameterwechsel gemountet (E11). Ohne
      // Nachlesen filterte Board 2 mit dem Zustand von Board 1.
      stubStore([
        [hiddenEpicsStorageKey(1), JSON.stringify([9])],
        [hiddenEpicsStorageKey(2), JSON.stringify([10])],
      ])
      mBoards.get.mockImplementation((bid: number) =>
        Promise.resolve({ id: bid, projectId: 9, name: `B${bid}`, createdAt: '', columns: [] }),
      )
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben', true)

      await screen.findByTestId('vorhaben-zeile-10')
      fireEvent.click(umschalter())
      expect(umschalter()).toBeChecked()

      fireEvent.click(screen.getByText('Board wechseln'))

      await waitFor(() => expect(zeilenIds()).toEqual(['vorhaben-zeile-9']))
      expect(umschalter()).not.toBeChecked()
    })

    it('vergisst eben Ausgeblendetes beim Board-Wechsel', async () => {
      // Board 2 hat AUT ausgeblendet. Trüge die Seite die eben auf Board 1 ausgeblendete Zeile
      // über den Wechsel mit, stünde AUT dort, obwohl es auf Board 2 niemand gerade ausblendete.
      stubStore([[hiddenEpicsStorageKey(2), JSON.stringify([9])]])
      mBoards.get.mockImplementation((bid: number) =>
        Promise.resolve({ id: bid, projectId: 9, name: `B${bid}`, createdAt: '', columns: [] }),
      )
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben', true)

      await screen.findByTestId('vorhaben-zeile-9')
      fireEvent.click(schalter('AUT', 'ausblenden'))
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])

      fireEvent.click(screen.getByText('Board wechseln'))

      await waitFor(() => expect(zeilenIds()).toEqual(['vorhaben-zeile-10']))
    })

    it('blendet auch dann aus, wenn das Schreiben in localStorage fehlschlägt', async () => {
      vi.stubGlobal('localStorage', {
        getItem: () => null,
        setItem: () => { throw new Error('localStorage nicht verfügbar') },
        removeItem: () => { throw new Error('localStorage nicht verfügbar') },
        clear: () => {}, key: () => null, length: 0,
      })
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben')

      await screen.findByTestId('vorhaben-zeile-9')
      fireEvent.click(schalter('AUT', 'ausblenden'))

      // Der Zustand wirkt in dieser Sitzung — nur das Merken fällt aus (Plan #703, E8).
      expect(await screen.findByLabelText('Ausgeblendete zeigen (1)')).toBeInTheDocument()
      expect(within(screen.getByTestId('vorhaben-zeile-9')).getByText('Ausgeblendet')).toBeInTheDocument()
    })

    it('blendet nichts aus, wenn das Lesen aus localStorage wirft', async () => {
      vi.stubGlobal('localStorage', {
        getItem: () => { throw new Error('localStorage nicht verfügbar') },
        setItem: () => { throw new Error('localStorage nicht verfügbar') },
        removeItem: () => { throw new Error('localStorage nicht verfügbar') },
        clear: () => {}, key: () => null, length: 0,
      })
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderAufBoard('/boards/1/vorhaben')

      await screen.findByTestId('vorhaben-zeile-9')
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
      // Der Umschalter hängt an den Vorhaben, nicht am Ausblende-Zustand — ein defektes
      // `localStorage` nimmt ihn deshalb nicht weg, es lässt nur die Zahl bei „0".
      expect(screen.getByLabelText('Ausgeblendete zeigen (0)')).toBeInTheDocument()
    })

    it('ist per Tastatur erreichbar und auslösbar', async () => {
      stubStore([])
      mEpics.list.mockResolvedValue([auth])
      const user = userEvent.setup()
      renderPage()
      await screen.findByTestId('vorhaben-zeile-9')

      // Vom letzten Bedienelement vor der Liste über den Umschalter, den Titelknopf und das ⋮ zum
      // Schalter ganz rechts: Er liegt in der Tab-Reihenfolge und ist nicht bloß per Maus zu treffen.
      screen.getByRole('button', { name: 'Neues Vorhaben' }).focus()
      await user.tab()
      expect(umschalter()).toHaveFocus()
      await user.tab()
      await user.tab()
      await user.tab()
      expect(schalter('AUT', 'ausblenden')).toHaveFocus()

      await user.keyboard(' ')

      expect(await screen.findByLabelText('Ausgeblendete zeigen (1)')).toBeInTheDocument()
    })
  })

  // --- Listenansicht (Issue #848, fachlich #814; seit #1490 die einzige Ansicht) ----

  /**
   * Die zehn Kriterien AK 1 bis AK 10 aus Issue #814 — je ein Testfall. AK 11 (Tastatur und
   * Wahrnehmbarkeit der Schalterstellung) liegt an der Komponente selbst und ist in
   * `EpicVisibilityList.test.tsx` belegt.
   */
  describe('Listenansicht', () => {
    const auth = {
      id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1,
      memberNumbers: [1], rootNumbers: [1], requirementCardNumber: null,
    }
    const pflege = {
      id: 11, number: 4, title: 'Pflege', description: null, shortcode: 'PFL', done: 0, total: 1,
      memberNumbers: [3], rootNumbers: [3], requirementCardNumber: null,
    }
    const zahlung = {
      id: 10, number: 3, title: 'Zahlung', description: null, shortcode: 'ZAH', done: 0, total: 1,
      memberNumbers: [2], rootNumbers: [2], requirementCardNumber: null,
    }
    /** Absichtlich unsortiert übergeben: `sortEpics` ordnet nach Kürzel zu AUT, PFL, ZAH. */
    const drei = [zahlung, auth, pflege]

    const zeilenSchalter = (zeile: HTMLElement, kuerzel: string, aktion: string) =>
      within(zeile).getByLabelText(`Vorhaben ${kuerzel} ${aktion}`)

    it('AK 1: zeigt den Umschalter mit „(0)", sobald das Board ein Vorhaben hat', async () => {
      stubStore([])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      expect(await screen.findByLabelText('Ausgeblendete zeigen (0)')).toBeInTheDocument()
      expect(umschalter()).not.toBeChecked()
    })

    it('AK 1: lässt den Umschalter bei einem Board ohne Vorhaben aus', async () => {
      stubStore([])
      mEpics.list.mockResolvedValue([])
      renderPage()

      await screen.findByText('Noch keine Vorhaben.')
      expect(screen.queryByLabelText(/^Ausgeblendete zeigen/)).toBeNull()
    })

    it('AK 2: zeigt bei ausgeschaltetem Umschalter die Liste ohne die Ausgeblendeten', async () => {
      stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([11])]])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      await screen.findByTestId('vorhaben-zeile-9')
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
      expect(screen.getByTestId('vorhaben-liste')).toBeInTheDocument()
    })

    it('AK 3: zeigt im Zeige-Modus eine Zeile je Vorhaben, Ausgeblendete eingeschlossen', async () => {
      stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([11])]])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      fireEvent.click(await screen.findByLabelText(/^Ausgeblendete zeigen/))

      expect(await screen.findByTestId('vorhaben-liste')).toBeInTheDocument()
      // Eingeblendete und Ausgeblendete gemeinsam, jedes genau einmal.
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-11', 'vorhaben-zeile-10'])
      // Kopf, „Neues Vorhaben" und Umschalter bleiben.
      expect(screen.getByRole('button', { name: 'Neues Vorhaben' })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Projekte' })).toBeInTheDocument()
    })

    it('AK 4: hält die Reihenfolge vor dem Ausfiltern und verschiebt beim Schalten nichts', async () => {
      stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([11])]])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      // Ohne Zeige-Modus fehlt PFL, mit ihm steht es an derselben Stelle wie ohne Filter.
      await screen.findByTestId('vorhaben-zeile-9')
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])

      fireEvent.click(umschalter())
      const vorher = zeilenIds()
      expect(vorher).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-11', 'vorhaben-zeile-10'])

      fireEvent.click(zeilenSchalter(screen.getByTestId('vorhaben-zeile-11'), 'PFL', 'einblenden'))

      await waitFor(() =>
        expect(zeilenSchalter(screen.getByTestId('vorhaben-zeile-11'), 'PFL', 'ausblenden')).toBeChecked(),
      )
      expect(zeilenIds()).toEqual(vorher)
    })

    it('AK 5: schreibt ein Umlegen im Zeige-Modus sofort fort und filtert nach dem Ausschalten', async () => {
      const store = stubStore([])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      fireEvent.click(await screen.findByLabelText(/^Ausgeblendete zeigen/))
      fireEvent.click(zeilenSchalter(await screen.findByTestId('vorhaben-zeile-9'), 'AUT', 'ausblenden'))

      // Derselbe Schlüssel und dasselbe Format, die das Board liest (`lib/boardHiddenEpics`).
      await waitFor(() =>
        expect(JSON.parse(store.get(hiddenEpicsStorageKey(1)) as string)).toEqual([9]),
      )

      // Im Zeige-Modus ausgeblendet ist nicht „eben ausgeblendet" (Plan #1488, E5): Die Zeile war
      // dort ohnehin sichtbar, nach dem Ausschalten fehlt sie.
      fireEvent.click(umschalter())

      await waitFor(() => expect(zeilenIds()).toEqual(['vorhaben-zeile-11', 'vorhaben-zeile-10']))
    })

    it('AK 6: zieht die Zahl am Umschalter jedem Umlegen in der Liste sofort nach', async () => {
      stubStore([])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      fireEvent.click(await screen.findByLabelText('Ausgeblendete zeigen (0)'))
      fireEvent.click(zeilenSchalter(await screen.findByTestId('vorhaben-zeile-9'), 'AUT', 'ausblenden'))

      expect(await screen.findByLabelText('Ausgeblendete zeigen (1)')).toBeInTheDocument()

      fireEvent.click(zeilenSchalter(screen.getByTestId('vorhaben-zeile-10'), 'ZAH', 'ausblenden'))

      expect(await screen.findByLabelText('Ausgeblendete zeigen (2)')).toBeInTheDocument()
    })

    it('AK 7: lässt die Liste beim Ausblenden und beim letzten Einblenden stehen', async () => {
      stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([9])]])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      fireEvent.click(await screen.findByLabelText(/^Ausgeblendete zeigen/))

      // Ausblenden nimmt die Zeile im Zeige-Modus nicht aus der Liste.
      fireEvent.click(zeilenSchalter(await screen.findByTestId('vorhaben-zeile-10'), 'ZAH', 'ausblenden'))
      await waitFor(() =>
        expect(zeilenSchalter(screen.getByTestId('vorhaben-zeile-10'), 'ZAH', 'einblenden')).toBeInTheDocument(),
      )
      expect(zeilenIds()).toHaveLength(3)

      // Und die Seite bleibt in der Liste, auch wenn nichts mehr ausgeblendet ist.
      fireEvent.click(zeilenSchalter(screen.getByTestId('vorhaben-zeile-9'), 'AUT', 'einblenden'))
      fireEvent.click(zeilenSchalter(screen.getByTestId('vorhaben-zeile-10'), 'ZAH', 'einblenden'))

      expect(await screen.findByLabelText('Ausgeblendete zeigen (0)')).toBeInTheDocument()
      expect(screen.getByTestId('vorhaben-liste')).toBeInTheDocument()
    })

    it('AK 8: öffnet ein Vorhaben aus der Liste im Detail-Dialog', async () => {
      stubStore([])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      fireEvent.click(await screen.findByLabelText(/^Ausgeblendete zeigen/))
      fireEvent.click(within(await screen.findByTestId('vorhaben-zeile-9')).getByText('Auth'))

      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByLabelText('Fortschritt')).toHaveTextContent('0 von 1 fertig')
    })

    it('führt im ⋮-Menü kein „Ausblenden" mehr — das tut der Schalter (Plan #1488, E7)', async () => {
      stubStore([])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      await oeffneMenue(auth)

      expect(screen.getAllByRole('menuitem').map((m) => m.textContent)).toEqual(['Löschen'])
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('AK 10: lässt einen Nur-Leser in der Liste schalten', async () => {
      // Ausblenden verändert nichts am Server — einem VIEWER zu verbieten, seine eigene Ansicht
      // aufzuräumen, wäre keine Schutzwirkung (Plan #846, E12).
      const store = stubStore([])
      mBoards.get.mockResolvedValue({ id: 1, projectId: 42, name: 'B', createdAt: '', columns: [] })
      mProjects.list.mockResolvedValue([{ id: 42, name: 'Fremd', role: 'VIEWER', createdAt: '' }])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      await waitFor(() => expect(mProjects.list).toHaveBeenCalled())
      expect(screen.queryByRole('button', { name: 'Neues Vorhaben' })).not.toBeInTheDocument()

      fireEvent.click(umschalter())
      fireEvent.click(zeilenSchalter(await screen.findByTestId('vorhaben-zeile-9'), 'AUT', 'ausblenden'))

      await waitFor(() =>
        expect(JSON.parse(store.get(hiddenEpicsStorageKey(1)) as string)).toEqual([9]),
      )
    })
    it('öffnet in der Liste das Menü am Vorhaben für jemanden, der bearbeiten darf (Issue #1489)', async () => {
      stubStore([])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      fireEvent.click(within(await screen.findByTestId('vorhaben-zeile-9')).getByRole('button', { name: 'Menü Auth' }))

      expect(await screen.findByRole('menuitem', { name: 'Löschen' })).toBeInTheDocument()
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('zeigt einem Nur-Leser in der Liste keinen Menü-Knopf (Issue #1489)', async () => {
      stubStore([])
      mBoards.get.mockResolvedValue({ id: 1, projectId: 42, name: 'B', createdAt: '', columns: [] })
      mProjects.list.mockResolvedValue([{ id: 42, name: 'Fremd', role: 'VIEWER', createdAt: '' }])
      mEpics.list.mockResolvedValue(drei)
      renderPage()

      await waitFor(() => expect(mProjects.list).toHaveBeenCalled())
      const zeile = await screen.findByTestId('vorhaben-zeile-9')

      expect(within(zeile).queryByRole('button', { name: /^Menü / })).not.toBeInTheDocument()
    })
  })

  // --- Vorhaben-Auswahl im Detail-Dialog (Issue #756, Plan #717 A1/A2) -------

  /**
   * Der Optionsvorrat des Kartenformulars folgt der Ausblendung des Boards, die Anzeige nicht:
   * `epics` bleibt die volle Liste (Titel, Fortschritt), `selectableEpics` trägt die Auswahl.
   */
  describe('Vorhaben-Auswahl im Detail-Dialog', () => {
    const auth = {
      id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1,
      memberNumbers: [], rootNumbers: [], requirementCardNumber: null,
    }
    const zahlung = {
      id: 10, number: 3, title: 'Zahlung', description: null, shortcode: 'ZAH', done: 0, total: 1,
      memberNumbers: [], rootNumbers: [], requirementCardNumber: 4,
    }

    /** Die Anforderungskarte von `zahlung`, wahlweise dem ausgeblendeten `auth` zugeordnet. */
    const anforderung = (parentId: number | null) => ({
      id: 40, boardId: 1, columnId: 10, number: 4, title: 'Anforderung', description: null,
      positionInColumn: 0, archived: false, movedToDoneAt: null,
      dependencies: [], type: 'CARD', parentId, shortcode: null, assignees: [], dueDate: null,
      labels: [], status: null, canSetStatus: false,
    })

    it('lässt den Umschalter „Ausgeblendete zeigen" den Optionsvorrat des Formulars unberührt', async () => {
      // Der Umschalter steuert die Liste dieser Seite. Zöge er hier mit, entschiede eine
      // Ansichtseinstellung darüber, was man einer Karte zuordnen kann (Plan #717, A1).
      stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([9])]])
      mEpics.list.mockResolvedValue([auth, zahlung])
      mCards.list.mockResolvedValue([anforderung(null)])
      renderPage()

      // Die Karte wird VOR dem Umschalten geöffnet; der Dialog bleibt beim Umschalten offen.
      fireEvent.click(await screen.findByRole('button', { name: '#4 · Anforderung' }))
      await screen.findByRole('dialog')
      fireEvent.click(umschalter())

      fireEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))

      expect(await screen.findByRole('option', { name: 'ZAH – Zahlung' })).toBeInTheDocument()
      expect(screen.queryByRole('option', { name: 'AUT – Auth' })).toBeNull()
    })

    it('bietet ein ausgeblendetes Vorhaben nicht zur Auswahl an, zeigt seinen Titel aber weiter', async () => {
      stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([9])]])
      mEpics.list.mockResolvedValue([auth, zahlung])
      mCards.list.mockResolvedValue([anforderung(9)])
      renderPage()

      fireEvent.click(await screen.findByRole('button', { name: '#4 · Anforderung' }))
      fireEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))

      // Der Titel stammt aus der vollen `epics`-Liste; im Optionsvorrat steht das Vorhaben nicht
      // mehr, das Feld bleibt deshalb lesend statt nur „(kein Vorhaben)" anzubieten.
      expect(await screen.findByLabelText('Vorhaben')).toHaveValue('AUT – Auth')
      expect(screen.queryByRole('option', { name: 'AUT – Auth' })).toBeNull()
    })

    it('zeigt am ausgeblendeten Vorhaben aus der Liste weiterhin seinen Fortschritt', async () => {
      stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([9])]])
      mEpics.list.mockResolvedValue([auth, zahlung])
      renderPage()

      fireEvent.click(await screen.findByLabelText(/^Ausgeblendete zeigen/))
      const zeile = await screen.findByTestId('vorhaben-zeile-9')
      fireEvent.click(within(zeile).getByText('Auth'))

      // Fortschritt und Titel kommen aus `epics` — die Filterung trifft nur den Optionsvorrat.
      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByLabelText('Fortschritt')).toHaveTextContent('0 von 1 fertig')
    })
  })

  /**
   * Der Detail-Dialog bekommt von dieser Seite denselben Kontext wie von den übrigen Aufrufstellen
   * (Issue #687). Ohne ihn verpuffte der Klick auf eine Baumzeile lautlos, und die Karte zeigte
   * Zuständige und Labels als nackte IDs.
   */
  describe('Kontext des Detail-Dialogs', () => {
    const vorhabenAuth = {
      id: 9, number: 2, title: 'Auth', description: 'Text', shortcode: 'AUT', done: 0, total: 1,
      memberNumbers: [3], rootNumbers: [3], requirementCardNumber: null,
    }
    const baumZeile = {
      number: 3, title: 'Kind', type: 'CARD', derivedFrom: null, depth: 0, done: false,
      blocked: false, dependencies: [], externalDependencies: [], externalOrigin: false,
      broken: false, labels: [],
    }
    const verknuepfteKarte = (assignees: number[]) => ({
      id: 30, boardId: 1, columnId: 10, number: 3, title: 'Kind aus dem Baum', description: null,
      type: 'CARD', dependencies: [], assignees, labels: [], parentId: null, shortcode: null,
      dueDate: null, archived: false, derivedFrom: null, status: null, canSetStatus: false,
    })

    /** Öffnet das Vorhaben und löst Enter auf der ersten Baumzeile aus. */
    const oeffneBaumzeile = async () => {
      fireEvent.click(await screen.findByText('Auth'))
      const zeile = await screen.findByRole('treeitem', { name: /Kind/ })
      fireEvent.keyDown(zeile, { key: 'Enter' })
    }

    it('lädt die Karte einer Baumzeile in denselben Dialog', async () => {
      mEpics.list.mockResolvedValue([vorhabenAuth])
      mEpicTree.epicTree.mockResolvedValue([baumZeile])
      mCards.byNumber.mockResolvedValue(verknuepfteKarte([]))
      renderPage()

      await oeffneBaumzeile()

      // Über die `projectId` des Boards aufgelöst — ohne sie baut der Dialog den Handler nicht.
      await waitFor(() => expect(mCards.byNumber).toHaveBeenCalledWith(9, 3))
      expect(await screen.findByText('Kind aus dem Baum')).toBeInTheDocument()
    })

    it('zeigt an der Karte aus dem Baum die Namen der Zuständigen statt der IDs', async () => {
      mEpics.list.mockResolvedValue([vorhabenAuth])
      mEpicTree.epicTree.mockResolvedValue([baumZeile])
      mCards.byNumber.mockResolvedValue(verknuepfteKarte([7]))
      mMembers.list.mockResolvedValue([
        { userId: 7, email: 'anna@example.org', displayName: 'Anna Arbeit', role: 'MEMBER' },
      ])
      renderPage()

      await oeffneBaumzeile()

      expect(await screen.findByText('Anna Arbeit')).toBeInTheDocument()
      expect(screen.queryByText('#7')).toBeNull()
    })

    it('bietet im Edit-Modus die Board-Labels mit Namen und die geladenen Vorhaben an', async () => {
      mEpics.list.mockResolvedValue([
        { ...vorhabenAuth, requirementCardNumber: 3, memberNumbers: [], rootNumbers: [] },
      ])
      mCards.list.mockResolvedValue([
        {
          id: 30, boardId: 1, columnId: 10, number: 3, title: 'Anforderung', description: null,
          positionInColumn: 0, archived: false, movedToDoneAt: null,
          dependencies: [], type: 'CARD', parentId: null, shortcode: null, assignees: [],
          dueDate: null, labels: [5], status: null, canSetStatus: false,
        },
      ])
      mLabels.list.mockResolvedValue([{ id: 5, name: 'Fehler', color: '#ff0000' }])
      renderPage()
      await screen.findByText('Auth')

      fireEvent.click(screen.getByRole('button', { name: '#3 · Anforderung' }))
      fireEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))

      // Ohne `boardLabels` bliebe nur die ID `#5`, ohne `epics` nur „(kein Vorhaben)".
      expect(await screen.findByText('Fehler')).toBeInTheDocument()
      expect(screen.getByRole('option', { name: 'AUT – Auth' })).toBeInTheDocument()
    })

    it('lässt einen fehlgeschlagenen Mitglieder-Abruf die Seite nicht scheitern', async () => {
      mMembers.list.mockRejectedValue(new Error('kein Zugriff'))
      mEpics.list.mockResolvedValue([vorhabenAuth])
      renderPage()

      expect(await screen.findByText('Auth')).toBeInTheDocument()
      await waitFor(() => expect(mMembers.list).toHaveBeenCalledWith(9))
    })
  })

  // --- Löschen über das ⋮-Menü (Issue #820, Plan #819, fachlich #815) --------

  describe('⋮-Menü „Löschen"', () => {
    const ohneKarten = {
      id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 0,
      memberNumbers: [], rootNumbers: [], requirementCardNumber: null,
    }
    const eineKarte = { ...ohneKarten, total: 1, memberNumbers: [1], rootNumbers: [1] }
    const zweiKarten = { ...ohneKarten, total: 2, memberNumbers: [1, 2], rootNumbers: [1, 2] }
    const zahlung = {
      id: 10, number: 3, title: 'Zahlung', description: null, shortcode: 'ZAH', done: 0, total: 0,
      memberNumbers: [], rootNumbers: [], requirementCardNumber: null,
    }

    /**
     * Rendert die Seite im `SnackbarProvider`. Ohne ihn ist `useSnackbar` ein No-op, und der
     * Fehlerfall unten wäre nicht prüfbar — der Toast entstünde nie.
     */
    const renderMitSnackbar = () =>
      render(
        <MemoryRouter initialEntries={['/boards/1/vorhaben']}>
          <SnackbarProvider>
            <Routes>
              <Route path="/boards/:boardId/vorhaben" element={<EpicsPage />} />
            </Routes>
          </SnackbarProvider>
        </MemoryRouter>,
      )

    /** Öffnet das ⋮-Menü und darin die Rückfrage. */
    const oeffneRueckfrage = async (epic: { id: number; title: string }) => {
      await oeffneMenue(epic)
      fireEvent.click(screen.getByRole('menuitem', { name: 'Löschen' }))
    }

    it('bietet „Löschen" im Menü an, wer bearbeiten darf', async () => {
      mEpics.list.mockResolvedValue([ohneKarten])
      renderMitSnackbar()

      await oeffneMenue(ohneKarten)

      expect(screen.getByRole('menuitem', { name: 'Löschen' })).toBeInTheDocument()
    })

    it('lässt ein per Escape geschlossenes Menü alles stehen', async () => {
      mEpics.list.mockResolvedValue([eineKarte, zahlung])
      renderMitSnackbar()

      await oeffneMenue(eineKarte)
      fireEvent.keyDown(screen.getByRole('menuitem', { name: 'Löschen' }), { key: 'Escape' })

      // Das Menü zu öffnen ist noch keine Entscheidung — es wieder zu schließen ändert nichts.
      await waitFor(() => expect(screen.queryByRole('menuitem')).toBeNull())
      expect(screen.queryByRole('dialog')).toBeNull()
      expect(mEpics.remove).not.toHaveBeenCalled()
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
    })

    it('bietet „Löschen" einem Nur-Leser nicht an', async () => {
      // Anders als das Ausblenden verändert Löschen den Server — ein VIEWER bekommt die
      // Möglichkeit gar nicht erst angeboten.
      mBoards.get.mockResolvedValue({ id: 1, projectId: 42, name: 'B', createdAt: '', columns: [] })
      mProjects.list.mockResolvedValue([{ id: 42, name: 'Fremd', role: 'VIEWER', createdAt: '' }])
      mEpics.list.mockResolvedValue([ohneKarten])
      renderMitSnackbar()

      await waitFor(() => expect(mProjects.list).toHaveBeenCalled())
      const zeile = await screen.findByTestId('vorhaben-zeile-9')

      // Ohne „Löschen" bliebe das Menü leer; deshalb fehlt schon der Knopf (Plan #1488, E7).
      expect(within(zeile).queryByRole('button', { name: /^Menü / })).not.toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Löschen' })).toBeNull()
    })

    it.each([
      [
        'ohne zugeordnete Karte',
        ohneKarten,
        '„Auth" wird gelöscht. Keine aktive Karte ist direkt zugeordnet. Das lässt sich nicht rückgängig machen.',
      ],
      [
        'mit einer zugeordneten Karte',
        eineKarte,
        '„Auth" wird gelöscht. 1 direkt zugeordnete aktive Karte bleibt erhalten und zeigt danach „(kein Vorhaben)". Das lässt sich nicht rückgängig machen.',
      ],
      [
        'mit zwei zugeordneten Karten',
        zweiKarten,
        '„Auth" wird gelöscht. 2 direkt zugeordnete aktive Karten bleiben erhalten und zeigen danach „(kein Vorhaben)". Das lässt sich nicht rückgängig machen.',
      ],
    ])('nennt in der Rückfrage %s Titel und Zahl', async (_fall, epic, text) => {
      mEpics.list.mockResolvedValue([epic])
      renderMitSnackbar()

      await oeffneRueckfrage(epic)

      expect(screen.getByRole('heading', { name: 'Vorhaben löschen?' })).toBeInTheDocument()
      expect(screen.getByText(text)).toBeInTheDocument()
    })

    it('lässt „Abbrechen" die Zeile stehen und ruft nichts auf', async () => {
      mEpics.list.mockResolvedValue([eineKarte, zahlung])
      renderMitSnackbar()

      await oeffneRueckfrage(eineKarte)
      fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
      expect(mEpics.remove).not.toHaveBeenCalled()
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
    })

    it('löscht das Vorhaben und nimmt die Zeile aus der Liste', async () => {
      // Zwei getrennte Antworten: die erste für den Erstaufruf, die zweite für das Nachladen
      // nach dem Löschen. Mit einer dauerhaften Antwort bliebe die Zeile stehen, und der Test
      // prüfte die Mock-Mechanik statt der Wirkung.
      mEpics.list.mockResolvedValueOnce([eineKarte, zahlung]).mockResolvedValueOnce([zahlung])
      mEpics.remove.mockResolvedValue(undefined)
      renderMitSnackbar()

      await oeffneRueckfrage(eineKarte)
      fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))

      await waitFor(() => expect(mEpics.remove).toHaveBeenCalledWith(9))
      await waitFor(() => expect(zeilenIds()).toEqual(['vorhaben-zeile-10']))
    })

    it('meldet einen Fehlschlag und lässt die Zeile stehen, die Rückfrage ist dann schon zu', async () => {
      mEpics.list.mockResolvedValue([eineKarte, zahlung])
      mEpics.remove.mockRejectedValue(new Error('kaputt'))
      renderMitSnackbar()

      await oeffneRueckfrage(eineKarte)
      fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))

      expect(await screen.findByText('Löschen fehlgeschlagen.')).toBeInTheDocument()
      // Der Dialog schließt vor dem Aufruf — sonst schickte ein zweiter Klick ein zweites DELETE.
      expect(screen.queryByRole('button', { name: 'Abbrechen' })).toBeNull()
      expect(zeilenIds()).toEqual(['vorhaben-zeile-9', 'vorhaben-zeile-10'])
    })
  })
})
