import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { projectsApi } from '../api/projects'
import { TransferCardDialog } from './TransferCardDialog'

vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn() } }))
vi.mock('../api/cards', () => ({ cardsApi: { bulkTransfer: vi.fn() } }))

const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedBoards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedCards = cardsApi as unknown as { bulkTransfer: ReturnType<typeof vi.fn> }

const card: Card = {
  id: 7, boardId: 99, columnId: 1, number: 3, title: 'Karte', description: null,
  positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
  derivedFrom: null,
}

function renderDialog(platformAdmin = false, sourceColumnPosition: number | null = null) {
  return render(
    <TransferCardDialog
      cardIds={[card.id]}
      currentBoardId={99}
      currentProjectId={1}
      sourceColumnPosition={sourceColumnPosition}
      platformAdmin={platformAdmin}
      onClose={vi.fn()}
      onTransferred={vi.fn()}
    />,
  )
}

describe('TransferCardDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedProjects.list.mockResolvedValue([
      { id: 1, name: 'Eigenes', role: 'OWNER', createdAt: '' },
      { id: 2, name: 'Fremdes', role: 'MEMBER', createdAt: '' },
    ])
    mockedBoards.list.mockResolvedValue([
      { id: 10, name: 'Ziel', projectId: 1, createdAt: '', columns: [
        { id: 100, name: 'Backlog', position: 0, wipLimit: null },
      ] },
      { id: 99, name: 'Aktuell', projectId: 1, createdAt: '', columns: [] },
    ])
  })

  it('zeigt nur OWNER-Projekte und den Warnhinweis', async () => {
    renderDialog(false)
    expect(await screen.findByRole('option', { name: 'Eigenes' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Fremdes' })).not.toBeInTheDocument()
    expect(screen.getByText(/Vorhaben-Zuordnung und Abhängigkeiten/)).toBeInTheDocument()
  })

  it('formuliert den Hinweis im Plural bei mehreren Karten', async () => {
    render(
      <TransferCardDialog
        cardIds={[7, 8]}
        currentBoardId={99}
        currentProjectId={1}
        sourceColumnPosition={null}
        platformAdmin={false}
        onClose={vi.fn()}
        onTransferred={vi.fn()}
      />,
    )
    expect(await screen.findByText(/Die 2 Karten werden/)).toBeInTheDocument()
  })

  it('zeigt einem Plattform-Admin alle Projekte', async () => {
    renderDialog(true)
    expect(await screen.findByRole('option', { name: 'Eigenes' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Fremdes' })).toBeInTheDocument()
  })

  it('verschiebt nach Auswahl von Projekt, Board und Spalte (aktuelles Board ausgeschlossen)', async () => {
    mockedCards.bulkTransfer.mockResolvedValue([{ ...card, boardId: 10 }])
    const onTransferred = vi.fn()
    render(
      <TransferCardDialog
        cardIds={[card.id]}
        currentBoardId={99}
        currentProjectId={1}
        sourceColumnPosition={null}
        platformAdmin={false}
        onClose={vi.fn()}
        onTransferred={onTransferred}
      />,
    )

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '1' } })
    await waitFor(() => expect(screen.getByLabelText('Zielboard')).not.toBeDisabled())
    // Das aktuelle Board (99) darf nicht als Ziel wählbar sein.
    expect(screen.queryByRole('option', { name: 'Aktuell' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '10' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '100' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mockedCards.bulkTransfer).toHaveBeenCalledWith([7], 10, 100))
    expect(onTransferred).toHaveBeenCalled()
  })

  it('leert die Auswahl über die (wählen)-Option und deaktiviert dann Verschieben', async () => {
    renderDialog(false)

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '1' } })
    await waitFor(() => expect(screen.getByLabelText('Zielboard')).not.toBeDisabled())
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '10' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '100' } })

    // Auswahl wieder leeren -> deckt den ''-Zweig der drei onChange ab.
    fireEvent.change(screen.getByLabelText('Zielspalte'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Zielprojekt'), { target: { value: '' } })

    expect(screen.getByRole('button', { name: 'Verschieben' })).toBeDisabled()
  })

  it('zeigt eine Fehlermeldung, wenn das Verschieben fehlschlägt', async () => {
    mockedCards.bulkTransfer.mockRejectedValue(new Error('boom'))
    renderDialog(false)

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '1' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '10' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '100' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    expect(await screen.findByText('Verschieben fehlgeschlagen.')).toBeInTheDocument()
  })

  it('öffnet mit dem Projekt des Quellboards bereits ausgewählt', async () => {
    renderDialog(false)

    // Ohne einen einzigen Klick: Projekt gesetzt, Board-Auswahl frei und schon befüllt.
    expect(await screen.findByRole('option', { name: 'Ziel' })).toBeInTheDocument()
    expect(screen.getByLabelText('Zielprojekt')).toHaveValue('1')
    expect(screen.getByLabelText('Zielboard')).not.toBeDisabled()
    expect(screen.getByLabelText('Zielboard')).toHaveValue('')
  })

  it('belegt die Zielspalte an derselben Ordnungsposition wie die Quellspalte vor', async () => {
    // Absichtlich unsortiert geliefert: die Vorbelegung muss nach `position` sortieren.
    mockedBoards.list.mockResolvedValue([
      { id: 10, name: 'Ziel', projectId: 1, createdAt: '', columns: [
        { id: 102, name: 'Doing', position: 1, wipLimit: null },
        { id: 101, name: 'Backlog', position: 0, wipLimit: null },
      ] },
    ])
    renderDialog(false, 1)

    await screen.findByRole('option', { name: 'Ziel' })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '10' } })

    expect(screen.getByLabelText('Zielspalte')).toHaveValue('102')
    expect(screen.getByLabelText('Zielspalte')).not.toBeDisabled()
  })

  it('lässt die Zielspalte leer, wenn das Zielboard an der Quellposition keine Spalte hat', async () => {
    // Standard-Mock: Board 10 hat genau eine Spalte, Position 4 existiert dort nicht.
    renderDialog(false, 4)

    await screen.findByRole('option', { name: 'Ziel' })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '10' } })

    expect(screen.getByLabelText('Zielspalte')).toHaveValue('')
  })

  it('lässt die Zielspalte leer, wenn die Quellspalte nicht eindeutig ist', async () => {
    renderDialog(false, null)

    await screen.findByRole('option', { name: 'Ziel' })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '10' } })

    expect(screen.getByLabelText('Zielspalte')).toHaveValue('')
  })

  it('belegt die Zielspalte beim Wechsel des Zielboards erneut anhand der Quellposition vor', async () => {
    mockedBoards.list.mockResolvedValue([
      { id: 10, name: 'Ziel A', projectId: 1, createdAt: '', columns: [
        { id: 101, name: 'Backlog', position: 0, wipLimit: null },
        { id: 102, name: 'Doing', position: 1, wipLimit: null },
      ] },
      { id: 11, name: 'Ziel B', projectId: 1, createdAt: '', columns: [
        { id: 111, name: 'Offen', position: 0, wipLimit: null },
        { id: 112, name: 'Läuft', position: 1, wipLimit: null },
      ] },
    ])
    renderDialog(false, 1)

    await screen.findByRole('option', { name: 'Ziel A' })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '10' } })
    expect(screen.getByLabelText('Zielspalte')).toHaveValue('102')

    // Manuell umgewählt — die Wahl darf nicht auf das nächste Board „durchschlagen“.
    fireEvent.change(screen.getByLabelText('Zielspalte'), { target: { value: '101' } })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '11' } })

    expect(screen.getByLabelText('Zielspalte')).toHaveValue('112')
  })
})
