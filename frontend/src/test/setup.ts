import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'

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
