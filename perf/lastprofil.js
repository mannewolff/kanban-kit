/**
 * Lastprofil fuer den Durchsatznachweis der Kanban-Compat-API (Issue #996, Plan #995).
 *
 * Bildet die Zusage der fachlichen Quelle #970 nach: 50 gleichzeitig aktive Personen mit je
 * 60 Befehlen pro Minute, davon hoechstens 10 gleichzeitig, ueber 30 Minuten.
 *
 * Eine virtuelle Person = eine k6-VU = ein Access-Token. Eine Iteration schickt
 * MANBAN_GLEICHZEITIG Befehle im Stapel (das ist die Gleichzeitigkeit *einer* Person) und wartet
 * danach bis zum naechsten Takt, sodass ueber die Minute genau MANBAN_BEFEHLE_PRO_MINUTE Befehle
 * herauskommen. Deshalb `ramping-vus` mit eigenem Takt und nicht `constant-arrival-rate`: Ein
 * Ankunftsraten-Executor haelt nur die *Gesamtrate* ein und verteilt sie beliebig ueber die VUs —
 * die Grenze, gegen die hier gemessen wird, gilt aber je Person.
 *
 * Gemischt wird ueber alle elf Befehle des KanbanCompatController:
 *
 *   GET    /items
 *   POST   /items
 *   PUT    /items/{id}
 *   PUT    /items/{id}/move
 *   PUT    /items/{id}/dependencies
 *   POST   /items/{id}/labels
 *   DELETE /items/{id}/labels
 *   POST   /items/{id}/comments
 *   GET    /items/{id}/comments
 *   GET    /items/{id}/activity
 *   GET    /epics
 *
 * Datei-Anhaenge sind ausdrueckliches Nicht-Ziel und kommen nicht vor.
 *
 * Aufruf, Testdaten und Ablage der Protokolle: siehe perf/README.md.
 */
import http from 'k6/http'
import { sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'

// --- Konfiguration --------------------------------------------------------
// Jede Angabe hat eine Vorgabe, und keine wirft hier. `k6 archive` fuehrt den Init-Kontext aus;
// ein geworfener Fehler machte schon das Packen des Skripts unmoeglich. Geprueft wird in setup(),
// das nur der echte Lauf ausfuehrt.

const BASIS = __ENV.MANBAN_BASIS || 'https://localhost'
const TOKENS = liste(__ENV.MANBAN_TOKENS)
const LABELS = liste(__ENV.MANBAN_LABELS || 'last-a,last-b,last-c')
const PERSONEN = zahl(__ENV.MANBAN_PERSONEN, 50)
const BEFEHLE_PRO_MINUTE = zahl(__ENV.MANBAN_BEFEHLE_PRO_MINUTE, 60)
const GLEICHZEITIG = zahl(__ENV.MANBAN_GLEICHZEITIG, 10)
const RAMPE = __ENV.MANBAN_RAMPE || '2m'
const DAUER = __ENV.MANBAN_DAUER || '30m'
const WIEDERHOLUNGEN_MAX = zahl(__ENV.MANBAN_WIEDERHOLUNGEN, 4)
const WARTE_MAX_S = zahl(__ENV.MANBAN_WARTE_MAX_S, 30)
const POOL_MAX = zahl(__ENV.MANBAN_POOL_MAX, 50)
const TLS_UNGEPRUEFT = (__ENV.MANBAN_TLS_UNGEPRUEFT || '') !== ''

/** Sekunden je Iteration, damit ueber die Minute die zugesagte Befehlszahl herauskommt. */
const TAKT_S = (60 * GLEICHZEITIG) / BEFEHLE_PRO_MINUTE

function liste(wert) {
  return (wert || '')
    .split(',')
    .map((teil) => teil.trim())
    .filter((teil) => teil !== '')
}

function zahl(wert, vorgabe) {
  const n = Number(wert)
  return Number.isFinite(n) && n > 0 ? n : vorgabe
}

// --- Kennzahlen -----------------------------------------------------------
// Erstversuche und Wiederholungen werden getrennt gezaehlt: Eine Gesamtzahl allein liesse einen
// Lauf, der jeden zweiten Befehl wiederholen musste, wie einen glatten Lauf aussehen.

const erstversuche = new Counter('befehle_erstversuch')
const wiederholungen = new Counter('befehle_wiederholung')
const abgewiesen429 = new Counter('befehle_abgewiesen_429')
const aufgegeben = new Counter('befehle_aufgegeben')
const wiederholtQuote = new Rate('befehl_wurde_wiederholt')
const fehlerQuote = new Rate('befehl_fehlgeschlagen')
const dauerErstversuch = new Trend('befehl_dauer_erstversuch', true)
const dauerGesamt = new Trend('befehl_dauer_gesamt', true)

export const options = {
  insecureSkipTLSVerify: TLS_UNGEPRUEFT,
  scenarios: {
    befehle: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMPE, target: PERSONEN },
        { duration: DAUER, target: PERSONEN },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // Die Zusage aus #970: kein fachlich zulaessiger Befehl scheitert, und im schlechtesten Fall
    // antwortet der Server binnen 5 Sekunden. Gemessen an der *Antwort des Servers*
    // (befehl_dauer_erstversuch), nicht an befehl_dauer_gesamt: Dort steckt die Wartezeit nach
    // einem 429 mit drin, und die ist das vorgesehene Verhalten und kein Fehler.
    // Ein roter Schwellwert ist beim Ausgangslauf kein Mangel des Skripts, sondern das
    // Messergebnis — siehe perf/README.md.
    befehl_fehlgeschlagen: ['rate<0.001'],
    befehl_dauer_erstversuch: ['p(95)<2000', 'max<5000'],
  },
}

// --- Befehlsmischung ------------------------------------------------------
// `name` ist zugleich der k6-Tag, unter dem die Kennzahlen gruppiert werden: ohne ihn zerfiele
// jede Kennzahl in tausende Einzel-URLs mit konkreten IDs.

const BEFEHLE = [
  {
    name: 'GET /items',
    gewicht: 22,
    brauchtItem: false,
    bauen: () => ({ method: 'GET', url: `${BASIS}/api/kanban/items` }),
  },
  {
    name: 'GET /epics',
    gewicht: 5,
    brauchtItem: false,
    bauen: () => ({ method: 'GET', url: `${BASIS}/api/kanban/epics` }),
  },
  {
    name: 'POST /items',
    gewicht: 10,
    brauchtItem: false,
    bauen: (zustand) => ({
      method: 'POST',
      url: `${BASIS}/api/kanban/items`,
      body: JSON.stringify({
        title: `Lasttest ${__VU}/${zustand.laufendeNummer}`,
        body: 'Angelegt vom Lastprofil (perf/lastprofil.js).',
        direct: true,
        // Idempotenz-Schluessel: Eine Wiederholung nach 429 legte sonst eine zweite Karte an und
        // faelschte damit genau die Zahl, die hier gemessen wird.
        externalKey: `lasttest-${__VU}-${zustand.laufendeNummer++}`,
      }),
    }),
  },
  {
    name: 'PUT /items/{id}',
    gewicht: 10,
    brauchtItem: true,
    bauen: (zustand, item) => ({
      method: 'PUT',
      url: `${BASIS}/api/kanban/items/${item.id}`,
      body: JSON.stringify({
        title: `Lasttest ${item.number} (${zustand.laufendeNummer})`,
        body: 'Fortgeschrieben vom Lastprofil.',
      }),
    }),
  },
  {
    name: 'PUT /items/{id}/move',
    gewicht: 10,
    brauchtItem: true,
    bauen: (zustand, item) => ({
      method: 'PUT',
      url: `${BASIS}/api/kanban/items/${item.id}/move`,
      body: JSON.stringify({ column: eins(zustand.spalten), position: 0 }),
    }),
  },
  {
    name: 'PUT /items/{id}/dependencies',
    gewicht: 4,
    brauchtItem: true,
    bauen: (zustand, item) => ({
      method: 'PUT',
      url: `${BASIS}/api/kanban/items/${item.id}/dependencies`,
      body: JSON.stringify({ dependsOn: abhaengigkeit(zustand, item) }),
    }),
  },
  {
    name: 'POST /items/{id}/labels',
    gewicht: 6,
    brauchtItem: true,
    bauen: (zustand, item) => ({
      method: 'POST',
      url: `${BASIS}/api/kanban/items/${item.id}/labels`,
      body: JSON.stringify({ name: eins(LABELS) }),
    }),
  },
  {
    name: 'DELETE /items/{id}/labels',
    gewicht: 5,
    brauchtItem: true,
    // Der Name steht im Query-Parameter, nicht im Pfad (siehe KanbanCompatController). Ein nicht
    // zugeordnetes Label zu entfernen ist Erfolg — der Befehl braucht kein vorheriges Setzen.
    bauen: (zustand, item) => ({
      method: 'DELETE',
      url: `${BASIS}/api/kanban/items/${item.id}/labels?name=${encodeURIComponent(eins(LABELS))}`,
    }),
  },
  {
    name: 'POST /items/{id}/comments',
    gewicht: 10,
    brauchtItem: true,
    bauen: (zustand, item) => ({
      method: 'POST',
      url: `${BASIS}/api/kanban/items/${item.id}/comments`,
      body: JSON.stringify({ body: `Lastkommentar ${zustand.laufendeNummer} an ${item.number}.` }),
    }),
  },
  {
    name: 'GET /items/{id}/comments',
    gewicht: 10,
    brauchtItem: true,
    bauen: (zustand, item) => ({
      method: 'GET',
      url: `${BASIS}/api/kanban/items/${item.id}/comments`,
    }),
  },
  {
    name: 'GET /items/{id}/activity',
    gewicht: 8,
    brauchtItem: true,
    bauen: (zustand, item) => ({
      method: 'GET',
      url: `${BASIS}/api/kanban/items/${item.id}/activity`,
    }),
  },
]

export function setup() {
  if (TOKENS.length === 0) {
    throw new Error(
      'MANBAN_TOKENS ist leer: eine kommagetrennte Liste von Access-Tokens angeben, je eines ' +
        'pro virtueller Person (siehe perf/README.md).',
    )
  }
  if (TOKENS.length < PERSONEN) {
    console.warn(
      `Nur ${TOKENS.length} Tokens fuer ${PERSONEN} Personen: mehrere Personen teilen sich ` +
        'dieselbe Personen-Grenze, der Nachweis ist dann nicht aussagekraeftig.',
    )
  }
  const antwort = http.get(`${BASIS}/api/kanban/items`, kopf(TOKENS[0]))
  if (antwort.status !== 200) {
    throw new Error(
      `Vorpruefung fehlgeschlagen: GET /api/kanban/items antwortete ${antwort.status}. ` +
        'MANBAN_BASIS und MANBAN_TOKENS pruefen.',
    )
  }
  return { spalten: Object.keys(antwort.json()) }
}

/** Zustand je virtueller Person; ueberlebt die Iterationen einer VU. */
let zustand = null

export default function (daten) {
  const start = Date.now()
  if (zustand === null) {
    zustand = personZustand(daten)
  }
  stapelFahren(zustand)
  // Rest des Takts abwarten. Hat der Stapel laenger gebraucht, faellt die Pause aus — die
  // Verzoegerung steht dann in den Kennzahlen und wird nicht durch kuerzeres Warten verdeckt.
  const verbraucht = (Date.now() - start) / 1000
  if (verbraucht < TAKT_S) {
    sleep(TAKT_S - verbraucht)
  }
}

function personZustand(daten) {
  const token = TOKENS[(__VU - 1) % TOKENS.length]
  return {
    token,
    kopf: kopf(token),
    spalten: daten.spalten.length > 0 ? daten.spalten : ['Backlog'],
    items: [],
    laufendeNummer: 1,
  }
}

function kopf(token) {
  return {
    headers: {
      'X-Kanban-Token': token,
      'Content-Type': 'application/json',
    },
  }
}

/**
 * Ein Stapel gleichzeitiger Befehle samt Wiederholung der abgewiesenen.
 *
 * Wiederholt wird rundenweise und wieder im Stapel, nicht Befehl fuer Befehl: Nacheinander
 * gewartet summierten sich zehn Wartezeiten zu einer Iteration, die den Takt der Person um ein
 * Vielfaches ueberzieht — gemessen wuerde dann das Skript und nicht der Server.
 */
function stapelFahren(zustand) {
  const stapel = stapelBauen(zustand)
  let antworten = http.batch(stapel.map((eintrag) => eintrag.anfrage))
  stapel.forEach((eintrag, i) => ersterVersuch(eintrag, antworten[i]))

  let runde = 0
  let offen = stapel.filter((eintrag) => eintrag.antwort.status === 429)
  while (offen.length > 0 && runde < WIEDERHOLUNGEN_MAX) {
    const warte = wartezeit(offen, runde)
    sleep(warte)
    runde++
    antworten = http.batch(offen.map((eintrag) => eintrag.anfrage))
    offen.forEach((eintrag, i) => {
      const kennzeichen = { name: eintrag.befehl.name }
      wiederholungen.add(1, kennzeichen)
      eintrag.wiederholt = true
      eintrag.wartezeitMs += warte * 1000
      eintrag.dauerMs += antworten[i].timings.duration
      eintrag.antwort = antworten[i]
      if (antworten[i].status === 429) abgewiesen429.add(1, kennzeichen)
    })
    offen = offen.filter((eintrag) => eintrag.antwort.status === 429)
  }

  for (const eintrag of stapel) {
    abschliessen(zustand, eintrag)
  }
}

function stapelBauen(zustand) {
  const stapel = []
  for (let i = 0; i < GLEICHZEITIG; i++) {
    const befehl = befehlWaehlen(zustand)
    const item = befehl.brauchtItem ? eins(zustand.items) : null
    const anfrage = befehl.bauen(zustand, item)
    anfrage.params = { ...zustand.kopf, tags: { name: befehl.name } }
    stapel.push({ befehl, anfrage, antwort: null, wiederholt: false, dauerMs: 0, wartezeitMs: 0 })
  }
  return stapel
}

/**
 * Waehlt einen Befehl nach Gewicht. Solange die Person noch kein Item kennt, kommt nur in Frage,
 * was ohne Item auskommt — der erste Stapel bringt ueber `GET /items` und `POST /items` die IDs
 * herein, mit denen die folgenden arbeiten.
 */
function befehlWaehlen(zustand) {
  const moeglich =
    zustand.items.length > 0 ? BEFEHLE : BEFEHLE.filter((befehl) => !befehl.brauchtItem)
  const summe = moeglich.reduce((wert, befehl) => wert + befehl.gewicht, 0)
  let wurf = Math.random() * summe
  for (const befehl of moeglich) {
    wurf -= befehl.gewicht
    if (wurf <= 0) return befehl
  }
  return moeglich[moeglich.length - 1]
}

function ersterVersuch(eintrag, antwort) {
  const kennzeichen = { name: eintrag.befehl.name }
  erstversuche.add(1, kennzeichen)
  dauerErstversuch.add(antwort.timings.duration, kennzeichen)
  eintrag.dauerMs = antwort.timings.duration
  eintrag.antwort = antwort
  if (antwort.status === 429) abgewiesen429.add(1, kennzeichen)
}

/** Buchung der Kennzahlen eines Befehls, nachdem ueber Wiederholungen entschieden ist. */
function abschliessen(zustand, eintrag) {
  const antwort = eintrag.antwort
  const kennzeichen = { name: eintrag.befehl.name }
  if (antwort.status === 429) {
    aufgegeben.add(1, kennzeichen)
  }
  wiederholtQuote.add(eintrag.wiederholt, kennzeichen)
  dauerGesamt.add(eintrag.dauerMs + eintrag.wartezeitMs, kennzeichen)
  fehlerQuote.add(antwort.status < 200 || antwort.status >= 300, kennzeichen)
  merken(zustand, eintrag.befehl, antwort)
}

/**
 * Wartezeit vor der naechsten Runde: der groesste von den Abgewiesenen genannte `Retry-After` in
 * Sekunden, sonst wachsende Pausen. Gedeckelt, damit ein ueberhoehter Wert nicht den Lauf anhaelt.
 */
function wartezeit(offen, bisherigeRunden) {
  let sekunden = 0
  for (const eintrag of offen) {
    const genannt = Number(eintrag.antwort.headers['Retry-After'])
    if (Number.isFinite(genannt) && genannt > sekunden) sekunden = genannt
  }
  if (sekunden <= 0) sekunden = 2 ** bisherigeRunden
  return Math.min(sekunden, WARTE_MAX_S)
}

/** Haelt den Item-Vorrat der Person aktuell, aus den Antworten der Lesebefehle und der Anlage. */
function merken(zustand, befehl, antwort) {
  if (antwort.status < 200 || antwort.status >= 300) return
  if (befehl.name === 'GET /items') {
    const spalten = antwort.json()
    if (spalten === null || typeof spalten !== 'object') return
    const namen = Object.keys(spalten)
    if (namen.length > 0) zustand.spalten = namen
    for (const items of Object.values(spalten)) {
      for (const item of items) {
        aufnehmen(zustand, { id: item.id, number: item.number })
      }
    }
    return
  }
  if (befehl.name === 'POST /items') {
    const angelegt = antwort.json()
    if (angelegt !== null && typeof angelegt === 'object') {
      aufnehmen(zustand, { id: angelegt.id, number: angelegt.number })
    }
  }
}

/**
 * Nimmt ein Item in den Vorrat auf. Der Deckel haelt den Speicher je VU endlich: Ueber 30 Minuten
 * legte das Profil sonst zehntausende Karten in eine Liste, die nie kleiner wird.
 */
function aufnehmen(zustand, item) {
  if (item.id === undefined || item.id === null) return
  if (zustand.items.length >= POOL_MAX) {
    zustand.items[Math.floor(Math.random() * POOL_MAX)] = item
    return
  }
  zustand.items.push(item)
}

/** Eine Kartennummer aus dem Vorrat, nie die eigene — ein Selbstverweis wird abgelehnt. */
function abhaengigkeit(zustand, item) {
  const andere = zustand.items.filter(
    (kandidat) => kandidat.number !== undefined && kandidat.number !== item.number,
  )
  return andere.length === 0 ? [] : [eins(andere).number]
}

function eins(werte) {
  return werte[Math.floor(Math.random() * werte.length)]
}
