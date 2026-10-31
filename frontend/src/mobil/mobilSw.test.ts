import { runInNewContext } from 'node:vm'
import { describe, expect, it, vi } from 'vitest'
import swQuelle from '../../public/mobil-sw.js?raw'

/**
 * Der Service Worker der mobilen Oberfläche (Issue #1999, Plan #1989 E13, E17). Die Datei liegt
 * unter `public/` und damit außerhalb von ESLint, tsc und Stryker; der Test liest sie als Text und
 * führt sie in `node:vm` mit nachgebautem `self` aus.
 */

type Behandler = (event: unknown) => void

interface Fenster {
  url: string
  focus: ReturnType<typeof vi.fn>
  navigate: ReturnType<typeof vi.fn>
}

function fenster(url: string): Fenster {
  const f: Fenster = { url, focus: vi.fn(), navigate: vi.fn() }
  f.focus.mockResolvedValue(f)
  f.navigate.mockResolvedValue(f)
  return f
}

function starte(offeneFenster: Fenster[] = []) {
  const behandler = new Map<string, Behandler>()
  const self = {
    location: { origin: 'https://kanban.example' },
    addEventListener: (typ: string, b: Behandler) => behandler.set(typ, b),
    registration: { showNotification: vi.fn().mockResolvedValue(undefined) },
    clients: {
      matchAll: vi.fn().mockResolvedValue(offeneFenster),
      openWindow: vi.fn().mockResolvedValue(null),
    },
  }
  runInNewContext(swQuelle, { self, URL })
  return { self, behandler }
}

/** Löst ein Ereignis aus und wartet auf das, was der Worker an `waitUntil` übergibt. */
async function loese(behandler: Map<string, Behandler>, typ: string, event: Record<string, unknown>) {
  const warten: Promise<unknown>[] = []
  behandler.get(typ)?.({ ...event, waitUntil: (p: Promise<unknown>) => warten.push(p) })
  await Promise.all(warten)
}

function klick(url: unknown) {
  return { notification: { data: { url }, close: vi.fn() } }
}

describe('mobil-sw.js', () => {
  it('hört nur auf push und notificationclick', () => {
    const { behandler } = starte()
    expect([...behandler.keys()].sort()).toEqual(['notificationclick', 'push'])
  })

  it('zeigt zu einem push-Ereignis eine Nachricht mit Titel und Text', async () => {
    const { self, behandler } = starte()
    const nutzdaten = { title: 'Leitstand · Produkt', body: 'Lauf hängt', url: '/mobil/laeufe/42' }
    await loese(behandler, 'push', { data: { json: () => nutzdaten } })
    expect(self.registration.showNotification).toHaveBeenCalledWith('Leitstand · Produkt', {
      body: 'Lauf hängt',
      data: { url: '/mobil/laeufe/42' },
    })
  })

  it('zeigt auch ohne Nutzdaten eine Nachricht', async () => {
    const { self, behandler } = starte()
    await loese(behandler, 'push', { data: null })
    expect(self.registration.showNotification).toHaveBeenCalledWith('kanban-kit', {
      body: '',
      data: { url: '/mobil/laeufe' },
    })
  })

  it('öffnet beim Tipp die Adresse aus den Nutzdaten in einem neuen Fenster, wenn keines offen ist', async () => {
    const { self, behandler } = starte()
    const event = klick('/mobil/laeufe/42')
    await loese(behandler, 'notificationclick', event)
    expect(event.notification.close).toHaveBeenCalled()
    expect(self.clients.matchAll).toHaveBeenCalledWith({ type: 'window' })
    expect(self.clients.openWindow).toHaveBeenCalledWith('https://kanban.example/mobil/laeufe/42')
  })

  it('öffnet die Adresse in einem offenen Fenster des Bereichs /mobil', async () => {
    const leitstand = fenster('https://kanban.example/projects/1')
    const mobil = fenster('https://kanban.example/mobil/einstellungen')
    const { self, behandler } = starte([leitstand, mobil])
    await loese(behandler, 'notificationclick', klick('/mobil/laeufe/42'))
    expect(mobil.focus).toHaveBeenCalled()
    expect(mobil.navigate).toHaveBeenCalledWith('https://kanban.example/mobil/laeufe/42')
    expect(leitstand.navigate).not.toHaveBeenCalled()
    expect(self.clients.openWindow).not.toHaveBeenCalled()
  })

  it('zählt ein Fenster nur zum Bereich, wenn sein Pfad /mobil ist oder darunter liegt', async () => {
    const daneben = fenster('https://kanban.example/mobilfunk')
    const { self, behandler } = starte([daneben])
    await loese(behandler, 'notificationclick', klick('/mobil/laeufe/42'))
    expect(daneben.navigate).not.toHaveBeenCalled()
    expect(self.clients.openWindow).toHaveBeenCalledWith('https://kanban.example/mobil/laeufe/42')
  })

  it.each([
    ['eine fremde Herkunft', 'https://boese.example/mobil/laeufe/42'],
    ['eine Adresse außerhalb von /mobil', '/projects/1'],
    ['einen Pfad, der nur mit /mobil beginnt', '/mobilfunk'],
    ['keine Adresse', undefined],
  ])('öffnet für %s die Läufe-Liste', async (_fall, url) => {
    const { self, behandler } = starte()
    await loese(behandler, 'notificationclick', klick(url))
    expect(self.clients.openWindow).toHaveBeenCalledWith('https://kanban.example/mobil/laeufe')
  })

  it('öffnet ein neues Fenster, wenn das offene sich nicht umleiten lässt', async () => {
    const mobil = fenster('https://kanban.example/mobil')
    mobil.navigate.mockRejectedValue(new TypeError('not controlled'))
    const { self, behandler } = starte([mobil])
    await loese(behandler, 'notificationclick', klick('/mobil/laeufe/7'))
    expect(self.clients.openWindow).toHaveBeenCalledWith('https://kanban.example/mobil/laeufe/7')
  })
})
