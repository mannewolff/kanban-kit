import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'

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
