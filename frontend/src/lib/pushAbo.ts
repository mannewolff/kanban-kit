import type { Vorschau } from '../api/auth'
import { ApiError } from '../api/client'
import { pushApi } from '../api/push'

/**
 * Das Abo für Benachrichtigungen auf diesem Gerät (Issue #1999, Plan #1989 E14, E18): Service
 * Worker mit dem Bereich `/mobil` anmelden, Erlaubnis einholen, abonnieren, auffrischen und
 * abmelden. Das Abo gilt am Server bis zum Ende der Sitzung; `/mobil` frischt es darum bei jedem
 * Öffnen auf.
 */

const BEREICH = '/mobil'
const WORKER = '/mobil-sw.js'

/** Kann dieser Browser Push (E18)? Ohne Service Worker und `PushManager` nicht. */
export function kannPush(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window
}

/** Wurde die Oberfläche vom Startbildschirm geöffnet (E18)? Auf dem iPhone geht Push nur dann. */
export function vomStartbildschirm(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(display-mode: standalone)').matches
}

/** Der öffentliche Schlüssel des Servers (base64url) als Bytes für `applicationServerKey`. */
export function schluesselBytes(base64url: string): Uint8Array<ArrayBuffer> {
  const base64 = base64url.replaceAll('-', '+').replaceAll('_', '/')
  const roh = atob(base64 + '='.repeat((4 - (base64.length % 4)) % 4))
  return Uint8Array.from(roh, (zeichen) => zeichen.charCodeAt(0))
}

/** Das Abo dieses Geräts, wenn ein Worker im Bereich `/mobil` eines hält; sonst `null`. */
export async function bestehendesAbo(): Promise<PushSubscription | null> {
  if (!kannPush()) return null
  const registrierung = await navigator.serviceWorker.getRegistration(BEREICH)
  return registrierung ? registrierung.pushManager.getSubscription() : null
}

function istVergeben(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409
}

/**
 * Schaltet Benachrichtigungen auf diesem Gerät ein: Erlaubnis einholen, Worker anmelden, abonnieren,
 * Abo setzen. `null`, wenn die Erlaubnis fehlt. Gehört der Endpunkt am Server einem anderen Konto
 * (409), legt das Gerät ein neues Abo an.
 */
export async function einschalten(publicKey: string): Promise<PushSubscription | null> {
  if ((await Notification.requestPermission()) !== 'granted') return null
  await navigator.serviceWorker.register(WORKER, { scope: BEREICH })
  const registrierung = await navigator.serviceWorker.ready
  const optionen = { userVisibleOnly: true, applicationServerKey: schluesselBytes(publicKey) }
  const sub = await registrierung.pushManager.subscribe(optionen)
  try {
    await pushApi.aboSetzen(sub)
    return sub
  } catch (error) {
    if (!istVergeben(error)) throw error
  }
  await sub.unsubscribe()
  const neu = await registrierung.pushManager.subscribe(optionen)
  await pushApi.aboSetzen(neu)
  return neu
}

/**
 * Frischt das Abo dieses Geräts bis zum Ende der laufenden Sitzung auf — nur, wenn eines besteht und
 * keine Vorschau läuft. Gehört der Endpunkt einem anderen Konto, endet das Abo auf dem Gerät: Seine
 * Nachrichten kämen sonst für das andere Konto.
 */
export async function auffrischen(vorschau: Vorschau | null): Promise<void> {
  if (vorschau !== null) return
  const sub = await bestehendesAbo()
  if (!sub) return
  try {
    await pushApi.aboSetzen(sub)
  } catch (error) {
    if (!istVergeben(error)) throw error
    await sub.unsubscribe()
  }
}

/**
 * Beendet das Abo dieses Geräts: zuerst `unsubscribe()` beim Push-Dienst, dann am Server. Kennt der
 * Server das Abo nicht mehr (404), ist es schon beendet.
 */
export async function abmelden(): Promise<void> {
  const sub = await bestehendesAbo()
  if (!sub) return
  await sub.unsubscribe()
  try {
    await pushApi.aboLoeschen(sub.endpoint)
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 404)) throw error
  }
}
