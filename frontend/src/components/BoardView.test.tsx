import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Board } from '../api/boards'
import { cardsApi } from '../api/cards'
import type { Card } from '../api/cards'
import { ApiError } from '../api/client'
import { columnsApi } from '../api/columns'
import { boardsApi } from '../api/boards'
import { projectsApi } from '../api/projects'
import { BoardView } from './BoardView'

vi.mock('../api/columns', () => ({
  columnsApi: { create: vi.fn(), update: vi.fn(), remove: vi.fn(), reorder: vi.fn() },
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

  it('legt über den +Dialog eine Karte mit Beschreibung an', async () => {
    const created: Card = { ...card, id: 200, number: 2, title: 'Neu', columnId: 20 }
    const api = mkApi({ create: vi.fn().mockResolvedValue(created) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Karte in Done anlegen'))
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith(1, 20, 'Neu', expect.stringContaining('## Kontext'), null, false, {
        dependencies: [],
        dueDate: null,
        assigneeIds: [],
        labelIds: [],
      }),
    )
    expect(within(screen.getByTestId('column-20')).getByTestId('card-200')).toBeInTheDocument()
  })

  it('blendet Anlege-Buttons für Nicht-Editoren aus', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)
    expect(screen.queryByLabelText('Karte in Done anlegen')).not.toBeInTheDocument()
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

    fireEvent.click(screen.getByLabelText('Karte in Backlog anlegen'))
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
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 }]
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
    fireEvent.click(screen.getByRole('menuitem', { name: 'Nach Done' }))
    await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 20, 0))
  })

  it('zeigt Ideen (ideaStored) nicht in der Spaltenansicht', () => {
    const idea: Card = { ...card, id: 500, number: 5, title: 'Idee', ideaStored: true }
    render(<BoardView board={board} initialCards={[card, idea]} canEdit api={mkApi()} />)

    expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
    expect(within(screen.getByTestId('column-10')).queryByTestId('card-500')).not.toBeInTheDocument()
  })

  it('legt eine Karte über das ⋮-Menü in den Ideen-Speicher und entfernt sie optimistisch', async () => {
    const api = mkApi({ moveToIdeaStorage: vi.fn().mockResolvedValue({}) })
    const onCardsChanged = vi.fn()
    // Zweite Karte in derselben Spalte: der optimistische map bleibt für sie unverändert (: c-Zweig).
    const other: Card = { ...card, id: 101, number: 2, title: 'Andere' }
    render(<BoardView board={board} initialCards={[card, other]} canEdit api={api} onCardsChanged={onCardsChanged} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'In Ideen-Speicher' }))

    await waitFor(() => expect(api.moveToIdeaStorage).toHaveBeenCalledWith(100))
    expect(onCardsChanged).toHaveBeenCalled()
    // Optimistisch aus dem Board entfernt (ideaStored filtert die Spaltenansicht).
    expect(within(screen.getByTestId('column-10')).queryByTestId('card-100')).not.toBeInTheDocument()
    // Die zweite Karte bleibt unangetastet sichtbar.
    expect(within(screen.getByTestId('column-10')).getByTestId('card-101')).toBeInTheDocument()
  })

  it('rollt bei Fehler im Ideen-Speicher zurück und zeigt die Karte wieder', async () => {
    const api = mkApi({ moveToIdeaStorage: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'In Ideen-Speicher' }))

    await screen.findByText('In den Ideen-Speicher fehlgeschlagen.')
    expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
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
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 }]
    const inEpic: Card = { ...card, id: 100, parentId: 9 }
    const other: Card = { ...card, id: 200, number: 2, parentId: null }
    render(<BoardView board={board} initialCards={[inEpic, other]} canEdit epics={epics} api={mkApi()} />)

    expect(screen.getByTestId('card-100')).toBeInTheDocument()
    expect(screen.getByTestId('card-200')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Epic-Filter'), { target: { value: '9' } })
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
    const boardLabels = [{ id: 5, boardId: 1, name: 'Bug', color: '#f00' }]
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
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

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
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

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
    fireEvent.click(screen.getByRole('menuitem', { name: 'Nach Done' }))

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

  it('blendet die Fehler-Snackbar nach Ablauf der Anzeigedauer wieder aus', async () => {
    vi.useFakeTimers()
    try {
      const api = mkApi({ bulkArchive: vi.fn().mockRejectedValue(new Error('fail')) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      fireEvent.click(screen.getByTestId('card-100'))
      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

      await vi.waitFor(() =>
        expect(screen.getByText('Archivieren fehlgeschlagen.')).toBeInTheDocument(),
      )

      vi.advanceTimersByTime(5000)

      await vi.waitFor(() =>
        expect(screen.queryByText('Archivieren fehlgeschlagen.')).not.toBeInTheDocument(),
      )
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
      const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 }]
      const inEpic: Card = { ...card, parentId: 9 }
      render(<BoardView board={board} initialCards={[inEpic]} canEdit epics={epics} api={mkApi()} />)
      fireEvent.change(screen.getByLabelText('Epic-Filter'), { target: { value: '9' } })
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
      const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 }]
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
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 }]
    const inEpic: Card = { ...card, id: 100, parentId: 9 }
    const other: Card = { ...card, id: 200, number: 2, parentId: null }
    render(<BoardView board={board} initialCards={[inEpic, other]} canEdit epics={epics} api={mkApi()} />)

    // Erst filtern (setItem/Number-Zweig) ...
    fireEvent.change(screen.getByLabelText('Epic-Filter'), { target: { value: '9' } })
    expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
    // ... dann auf leeren Wert zurück: onChange nimmt den null-Zweig, changeEpicFilter den removeItem-Zweig.
    fireEvent.change(screen.getByLabelText('Epic-Filter'), { target: { value: '' } })
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
      expect(screen.getByLabelText('Karte in Backlog anlegen')).toBeInTheDocument()
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
})
