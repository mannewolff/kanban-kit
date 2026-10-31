// Service Worker der mobilen Oberfläche (Issue #1999, Plan #1989 E13, E17). Angemeldet mit dem
// Bereich `/mobil`; er zeigt Nachrichten zu Läufen und öffnet beim Tipp den Lauf. Er speichert
// nichts zwischen. Die Datei liegt unter `public/` und damit außerhalb von ESLint, tsc und Stryker;
// geprüft wird sie in `src/mobil/mobilSw.test.ts`.

const BEREICH = '/mobil'
const LAEUFE = '/mobil/laeufe'

/** Liegt der Pfad im Bereich `/mobil`? `/mobilfunk` gehört nicht dazu. */
function imBereich(pfad) {
  return pfad === BEREICH || pfad.startsWith(BEREICH + '/')
}

/** Die Adresse aus den Nutzdaten, wenn sie in den Bereich dieser Herkunft führt, sonst die Läufe-Liste. */
function zielAdresse(url) {
  const herkunft = self.location.origin
  if (typeof url === 'string') {
    const ziel = new URL(url, herkunft)
    if (ziel.origin === herkunft && imBereich(ziel.pathname)) return ziel.href
  }
  return new URL(LAEUFE, herkunft).href
}

self.addEventListener('push', (event) => {
  const daten = event.data ? event.data.json() : {}
  event.waitUntil(
    self.registration.showNotification(daten.title || 'kanban-kit', {
      body: daten.body || '',
      data: { url: daten.url || LAEUFE },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const ziel = zielAdresse(event.notification.data ? event.notification.data.url : undefined)
  event.waitUntil(
    (async () => {
      const fenster = await self.clients.matchAll({ type: 'window' })
      const offen = fenster.find((f) => imBereich(new URL(f.url).pathname))
      if (offen) {
        try {
          await offen.focus()
          await offen.navigate(ziel)
          return
        } catch {
          // Ein Fenster, das dieser Worker nicht steuert, lässt sich nicht umleiten.
        }
      }
      await self.clients.openWindow(ziel)
    })(),
  )
})
