import { apiFetch } from './client'

/**
 * Benachrichtigungen auf dem Handy (Issue #1999, Plan #1989 E13, E14), gegen `/api/push/**` aus
 * Issue #1997. Ein Abo wird über den Endpunkt des Push-Dienstes angesprochen, der für das Gerät steht.
 */

/** Ein Board und ob Nachrichten zu seinen Läufen auf diesem Gerät kommen. */
export interface PushBoard {
  boardId: number
  boardName: string
  projectId: number
  projectName: string
  an: boolean
}

/** Das Abo, wie der Server es erwartet: flach statt `keys.p256dh` und `keys.auth`. */
function aboKoerper(sub: PushSubscription): string {
  const { endpoint, keys } = sub.toJSON()
  return JSON.stringify({ endpoint, p256dh: keys?.p256dh, auth: keys?.auth })
}

export const pushApi = {
  /** Der öffentliche Schlüssel des Servers; 404, wenn Benachrichtigungen nicht eingerichtet sind. */
  schluessel: () => apiFetch<{ publicKey: string }>('/api/push/schluessel'),
  /** Legt das Abo dieses Geräts an oder frischt es bis zum Ende der Sitzung auf. */
  aboSetzen: (sub: PushSubscription) => apiFetch<void>('/api/push/abo', { method: 'PUT', body: aboKoerper(sub) }),
  /** Beendet das Abo dieses Geräts samt seinen Board-Schaltern. */
  aboLoeschen: (endpoint: string) =>
    apiFetch<void>('/api/push/abo', { method: 'DELETE', body: JSON.stringify({ endpoint }) }),
  /** Die Boards, zu deren Läufen auf diesem Gerät Nachrichten kommen können. */
  boards: (endpoint: string) => apiFetch<PushBoard[]>(`/api/push/boards?${new URLSearchParams({ endpoint })}`),
  /** Schaltet Nachrichten zu Läufen eines Boards auf diesem Gerät ein oder ab. */
  boardSchalten: (endpoint: string, boardId: number, an: boolean) =>
    apiFetch<void>(`/api/push/boards/${boardId}`, { method: 'PUT', body: JSON.stringify({ endpoint, an }) }),
}
