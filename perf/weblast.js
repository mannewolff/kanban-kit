/**
 * Weblast fuer den Durchsatznachweis (Issue #996, Plan #995).
 *
 * Laeuft parallel zu perf/lastprofil.js und belegt die zweite Haelfte der Zusage aus #970: Die
 * Weboberflaeche bleibt von der Bremse unberuehrt und antwortet auch unter voller Last. Ohne
 * diesen Lauf misst das Lastprofil nur sich selbst — dass der Browser daneben noch bedienbar ist,
 * stuende nirgends.
 *
 * Zehn Personen mit Sitzungs-Anmeldung (Cookie, kein Token) auf den typischen Lesepfaden der
 * Oberflaeche: Projekte, Boards, Spalten, Karten, Labels, Kennzahlen, Kommentare. Kein
 * Schreibpfad — die Oberflaeche wird hier als Leser gemessen, und ein schreibender Weblauf
 * vermengte seine Wirkung mit der des Lastprofils auf denselben Karten.
 *
 * Aufruf, Testdaten und Ablage der Protokolle: siehe perf/README.md.
 */
import http from 'k6/http'
import { sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'

// Wie im Lastprofil: keine Angabe wirft im Init-Kontext, weil `k6 archive` ihn ausfuehrt.
const BASIS = __ENV.MANBAN_BASIS || 'https://localhost'
const KONTEN = konten(__ENV.MANBAN_WEB_KONTEN)
const NUTZER = zahl(__ENV.MANBAN_WEB_NUTZER, 10)
const RAMPE = __ENV.MANBAN_RAMPE || '2m'
const DAUER = __ENV.MANBAN_DAUER || '30m'
const DENKPAUSE_S = zahl(__ENV.MANBAN_WEB_DENKPAUSE_S, 5)
const TLS_UNGEPRUEFT = (__ENV.MANBAN_TLS_UNGEPRUEFT || '') !== ''

/**
 * `MANBAN_WEB_KONTEN` als `mail:passwort` je Konto, kommagetrennt. Getrennt wird am *ersten*
 * Doppelpunkt: Ein Passwort darf welche enthalten.
 */
function konten(wert) {
  return (wert || '')
    .split(',')
    .map((teil) => teil.trim())
    .filter((teil) => teil.includes(':'))
    .map((teil) => ({
      email: teil.slice(0, teil.indexOf(':')),
      passwort: teil.slice(teil.indexOf(':') + 1),
    }))
}

function zahl(wert, vorgabe) {
  const n = Number(wert)
  return Number.isFinite(n) && n > 0 ? n : vorgabe
}

const anmeldungen = new Counter('web_anmeldungen')
const anmeldungFehler = new Counter('web_anmeldung_fehlgeschlagen')
const seitenFehler = new Rate('web_seite_fehlgeschlagen')
const seitenDauer = new Trend('web_seite_dauer', true)
const abgewiesen429 = new Counter('web_abgewiesen_429')

export const options = {
  insecureSkipTLSVerify: TLS_UNGEPRUEFT,
  scenarios: {
    weboberflaeche: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMPE, target: NUTZER },
        { duration: DAUER, target: NUTZER },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // Die Zusage: Die Oberflaeche wird nie wegen Last abgewiesen, und sie bleibt bedienbar.
    // Eine einzige 429 auf dem Sitzungspfad widerlegt die erste Haelfte, deshalb `count==0`.
    web_abgewiesen_429: ['count==0'],
    web_seite_fehlgeschlagen: ['rate<0.001'],
    web_seite_dauer: ['p(95)<2000', 'max<5000'],
  },
}

export function setup() {
  if (KONTEN.length === 0) {
    throw new Error(
      'MANBAN_WEB_KONTEN ist leer: kommagetrennte `mail:passwort`-Paare angeben ' +
        '(siehe perf/README.md).',
    )
  }
}

/** Sitzung je virtueller Person; der Cookie-Behaelter einer VU ueberlebt ihre Iterationen. */
let sitzung = null

export default function () {
  if (sitzung === null) {
    sitzung = anmelden()
  }
  if (sitzung.angemeldet !== true) {
    // Ohne Sitzung gibt es nichts zu messen — erneut versuchen statt blind weiterzulesen und
    // jede Folgeanfrage als 401 in die Fehlerquote zu schreiben.
    sleep(DENKPAUSE_S)
    sitzung = anmelden()
    return
  }
  leseRunde()
  sleep(DENKPAUSE_S)
}

function anmelden() {
  const konto = KONTEN[(__VU - 1) % KONTEN.length]
  const antwort = http.post(
    `${BASIS}/api/auth/login`,
    JSON.stringify({ email: konto.email, password: konto.passwort }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/auth/login' } },
  )
  if (antwort.status !== 200) {
    anmeldungFehler.add(1)
    console.warn(`Anmeldung von ${konto.email} fehlgeschlagen: ${antwort.status}`)
    return { angemeldet: false }
  }
  anmeldungen.add(1)
  return { angemeldet: true }
}

/**
 * Eine Runde der typischen Lesepfade. Die Antwort jeder Seite bestimmt die naechste — Board-IDs
 * und Karten-IDs stehen nicht fest, und geratene IDs maessen 404-Antworten statt Lesepfaden.
 */
function leseRunde() {
  seite('GET /api/me', `${BASIS}/api/me`)
  seite('GET /api/config', `${BASIS}/api/config`)

  const projekte = seite('GET /api/projects', `${BASIS}/api/projects`)
  const projekt = erstes(projekte)
  if (projekt === null) return

  const boards = seite(
    'GET /api/projects/{id}/boards',
    `${BASIS}/api/projects/${projekt.id}/boards`,
  )
  const board = erstes(boards)
  if (board === null) return

  seite('GET /api/boards/{id}/columns', `${BASIS}/api/boards/${board.id}/columns`)
  seite('GET /api/boards/{id}/labels', `${BASIS}/api/boards/${board.id}/labels`)
  seite('GET /api/boards/{id}/dashboard', `${BASIS}/api/boards/${board.id}/dashboard`)

  const karten = seite('GET /api/boards/{id}/cards', `${BASIS}/api/boards/${board.id}/cards`)
  const karte = eine(karten)
  if (karte === null) return

  seite('GET /api/cards/{id}', `${BASIS}/api/cards/${karte.id}`)
  seite('GET /api/cards/{id}/comments', `${BASIS}/api/cards/${karte.id}/comments`)
  seite('GET /api/cards/{id}/activity', `${BASIS}/api/cards/${karte.id}/activity`)
}

/** Holt eine Seite, bucht ihre Kennzahlen und gibt den gelesenen Rumpf zurueck. */
function seite(name, url) {
  const antwort = http.get(url, { tags: { name } })
  const kennzeichen = { name }
  seitenDauer.add(antwort.timings.duration, kennzeichen)
  seitenFehler.add(antwort.status < 200 || antwort.status >= 300, kennzeichen)
  if (antwort.status === 429) {
    abgewiesen429.add(1, kennzeichen)
  }
  if (antwort.status < 200 || antwort.status >= 300) return null
  try {
    return antwort.json()
  } catch (fehler) {
    return null
  }
}

function erstes(werte) {
  return Array.isArray(werte) && werte.length > 0 ? werte[0] : null
}

function eine(werte) {
  if (!Array.isArray(werte) || werte.length === 0) return null
  return werte[Math.floor(Math.random() * werte.length)]
}
