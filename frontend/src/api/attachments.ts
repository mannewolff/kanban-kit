import { ApiError } from './client'

export interface Attachment {
  id: number
  cardId: number
  filename: string
  contentType: string
  size: number
  createdAt: string
}

async function listAttachments(cardId: number): Promise<Attachment[]> {
  const res = await fetch(`/api/cards/${cardId}/attachments`, { credentials: 'include' })
  if (!res.ok) throw new ApiError(res.status, res.statusText)
  return res.json()
}

async function upload(cardId: number, file: File): Promise<Attachment> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`/api/cards/${cardId}/attachments`, {
    method: 'POST',
    credentials: 'include',
    body: form, // KEIN Content-Type setzen -> Browser setzt multipart-Boundary
  })
  if (!res.ok) throw new ApiError(res.status, res.statusText)
  return res.json()
}

async function remove(id: number): Promise<void> {
  const res = await fetch(`/api/attachments/${id}`, { method: 'DELETE', credentials: 'include' })
  if (!res.ok) throw new ApiError(res.status, res.statusText)
}

/** Lädt den Blob (für die Inline-Vorschau; der Download-Endpunkt liefert Content-Disposition: attachment). */
async function fetchBlob(id: number): Promise<Blob> {
  const res = await fetch(`/api/attachments/${id}`, { credentials: 'include' })
  if (!res.ok) throw new ApiError(res.status, res.statusText)
  return res.blob()
}

export const attachmentsApi = { list: listAttachments, upload, remove, fetchBlob }
export type AttachmentsApi = typeof attachmentsApi
