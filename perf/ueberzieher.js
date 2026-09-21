/**
 * Ueberzieher fuer den Durchsatznachweis (Issue #996, Plan #995).
 *
 * Eine einzige Person, die dauerhaft mehr schickt, als ihr zusteht. Belegt zusammen mit den
 * Kennzahlen aus perf/lastprofil.js und perf/weblast.js den dritten Teil der Zusage aus #970: Wer
 * die eigene Grenze ueberschreitet, bekommt eine als "ausgelastet" erkennbare Antwort mit
 * Wartehinweis — und die Antwortzeiten der uebrigen bleiben davon unberuehrt.
 *
 * Deshalb ein *eigenes* Skript mit einem *eigenen* Token: Steckte der Ueberzieher im Lastprofil,
 * gingen seine Abweisungen in dessen Kennzahlen ein, und genau die Frage "stoert er die anderen?"
 * liesse sich nicht mehr beantworten. Gegengelesen wird gegen die parallel laufenden Zahlen der
 * beiden anderen Skripte.
 *
 * Dieses Skript wiederholt bewusst *nicht*: Es soll die Grenze halten, nicht sie umgehen.
 *
 * Aufruf, Testdaten und Ablage der Protokolle: siehe perf/README.md.
 */
import http from 'k6/http'
import { Counter, Rate, Trend } from 'k6/metrics'

// Wie in den anderen Skripten: keine Angabe wirft im Init-Kontext, weil `k6 archive` ihn ausfuehrt.
const BASIS = __ENV.MANBAN_BASIS || 'https://localhost'
const TOKEN = (__ENV.MANBAN_UEBERZIEHER_TOKEN || '').trim()
const GLEICHZEITIG = zahl(__ENV.MANBAN_UEBERZIEHER_GLEICHZEITIG, 20)
const RAMPE = __ENV.MANBAN_RAMPE || '2m'
const DAUER = __ENV.MANBAN_DAUER || '30m'
const TLS_UNGEPRUEFT = (__ENV.MANBAN_TLS_UNGEPRUEFT || '') !== ''

/** Die erwartete Kennung der Ueberlast-Antwort (Plan #995, E5). */
const UEBERLAST_TYP = __ENV.MANBAN_UEBERLAST_TYP || 'urn:manban:overload'

function zahl(wert, vorgabe) {
  const n = Number(wert)
  return Number.isFinite(n) && n > 0 ? n : vorgabe
}

const angenommen = new Counter('ueberzieher_angenommen')
const abgewiesen = new Counter('ueberzieher_abgewiesen_429')
const sonstigeFehler = new Counter('ueberzieher_sonstige_fehler')
const abweisungsQuote = new Rate('ueberzieher_abgewiesen_quote')
const mitWartehinweis = new Rate('ueberzieher_abweisung_mit_wartehinweis')
const alsUeberlastErkennbar = new Rate('ueberzieher_abweisung_als_ueberlast_erkennbar')
const dauer = new Trend('ueberzieher_dauer', true)

export const options = {
  insecureSkipTLSVerify: TLS_UNGEPRUEFT,
  scenarios: {
    ueberzieher: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMPE, target: 1 },
        { duration: DAUER, target: 1 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // Jede Abweisung nennt eine Wartezeit und ist als Ueberlast erkennbar — ein blosser
    // Statuscode ist kein Vertrag (E5). Beide Quoten zaehlen nur ueber die Abweisungen.
    ueberzieher_abweisung_mit_wartehinweis: ['rate==1'],
    ueberzieher_abweisung_als_ueberlast_erkennbar: ['rate==1'],
  },
}

export function setup() {
  if (TOKEN === '') {
    throw new Error(
      'MANBAN_UEBERZIEHER_TOKEN ist leer: ein eigenes Access-Token einer Person angeben, die in ' +
        'MANBAN_TOKENS des Lastprofils *nicht* vorkommt (siehe perf/README.md).',
    )
  }
}

const KOPF = {
  headers: { 'X-Kanban-Token': TOKEN, 'Content-Type': 'application/json' },
  tags: { name: 'GET /items' },
}

export default function () {
  // Ohne Pause und im Stapel: Das ist gerade das Verhalten, gegen das die Bremse antritt. Ein
  // Lesebefehl, weil der Ueberzieher die Grenze belegen und nicht den Datenbestand aufblaehen soll.
  const anfragen = []
  for (let i = 0; i < GLEICHZEITIG; i++) {
    anfragen.push({ method: 'GET', url: `${BASIS}/api/kanban/items`, params: KOPF })
  }
  for (const antwort of http.batch(anfragen)) {
    auswerten(antwort)
  }
}

function auswerten(antwort) {
  dauer.add(antwort.timings.duration)
  abweisungsQuote.add(antwort.status === 429)
  if (antwort.status === 429) {
    abgewiesen.add(1)
    const warte = Number(antwort.headers['Retry-After'])
    mitWartehinweis.add(Number.isFinite(warte) && warte > 0)
    alsUeberlastErkennbar.add(ueberlast(antwort))
    return
  }
  if (antwort.status >= 200 && antwort.status < 300) {
    angenommen.add(1)
    return
  }
  sonstigeFehler.add(1)
}

/** Ob die Abweisung maschinenlesbar als Ueberlast ausgewiesen ist (Problem-Detail `type`, E5). */
function ueberlast(antwort) {
  try {
    const problem = antwort.json()
    return problem !== null && typeof problem === 'object' && problem.type === UEBERLAST_TYP
  } catch (fehler) {
    return false
  }
}
