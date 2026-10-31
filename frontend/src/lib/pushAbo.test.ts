import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { pushApi } from '../api/push'
import { abmelden, auffrischen, bestehendesAbo, einschalten, kannPush, schluesselBytes, vomStartbildschirm } from './pushAbo'

vi.mock('../api/push', () => ({
  pushApi: { aboSetzen: vi.fn(), aboLoeschen: vi.fn() },
}))

const api = vi.mocked(pushApi)

function abo(endpoint = 'https://push.example/abc') {
  return { endpoint, unsubscribe: vi.fn().mockResolvedValue(true) } as unknown as PushSubscription & {
    unsubscribe: ReturnType<typeof vi.fn>
  }
}

interface Geraet {
  getRegistration: ReturnType<typeof vi.fn>
  register: ReturnType<typeof vi.fn>
  getSubscription: ReturnType<typeof vi.fn>
  subscribe: ReturnType<typeof vi.fn>
  requestPermission: ReturnType<typeof vi.fn>
}

/** Baut Service Worker, PushManager und Notification nach; `registriert` sagt, ob schon einer angemeldet ist. */
function geraet(bestehend: PushSubscription | null, registriert = true): Geraet {
  const pushManager = { getSubscription: vi.fn().mockResolvedValue(bestehend), subscribe: vi.fn() }
  const registrierung = { pushManager }
  const g: Geraet = {
    getRegistration: vi.fn().mockResolvedValue(registriert ? registrierung : undefined),
    register: vi.fn().mockResolvedValue(registrierung),
    getSubscription: pushManager.getSubscription,
    subscribe: pushManager.subscribe,
    requestPermission: vi.fn().mockResolvedValue('granted'),
  }
  vi.stubGlobal('PushManager', function PushManager() {})
  vi.stubGlobal('Notification', { requestPermission: g.requestPermission })
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      getRegistration: g.getRegistration,
      register: g.register,
      ready: Promise.resolve(registrierung),
    },
  })
  return g
}

function ohneServiceWorker() {
  Reflect.deleteProperty(navigator, 'serviceWorker')
}

const vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-29T10:00:00Z' }

describe('pushAbo', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.aboSetzen.mockResolvedValue(undefined)
    api.aboLoeschen.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    ohneServiceWorker()
  })

  describe('kannPush', () => {
    it('ist wahr mit Service Worker und PushManager', () => {
      geraet(null)
      expect(kannPush()).toBe(true)
    })

    it('ist falsch ohne PushManager', () => {
      geraet(null)
      vi.unstubAllGlobals()
      expect(kannPush()).toBe(false)
    })

    it('ist falsch ohne Service Worker', () => {
      vi.stubGlobal('PushManager', function PushManager() {})
      expect(kannPush()).toBe(false)
    })
  })

  describe('vomStartbildschirm', () => {
    it('folgt display-mode standalone', () => {
      const matchMedia = vi.fn().mockReturnValue({ matches: true })
      vi.stubGlobal('matchMedia', matchMedia)
      expect(vomStartbildschirm()).toBe(true)
      expect(matchMedia).toHaveBeenCalledWith('(display-mode: standalone)')
      matchMedia.mockReturnValue({ matches: false })
      expect(vomStartbildschirm()).toBe(false)
    })

    it('ist falsch ohne matchMedia', () => {
      vi.stubGlobal('matchMedia', undefined)
      expect(vomStartbildschirm()).toBe(false)
    })
  })

  it('wandelt einen base64url-Schlüssel in Bytes', () => {
    expect([...schluesselBytes('AQID_-8')]).toEqual([1, 2, 3, 255, 239])
  })

  describe('bestehendesAbo', () => {
    it('liest das Abo des Workers im Bereich /mobil', async () => {
      const sub = abo()
      const g = geraet(sub)
      await expect(bestehendesAbo()).resolves.toBe(sub)
      expect(g.getRegistration).toHaveBeenCalledWith('/mobil')
    })

    it('ist null ohne angemeldeten Worker', async () => {
      geraet(null, false)
      await expect(bestehendesAbo()).resolves.toBeNull()
    })

    it('ist null ohne Push', async () => {
      await expect(bestehendesAbo()).resolves.toBeNull()
    })
  })

  describe('einschalten', () => {
    it('meldet den Worker an, holt die Erlaubnis, abonniert und setzt das Abo', async () => {
      const sub = abo()
      const g = geraet(null, false)
      g.subscribe.mockResolvedValue(sub)
      await expect(einschalten('AQID')).resolves.toBe(sub)
      expect(g.requestPermission).toHaveBeenCalled()
      expect(g.register).toHaveBeenCalledWith('/mobil-sw.js', { scope: '/mobil' })
      expect(g.subscribe).toHaveBeenCalledWith({ userVisibleOnly: true, applicationServerKey: new Uint8Array([1, 2, 3]) })
      expect(api.aboSetzen).toHaveBeenCalledWith(sub)
    })

    it('abonniert nicht, wenn die Erlaubnis fehlt', async () => {
      const g = geraet(null, false)
      g.requestPermission.mockResolvedValue('denied')
      await expect(einschalten('AQID')).resolves.toBeNull()
      expect(g.register).not.toHaveBeenCalled()
      expect(g.subscribe).not.toHaveBeenCalled()
      expect(api.aboSetzen).not.toHaveBeenCalled()
    })

    it('legt ein neues Abo an, wenn der Endpunkt einem anderen Konto gehört', async () => {
      const alt = abo('https://push.example/alt')
      const neu = abo('https://push.example/neu')
      const g = geraet(null, false)
      g.subscribe.mockResolvedValueOnce(alt).mockResolvedValueOnce(neu)
      api.aboSetzen.mockRejectedValueOnce(new ApiError(409, 'vergeben'))
      await expect(einschalten('AQID')).resolves.toBe(neu)
      expect(alt.unsubscribe).toHaveBeenCalled()
      expect(api.aboSetzen).toHaveBeenLastCalledWith(neu)
    })

    it('gibt einen anderen Fehler weiter', async () => {
      const g = geraet(null, false)
      g.subscribe.mockResolvedValue(abo())
      api.aboSetzen.mockRejectedValue(new ApiError(500, 'kaputt'))
      await expect(einschalten('AQID')).rejects.toThrow('kaputt')
    })
  })

  describe('auffrischen', () => {
    it('frischt ein bestehendes Abo auf', async () => {
      const sub = abo()
      geraet(sub)
      await auffrischen(null)
      expect(api.aboSetzen).toHaveBeenCalledWith(sub)
    })

    it('frischt ohne Abo nichts auf', async () => {
      geraet(null)
      await auffrischen(null)
      expect(api.aboSetzen).not.toHaveBeenCalled()
    })

    it('frischt ohne Push nichts auf', async () => {
      await auffrischen(null)
      expect(api.aboSetzen).not.toHaveBeenCalled()
    })

    it('frischt in der Vorschau nichts auf', async () => {
      const g = geraet(abo())
      await auffrischen(vorschau)
      expect(g.getRegistration).not.toHaveBeenCalled()
      expect(api.aboSetzen).not.toHaveBeenCalled()
    })

    it('beendet das Abo auf dem Gerät, wenn sein Endpunkt einem anderen Konto gehört', async () => {
      const sub = abo()
      geraet(sub)
      api.aboSetzen.mockRejectedValue(new ApiError(409, 'vergeben'))
      await auffrischen(null)
      expect(sub.unsubscribe).toHaveBeenCalled()
    })

    it('gibt einen anderen Fehler weiter', async () => {
      const sub = abo()
      geraet(sub)
      api.aboSetzen.mockRejectedValue(new TypeError('Failed to fetch'))
      await expect(auffrischen(null)).rejects.toThrow('Failed to fetch')
      expect(sub.unsubscribe).not.toHaveBeenCalled()
    })
  })

  describe('abmelden', () => {
    it('beendet zuerst das Abo auf dem Gerät und dann am Server', async () => {
      const sub = abo()
      geraet(sub)
      const reihenfolge: string[] = []
      sub.unsubscribe.mockImplementation(() => {
        reihenfolge.push('unsubscribe')
        return Promise.resolve(true)
      })
      api.aboLoeschen.mockImplementation(() => {
        reihenfolge.push('delete')
        return Promise.resolve()
      })
      await abmelden()
      expect(reihenfolge).toEqual(['unsubscribe', 'delete'])
      expect(api.aboLoeschen).toHaveBeenCalledWith('https://push.example/abc')
    })

    it('nimmt hin, dass der Server das Abo nicht mehr kennt', async () => {
      geraet(abo())
      api.aboLoeschen.mockRejectedValue(new ApiError(404, 'weg'))
      await expect(abmelden()).resolves.toBeUndefined()
    })

    it('gibt einen anderen Fehler weiter', async () => {
      geraet(abo())
      api.aboLoeschen.mockRejectedValue(new ApiError(500, 'kaputt'))
      await expect(abmelden()).rejects.toThrow('kaputt')
    })

    it('tut ohne Abo nichts', async () => {
      geraet(null)
      await abmelden()
      expect(api.aboLoeschen).not.toHaveBeenCalled()
    })

    it('tut ohne Push nichts', async () => {
      await abmelden()
      expect(api.aboLoeschen).not.toHaveBeenCalled()
    })
  })
})
