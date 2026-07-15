import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Board } from '../api/boards'
import type { Card } from '../api/cards'
import { ApiError } from '../api/client'
import { columnsApi } from '../api/columns'
import { BoardView } from './BoardView'

vi.mock('../api/columns', () => ({
  columnsApi: { create: vi.fn(), update: vi.fn(), remove: vi.fn(), reorder: vi.fn() },
}))
// Nur vom Transfer-Dialog zur Laufzeit genutzt; leere Listen genügen zum Öffnen.
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn().mockResolvedValue([]) } }))
const mColumns = columnsApi as unknown as {
  create: ReturnType<typeof vi.fn>
  update: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
  reorder: ReturnType<typeof vi.fn>
}

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
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
}

function mkApi(over: Record<string, unknown> = {}) {
  return {
    create: vi.fn(), move: vi.fn(), archive: vi.fn(), restore: vi.fn(), remove: vi.fn(),
    bulkArchive: vi.fn(), bulkTransfer: vi.fn(), ...over,
  }
}

function dropOnColumn(columnId: number, cardId: number) {
  fireEvent.drop(screen.getByTestId(`column-${columnId}`), {
    dataTransfer: { getData: () => String(cardId) },
  })
}

describe('BoardView', () => {
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
      expect(api.create).toHaveBeenCalledWith(1, 20, 'Neu', expect.stringContaining('## Kontext'), null),
    )
    expect(within(screen.getByTestId('column-20')).getByTestId('card-200')).toBeInTheDocument()
  })

  it('blendet Anlege-Buttons für Nicht-Editoren aus', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)
    expect(screen.queryByLabelText('Karte in Done anlegen')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Neues Item' })).not.toBeInTheDocument()
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

  it('dupliziert eine Karte über das ⋮-Menü vorbefüllt in derselben Spalte', async () => {
    const source: Card = { ...card, title: 'Original', description: 'Original-Text', parentId: 9 }
    const created: Card = { ...card, id: 300, number: 3, title: 'Original' }
    const api = mkApi({ create: vi.fn().mockResolvedValue(created) })
    render(<BoardView board={board} initialCards={[source]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Menü Original'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Duplizieren' }))

    expect(screen.getByLabelText('Titel')).toHaveValue('Original')
    expect(screen.getByLabelText('Beschreibung')).toHaveValue('Original-Text')

    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(api.create).toHaveBeenCalledWith(1, 10, 'Original', 'Original-Text', 9))
    // Quellkarte bleibt unverändert erhalten.
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
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

  it('ordnet Spalten per Drag & Drop neu und persistiert die Reihenfolge', async () => {
    mColumns.reorder.mockResolvedValue([
      { id: 20, name: 'Done', position: 0, wipLimit: null },
      { id: 10, name: 'Backlog', position: 1, wipLimit: null },
    ])
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    fireEvent.drop(screen.getByTestId('column-header-10'))

    await waitFor(() => expect(mColumns.reorder).toHaveBeenCalledWith(1, [20, 10]))
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

  it('zeigt Zuständigen-Avatare mit Initialen auf der Karte', () => {
    const assigned: Card = { ...card, assignees: [5] }
    const members = [{ userId: 5, email: 'm@x.de', displayName: 'Max Mustermann', role: 'MEMBER' as const }]
    render(
      <BoardView board={board} initialCards={[assigned]} canEdit members={members} api={mkApi()} />,
    )

    expect(screen.getByLabelText('Zuständige Aufgabe')).toBeInTheDocument()
    expect(screen.getByText('MM')).toBeInTheDocument()
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
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))

    // Bestätigungsdialog erscheint; erst dessen Bestätigung löst die API aus.
    expect(api.bulkArchive).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

    await waitFor(() => expect(api.bulkArchive).toHaveBeenCalledWith([100]))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
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

    await waitFor(() => expect(screen.getByText('Archivieren fehlgeschlagen.')).toBeInTheDocument())
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
})
