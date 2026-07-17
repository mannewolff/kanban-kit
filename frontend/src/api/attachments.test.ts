import { afterEach, describe, expect, it, vi } from 'vitest'
import { attachmentsApi } from './attachments'
import { ApiError } from './client'

function spyFetch(overrides: Partial<Response> = {}) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    json: () => Promise.resolve({}),
    blob: () => Promise.resolve(new Blob(['data'])),
    ...overrides,
  } as Response)
}

afterEach(() => vi.restoreAllMocks())

const attachment = {
  id: 1, cardId: 7, filename: 'doc.pdf', contentType: 'application/pdf', size: 1024, createdAt: '2026-01-01',
}

describe('attachmentsApi', () => {
  it('list ruft GET /api/cards/{id}/attachments mit Cookies und liefert die geparste Antwort', async () => {
    const f = spyFetch({ json: () => Promise.resolve([attachment]) })
    const result = await attachmentsApi.list(7)
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/cards/7/attachments')
    expect(init?.credentials).toBe('include')
    expect(result).toEqual([attachment])
  })

  it('list wirft ApiError bei Fehlerantwort', async () => {
    spyFetch({ ok: false, status: 404, statusText: 'Not Found' })
    await expect(attachmentsApi.list(7)).rejects.toMatchObject({ status: 404 })
  })

  it('upload ruft POST mit FormData-Body ohne explizites Content-Type', async () => {
    const f = spyFetch({ json: () => Promise.resolve(attachment) })
    const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' })
    const result = await attachmentsApi.upload(7, file)
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/cards/7/attachments')
    expect(init?.method).toBe('POST')
    expect(init?.credentials).toBe('include')
    expect(init?.body).toBeInstanceOf(FormData)
    expect((init?.body as FormData).get('file')).toBe(file)
    expect((init?.headers as Record<string, string> | undefined)?.['Content-Type']).toBeUndefined()
    expect(result).toEqual(attachment)
  })

  it('upload wirft ApiError bei Fehlerantwort (z. B. Anhangslimit)', async () => {
    spyFetch({ ok: false, status: 413, statusText: 'Payload Too Large' })
    const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' })
    await expect(attachmentsApi.upload(7, file)).rejects.toBeInstanceOf(ApiError)
  })

  it('remove ruft DELETE /api/attachments/{id}', async () => {
    const f = spyFetch()
    await attachmentsApi.remove(1)
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/attachments/1')
    expect(init?.method).toBe('DELETE')
    expect(init?.credentials).toBe('include')
  })

  it('remove wirft ApiError bei Fehlerantwort', async () => {
    spyFetch({ ok: false, status: 403, statusText: 'Forbidden' })
    await expect(attachmentsApi.remove(1)).rejects.toBeInstanceOf(ApiError)
  })

  it('fetchBlob ruft GET /api/attachments/{id} und liefert einen Blob', async () => {
    const blob = new Blob(['bilddaten'])
    const f = spyFetch({ blob: () => Promise.resolve(blob) })
    const result = await attachmentsApi.fetchBlob(1)
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/attachments/1')
    expect(init?.credentials).toBe('include')
    expect(result).toBe(blob)
  })

  it('fetchBlob wirft ApiError bei Fehlerantwort', async () => {
    spyFetch({ ok: false, status: 404, statusText: 'Not Found' })
    await expect(attachmentsApi.fetchBlob(1)).rejects.toBeInstanceOf(ApiError)
  })
})
