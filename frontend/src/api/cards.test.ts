import { afterEach, describe, expect, it, vi } from 'vitest'
import { cardsApi } from './cards'

function spyFetch(body = '{}') {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(body),
  } as Response)
}

function lastCall(fetchSpy: ReturnType<typeof spyFetch>) {
  const [url, init] = fetchSpy.mock.calls[fetchSpy.mock.calls.length - 1]
  return { url, method: init?.method, body: init?.body }
}

afterEach(() => vi.restoreAllMocks())

const card = {
  id: 1, boardId: 3, columnId: 10, number: 5, title: 'Karte', description: null,
  positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD' as const, parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
}

describe('cardsApi', () => {
  it('list ruft GET /api/boards/{id}/cards und liefert die geparste Antwort', async () => {
    spyFetch(JSON.stringify([card]))
    const result = await cardsApi.list(3)
    expect(result).toEqual([card])
  })

  it('getActivity ruft GET /api/cards/{id}/activity und liefert die geparste Antwort', async () => {
    const activity = [{ id: 1, actorUserId: 5, type: 'MOVED', detail: 'Von A nach B', createdAt: '2026-01-01' }]
    spyFetch(JSON.stringify(activity))
    const result = await cardsApi.getActivity(1)
    expect(result).toEqual(activity)
  })

  it('listTrash ruft GET /api/boards/{id}/trash', async () => {
    const f = spyFetch()
    await cardsApi.listTrash(3)
    expect(lastCall(f).url).toBe('/api/boards/3/trash')
  })

  it('restoreDeleted ruft POST /api/cards/{id}/restore-deleted', async () => {
    const f = spyFetch()
    await cardsApi.restoreDeleted(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1/restore-deleted')
    expect(c.method).toBe('POST')
  })

  it('purge ruft DELETE /api/cards/{id}/purge', async () => {
    const f = spyFetch()
    await cardsApi.purge(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1/purge')
    expect(c.method).toBe('DELETE')
  })

  it('create ruft POST /api/boards/{id}/cards mit Spalte, Titel, Beschreibung und Epic', async () => {
    const f = spyFetch()
    await cardsApi.create(3, 10, 'Neue Karte', 'Text', 7)
    const c = lastCall(f)
    expect(c.url).toBe('/api/boards/3/cards')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ columnId: 10, title: 'Neue Karte', description: 'Text', parentId: 7 })
  })

  it('create reicht ideaStored durch, wenn gesetzt', async () => {
    const f = spyFetch()
    await cardsApi.create(3, 10, 'Idee', undefined, null, true)
    const c = lastCall(f)
    expect(JSON.parse(String(c.body))).toEqual({ columnId: 10, title: 'Idee', parentId: null, ideaStored: true })
  })

  it('moveToIdeaStorage ruft POST /api/cards/{id}/idea-storage', async () => {
    const f = spyFetch()
    await cardsApi.moveToIdeaStorage(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1/idea-storage')
    expect(c.method).toBe('POST')
  })


  it('move ruft POST /api/cards/{id}/move mit Spalte und Position', async () => {
    const f = spyFetch()
    await cardsApi.move(1, 10, 2)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1/move')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ columnId: 10, position: 2 })
  })

  it('transfer ruft POST /api/cards/{id}/transfer mit Ziel-Board und -Spalte', async () => {
    const f = spyFetch()
    await cardsApi.transfer(1, 4, 11)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1/transfer')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ targetBoardId: 4, targetColumnId: 11 })
  })

  it('setAssignees ruft PUT /api/cards/{id}/assignees', async () => {
    const f = spyFetch()
    await cardsApi.setAssignees(1, [5, 6])
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1/assignees')
    expect(c.method).toBe('PUT')
    expect(JSON.parse(String(c.body))).toEqual({ assignees: [5, 6] })
  })

  it('setLabels ruft PUT /api/cards/{id}/labels', async () => {
    const f = spyFetch()
    await cardsApi.setLabels(1, [2, 3])
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1/labels')
    expect(c.method).toBe('PUT')
    expect(JSON.parse(String(c.body))).toEqual({ labels: [2, 3] })
  })

  it('archive ruft POST /api/cards/{id}/archive', async () => {
    const f = spyFetch()
    await cardsApi.archive(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1/archive')
    expect(c.method).toBe('POST')
  })

  it('bulkArchive ruft POST /api/cards/bulk-archive mit den Karten-IDs', async () => {
    const f = spyFetch()
    await cardsApi.bulkArchive([1, 2, 3])
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/bulk-archive')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ cardIds: [1, 2, 3] })
  })

  it('bulkTransfer ruft POST /api/cards/bulk-transfer mit IDs, Ziel-Board und -Spalte', async () => {
    const f = spyFetch()
    await cardsApi.bulkTransfer([1, 2], 4, 11)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/bulk-transfer')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ cardIds: [1, 2], targetBoardId: 4, targetColumnId: 11 })
  })

  it('bulkDelete ruft POST /api/cards/bulk-delete mit den Karten-IDs', async () => {
    const f = spyFetch()
    await cardsApi.bulkDelete([1, 2])
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/bulk-delete')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ cardIds: [1, 2] })
  })

  it('restore ruft POST /api/cards/{id}/restore', async () => {
    const f = spyFetch()
    await cardsApi.restore(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1/restore')
    expect(c.method).toBe('POST')
  })

  it('remove ruft DELETE /api/cards/{id}', async () => {
    const f = spyFetch()
    await cardsApi.remove(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1')
    expect(c.method).toBe('DELETE')
  })

  it('update ruft PATCH /api/cards/{id} mit allen Feldern', async () => {
    const f = spyFetch()
    await cardsApi.update(1, 'Titel', 'Beschreibung', [2, 3], 'ABC', 7, '2026-12-31')
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({
      title: 'Titel', description: 'Beschreibung', dependencies: [2, 3],
      shortcode: 'ABC', parentId: 7, dueDate: '2026-12-31',
    })
  })

  it('update funktioniert auch ohne die optionalen Felder', async () => {
    const f = spyFetch()
    await cardsApi.update(1, 'Titel', null)
    const c = lastCall(f)
    expect(JSON.parse(String(c.body))).toEqual({
      title: 'Titel', description: null, dependencies: undefined,
      shortcode: undefined, parentId: undefined, dueDate: undefined,
    })
  })
})
