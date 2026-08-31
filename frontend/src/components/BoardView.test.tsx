import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Board } from '../api/boards'
import { cardsApi } from '../api/cards'
import type { Card } from '../api/cards'
import { ApiError } from '../api/client'
import { columnsApi } from '../api/columns'
import { boardsApi } from '../api/boards'
import { projectsApi } from '../api/projects'
import { BoardView } from './BoardView'
import { SnackbarProvider } from './SnackbarProvider'
import { statusColors } from '../lib/statusColors'
import { STATUS_EDGE_WIDTH } from '../theme'

vi.mock('../api/columns', () => ({
  columnsApi: {
    create: vi.fn(), update: vi.fn(), remove: vi.fn(), reorder: vi.fn(), sortByNumber: vi.fn(),
  },
}))
// Nur vom Transfer-Dialog zur Laufzeit genutzt; leere Listen genügen zum Öffnen, einzelne Tests
// überschreiben sie mit echten Projekten/Boards, um den Verschieben-Flow bis zum Ende zu treiben.
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('../api/cards', () => ({ cardsApi: { bulkTransfer: vi.fn() } }))
// Editiermodus wird gemockt: Bestandstests laufen mit editMode=true (Bleistifte sichtbar); einzelne
// Tests schalten editMode.value=false, um das Ausblenden der Struktur-Affordances zu prüfen.
const editMode = vi.hoisted(() => ({ value: true }))
vi.mock('../lib/EditModeContext', () => ({
  useEditMode: () => ({ editMode: editMode.value, setEditMode: vi.fn(), toggleEditMode: vi.fn() }),
}))
const mColumns = columnsApi as unknown as {
  create: ReturnType<typeof vi.fn>
  update: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
  reorder: ReturnType<typeof vi.fn>
  sortByNumber: ReturnType<typeof vi.fn>
}
const mProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mBoards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as { bulkTransfer: ReturnType<typeof vi.fn> }

const board: Board = {
  id: 1,
  projectId: 1,
  name: 'Board',
  createdAt: '2026-01-01T00:00:00Z',
  columns: [
    { id: 10, name: 'Backlog', position: 0, wipLimit: null },
    { id: 20, name: 'Done', position: 1, wipLimit: null },
  ],
}

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null,
  positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
  derivedFrom: null,
}

function mkApi(over: Record<string, unknown> = {}) {
  return {
    create: vi.fn(), move: vi.fn(), archive: vi.fn(), moveToIdeaStorage: vi.fn(),
    restore: vi.fn(), remove: vi.fn(),
    bulkArchive: vi.fn(), bulkTransfer: vi.fn(), bulkDelete: vi.fn(), ...over,
  }
}

function dropOnColumn(columnId: number, cardId: number) {
  fireEvent.drop(screen.getByTestId(`column-${columnId}`), {
    dataTransfer: { getData: () => String(cardId) },
  })
}

describe('BoardView', () => {
  beforeEach(() => {
    editMode.value = true
    mProjects.list.mockResolvedValue([])
    mBoards.list.mockResolvedValue([])
    mCards.bulkTransfer.mockReset()
    mColumns.create.mockReset()
    mColumns.update.mockReset()
    mColumns.remove.mockReset()
    mColumns.reorder.mockReset()
    mColumns.sortByNumber.mockReset()
  })

  it('verschiebt die Karte optimistisch in die Zielspalte', async () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    dropOnColumn(20, 100)

    await waitFor(() => expect(within(screen.getByTestId('column-20')).getByTestId('card-100')).toBeInTheDocument())
    expect(api.move).toHaveBeenCalledWith(100, 20, 0)
  })

  it('rollt bei einem API-Fehler auf den vorigen Stand zurück', async () => {
    const api = mkApi({ move: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    dropOnColumn(20, 100)

    await waitFor(() => expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument())
    expect(within(screen.getByTestId('column-20')).queryByTestId('card-100')).not.toBeInTheDocument()
  })

  it('legt über den Anlage-Dialog eine Karte mit Beschreibung an', async () => {
    const created: Card = { ...card, id: 200, number: 2, title: 'Neu' }
    const api = mkApi({ create: vi.fn().mockResolvedValue(created) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith(1, 10, 'Neu', expect.stringContaining('## Kontext'), null, false, {
        dependencies: [],
        dueDate: null,
        assigneeIds: [],
        labelIds: [],
      }),
    )
    expect(within(screen.getByTestId('column-10')).getByTestId('card-200')).toBeInTheDocument()
  })

  it('blendet den Anlege-Button für Nicht-Editoren aus', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)
    expect(screen.queryByRole('button', { name: 'Neu anlegen' })).not.toBeInTheDocument()
  })

  it('legt über Typ=Epic ein Epic an statt einer Karte', async () => {
    const api = mkApi()
    const epicsApi = { create: vi.fn().mockResolvedValue({ id: 5 }) }
    const onEpicsChanged = vi.fn()
    render(
      <BoardView board={board} initialCards={[card]} canEdit api={api}
        epicsApi={epicsApi} onEpicsChanged={onEpicsChanged} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
    fireEvent.change(screen.getByLabelText('Typ'), { target: { value: 'EPIC' } })
    fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: 'AUT' } })
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Auth' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(epicsApi.create).toHaveBeenCalledWith(1, 'Auth', expect.any(String), 'AUT'))
    expect(api.create).not.toHaveBeenCalled()
    expect(onEpicsChanged).toHaveBeenCalled()
  })

  it('zeigt ein Epic-Badge auf zugeordneten Karten', () => {
    const assigned: Card = { ...card, parentId: 9 }
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    render(<BoardView board={board} initialCards={[assigned]} canEdit epics={epics} api={mkApi()} />)
    expect(screen.getByText('AUT')).toBeInTheDocument()
  })

  it('archiviert und verschiebt über das ⋮-Menü', async () => {
    const api = mkApi({ archive: vi.fn().mockResolvedValue({}), move: vi.fn().mockResolvedValue({}) })
    const onCardsChanged = vi.fn()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} onCardsChanged={onCardsChanged} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Archivieren' }))
    await waitFor(() => expect(api.archive).toHaveBeenCalledWith(100))
    expect(onCardsChanged).toHaveBeenCalled()

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Nach rechts verschieben' }))
    await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 20, 0))
  })

  it('zeigt Ideen (ideaStored) nicht in der Spaltenansicht', () => {
    const idea: Card = { ...card, id: 500, number: 5, title: 'Idee', ideaStored: true }
    render(<BoardView board={board} initialCards={[card, idea]} canEdit api={mkApi()} />)

    expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
    expect(within(screen.getByTestId('column-10')).queryByTestId('card-500')).not.toBeInTheDocument()
  })

  it('legt eine Karte über das ⋮-Menü in den Ideen-Pool und entfernt sie optimistisch', async () => {
    const api = mkApi({ moveToIdeaStorage: vi.fn().mockResolvedValue({}) })
    const onCardsChanged = vi.fn()
    // Zweite Karte in derselben Spalte: der optimistische map bleibt für sie unverändert (: c-Zweig).
    const other: Card = { ...card, id: 101, number: 2, title: 'Andere' }
    render(
      <BoardView board={board} initialCards={[card, other]} canEdit api={api} onCardsChanged={onCardsChanged} />,
      { wrapper: SnackbarProvider },
    )

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'In den Ideen-Pool' }))

    await waitFor(() => expect(api.moveToIdeaStorage).toHaveBeenCalledWith(100))
    expect(onCardsChanged).toHaveBeenCalled()
    // Erfolgs-Toast benennt den Zielort.
    expect(await screen.findByText('In den Ideen-Pool verschoben — unter Ideen zu finden.')).toBeInTheDocument()
    // Optimistisch aus dem Board entfernt (ideaStored filtert die Spaltenansicht).
    expect(within(screen.getByTestId('column-10')).queryByTestId('card-100')).not.toBeInTheDocument()
    // Die zweite Karte bleibt unangetastet sichtbar.
    expect(within(screen.getByTestId('column-10')).getByTestId('card-101')).toBeInTheDocument()
  })

  it('rollt bei Fehler im Ideen-Pool zurück und zeigt die Karte wieder', async () => {
    const api = mkApi({ moveToIdeaStorage: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'In den Ideen-Pool' }))

    await screen.findByText('In den Ideen-Pool verschieben fehlgeschlagen.')
    expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
  })

  it('verschiebt eine Karte über das ⋮-Menü nach Bestätigung in den Papierkorb', async () => {
    const api = mkApi({ bulkDelete: vi.fn().mockResolvedValue(undefined) })
    const onCardsChanged = vi.fn()
    // Zweite Karte in derselben Spalte: sie darf vom optimistischen Filtern unberührt bleiben.
    const other: Card = { ...card, id: 101, number: 2, title: 'Andere' }
    render(
      <BoardView board={board} initialCards={[card, other]} canEdit api={api} onCardsChanged={onCardsChanged} />,
    )

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Löschen' }))

    // Erst die Bestätigung löst den Aufruf aus — das Menü allein löscht nichts.
    expect(api.bulkDelete).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
    )

    await waitFor(() => expect(api.bulkDelete).toHaveBeenCalledWith([100]))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    expect(screen.getByTestId('card-101')).toBeInTheDocument()
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('löscht nichts, wenn der Papierkorb-Dialog aus dem ⋮-Menü abgebrochen wird', () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Löschen' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Abbrechen' }))

    expect(api.bulkDelete).not.toHaveBeenCalled()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('rollt beim Fehler des Einzel-Papierkorbs zurück und meldet ihn', async () => {
    const api = mkApi({ bulkDelete: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Löschen' }))
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
    )

    expect(await screen.findByText('In den Papierkorb verschieben fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('dupliziert eine Karte über das ⋮-Menü vorbefüllt, aber immer nach Backlog (erste Spalte)', async () => {
    // Quelle bewusst NICHT in der ersten Spalte (Backlog=10), sondern in Done=20 — die Kopie ist
    // ein neues Item und muss den kompletten Prozess durchlaufen, unabhängig davon, wo die
    // Quellkarte gerade steht.
    const source: Card = { ...card, columnId: 20, title: 'Original', description: 'Original-Text', parentId: 9 }
    const created: Card = { ...card, id: 300, number: 3, columnId: 10, title: 'Original' }
    const api = mkApi({ create: vi.fn().mockResolvedValue(created) })
    render(<BoardView board={board} initialCards={[source]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Menü Original'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Duplizieren' }))

    expect(screen.getByRole('heading', { name: 'Neue Karte in „Backlog“' })).toBeInTheDocument()
    expect(screen.getByLabelText('Titel')).toHaveValue('Original')
    expect(screen.getByLabelText('Markdown-Beschreibung')).toHaveValue('Original-Text')

    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith(1, 10, 'Original', 'Original-Text', 9, false, {
        dependencies: [],
        dueDate: null,
        assigneeIds: [],
        labelIds: [],
      }),
    )
    // Quellkarte bleibt unverändert in ihrer Spalte (Done) erhalten.
    expect(within(screen.getByTestId('column-20')).getByTestId('card-100')).toBeInTheDocument()
  })

  it('legt beim Abbrechen des Duplizieren-Dialogs keine neue Karte an', () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Duplizieren' }))
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(api.create).not.toHaveBeenCalled()
  })

  it('zeigt den Archiv-Countdown auf Done-Karten', () => {
    const doneCard: Card = { ...card, columnId: 20, movedToDoneAt: new Date().toISOString() }
    render(<BoardView board={board} initialCards={[doneCard]} canEdit retentionDays={5} api={mkApi()} />)
    expect(screen.getByText(/wird in 5 Tagen archiviert/)).toBeInTheDocument()
  })

  it('zeigt keinen Archiv-Countdown, wenn die Aufbewahrung 0 ist (kein Auto-Archiv)', () => {
    const doneCard: Card = { ...card, columnId: 20, movedToDoneAt: new Date().toISOString() }
    render(<BoardView board={board} initialCards={[doneCard]} canEdit retentionDays={0} api={mkApi()} />)
    expect(screen.queryByText(/archiviert/)).not.toBeInTheDocument()
  })

  it('filtert das Board nach Epic', () => {
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    const inEpic: Card = { ...card, id: 100, parentId: 9 }
    const other: Card = { ...card, id: 200, number: 2, parentId: null }
    render(<BoardView board={board} initialCards={[inEpic, other]} canEdit epics={epics} api={mkApi()} />)

    expect(screen.getByTestId('card-100')).toBeInTheDocument()
    expect(screen.getByTestId('card-200')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
    expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
  })

  it('legt eine neue Spalte an (mit canEdit)', async () => {
    mColumns.create.mockResolvedValue({ id: 30, name: 'Neu', position: 2, wipLimit: null })
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Spalte' }))
    fireEvent.change(screen.getByLabelText('Spaltenname'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(mColumns.create).toHaveBeenCalledWith(1, 'Neu', null))
    expect(await screen.findByText('Neu')).toBeInTheDocument()
  })

  it('bearbeitet Name und WIP-Limit einer Spalte', async () => {
    mColumns.update.mockResolvedValue({ id: 10, name: 'Todo', position: 0, wipLimit: 3 })
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog bearbeiten'))
    fireEvent.change(screen.getByLabelText('Spaltenname'), { target: { value: 'Todo' } })
    fireEvent.change(screen.getByLabelText('WIP-Limit'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(mColumns.update).toHaveBeenCalledWith(10, 'Todo', 3))
  })

  it('blendet Spalten-Bearbeitung ohne canEdit aus', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)
    expect(screen.queryByRole('button', { name: 'Spalte' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Spalte Backlog bearbeiten')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Spalte Backlog löschen')).not.toBeInTheDocument()
  })

  it('löscht eine leere Spalte nach Bestätigung', async () => {
    mColumns.remove.mockResolvedValue(undefined)
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Done löschen'))
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))

    await waitFor(() => expect(mColumns.remove).toHaveBeenCalledWith(20))
    await waitFor(() => expect(screen.queryByText('Done')).not.toBeInTheDocument())
  })

  it('zeigt einen Fehler, wenn die Spalte noch Karten enthält (409)', async () => {
    mColumns.remove.mockRejectedValue(new ApiError(409, 'nicht leer'))
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog löschen'))
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))

    expect(
      await screen.findByText('Spalte enthält noch Karten und kann nicht gelöscht werden.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Backlog')).toBeInTheDocument()
  })

  it('zeigt eine generische Fehlermeldung bei einem anderen Löschfehler', async () => {
    mColumns.remove.mockRejectedValue(new Error('boom'))
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog löschen'))
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))

    expect(await screen.findByText('Löschen fehlgeschlagen.')).toBeInTheDocument()
  })

  it('ordnet Spalten per Drag & Drop neu und persistiert die Reihenfolge', async () => {
    mColumns.reorder.mockResolvedValue([
      { id: 20, name: 'Done', position: 0, wipLimit: null },
      { id: 10, name: 'Backlog', position: 1, wipLimit: null },
    ])
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    // Während des Ziehens über eine ANDERE Spalte hinweg (dragOver) und am Ende dragEnd —
    // vollständige Drag-Sequenz statt nur des isolierten drop.
    fireEvent.dragOver(screen.getByTestId('column-header-10'))
    fireEvent.drop(screen.getByTestId('column-header-10'))
    fireEvent.dragEnd(screen.getByTestId('column-header-10'))

    await waitFor(() => expect(mColumns.reorder).toHaveBeenCalledWith(1, [20, 10]))
  })

  it('ignoriert dragOver über der eigenen Spalte beim Spalten-Reorder', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    // dragOver über der Spalte, von der aus gezogen wird: kein preventDefault/Fehler.
    fireEvent.dragOver(screen.getByTestId('column-header-20'))
    expect(screen.getByTestId('column-header-20')).toBeInTheDocument()
  })

  it('reordert nicht, wenn eine Spalte auf sich selbst fallengelassen wird', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    fireEvent.drop(screen.getByTestId('column-header-20'))
    expect(mColumns.reorder).not.toHaveBeenCalled()
  })

  it('stellt die Spalten-Reihenfolge bei einem Fehler wieder her', async () => {
    mColumns.reorder.mockRejectedValue(new Error('kaputt'))
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    fireEvent.drop(screen.getByTestId('column-header-10'))

    await waitFor(() => expect(mColumns.reorder).toHaveBeenCalled())
    // Nach dem Rollback steht Backlog wieder vor Done.
    const headers = screen.getAllByTestId(/^column-header-/)
    expect(headers[0]).toHaveAttribute('data-testid', 'column-header-10')
    expect(headers[1]).toHaveAttribute('data-testid', 'column-header-20')
  })

  it('macht Spalten ohne canEdit nicht draggable', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)
    expect(screen.getByTestId('column-header-10')).not.toHaveAttribute('draggable', 'true')
  })

  it('zeigt den Verschieben-Menüeintrag nur mit canTransfer', () => {
    const { unmount } = render(
      <BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />,
    )
    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    expect(screen.queryByText('Auf anderes Board verschieben…')).not.toBeInTheDocument()
    unmount()

    render(<BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()} />)
    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    expect(screen.getByText('Auf anderes Board verschieben…')).toBeInTheDocument()
  })

  it('zeigt farbige Label-Chips auf der Karte', () => {
    const labelled: Card = { ...card, labels: [5] }
    const boardLabels = [{ id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false }]
    render(
      <BoardView board={board} initialCards={[labelled]} canEdit boardLabels={boardLabels} api={mkApi()} />,
    )

    expect(screen.getByLabelText('Labels Aufgabe')).toBeInTheDocument()
    expect(screen.getByText('Bug')).toBeInTheDocument()
  })

  it('zeigt Zuständigen-Avatare mit Initialen (zwei Wörter, ein Wort, leerer Name, Fallback)', () => {
    // Deckt alle initials()-Zweige ab: 'Max Mustermann' -> MM (zwei Wörter), 'Cher' -> C (ein Wort),
    // leerer Anzeigename -> '?' (kein Wort) und ein Assignee ohne Mitglied -> '#8' -> '#'.
    const assigned: Card = { ...card, assignees: [5, 6, 7, 8] }
    const members = [
      { userId: 5, email: 'a@x.de', displayName: 'Max Mustermann', role: 'MEMBER' as const },
      { userId: 6, email: 'b@x.de', displayName: 'Cher', role: 'MEMBER' as const },
      { userId: 7, email: 'c@x.de', displayName: '', role: 'MEMBER' as const },
    ]
    render(
      <BoardView board={board} initialCards={[assigned]} canEdit members={members} api={mkApi()} />,
    )

    const group = screen.getByLabelText('Zuständige Aufgabe')
    expect(group).toBeInTheDocument()
    expect(within(group).getByText('MM')).toBeInTheDocument()
    expect(within(group).getByText('C')).toBeInTheDocument()
    expect(within(group).getByText('?')).toBeInTheDocument()
    expect(within(group).getByText('#')).toBeInTheDocument()
  })

  it('trägt den Status der Spalte an ihrer Oberkante', () => {
    // Kanten-Semantik (#649): oben = Status. Der frühere Farbpunkt im Spaltenkopf entfällt dafür.
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    expect(screen.getByTestId('column-10')).toHaveStyle({
      borderTopColor: statusColors('Backlog').dot,
      borderTopWidth: `${STATUS_EDGE_WIDTH}px`,
    })
    expect(screen.getByTestId('column-20')).toHaveStyle({ borderTopColor: statusColors('Done').dot })
  })

  it('trägt den Status an der linken Kante der Karte', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    expect(screen.getByTestId('card-100')).toHaveStyle({
      borderLeftColor: statusColors('Backlog').dot,
      borderLeftWidth: `${STATUS_EDGE_WIDTH}px`,
    })
  })

  // Die Oberkante gehört dem Panel, nicht der Karte: Bis 2026-08-31 trug die Karte den Status oben,
  // und die Spalte trug ihn ebenfalls — dieselbe Farbe zweimal übereinander, zwei Pixel auseinander.
  it('trägt den Status an der Spalte oben und an der Karte nicht doppelt', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    expect(screen.getByTestId('column-10')).toHaveStyle({
      borderTopColor: statusColors('Backlog').dot,
      borderTopWidth: `${STATUS_EDGE_WIDTH}px`,
    })
    expect(screen.getByTestId('card-100')).not.toHaveStyle({
      borderTopWidth: `${STATUS_EDGE_WIDTH}px`,
    })
  })

  it('färbt den Label-Chip-Text nach Kontrast, auf hellem Label also nicht weiß', () => {
    const labelled: Card = { ...card, labels: [5, 6] }
    const boardLabels = [
      { id: 5, boardId: 1, name: 'Hell', color: '#FFF59D', countOnEpicTile: false },
      { id: 6, boardId: 1, name: 'Dunkel', color: '#1E5F68', countOnEpicTile: false },
    ]
    render(
      <BoardView board={board} initialCards={[labelled]} canEdit boardLabels={boardLabels} api={mkApi()} />,
    )

    // Der Chip-Rumpf traegt die Textfarbe, nicht das Label-Element darin: gesucht wird deshalb
    // ueber den Textinhalt des Rumpfes statt ueber einen Aufstieg im DOM.
    const chip = (name: string) =>
      screen.getByText((_content, element) =>
        element?.classList.contains('MuiChip-root') === true && element.textContent === name)

    // Erwartete Werte ausgeschrieben statt `getContrastText` gespiegelt: sonst prueft der Test
    // denselben Mechanismus, den er absichern soll, und bliebe auch bei falscher Wahl gruen.
    expect(chip('Hell')).toHaveStyle({ color: 'rgba(0, 0, 0, 0.87)' })
    expect(chip('Dunkel')).toHaveStyle({ color: 'rgb(255, 255, 255)' })
  })

  it('rendert weiter, wenn eine Labelfarbe keine CSS-Farbe ist', () => {
    // Die Labelfarbe ist serverseitig nur laengenbegrenzt, das Domain-Modell erlaubt ausdruecklich
    // auch Theme-Token. `getContrastText` wirft darauf, und es gibt keine ErrorBoundary — ohne
    // Fangnetz nimmt ein einziges solches Label den ganzen Board-Baum mit.
    const labelled: Card = { ...card, labels: [7, 8] }
    const boardLabels = [
      { id: 7, boardId: 1, name: 'Pfad', color: 'primary.main', countOnEpicTile: false },
      { id: 8, boardId: 1, name: 'Wort', color: 'red', countOnEpicTile: false },
    ]

    expect(() =>
      render(
        <BoardView board={board} initialCards={[labelled]} canEdit boardLabels={boardLabels} api={mkApi()} />,
      ),
    ).not.toThrow()
    expect(screen.getByText('Pfad')).toBeInTheDocument()
    expect(screen.getByText('Wort')).toBeInTheDocument()
  })

  it('zeigt für ein unbekanntes Label die Id als grauen Fallback-Chip', () => {
    // labelId 999 hat kein Board-Label -> Chip zeigt „#999" und die graue Fallback-Farbe.
    const labelled: Card = { ...card, labels: [999] }
    render(<BoardView board={board} initialCards={[labelled]} canEdit boardLabels={[]} api={mkApi()} />)

    expect(screen.getByLabelText('Labels Aufgabe')).toBeInTheDocument()
    expect(screen.getByText('#999')).toBeInTheDocument()
  })

  it('blendet im Auswahlmodus Checkboxen ein und selektiert per Klick', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    // Vor dem Auswahlmodus keine Checkbox.
    expect(screen.queryByLabelText('Karte Aufgabe auswählen')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    expect(screen.getByLabelText('Karte Aufgabe auswählen')).toBeInTheDocument()

    fireEvent.click(screen.getByTestId('card-100'))
    expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()

    // Erneuter Klick hebt die Auswahl auf -> Aktionsleiste verschwindet.
    fireEvent.click(screen.getByTestId('card-100'))
    expect(screen.queryByText('1 ausgewählt')).not.toBeInTheDocument()
  })

  it('öffnet im Auswahlmodus nicht das Detail beim Klick auf die Karte', () => {
    const onCardClick = vi.fn()
    render(
      <BoardView board={board} initialCards={[card]} canEdit api={mkApi()} onCardClick={onCardClick} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))

    expect(onCardClick).not.toHaveBeenCalled()
  })

  it('archiviert die Auswahl nach Bestätigung über die Bulk-API und entfernt sie optimistisch', async () => {
    const api = mkApi({ bulkArchive: vi.fn().mockResolvedValue([]) })
    const onCardsChanged = vi.fn()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} onCardsChanged={onCardsChanged} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))

    // Bestätigungsdialog erscheint; erst dessen Bestätigung löst die API aus.
    expect(api.bulkArchive).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

    await waitFor(() => expect(api.bulkArchive).toHaveBeenCalledWith([100]))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    // Erfolgspfad benachrichtigt das Elternteil (onCardsChanged?.()-Aufrufzweig).
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('archiviert nicht, wenn die Bestätigung abgebrochen wird', () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Abbrechen' }))

    expect(api.bulkArchive).not.toHaveBeenCalled()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('rollt beim Fehler des Bulk-Archivierens zurück und meldet ihn', async () => {
    const api = mkApi({ bulkArchive: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

    expect(await screen.findByText('Archivieren fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('verschiebt die Auswahl nach Bestätigung in den Papierkorb', async () => {
    const api = mkApi({ bulkDelete: vi.fn().mockResolvedValue(undefined) })
    const onCardsChanged = vi.fn()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} onCardsChanged={onCardsChanged} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))

    expect(api.bulkDelete).not.toHaveBeenCalled()
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
    )

    await waitFor(() => expect(api.bulkDelete).toHaveBeenCalledWith([100]))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    // Erfolgspfad benachrichtigt das Elternteil (onCardsChanged?.()-Aufrufzweig).
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('rollt beim Fehler des Bulk-Papierkorbs zurück und meldet ihn', async () => {
    const api = mkApi({ bulkDelete: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
    )

    expect(await screen.findByText('In den Papierkorb verschieben fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('öffnet mit Transfer-Recht den Verschieben-Dialog für die Auswahl', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    expect(screen.getByText('Auf anderes Board verschieben')).toBeInTheDocument()
  })

  it('leert die Auswahl beim Abbrechen', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(screen.queryByLabelText('Karte Aufgabe auswählen')).not.toBeInTheDocument()
    expect(screen.queryByText('1 ausgewählt')).not.toBeInTheDocument()
  })

  it('zeigt das Fälligkeitsdatum-Badge, hervorgehoben bei überfälligen Karten', () => {
    const future = new Date(Date.now() + 86_400_000).toISOString()
    const past = new Date(Date.now() - 86_400_000).toISOString()
    const dueSoon: Card = { ...card, id: 100, title: 'Bald fällig', dueDate: future }
    const overdue: Card = { ...card, id: 200, number: 2, title: 'Überfällig', dueDate: past }
    render(<BoardView board={board} initialCards={[dueSoon, overdue]} canEdit api={mkApi()} />)

    expect(screen.getByLabelText('Fällig Bald fällig')).toBeInTheDocument()
    expect(screen.getByLabelText('Fällig Überfällig')).toBeInTheDocument()
    expect(screen.getAllByText(/📅/)).toHaveLength(2)
  })

  it('rollt eine fehlgeschlagene Kartenverschiebung zurück', async () => {
    const api = mkApi({ move: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Nach rechts verschieben' }))

    await waitFor(() => expect(api.move).toHaveBeenCalled())
    expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
    expect(within(screen.getByTestId('column-20')).queryByTestId('card-100')).not.toBeInTheDocument()
  })

  it('verschiebt eine einzelne Karte über das Menü auf ein anderes Board', async () => {
    const onCardsChanged = vi.fn()
    mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
        columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
    ])
    mCards.bulkTransfer.mockResolvedValue([{ ...card, boardId: 99, columnId: 900 }])
    render(
      <BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()}
        onCardsChanged={onCardsChanged} />,
    )

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Auf anderes Board verschieben…' }))

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([100], 99, 900))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('verschiebt die Auswahl über den Bulk-Transfer-Dialog und entfernt sie aus der Ansicht', async () => {
    const onCardsChanged = vi.fn()
    mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
        columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
    ])
    mCards.bulkTransfer.mockResolvedValue([{ ...card, boardId: 99, columnId: 900 }])
    render(
      <BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()}
        onCardsChanged={onCardsChanged} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([100], 99, 900))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    expect(screen.queryByText('1 ausgewählt')).not.toBeInTheDocument()
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('belegt im Verschieben-Dialog Projekt und Zielspalte der Einzelkarte vor', async () => {
    mProjects.list.mockResolvedValue([{ id: 1, name: 'Eigenes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 1, name: 'Ziel', createdAt: '', columns: [
        { id: 900, name: 'Backlog', position: 0, wipLimit: null },
        { id: 901, name: 'Fertig', position: 1, wipLimit: null },
      ] },
    ])
    // Karte liegt in der zweiten Spalte (Position 1) des Quellboards.
    const inDone: Card = { ...card, id: 105, columnId: 20, title: 'Fertige Aufgabe' }
    render(<BoardView board={board} initialCards={[inDone]} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Menü Fertige Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Auf anderes Board verschieben…' }))

    await screen.findByRole('option', { name: 'Ziel' })
    expect(screen.getByLabelText('Zielprojekt')).toHaveValue('1')
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '99' } })

    expect(screen.getByLabelText('Zielspalte')).toHaveValue('901')
  })

  it('belegt die Zielspalte einer einspaltigen Bulk-Auswahl vor, bei mehrspaltiger nicht', async () => {
    mProjects.list.mockResolvedValue([{ id: 1, name: 'Eigenes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 1, name: 'Ziel', createdAt: '', columns: [
        { id: 900, name: 'Backlog', position: 0, wipLimit: null },
        { id: 901, name: 'Fertig', position: 1, wipLimit: null },
      ] },
    ])
    const cards: Card[] = [
      { ...card, id: 301, columnId: 10, positionInColumn: 0, title: 'Links oben' },
      { ...card, id: 302, columnId: 10, positionInColumn: 1, title: 'Links unten' },
      { ...card, id: 303, columnId: 20, positionInColumn: 0, title: 'Rechts' },
    ]
    render(<BoardView board={board} initialCards={cards} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-301'))
    fireEvent.click(screen.getByTestId('card-302'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await screen.findByRole('option', { name: 'Ziel' })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '99' } })
    expect(screen.getByLabelText('Zielspalte')).toHaveValue('900')

    // Dritte Karte aus der anderen Spalte dazunehmen: die Quellspalte ist nicht mehr eindeutig.
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    fireEvent.click(screen.getByTestId('card-303'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await screen.findByRole('option', { name: 'Ziel' })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '99' } })
    expect(screen.getByLabelText('Zielspalte')).toHaveValue('')
  })

  it('übergibt die Auswahl in Sichtreihenfolge, nicht in Klick-Reihenfolge', async () => {
    const cards: Card[] = [
      { ...card, id: 101, positionInColumn: 0, title: 'Eins' },
      { ...card, id: 102, positionInColumn: 1, title: 'Zwei' },
      { ...card, id: 103, positionInColumn: 2, title: 'Drei' },
    ]
    mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
        columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
    ])
    mCards.bulkTransfer.mockResolvedValue([])
    render(<BoardView board={board} initialCards={cards} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    // Von unten nach oben anhaken — genau der Fall, der ohne Sortierung verdreht im Ziel landet.
    fireEvent.click(screen.getByTestId('card-103'))
    fireEvent.click(screen.getByTestId('card-102'))
    fireEvent.click(screen.getByTestId('card-101'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([101, 102, 103], 99, 900))
  })

  it('sortiert eine spaltenübergreifende Auswahl zuerst nach Spaltenposition', async () => {
    const cards: Card[] = [
      { ...card, id: 201, columnId: 20, positionInColumn: 0, title: 'Rechts oben' },
      { ...card, id: 202, columnId: 10, positionInColumn: 5, title: 'Links unten' },
    ]
    mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
        columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
    ])
    mCards.bulkTransfer.mockResolvedValue([])
    render(<BoardView board={board} initialCards={cards} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    // Die Karte der hinteren Spalte zuerst anhaken: die Spaltenposition muss die kleinere
    // Position-in-Spalte der anderen Karte schlagen.
    fireEvent.click(screen.getByTestId('card-201'))
    fireEvent.click(screen.getByTestId('card-202'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([202, 201], 99, 900))
  })

  it('lässt Archivieren und Papierkorb bei der Klick-Reihenfolge — dort ist sie bedeutungslos', async () => {
    const cards: Card[] = [
      { ...card, id: 101, positionInColumn: 0, title: 'Eins' },
      { ...card, id: 102, positionInColumn: 1, title: 'Zwei' },
    ]
    const api = mkApi({ bulkArchive: vi.fn().mockResolvedValue([]) })
    render(<BoardView board={board} initialCards={cards} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-102'))
    fireEvent.click(screen.getByTestId('card-101'))
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

    await waitFor(() => expect(api.bulkArchive).toHaveBeenCalledWith([102, 101]))
  })

  it('öffnet den Anlage-Dialog für die erste Spalte über „Neu anlegen"', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
    expect(screen.getByRole('heading', { name: 'Neue Karte in „Backlog“' })).toBeInTheDocument()
  })

  it('wählt eine Karte per Klick auf die Checkbox selbst aus', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByLabelText('Karte Aufgabe auswählen'))
    expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()
  })

  it('setzt beim Ziehen einer Karte die dataTransfer-Nutzlast und reagiert auf dragOver der Zielspalte', async () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    const setData = vi.fn()
    fireEvent.dragStart(screen.getByTestId('card-100'), { dataTransfer: { setData } })
    fireEvent.dragOver(screen.getByTestId('column-20'), { dataTransfer: {} })
    fireEvent.drop(screen.getByTestId('column-20'), { dataTransfer: { getData: () => '100' } })

    expect(setData).toHaveBeenCalledWith('text/plain', '100')
    await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 20, 0))
  })

  it('bearbeitet eine Karte über „Bearbeiten“ im Menü', () => {
    const onEditCard = vi.fn()
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} onEditCard={onEditCard} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Bearbeiten' }))

    expect(onEditCard).toHaveBeenCalledWith(card)
  })

  it('bricht das Löschen einer Spalte über Abbrechen ab und schließt den Dialog per Escape', async () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog löschen'))
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    await waitFor(() => expect(screen.queryByText('Spalte löschen?')).not.toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('Spalte Backlog löschen'))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() => expect(screen.queryByText('Spalte löschen?')).not.toBeInTheDocument())
  })

  it('bricht das Verschieben der Auswahl in den Papierkorb über Abbrechen ab', () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Abbrechen' }),
    )

    expect(api.bulkDelete).not.toHaveBeenCalled()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('schließt die Bulk-Bestätigungsdialoge (Archivieren/Papierkorb) per Escape ohne Aktion', async () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))

    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() => expect(screen.queryByText('Karten archivieren?')).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() =>
      expect(screen.queryByText('In den Papierkorb verschieben?')).not.toBeInTheDocument(),
    )

    expect(api.bulkArchive).not.toHaveBeenCalled()
    expect(api.bulkDelete).not.toHaveBeenCalled()
  })

  it('schließt den Einzelkarten- und den Bulk-Verschieben-Dialog per Escape', async () => {
    mProjects.list.mockResolvedValue([])
    render(<BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Auf anderes Board verschieben…' }))
    fireEvent.keyDown(await screen.findByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() =>
      expect(screen.queryByText('Auf anderes Board verschieben')).not.toBeInTheDocument(),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))
    fireEvent.keyDown(await screen.findByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() =>
      expect(screen.queryByText('Auf anderes Board verschieben')).not.toBeInTheDocument(),
    )
  })

  it('lässt die Fehler-Toast-Meldung stehen (kein Auto-Hide für Fehler)', async () => {
    vi.useFakeTimers()
    try {
      const api = mkApi({ bulkArchive: vi.fn().mockRejectedValue(new Error('fail')) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      fireEvent.click(screen.getByTestId('card-100'))
      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

      await vi.waitFor(() =>
        expect(screen.getByText('Archivieren fehlgeschlagen.')).toBeInTheDocument(),
      )

      // Fehler-Toasts blenden sich NICHT selbst aus (bleiben bis zum Wegklicken).
      vi.advanceTimersByTime(5000)

      expect(screen.getByText('Archivieren fehlgeschlagen.')).toBeInTheDocument()
    } finally {
      vi.useRealTimers()
    }
  })

  it('bricht das Spalten-Reorder ab, wenn die gezogene Spalte zwischenzeitlich verschwunden ist', () => {
    const { rerender } = render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.dragStart(screen.getByTestId('column-header-20'))

    // Spalte 20 verschwindet aus board.columns, bevor der Drop passiert (z. B. andere Session).
    const updatedBoard: Board = { ...board, columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }] }
    rerender(<BoardView board={updatedBoard} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.drop(screen.getByTestId('column-header-10'))
    expect(mColumns.reorder).not.toHaveBeenCalled()
  })

  it('ignoriert das Verschieben einer Karte auf ihre eigene Spalte', () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)
    dropOnColumn(10, 100)
    expect(api.move).not.toHaveBeenCalled()
  })

  it('ändert den Epic-Filter auch, wenn localStorage nicht verfügbar ist', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => { throw new Error('storage disabled') },
      setItem: () => { throw new Error('storage disabled') },
      removeItem: () => { throw new Error('storage disabled') },
      clear: () => {}, key: () => null, length: 0,
    })
    try {
      const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
      const inEpic: Card = { ...card, parentId: 9 }
      render(<BoardView board={board} initialCards={[inEpic]} canEdit epics={epics} api={mkApi()} />)
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
      // Filter greift trotz localStorage-Fehler (nur das Persistieren schlägt stumm fehl).
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('liest einen gespeicherten Epic-Filter beim Mount aus localStorage', () => {
    // Funktionaler localStorage-Stub mit vorbelegtem Wert -> der Lazy-Initializer nimmt beim
    // Mount den truthy-Zweig (Number(raw)) statt des null-Fallbacks.
    const store = new Map<string, string>([['manban.boardEpicFilter.1', '9']])
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => { store.set(k, v) },
      removeItem: (k: string) => { store.delete(k) },
      clear: () => store.clear(), key: () => null, length: 0,
    })
    try {
      const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
      const inEpic: Card = { ...card, id: 100, parentId: 9 }
      const other: Card = { ...card, id: 200, number: 2, parentId: null }
      render(<BoardView board={board} initialCards={[inEpic, other]} canEdit epics={epics} api={mkApi()} />)

      // Filter greift sofort beim Mount: nur die Epic-Karte ist sichtbar.
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
      expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('startet ohne aktiven Epic-Filter, wenn nichts gespeichert ist', () => {
    // Funktionaler, aber leerer localStorage: getItem liefert null -> der Initializer nimmt den
    // null-Zweig von `raw ? Number(raw) : null` (nicht den catch-Fallback).
    vi.stubGlobal('localStorage', {
      getItem: () => null,
      setItem: () => {}, removeItem: () => {},
      clear: () => {}, key: () => null, length: 0,
    })
    try {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('setzt den Epic-Filter auf „Alle" zurück und entfernt den gespeicherten Wert', () => {
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    const inEpic: Card = { ...card, id: 100, parentId: 9 }
    const other: Card = { ...card, id: 200, number: 2, parentId: null }
    render(<BoardView board={board} initialCards={[inEpic, other]} canEdit epics={epics} api={mkApi()} />)

    // Erst filtern (setItem/Number-Zweig) ...
    fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
    expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
    // ... dann auf leeren Wert zurück: onChange nimmt den null-Zweig, changeEpicFilter den removeItem-Zweig.
    fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '' } })
    expect(screen.getByTestId('card-200')).toBeInTheDocument()
  })

  it('füllt beim Bearbeiten das WIP-Limit-Feld mit dem bestehenden Wert vor', () => {
    const boardWithWip: Board = {
      ...board,
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: 5 }, board.columns[1]],
    }
    render(<BoardView board={boardWithWip} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog bearbeiten'))
    // Bestehendes WIP-Limit (!= null) wird als String vorbefüllt.
    expect(screen.getByLabelText('WIP-Limit')).toHaveValue(5)
  })

  it('zeigt im WIP-Zähler den vollen Bestand der Spalte', () => {
    // Regressionsschutz: ohne gesetzten Vorhaben-Filter sind voller und gefilterter Bestand gleich.
    const boardWithWip: Board = {
      ...board,
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: 5 }, board.columns[1]],
    }
    const cards: Card[] = [
      { ...card, id: 100, number: 1, parentId: 9, positionInColumn: 0 },
      { ...card, id: 200, number: 2, parentId: 9, positionInColumn: 1 },
      { ...card, id: 300, number: 3, parentId: null, positionInColumn: 2 },
    ]
    render(<BoardView board={boardWithWip} initialCards={cards} canEdit api={mkApi()} />)

    expect(within(screen.getByTestId('column-header-10')).getByText('3/5')).toBeInTheDocument()
  })

  it('zeigt im WIP-Zähler den vollen Bestand auch bei gesetztem Vorhaben-Filter', () => {
    // Verhaltensänderung: der Zähler misst die WIP-Grenze, nicht den Filterstand. Sichtbar sind
    // nur die gefilterten Karten, gezählt wird der volle Bestand.
    const boardWithWip: Board = {
      ...board,
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: 5 }, board.columns[1]],
    }
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    const cards: Card[] = [
      { ...card, id: 100, number: 1, parentId: 9, positionInColumn: 0 },
      { ...card, id: 200, number: 2, parentId: 9, positionInColumn: 1 },
      { ...card, id: 300, number: 3, parentId: null, positionInColumn: 2 },
    ]
    render(<BoardView board={boardWithWip} initialCards={cards} canEdit epics={epics} api={mkApi()} />)

    fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })

    // Dargestellt wird gefiltert ...
    expect(screen.queryByTestId('card-300')).not.toBeInTheDocument()
    // ... gezählt wird der volle Bestand.
    expect(within(screen.getByTestId('column-header-10')).getByText('3/5')).toBeInTheDocument()
  })

  it('deaktiviert „Speichern" bei ungültigem WIP-Limit', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog bearbeiten'))
    // 0 ist kein gültiges (positives) WIP-Limit -> parsedWip() === undefined -> Button disabled.
    fireEvent.change(screen.getByLabelText('WIP-Limit'), { target: { value: '0' } })
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
  })

  it('beendet den Auswahlmodus über denselben Umschalt-Button', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    expect(screen.getByLabelText('Karte Aufgabe auswählen')).toBeInTheDocument()
    // Erneuter Klick (jetzt „Auswahl beenden") nimmt den exitSelection-Zweig von toggleSelectionMode.
    fireEvent.click(screen.getByRole('button', { name: 'Auswahl beenden' }))
    expect(screen.queryByLabelText('Karte Aufgabe auswählen')).not.toBeInTheDocument()
  })

  it('bricht das Duplizieren ab, wenn zwischenzeitlich keine Spalte mehr existiert', () => {
    const api = mkApi()
    const { rerender } = render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)
    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

    // Alle Spalten verschwinden (z. B. andere Session), während das Menü offen ist.
    const emptyBoard: Board = { ...board, columns: [] }
    rerender(<BoardView board={emptyBoard} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('menuitem', { name: 'Duplizieren' }))
    // Guard columns.length === 0 greift: kein Anlage-Dialog, kein create.
    expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
    expect(api.create).not.toHaveBeenCalled()
  })

  describe('Editiermodus aus (editMode=false)', () => {
    it('blendet die Struktur-Affordances aus, lässt aber den Karten-Alltag stehen', () => {
      editMode.value = false
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      // Struktur-Bleistifte verschwinden ...
      expect(screen.queryByLabelText('Spalte Backlog bearbeiten')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Spalte Backlog löschen')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Spalte' })).not.toBeInTheDocument()

      // ... der tägliche Kanban-Alltag bleibt erhalten.
      expect(screen.getByRole('button', { name: 'Neu anlegen' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Menü Aufgabe' })).toBeInTheDocument()
    })

    it('zeigt im Karten-Menü kein „Bearbeiten“, aber weiterhin die Alltags-Aktionen', () => {
      editMode.value = false
      render(<BoardView board={board} initialCards={[card]} canEdit onEditCard={vi.fn()} api={mkApi()} />)

      fireEvent.click(screen.getByRole('button', { name: 'Menü Aufgabe' }))
      expect(screen.queryByRole('menuitem', { name: 'Bearbeiten' })).not.toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'Archivieren' })).toBeInTheDocument()
    })
  })

  describe('Tastenkürzel „+“', () => {
    const epics = [
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ]

    it('trägt in keinem Spaltenkopf mehr ein „+“', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      expect(screen.queryByLabelText('Karte in Backlog anlegen')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Karte in Done anlegen')).not.toBeInTheDocument()
    })

    it('öffnet den Anlage-Dialog für die erste Spalte', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      fireEvent.keyDown(document.body, { key: '+' })

      expect(screen.getByRole('heading', { name: 'Neue Karte in „Backlog“' })).toBeInTheDocument()
    })

    it('legt die Karte in der ersten Spalte an', async () => {
      const created: Card = { ...card, id: 200, number: 2, title: 'Neu' }
      const api = mkApi({ create: vi.fn().mockResolvedValue(created) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

      fireEvent.keyDown(document.body, { key: '+' })
      fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
      fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

      await waitFor(() =>
        expect(api.create).toHaveBeenCalledWith(1, 10, 'Neu', expect.any(String), null, false, expect.any(Object)),
      )
      expect(within(screen.getByTestId('column-10')).getByTestId('card-200')).toBeInTheDocument()
    })

    it('greift nicht, solange der Fokus in einem Eingabefeld steht', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit epics={epics} api={mkApi()} />)

      fireEvent.keyDown(screen.getByLabelText('Vorhaben-Filter'), { key: '+' })

      expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
    })

    it('greift nicht, solange ein Dialog offen ist', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      fireEvent.click(screen.getByLabelText('Spalte Backlog bearbeiten'))

      fireEvent.keyDown(screen.getByRole('dialog'), { key: '+' })

      expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Spalte bearbeiten' })).toBeInTheDocument()
    })

    it('greift nicht ohne Bearbeitungsrecht', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)

      fireEvent.keyDown(document.body, { key: '+' })

      expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
    })

    it('greift nicht, wenn das Board keine Spalten hat', () => {
      const emptyBoard: Board = { ...board, columns: [] }
      render(<BoardView board={emptyBoard} initialCards={[]} canEdit api={mkApi()} />)

      fireEvent.keyDown(document.body, { key: '+' })

      expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
    })

    it('meldet den Tastatur-Listener beim Verlassen der Ansicht wieder ab', () => {
      const add = vi.spyOn(document, 'addEventListener')
      const remove = vi.spyOn(document, 'removeEventListener')
      const { unmount } = render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      const registered = add.mock.calls.filter(([type]) => type === 'keydown').map(([, fn]) => fn)

      unmount()

      const removed = remove.mock.calls.filter(([type]) => type === 'keydown').map(([, fn]) => fn)
      expect(registered).toHaveLength(1)
      expect(removed).toEqual(registered)
      add.mockRestore()
      remove.mockRestore()
    })
  })

  describe('Verschieben im ⋮-Menü', () => {
    // Drei Spalten, damit es eine echte Mitte mit beiden Nachbarn gibt.
    const wideBoard: Board = {
      ...board,
      columns: [
        { id: 10, name: 'Backlog', position: 0, wipLimit: null },
        { id: 20, name: 'Doing', position: 1, wipLimit: null },
        { id: 30, name: 'Done', position: 2, wipLimit: null },
      ],
    }
    const middleCard: Card = { ...card, columnId: 20 }
    const lastCard: Card = { ...card, columnId: 30 }
    const moveItems = () =>
      screen.getAllByRole('menuitem').filter((item) => item.textContent?.startsWith('Nach '))

    it('zeigt in einer mittleren Spalte genau zwei Verschieben-Einträge', () => {
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      expect(moveItems().map((item) => item.textContent)).toEqual([
        'Nach links verschieben',
        'Nach rechts verschieben',
      ])
    })

    it('verschiebt nach rechts in die Spalte mit der nächsthöheren Position', async () => {
      const api = mkApi({ move: vi.fn().mockResolvedValue({}) })
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit api={api} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
      fireEvent.click(screen.getByRole('menuitem', { name: 'Nach rechts verschieben' }))

      await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 30, 0))
      expect(within(screen.getByTestId('column-30')).getByTestId('card-100')).toBeInTheDocument()
    })

    it('verschiebt nach links in die Spalte mit der nächstniedrigeren Position', async () => {
      const api = mkApi({ move: vi.fn().mockResolvedValue({}) })
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit api={api} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
      fireEvent.click(screen.getByRole('menuitem', { name: 'Nach links verschieben' }))

      await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 10, 0))
      expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
    })

    it('lässt in der ersten Spalte „nach links“ weg', () => {
      render(<BoardView board={wideBoard} initialCards={[card]} canEdit api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      expect(screen.getByRole('menuitem', { name: 'Nach rechts verschieben' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Nach links verschieben' })).not.toBeInTheDocument()
    })

    it('lässt in der letzten Spalte „nach rechts“ weg', () => {
      render(<BoardView board={wideBoard} initialCards={[lastCard]} canEdit api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      expect(screen.getByRole('menuitem', { name: 'Nach links verschieben' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Nach rechts verschieben' })).not.toBeInTheDocument()
    })

    it('zeigt bei einem Board mit nur einer Spalte keinen Verschieben-Eintrag', () => {
      const single: Board = { ...board, columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }] }
      render(<BoardView board={single} initialCards={[card]} canEdit api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      expect(moveItems()).toHaveLength(0)
    })

    it('lässt die übrigen Menüeinträge unangetastet', () => {
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit canTransfer
        onEditCard={vi.fn()} api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      for (const name of ['Bearbeiten', 'Duplizieren', 'Archivieren', 'In den Ideen-Pool',
        'Auf anderes Board verschieben…']) {
        expect(screen.getByRole('menuitem', { name })).toBeInTheDocument()
      }
    })

    it('ist ohne Maus bedienbar: Tab zum ⋮, Enter, Pfeiltasten, Enter', async () => {
      const user = userEvent.setup()
      const api = mkApi({ move: vi.fn().mockResolvedValue({}) })
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit api={api} />)
      const focused = (el: HTMLElement) => el.matches(':focus')
      const menuButton = screen.getByLabelText('Menü Aufgabe')

      // Tabben, bis der ⋮-Button den Fokus hat — er muss in der Tab-Reihenfolge liegen.
      for (let i = 0; i < 40 && !focused(menuButton); i++) {
        await user.tab()
      }
      expect(menuButton).toHaveFocus()

      await user.keyboard('{Enter}')
      const target = await screen.findByRole('menuitem', { name: 'Nach rechts verschieben' })
      for (let i = 0; i < 10 && !focused(target); i++) {
        await user.keyboard('{ArrowDown}')
      }
      expect(target).toHaveFocus()
      await user.keyboard('{Enter}')

      await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 30, 0))
    })
  })

  describe('Sortier-Toggle im Spaltenkopf', () => {
    const ascLabel = (column: string) => `Spalte ${column} nach Nummer aufsteigend sortieren`
    const descLabel = (column: string) => `Spalte ${column} nach Nummer absteigend sortieren`

    it('wechselt bei jedem Klick die Richtung: ASC, DESC, ASC', async () => {
      mColumns.sortByNumber.mockResolvedValue(undefined)
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      await screen.findByLabelText(descLabel('Backlog'))
      fireEvent.click(screen.getByLabelText(descLabel('Backlog')))
      await screen.findByLabelText(ascLabel('Backlog'))
      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      await waitFor(() => expect(mColumns.sortByNumber).toHaveBeenCalledTimes(3))

      expect(mColumns.sortByNumber.mock.calls).toEqual([[10, 'ASC'], [10, 'DESC'], [10, 'ASC']])
    })

    it('führt die Richtung je Spalte unabhängig', async () => {
      mColumns.sortByNumber.mockResolvedValue(undefined)
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      await screen.findByLabelText(descLabel('Backlog'))

      // Die zweite Spalte startet trotzdem bei aufsteigend.
      expect(screen.getByLabelText(ascLabel('Done'))).toBeInTheDocument()
      fireEvent.click(screen.getByLabelText(ascLabel('Done')))
      await waitFor(() => expect(mColumns.sortByNumber).toHaveBeenCalledTimes(2))

      expect(mColumns.sortByNumber.mock.calls).toEqual([[10, 'ASC'], [20, 'ASC']])
      expect(screen.getByLabelText(descLabel('Backlog'))).toBeInTheDocument()
    })

    it('zeigt den Button auf allen Spalten, auch ohne Editiermodus', () => {
      editMode.value = false
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      expect(screen.getByLabelText(ascLabel('Backlog'))).toBeInTheDocument()
      expect(screen.getByLabelText(ascLabel('Done'))).toBeInTheDocument()
    })

    it('zeigt den Button nicht ohne Verschieberecht', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      expect(screen.queryByLabelText(ascLabel('Backlog'))).not.toBeInTheDocument()
    })

    it('behält die Richtung bei, wenn das Sortieren fehlschlägt', async () => {
      mColumns.sortByNumber.mockRejectedValue(new Error('fail'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      expect(await screen.findByText('Sortieren fehlgeschlagen.')).toBeInTheDocument()
      expect(screen.getByLabelText(ascLabel('Backlog'))).toBeInTheDocument()
    })

    it('lädt die Karten nach erfolgreichem Sortieren neu', async () => {
      mColumns.sortByNumber.mockResolvedValue(undefined)
      const onCardsChanged = vi.fn()
      render(
        <BoardView
          board={board}
          initialCards={[card]}
          canEdit
          api={mkApi()}
          onCardsChanged={onCardsChanged} />,
        { wrapper: SnackbarProvider },
      )

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      await waitFor(() => expect(onCardsChanged).toHaveBeenCalledTimes(1))
    })

    it('lädt die Karten nicht neu, wenn das Sortieren fehlschlägt', async () => {
      mColumns.sortByNumber.mockRejectedValue(new Error('fail'))
      const onCardsChanged = vi.fn()
      render(
        <BoardView
          board={board}
          initialCards={[card]}
          canEdit
          api={mkApi()}
          onCardsChanged={onCardsChanged} />,
        { wrapper: SnackbarProvider },
      )

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      expect(await screen.findByText('Sortieren fehlgeschlagen.')).toBeInTheDocument()
      expect(onCardsChanged).not.toHaveBeenCalled()
    })

    it('meldet den Erfolg mit der tatsächlich sortierten Richtung', async () => {
      mColumns.sortByNumber.mockResolvedValue(undefined)
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      expect(await screen.findByText('Spalte Backlog aufsteigend sortiert')).toBeInTheDocument()

      fireEvent.click(screen.getByLabelText(descLabel('Backlog')))
      expect(await screen.findByText('Spalte Backlog absteigend sortiert')).toBeInTheDocument()
    })

    it('meldet keinen Erfolg, wenn das Sortieren fehlschlägt', async () => {
      mColumns.sortByNumber.mockRejectedValue(new Error('fail'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      expect(await screen.findByText('Sortieren fehlgeschlagen.')).toBeInTheDocument()
      expect(screen.queryByText('Spalte Backlog aufsteigend sortiert')).not.toBeInTheDocument()
    })

    it('sperrt den Button der laufenden Spalte gegen den zweiten Klick', async () => {
      let finish: () => void = () => {}
      mColumns.sortByNumber.mockReturnValue(
        new Promise<void>((resolve) => {
          finish = () => resolve()
        }),
      )
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      await waitFor(() => expect(screen.getByLabelText(ascLabel('Backlog'))).toBeDisabled())

      // Der zweite Klick während des Aufrufs läuft ins Leere — kein Doppel-Request.
      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      expect(mColumns.sortByNumber).toHaveBeenCalledTimes(1)
      // Andere Spalten bleiben bedienbar: gesperrt ist nur die laufende Spalte.
      expect(screen.getByLabelText(ascLabel('Done'))).toBeEnabled()

      finish()
      expect(await screen.findByLabelText(descLabel('Backlog'))).toBeEnabled()
    })

    it('gibt den Button nach einem Fehlschlag wieder frei', async () => {
      mColumns.sortByNumber.mockRejectedValue(new Error('fail'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      expect(await screen.findByText('Sortieren fehlgeschlagen.')).toBeInTheDocument()
      expect(screen.getByLabelText(ascLabel('Backlog'))).toBeEnabled()
    })
  })

  describe('Massenaktionen bei gesetztem Vorhaben-Filter', () => {
    // Massenaktionen dürfen nur treffen, was der Nutzer sieht: Wer auswählt und danach den
    // Vorhaben-Filter setzt, würde sonst Karten verschieben/archivieren/löschen, die nicht auf
    // dem Board stehen — und die Aktionsleiste nennte eine Zahl, die dazu nicht passt.
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    const epicA: Card = { ...card, id: 100, number: 1, title: 'Vorhaben A', parentId: 9, positionInColumn: 0 }
    const epicB: Card = { ...card, id: 101, number: 3, title: 'Vorhaben B', parentId: 9, positionInColumn: 1 }
    const frei: Card = { ...card, id: 200, number: 2, title: 'Ohne Vorhaben', parentId: null, positionInColumn: 2 }

    /** Auswahlmodus starten, die genannten Karten anhaken, danach auf Vorhaben 9 filtern. */
    const selectThenFilter = (ids: number[]) => {
      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      ids.forEach((id) => fireEvent.click(screen.getByTestId(`card-${id}`)))
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
    }

    const clearFilter = () =>
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '' } })

    it('zählt in der Aktionsleiste nur die sichtbaren Karten', () => {
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={mkApi()} />)

      selectThenFilter([100, 200])

      expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()
    })

    it('übergibt dem Verschieben-Dialog nur die sichtbaren Karten', async () => {
      mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
      mBoards.list.mockResolvedValue([
        { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
          columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
      ])
      mCards.bulkTransfer.mockResolvedValue([])
      render(
        <BoardView board={board} initialCards={[epicA, frei]} canEdit canTransfer epics={epics}
          api={mkApi()} />,
      )

      selectThenFilter([100, 200])
      fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

      fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
      fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
      fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
      fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

      await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([100], 99, 900))
    })

    it('archiviert nur die sichtbaren Karten und nimmt nur diese aus der Ansicht', async () => {
      const api = mkApi({ bulkArchive: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={api} />)

      selectThenFilter([100, 200])
      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

      await waitFor(() => expect(api.bulkArchive).toHaveBeenCalledWith([100]))
      // Die verborgene Karte hat das optimistische Entfernen nicht mitgemacht: Filter weg, Karte da.
      clearFilter()
      expect(screen.getByTestId('card-200')).toBeInTheDocument()
      expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()
    })

    it('verschiebt nur die sichtbaren Karten in den Papierkorb', async () => {
      const api = mkApi({ bulkDelete: vi.fn().mockResolvedValue(undefined) })
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={api} />)

      selectThenFilter([100, 200])
      fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
      fireEvent.click(
        within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
      )

      await waitFor(() => expect(api.bulkDelete).toHaveBeenCalledWith([100]))
    })

    it('nennt in beiden Bestätigungsdialogen die Zahl der sichtbaren Karten', async () => {
      render(
        <BoardView board={board} initialCards={[epicA, epicB, frei]} canEdit epics={epics}
          api={mkApi()} />,
      )

      // Drei ausgewählt, zwei davon sichtbar — die Dialoge müssen von zweien sprechen.
      selectThenFilter([100, 101, 200])

      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      expect(screen.getByText(/2 Karten werden archiviert\./)).toBeInTheDocument()
      fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })
      await waitFor(() => expect(screen.queryByText('Karten archivieren?')).not.toBeInTheDocument())

      fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
      expect(screen.getByText(/2 Karten werden in den Papierkorb verschoben\./)).toBeInTheDocument()
    })

    it('blendet die Aktionsleiste aus, wenn alle ausgewählten Karten verborgen sind', () => {
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={mkApi()} />)

      selectThenFilter([200])

      // Nicht „0 ausgewählt", sondern gar keine Leiste.
      expect(screen.queryByRole('region', { name: 'Massenaktionen' })).not.toBeInTheDocument()
    })

    it('macht die verborgene Auswahl nach Aufheben des Filters wieder wirksam', () => {
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={mkApi()} />)

      selectThenFilter([100, 200])
      expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()

      // Belegt, dass die wirksame Menge abgeleitet und die Auswahl nicht beschnitten wird.
      clearFilter()
      expect(screen.getByText('2 ausgewählt')).toBeInTheDocument()
    })

    it('beendet mit einer ausgeführten Massenaktion auch die verborgene Rest-Auswahl', async () => {
      const api = mkApi({ bulkArchive: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={api} />)

      selectThenFilter([100, 200])
      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))
      await waitFor(() => expect(api.bulkArchive).toHaveBeenCalledWith([100]))

      clearFilter()

      // Die Karte ist unversehrt zurück — aber exitSelection hat den Auswahlmodus samt der
      // verborgenen Rest-Auswahl beendet. Das Rückkehr-Versprechen gilt nur ohne Aktion dazwischen.
      expect(screen.getByTestId('card-200')).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Massenaktionen' })).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Karte Ohne Vorhaben auswählen')).not.toBeInTheDocument()
    })
  })

  describe('ausgeblendete Vorhaben', () => {
    // Ausblenden ist reine Darstellung (Plan #620): Der Zustand liegt ausschließlich im
    // localStorage-Schlüssel `manban.boardHiddenEpics.<boardId>`; ein Setz-Bedienelement gibt es
    // in diesem Paket noch nicht (Issue #669). Die Tests belegen den Zustand deshalb dort.
    const hiddenKey = 'manban.boardHiddenEpics.1'
    const filterKey = 'manban.boardEpicFilter.1'
    const mkEpic = (id: number, title: string, memberNumbers: number[]) => ({
      id, number: id, title, description: null, shortcode: title.slice(0, 3).toUpperCase(),
      done: 0, total: memberNumbers.length, memberNumbers, rootNumbers: memberNumbers,
      requirementCardNumber: null,
    })
    // Vorhaben A hält die Karten #1 (Backlog) und #3 (Done), Vorhaben B die Karte #2 (Backlog).
    const epics = [mkEpic(9, 'Auth', [1, 3]), mkEpic(8, 'Suche', [2])]
    const aBacklog: Card = { ...card, id: 100, number: 1, title: 'A im Backlog', parentId: 9, columnId: 10, positionInColumn: 0 }
    const bBacklog: Card = { ...card, id: 200, number: 2, title: 'B im Backlog', parentId: 8, columnId: 10, positionInColumn: 1 }
    const aDone: Card = { ...card, id: 101, number: 3, title: 'A in Done', parentId: 9, columnId: 20, positionInColumn: 0 }
    const frei: Card = { ...card, id: 300, number: 4, title: 'Ohne Vorhaben', parentId: null, columnId: 10, positionInColumn: 2 }

    /**
     * localStorage-Stub über eine echte Map — vorbelegbar und nach dem Test wieder auslesbar.
     * Nötig statt des nativen `localStorage`: Unter Node 26 ist es deaktiviert (siehe
     * `src/test/setup.ts`), ein Test gegen das globale Objekt wäre „grün lokal, rot in CI".
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

    /** Vorbelegte Ausblendung: derselbe Schlüssel und dasselbe Format, die das Board liest. */
    const stubHidden = (epicIds: number[]) => stubStore([[hiddenKey, JSON.stringify(epicIds)]])

    afterEach(() => vi.unstubAllGlobals())

    it('blendet die Karten eines ausgeblendeten Vorhabens aus allen Spalten aus', () => {
      stubHidden([9])
      render(<BoardView board={board} initialCards={[aBacklog, aDone, frei]} canEdit epics={epics} api={mkApi()} />)

      // Beide Spalten verlieren ihre Karte — nicht nur das Backlog.
      expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()
      expect(screen.queryByTestId('card-101')).not.toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
      // Und beide sagen es an (Singular-Form der Marke).
      expect(within(screen.getByTestId('column-header-10')).getByRole('button', { name: '1 Karte ausgeblendet, einblenden' })).toBeInTheDocument()
      expect(within(screen.getByTestId('column-header-20')).getByRole('button', { name: '1 Karte ausgeblendet, einblenden' })).toBeInTheDocument()
    })

    it('lässt Karten ohne Vorhaben stehen, auch wenn alle Vorhaben ausgeblendet sind', () => {
      stubHidden([9, 8])
      render(<BoardView board={board} initialCards={[aBacklog, bBacklog, frei]} canEdit epics={epics} api={mkApi()} />)

      expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()
      expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
    })

    it('meldet bei beiden Achsen gleichzeitig genau eine Erklärung je Spalte', () => {
      // Vorhaben B ausgeblendet, zusätzlich der Vorhaben-Filter auf A: Karte #2 fällt unter beide
      // Achsen und darf trotzdem nur einmal zählen (Vereinigung, Plan #620 E4).
      stubHidden([8])
      render(<BoardView board={board} initialCards={[aBacklog, bBacklog, aDone, frei]} canEdit epics={epics} api={mkApi()} />)

      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })

      const backlogHeader = within(screen.getByTestId('column-header-10'))
      // Genau eine Marke — nicht je Achse eine.
      expect(backlogHeader.getAllByRole('button', { name: /ausgeblendet/ })).toHaveLength(1)
      // Und genau eine Zahl: #2 (beide Achsen) und #4 (nur Filter) = 2, keine Doppelzählung.
      expect(backlogHeader.getByRole('button', { name: '2 Karten ausgeblendet, einblenden' })).toBeInTheDocument()
      // Done zeigt weiter die Karte des gefilterten Vorhabens und damit keine Marke.
      expect(screen.getByTestId('card-101')).toBeInTheDocument()
      expect(within(screen.getByTestId('column-header-20')).queryByRole('button', { name: /ausgeblendet/ })).not.toBeInTheDocument()
    })

    it('zeigt die Zahl der Ausgeblendeten als eigene Marke neben dem WIP-Zähler', () => {
      const boardWithWip: Board = {
        ...board,
        columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: 5 }, board.columns[1]],
      }
      const dritte: Card = { ...card, id: 102, number: 5, title: 'A drittens', parentId: 9, columnId: 10, positionInColumn: 3 }
      stubHidden([9])
      render(
        <BoardView board={boardWithWip} initialCards={[aBacklog, aDone, dritte, frei]} canEdit
          epics={[mkEpic(9, 'Auth', [1, 3, 5]), epics[1]]} api={mkApi()} />,
      )

      const backlogHeader = within(screen.getByTestId('column-header-10'))
      // Der WIP-Zähler bleibt am vollen Bestand, die Marke steht daneben.
      expect(backlogHeader.getByText('3/5')).toBeInTheDocument()
      expect(backlogHeader.getByRole('button', { name: '2 Karten ausgeblendet, einblenden' })).toBeInTheDocument()
      expect(backlogHeader.getByText('2 ausgeblendet')).toBeInTheDocument()
    })

    it('stellt über die Spaltenmarke die Karten wieder her und setzt beide Achsen zurück', () => {
      stubHidden([8])
      render(<BoardView board={board} initialCards={[aBacklog, bBacklog, frei]} canEdit epics={epics} api={mkApi()} />)
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })

      fireEvent.click(within(screen.getByTestId('column-header-10')).getByRole('button', { name: /ausgeblendet/ }))

      expect(screen.getByTestId('card-100')).toBeInTheDocument()
      expect(screen.getByTestId('card-200')).toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
      // Auch die Filter-Achse steht wieder auf „Alle Vorhaben".
      expect(screen.getByLabelText('Vorhaben-Filter')).toHaveValue('')
      expect(screen.queryByRole('button', { name: /ausgeblendet/ })).not.toBeInTheDocument()
    })

    it('vergisst beim Einblenden beide gespeicherten Schlüssel', () => {
      const store = stubHidden([9])
      const { unmount } = render(<BoardView board={board} initialCards={[aBacklog, frei]} canEdit epics={epics} api={mkApi()} />)
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
      fireEvent.click(screen.getAllByRole('button', { name: /ausgeblendet/ })[0])

      expect(store.has(hiddenKey)).toBe(false)
      expect(store.has(filterKey)).toBe(false)

      // Nach dem Neuladen bleibt alles sichtbar — sonst wäre die Ausblendung sofort zurück.
      unmount()
      render(<BoardView board={board} initialCards={[aBacklog, frei]} canEdit epics={epics} api={mkApi()} />)
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
    })

    it('liest die ausgeblendeten Vorhaben beim Mount aus localStorage', () => {
      // Funktionaler Stub mit vorbelegtem Wert: der Lazy-Initializer nimmt den truthy-Zweig, und
      // der Zustand übersteht damit Seitenwechsel und Neuladen.
      stubHidden([9])
      const { unmount } = render(<BoardView board={board} initialCards={[aBacklog, frei]} canEdit epics={epics} api={mkApi()} />)
      expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()

      unmount()
      render(<BoardView board={board} initialCards={[aBacklog, frei]} canEdit epics={epics} api={mkApi()} />)
      expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
    })

    it('blendet auch dann aus, wenn localStorage nicht verfügbar ist', () => {
      // Ohne funktionierendes localStorage fällt nur das Merken aus: Die Ausblendung greift, die
      // Marke erscheint, und das Einblenden wirkt trotz scheiterndem removeItem.
      vi.stubGlobal('localStorage', {
        getItem: () => { throw new Error('storage disabled') },
        setItem: () => { throw new Error('storage disabled') },
        removeItem: () => { throw new Error('storage disabled') },
        clear: () => {}, key: () => null, length: 0,
      })
      render(<BoardView board={board} initialCards={[aBacklog, bBacklog, frei]} canEdit epics={epics} api={mkApi()} />)
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })

      expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
      const marke = within(screen.getByTestId('column-header-10')).getByRole('button', { name: '2 Karten ausgeblendet, einblenden' })

      fireEvent.click(marke)
      expect(screen.getByTestId('card-200')).toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
    })

    it('lässt Verschieben und Anlegen auf dem vollen Bestand arbeiten', async () => {
      const created: Card = { ...card, id: 400, number: 6, title: 'Neu', parentId: null, columnId: 10 }
      const api = mkApi({ move: vi.fn().mockResolvedValue(undefined), create: vi.fn().mockResolvedValue(created) })
      stubHidden([9])
      render(<BoardView board={board} initialCards={[aBacklog, bBacklog, aDone]} canEdit epics={epics} api={api} />)

      // Die sichtbare Karte #2 nach Done ziehen: Done enthält im vollen Bestand bereits die
      // ausgeblendete Karte #3, der Zielindex ist deshalb 1 — nicht 0 (gefilterter Bestand).
      dropOnColumn(20, 200)
      await waitFor(() => expect(api.move).toHaveBeenCalledWith(200, 20, 1))

      fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
      fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
      fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
      expect(await screen.findByTestId('card-400')).toBeInTheDocument()

      // Kein Aus- oder Einblenden hat eine Karte bewegt: nach dem Einblenden stehen alle wieder da.
      // findAll…: der Anlage-Dialog legt beim Schließen kurz `aria-hidden` über das Board.
      fireEvent.click((await screen.findAllByRole('button', { name: /ausgeblendet/ }))[0])
      expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
      expect(within(screen.getByTestId('column-20')).getByTestId('card-101')).toBeInTheDocument()
      expect(within(screen.getByTestId('column-20')).getByTestId('card-200')).toBeInTheDocument()
    })
  })
})
