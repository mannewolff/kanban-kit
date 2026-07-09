import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { AttachmentsApi } from '../api/attachments'
import type { Card } from '../api/cards'
import type { CommentsApi } from '../api/comments'
import { CardDetailModal } from './CardDetailModal'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 7, email: 'a@b.c', displayName: 'A', platformRole: 'USER', memberships: [] } }),
}))

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 5, title: 'Aufgabe', description: '# Titel\n\n- a\n- b',
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [3, 4],
}

function makeApis() {
  const commentsApi = {
    list: vi.fn().mockResolvedValue([
      { id: 1, cardId: 100, authorUserId: 7, authorName: 'A', body: 'Hallo', createdAt: '', updatedAt: '' },
    ]),
    create: vi.fn().mockResolvedValue(
      { id: 2, cardId: 100, authorUserId: 7, authorName: 'A', body: 'Neu', createdAt: '', updatedAt: '' },
    ),
    update: vi.fn(),
    remove: vi.fn().mockResolvedValue(undefined),
  } satisfies CommentsApi
  const attachmentsApi = {
    list: vi.fn().mockResolvedValue([]),
    upload: vi.fn(),
    remove: vi.fn(),
    fetchBlob: vi.fn(),
  } satisfies AttachmentsApi
  const cardsApi = { update: vi.fn().mockResolvedValue({ ...card }) }
  return { commentsApi, attachmentsApi, cardsApi }
}

describe('CardDetailModal', () => {
  it('rendert Markdown-Beschreibung, Abhängigkeiten und vorhandene Kommentare', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    expect(screen.getByRole('heading', { name: 'Titel' })).toBeInTheDocument()
    expect(screen.getByText('#3')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('Hallo')).toBeInTheDocument())
  })

  it('legt einen Kommentar an und zeigt ihn sofort', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.change(screen.getByLabelText('Kommentar schreiben'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Senden' }))

    await waitFor(() => expect(apis.commentsApi.create).toHaveBeenCalledWith(100, 'Neu'))
    expect(await screen.findByText('Neu')).toBeInTheDocument()
  })

  it('speichert eine geänderte Beschreibung', async () => {
    const apis = makeApis()
    const onChanged = vi.fn()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} onChanged={onChanged} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Beschreibung bearbeiten'), { target: { value: 'Neuer Text' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(apis.cardsApi.update).toHaveBeenCalledWith(100, 'Aufgabe', 'Neuer Text'))
    expect(onChanged).toHaveBeenCalled()
  })
})
