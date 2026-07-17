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
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
}

function renderDialog(platformAdmin = false) {
  return render(
    <TransferCardDialog
      cardIds={[card.id]}
      currentBoardId={99}
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
    expect(screen.getByText(/Epic-Zuordnung und Abhängigkeiten/)).toBeInTheDocument()
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

  it('zeigt eine Fehlermeldung, wenn das Verschieben fehlschlägt', async () => {
    mockedCards.bulkTransfer.mockRejectedValue(new Error('boom'))
    renderDialog(false)

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '1' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '10' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '100' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    expect(await screen.findByText('Verschieben fehlgeschlagen.')).toBeInTheDocument()
  })
})
