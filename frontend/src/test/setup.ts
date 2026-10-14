import '@testing-library/jest-dom/vitest'
import { configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { TABLET_QUER_QUERY } from '../lib/tabletQuer'

// `findBy…` und `waitFor` geben nach dem Default von 1000 ms auf — unter der vollen Suite mit
// Coverage und paralleler Last zu knapp: Am 2026-09-24 scheiterten so Tests mit „Unable to find
// role …", deren Element nur spaet kam. `testTimeout` in vite.config.ts greift hier nicht, das
// Warten von Testing Library hat seine eigene Grenze (Issue #1196).
configure({ asyncUtilTimeout: 5000 })

// jsdom implementiert EventSource nicht. useBoardEvents öffnet beim Rendern eines Boards einen
// SSE-Stream; ohne diesen Stub scheiterten alle Board-Tests an `new EventSource(...)`. Minimaler
// Fake: merkt sich Listener, close() ist ein No-op. Tests, die den Stream steuern wollen (der
// useBoardEvents-Test), überschreiben EventSource für ihre Datei via vi.stubGlobal.
class EventSourceStub {
  url: string
  private readonly listeners = new Map<string, Set<(e: Event) => void>>()
  constructor(url: string) {
    this.url = url
  }
  addEventListener(type: string, cb: (e: Event) => void): void {
    const set = this.listeners.get(type) ?? new Set()
    set.add(cb)
    this.listeners.set(type, set)
  }
  removeEventListener(type: string, cb: (e: Event) => void): void {
    this.listeners.get(type)?.delete(cb)
  }
  close(): void {
    // bewusst leer: der Stub hält keine echte Verbindung, die geschlossen werden müsste.
  }
}
vi.stubGlobal('EventSource', EventSourceStub)

// `GET /api/language` antwortet in jedem Test mit Deutsch (Plan #1638, A10). Der `SpracheProvider`
// lädt die Sprache vor dem ersten Rendern; ohne diese Vorgabe liefe sein Abruf ins Leere. Alle
// anderen Pfade gehen unverändert an das bisherige `fetch` — ein Test, der `fetch` selbst
// ersetzt (`vi.spyOn`, `vi.stubGlobal`), überdeckt die Vorgabe für seine Dauer.
const bisherigesFetch = globalThis.fetch
globalThis.fetch = (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
  if (String(input instanceof Request ? input.url : input) === '/api/language') {
    return Promise.resolve({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(JSON.stringify({ language: 'de' })),
    } as Response)
  }
  return bisherigesFetch(input, init)
}

// localStorage zwischen Tests leeren. jsdom teilt localStorage über alle Tests einer Datei;
// ohne Reset leakt z. B. ein gesetzter Board-Epic-Filter (`manban.boardEpicFilter.*`) in
// nachfolgende Tests und blendet dort Karten aus. Unter Node 22 (CI) fällt das auf, unter
// Node 26 maskiert es das native (deaktivierte) localStorage — daher „grün lokal, rot in CI".
// Das try/catch fängt genau dieses deaktivierte native localStorage ab (Zugriff wirft dann);
// ohne funktionierendes localStorage gibt es aber auch keinen Leak, also ist kein Reset nötig.
afterEach(() => {
  try {
    localStorage.clear()
  } catch {
    // localStorage nicht verfügbar (Node 26 nativ, deaktiviert) — kein Leak, kein Reset nötig.
  }
})

// `matchMedia`-Attrappe (Plan #1707, E13). jsdom wertet keine Media-Query aus; ohne Attrappe gälte
// jede als nicht erfüllt. Die Attrappe beantwortet allein die Tablet-Query nach dem Umschalter und
// jede andere mit „nicht erfüllt“ — wie bisher. Standard ist der Desktop, nach jedem Test zurück.
// Direkt zugewiesen statt `vi.stubGlobal`: Ein Test, der `matchMedia` selbst stubbt und danach
// `vi.unstubAllGlobals()` ruft, bekommt so wieder diese Attrappe und nicht `undefined`.
const medien = { tabletQuer: false }

/** Schaltet die Attrappe auf Tablet im Querformat (`true`) oder Desktop (`false`). */
export function setzeMedien(werte: Readonly<{ tabletQuer: boolean }>): void {
  medien.tabletQuer = werte.tabletQuer
}

window.matchMedia = (query: string): MediaQueryList => ({
  matches: query === TABLET_QUER_QUERY && medien.tabletQuer,
  media: query,
  onchange: null,
  addEventListener: () => {},
  removeEventListener: () => {},
  addListener: () => {},
  removeListener: () => {},
  dispatchEvent: () => false,
})

afterEach(() => setzeMedien({ tabletQuer: false }))
