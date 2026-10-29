import { afterEach, describe, expect, it, vi } from 'vitest'
import { pushApi } from './push'

function spyFetch(body = '') {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: body === '' ? 204 : 200,
    statusText: 'OK',
    text: () => Promise.resolve(body),
  } as Response)
}

const ENDPUNKT = 'https://push.example/abc'

const abo = {
  toJSON: () => ({ endpoint: ENDPUNKT, keys: { p256dh: 'schluessel', auth: 'geheim' } }),
} as unknown as PushSubscription

describe('pushApi', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('liest den Schlüssel des Servers', async () => {
    const fetchSpy = spyFetch('{"publicKey":"BAA"}')
    await expect(pushApi.schluessel()).resolves.toEqual({ publicKey: 'BAA' })
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/push/schluessel')
  })

  it('setzt das Abo flach mit Endpunkt und Schlüsseln', async () => {
    const fetchSpy = spyFetch()
    await pushApi.aboSetzen(abo)
    const [pfad, optionen] = fetchSpy.mock.calls[0]
    expect(pfad).toBe('/api/push/abo')
    expect(optionen?.method).toBe('PUT')
    expect(JSON.parse(optionen?.body as string)).toEqual({ endpoint: ENDPUNKT, p256dh: 'schluessel', auth: 'geheim' })
  })

  it('löscht das Abo über seinen Endpunkt', async () => {
    const fetchSpy = spyFetch()
    await pushApi.aboLoeschen(ENDPUNKT)
    const [pfad, optionen] = fetchSpy.mock.calls[0]
    expect(pfad).toBe('/api/push/abo')
    expect(optionen?.method).toBe('DELETE')
    expect(JSON.parse(optionen?.body as string)).toEqual({ endpoint: ENDPUNKT })
  })

  it('liest die Boards des Abos', async () => {
    const fetchSpy = spyFetch('[{"boardId":4}]')
    await expect(pushApi.boards(ENDPUNKT)).resolves.toEqual([{ boardId: 4 }])
    expect(fetchSpy.mock.calls[0][0]).toBe(`/api/push/boards?endpoint=${encodeURIComponent(ENDPUNKT)}`)
  })

  it('schaltet ein Board', async () => {
    const fetchSpy = spyFetch()
    await pushApi.boardSchalten(ENDPUNKT, 40, false)
    const [pfad, optionen] = fetchSpy.mock.calls[0]
    expect(pfad).toBe('/api/push/boards/40')
    expect(optionen?.method).toBe('PUT')
    expect(JSON.parse(optionen?.body as string)).toEqual({ endpoint: ENDPUNKT, an: false })
  })
})
