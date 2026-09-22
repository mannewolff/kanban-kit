#!/usr/bin/env node
/**
 * tbx.mjs — Kommandozeilen-Client fuer die kanban-kit-Kanban-API.
 * Single-File, zero dependencies (natives fetch). Lauffaehig als Kopie ohne
 * weiteren Repo-Kontext.
 *
 * Authentifizierung ueber einen Kanban-Access-Token (PAT), der in der Web-UI
 * erzeugt wird (Einstellungen -> Kanban-Tokens). Kein Keycloak-Login/Device-Flow
 * mehr (#367): der PAT wird per Header `X-Kanban-Token` gesendet.
 *
 * Nutzung:
 *   tbx auth login [--host <url>] [--token <tk_...>]   (Token auch via TBX_TOKEN oder stdin)
 *   tbx auth status
 *   tbx auth logout
 *
 * Ausgabe: JSON auf stdout, Fehler auf stderr, Exit-Code 1 bei Fehlern.
 */

import { pathToFileURL } from 'node:url';
import { mkdirSync, readFileSync, writeFileSync, chmodSync, rmSync, existsSync, realpathSync } from 'node:fs';
import { homedir } from 'node:os';
import { randomUUID } from 'node:crypto';
import { dirname, join } from 'node:path';

const PROD_DEFAULT_HOST = 'https://kanban.mwolff.org';

/** HTTP-Header, ueber den der PAT gesendet wird (spiegelt das Backend, #365). */
const TOKEN_HEADER = 'X-Kanban-Token';

const HELP = `tbx — Kommandozeilen-Client fuer die kanban-kit-Kanban-API

Nutzung:
  tbx auth login [--host <url>] [--token <tk_...>]
      Token-Quelle (in dieser Reihenfolge): --token, Umgebungsvariable TBX_TOKEN, stdin.
  tbx auth status
  tbx auth logout

  tbx issue create --title <text> [--body <text>] [--idempotency-key <wert>]
  tbx issue get <nummer>            (inkl. Kommentaren der Karte)
  tbx issue list [--status <status>]
  tbx issue move <nummer> <status>
  tbx issue comment <nummer> --text <text> [--idempotency-key <wert>]

Status-Werte: backlog, ready, in_progress, in_review, done

Unter Last wiederholt tbx selbst: bei einer Abweisung wegen Ueberlast (429 mit
urn:manban:overload), bei Zeitablauf und Verbindungsabbruch, bei 5xx nur wo
gefahrlos — hoechstens 30 Sekunden lang. Endet ein anlegender Befehl mit
"Ausgang unklar", mit dem genannten --idempotency-key wiederholen, nie ohne:
derselbe Schluessel fuehrt die Wirkung hoechstens einmal aus.

Der Token wird in der Web-UI erzeugt (Einstellungen -> Kanban-Tokens) und mit
'tbx auth logout' bzw. in der Web-UI widerrufen.

Default (Produktion): --host ${PROD_DEFAULT_HOST}
Dev-Beispiel: tbx auth login --host http://localhost:8080 --token tk_...
`;

// --- Storage ---------------------------------------------------------------

/**
 * Basisverzeichnis fuer Config/Tokens. `baseDir` wird explizit durchgereicht (nicht
 * ueber ein globales env var gelesen) — Tests koennen so parallel laufen, ohne sich
 * über ein gemeinsames Mutable-Global (process.env) gegenseitig zu stoeren.
 */
export function configDir(baseDir) {
  return baseDir || process.env.TBX_CONFIG_DIR || join(homedir(), '.config', 'toolbox-cli');
}

export function configPath(baseDir) {
  return join(configDir(baseDir), 'config.json');
}

export function tokensPath(baseDir) {
  return join(configDir(baseDir), 'tokens.json');
}

export function readJsonFile(path) {
  if (!existsSync(path)) return null;
  try {
    return JSON.parse(readFileSync(path, 'utf-8'));
  } catch {
    return null;
  }
}

/** Schreibt JSON mit 0600-Rechten in ein 0700-Verzeichnis — chmod jeweils nach
 * dem Anlegen/Schreiben, damit ein bereits bestehendes Verzeichnis oder File
 * (anderer Umask/Vor-Zustand, z. B. eine aeltere CLI-Version) garantiert auf
 * die restriktiven Rechte landet; mkdirSync wendet `mode` sonst nur bei
 * tatsaechlicher Neuanlage an. */
export function writeJsonFileSecure(path, obj) {
  const dir = dirname(path);
  mkdirSync(dir, { recursive: true, mode: 0o700 });
  chmodSync(dir, 0o700);
  writeFileSync(path, JSON.stringify(obj, null, 2), { mode: 0o600 });
  chmodSync(path, 0o600);
}

export function deleteFile(path) {
  if (existsSync(path)) rmSync(path);
}

// --- Config-Aufloesung -------------------------------------------------------

/** Wirft, wenn ein Flag ohne folgenden Wert angegeben wurde (parseArgs liefert dann
 * boolean true statt eines Strings) — sonst landet "true" unvalidiert in einer URL. */
function requireStringFlag(flags, name) {
  if (flags[name] === true) {
    throw new CliError(`--${name} erwartet einen Wert`);
  }
  // Leerer String (--host '') wuerde sonst unvalidiert in eine URL fallen (#315).
  if (flags[name] === '') {
    throw new CliError(`--${name} darf nicht leer sein`);
  }
  return flags[name];
}

export function resolveHost(flags, storedConfig) {
  return requireStringFlag(flags, 'host') || storedConfig?.host || PROD_DEFAULT_HOST;
}

// --- Argument-Parser ---------------------------------------------------------

export function parseArgs(argv) {
  const result = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('--')) {
      const key = a.slice(2);
      const next = argv[i + 1];
      if (next !== undefined && !next.startsWith('--')) {
        result[key] = next;
        i++;
      } else {
        result[key] = true;
      }
    } else {
      result._.push(a);
    }
  }
  return result;
}

// --- Fehler ------------------------------------------------------------------

export class AuthError extends Error {
  constructor(message, reason) {
    super(message);
    this.reason = reason;
  }
}

/**
 * Allgemeiner CLI-Fehler (Validierung, Not-Found, API-Fehler) — main() faengt ihn wie jeden Error.
 * `rueckmeldung` ist die Auskunft ueber die Wirkung (siehe RUECKMELDUNG); der Default gilt fuer
 * jeden Fehler, bei dem nichts ausgefuehrt wurde.
 */
export class CliError extends Error {
  constructor(message, rueckmeldung = 'nicht-ausgefuehrt') {
    super(message);
    this.rueckmeldung = rueckmeldung;
  }
}

// --- Token-Beschaffung fuer login --------------------------------------------

/**
 * Liest den PAT fuer 'auth login' aus (in dieser Reihenfolge): --token, Umgebungsvariable
 * TBX_TOKEN, stdin. `io.env`/`io.readStdin` sind injizierbar (Tests).
 */
export async function acquireToken(flags, io) {
  const fromFlag = flags.token;
  if (typeof fromFlag === 'string' && fromFlag.trim()) return fromFlag.trim();
  if (fromFlag === true) throw new CliError('--token erwartet einen Wert');

  const env = io.env || process.env;
  if (env.TBX_TOKEN && env.TBX_TOKEN.trim()) return env.TBX_TOKEN.trim();

  const fromStdin = io.readStdin ? await io.readStdin() : '';
  if (fromStdin && fromStdin.trim()) return fromStdin.trim();

  throw new CliError('Kein Token angegeben. Nutze --token, TBX_TOKEN oder stdin.');
}

// ============================================================
// Wiederholung gegen Ueberlast (Issue #1005)
// ============================================================
//
// ACHTUNG, ZWILLING: Dieselbe Logik traegt `kit/board.mjs` im claude-workflow-kit
// (dort Issue #834). Die beiden Fassungen sind bewusst wortgleich kommentiert,
// damit eine spaetere Aenderung nicht nur eine Haelfte trifft — wer hier die
// Staffel, die Wiederholregeln oder die drei Rueckmeldungen anfasst, aendert
// die Schwesterfassung mit. Geteilter Code ist es nicht: tbx.mjs bleibt eine
// eigenstaendig kopierbare Einzeldatei ohne Fremdabhaengigkeiten.
//
// Der Anlass: Das Board begrenzt seit kanban-kit 2.5 die Befehle je Person und
// weist mit `429`, `Retry-After` und dem Problem-Detail `type: urn:manban:overload`
// ab. Ein Nachtlauf schickt Hunderte Befehle in Folge; ohne Wiederholung bricht
// er irgendwo ab und hinterlaesst eine halb bearbeitete Kette.
//
// Ein Unterschied zur Schwesterfassung bleibt bewusst (Plan #995, E11): tbx kennt
// keinen Nachtbetrieb und hat ein festes Gesamtbudget von 30 Sekunden.

/** Das Problem-Detail, an dem eine Ueberlast-Abweisung erkennbar ist. */
export const UEBERLAST_TYPE = 'urn:manban:overload';

/** Zeitgrenze je Einzelversuch. Drei volle Haenger passen so ins Budget. */
const VERSUCH_MS = 10_000;
/** Festes Gesamtbudget einer Wiederholschleife. */
export const BUDGET_MS = 30_000;
const WARTE_BASIS_MS = 500;
const WARTE_MAX_MS = 8_000;
const WARTE_MIN_MS = 100;
/** Anteil der Wartezeit, der zufaellig obendrauf kommt (Streuung gegen Gleichtakt). */
const STREUUNG = 0.25;

/**
 * Die drei Auskuenfte ueber die Wirkung eines Befehls: ausgefuehrt, sicher nicht
 * ausgefuehrt, oder unklar — dann kann die Wirkung eingetreten sein.
 */
export const RUECKMELDUNG = {
  AUSGEFUEHRT: 'ausgefuehrt',
  NICHT_AUSGEFUEHRT: 'nicht-ausgefuehrt',
  AUSGANG_UNKLAR: 'ausgang-unklar',
};

/**
 * Netzfehler-Codes, bei denen nachweislich kein Aufruf hinausging. Sie sind keine
 * Wiederholung wert: Ein abgeschalteter Server oder ein unbekannter Name wird
 * innerhalb des Budgets nicht wieder da sein, und der Aufruf hat sicher nichts
 * bewirkt — deshalb "nicht ausgefuehrt" statt "Ausgang unklar".
 */
const NETZ_ENDGUELTIG = new Set(['ECONNREFUSED', 'ENOTFOUND', 'ERR_INVALID_URL', 'EPROTO', 'CERT_HAS_EXPIRED']);

/**
 * Ordnet einen fetch-Wurf ein: `zeitablauf` (die eigene Zeitgrenze hat abgebrochen),
 * `endgueltig` (kein Aufruf ging hinaus) oder `abbruch` (die Verbindung brach
 * unterwegs ab — der Aufruf kann angekommen sein).
 */
export function netzfehlerArt(e) {
  if (e?.name === 'TimeoutError' || e?.name === 'AbortError') return 'zeitablauf';
  const code = e?.cause?.code ?? e?.code ?? '';
  return NETZ_ENDGUELTIG.has(code) ? 'endgueltig' : 'abbruch';
}

/**
 * Darf dieser Fehlschlag wiederholt werden? Die Regel in einem Satz: alles, was
 * entweder nichts ausgefuehrt hat (Abweisung wegen Ueberlast) oder gefahrlos
 * zweimal laufen darf (lesend, ersetzend, oder mit Idempotenz-Schluessel).
 *
 *  - `429` nur mit dem Ueberlast-`type`, dann aber bei JEDER Methode: Eine
 *    Abweisung hat die Wirkung nicht ausgefuehrt. Ein fremdes `429` ohne diesen
 *    `type` sagt nichts ueber den Ausgang und bleibt unwiederholt.
 *  - `5xx` bei `GET` (folgenlos), `PUT`/`DELETE` (dasselbe Ergebnis bei
 *    Wiederholung) und bei `POST` nur MIT Schluessel. Ein `POST` ohne Schluessel
 *    wuerde sich sonst nach einem 502 des vorgeschalteten Proxys doppeln.
 *  - `401` nie: ein widerrufener Token wird durch Warten nicht gueltig.
 */
export function darfWiederholen({ method, status = null, typ = null, hatSchluessel = false, netz = null }) {
  if (netz) return netz !== 'endgueltig';
  if (status === 429) return typ === UEBERLAST_TYPE;
  if (status === null || status < 500) return false;
  const m = (method || 'GET').toUpperCase();
  if (m === 'POST') return hatSchluessel;
  return true;
}

/**
 * Die Rueckmeldung zu einem abgeschlossenen Versuch (siehe RUECKMELDUNG).
 * Entscheidend ist, ob der Aufruf etwas veraendert haben KANN: Nur ein
 * schreibender Aufruf, der hinausging und ohne Antwort blieb, ist unklar.
 */
export function rueckmeldungFuer({ ok = false, status = null, netz = null, method = 'GET' }) {
  if (ok) return RUECKMELDUNG.AUSGEFUEHRT;
  const schreibend = (method || 'GET').toUpperCase() !== 'GET';
  if (!schreibend) return RUECKMELDUNG.NICHT_AUSGEFUEHRT;
  if (netz) return netz === 'endgueltig' ? RUECKMELDUNG.NICHT_AUSGEFUEHRT : RUECKMELDUNG.AUSGANG_UNKLAR;
  return status >= 500 ? RUECKMELDUNG.AUSGANG_UNKLAR : RUECKMELDUNG.NICHT_AUSGEFUEHRT;
}

/**
 * Wartezeit vor dem naechsten Versuch: verdoppelnd bis zur Deckelung, mit
 * Streuung nach oben. `Retry-After` (Sekunden) schlaegt die eigene Staffel — der
 * Server weiss besser, wann sein Fenster wieder offen ist. Die Untergrenze
 * verhindert eine Schleife ohne Fortschritt bei `Retry-After: 0`.
 */
export function wartezeitMs(versuch, retryAfterSek = null, zufall = Math.random) {
  const roh = Number(retryAfterSek);
  const basis = retryAfterSek !== null && retryAfterSek !== undefined && Number.isFinite(roh) && roh >= 0
    ? roh * 1000
    : Math.min(WARTE_BASIS_MS * 2 ** (versuch - 1), WARTE_MAX_MS);
  return Math.max(WARTE_MIN_MS, Math.round(basis + basis * STREUUNG * zufall()));
}

/** Shell-sicheres Zitat fuer das Wiederholkommando — nur, wo noetig. */
function zitiere(arg) {
  if (/^[\w@%+=:,./-]+$/.test(arg)) return arg;
  const maskiert = String(arg).replaceAll("'", String.raw`'\''`);
  return `'${maskiert}'`;
}

/**
 * Baut das Kommando, mit dem sich ein unklar ausgegangener Aufruf gefahrlos
 * wiederholen laesst: derselbe Aufruf, derselbe Schluessel. Ein bereits
 * uebergebener `--idempotency-key` wird ersetzt statt gedoppelt. `argv` ist
 * die Argumentliste ohne Programmnamen, wie `main` sie bekommt.
 */
export function wiederholKommando(schluessel, argv = []) {
  const args = [];
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--idempotency-key') {
      i++;
      continue;
    }
    args.push(argv[i]);
  }
  args.push('--idempotency-key', schluessel);
  return ['tbx', ...args].map(zitiere).join(' ');
}

const schlafe = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Liest die Lage eines fehlgeschlagenen Versuchs aus: Klartext-Grund, Problem-`type`
 * und `Retry-After`. Der Rumpf wird aus einer Kopie gelesen, damit der Aufrufer die
 * Antwort, die er am Ende bekommt, noch selbst auswerten kann.
 */
async function fehlerlage(res, wurf) {
  if (!res) return { grund: `Netzfehler (${wurf.name}: ${wurf.message})`, typ: null, retryAfter: null };
  let grund = `HTTP ${res.status}`;
  let typ = null;
  try {
    const body = await (typeof res.clone === 'function' ? res.clone() : res).json();
    if (body?.message) grund = `${grund}: ${body.message}`;
    if (typeof body?.type === 'string') typ = body.type;
  } catch {
    /* kein JSON-Body */
  }
  const roh = res.headers?.get?.('retry-after');
  const sek = roh === null || roh === undefined || roh === '' ? null : Number(roh);
  return { grund, typ, retryAfter: Number.isFinite(sek) ? sek : null };
}

/** Der Zusatz zur Meldung "Ausgang unklar": was passiert sein kann und wie es weitergeht. */
function unklarHinweis(method, path, schluessel, argv) {
  const kopf = `Ausgang unklar: ${method} ${path} ging hinaus, blieb aber ohne verwertbare Antwort — `
    + 'die Wirkung kann eingetreten sein.';
  if (!schluessel) {
    return `${kopf} Der Aufruf lief ohne Idempotenz-Schluessel; eine blinde Wiederholung `
      + 'kann ihn ein zweites Mal ausfuehren. Erst am Board nachsehen.';
  }
  return `${kopf} Schluessel: ${schluessel}. Mit genau diesem Schluessel wiederholen — derselbe `
    + `Schluessel fuehrt die Wirkung hoechstens einmal aus:\n  ${wiederholKommando(schluessel, argv)}`;
}

// --- API-Zugriff -------------------------------------------------------------

/**
 * Roher, token-authentifizierter Request gegen `${host}${path}` — ohne Zugriff auf
 * gespeicherte Dateien (fuer die Login-Validierung, bevor gespeichert wird). Mit
 * Zeitgrenze, Wiederholung und Idempotenz-Schluessel; die Regeln stehen in den
 * reinen Funktionen darueber, hier steht nur ihre Reihenfolge.
 *
 * `options.idempotencyKey` traegt den Schluessel fuer die beiden Endpunkte, die ihn
 * serverseitig auswerten; er bleibt ueber ALLE Versuche gleich. `umgebung.uhr` haelt
 * Zeitquelle, Schlafen, Zufall und die Meldespur (injizierbar wie `fetchImpl`),
 * `umgebung.argv` den Befehl fuer das Wiederholkommando.
 *
 * Eine Antwort, die nicht (mehr) wiederholt wird, geht an den Aufrufer zurueck — er
 * wertet 401, 404 und Co. aus wie bisher. Geworfen wird nur, wo keine Antwort vorliegt
 * oder der Ausgang unklar ist.
 */
export async function tokenFetch(host, token, path, options = {}, fetchImpl = fetch, umgebung = {}) {
  const { idempotencyKey, ...rest } = options || {};
  const uhr = umgebung.uhr || {};
  const jetzt = uhr.jetzt ?? Date.now;
  const schlaf = uhr.schlaf ?? schlafe;
  const zufall = uhr.zufall ?? Math.random;
  const melde = uhr.melde ?? ((zeile) => process.stderr.write(`${zeile}\n`));
  const method = (rest.method || 'GET').toUpperCase();
  const headers = { ...(rest.headers || {}), [TOKEN_HEADER]: token };
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  const frist = jetzt() + BUDGET_MS;

  for (let versuch = 1; ; versuch++) {
    let res = null;
    let wurf = null;
    try {
      res = await fetchImpl(`${host}${path}`, { ...rest, headers, signal: AbortSignal.timeout(VERSUCH_MS) });
    } catch (e) {
      wurf = e;
    }
    if (res?.ok || res?.status === 401) return res;

    const status = res?.status ?? null;
    const netz = wurf ? netzfehlerArt(wurf) : null;
    const { grund, typ, retryAfter } = await fehlerlage(res, wurf);
    const warte = wartezeitMs(versuch, retryAfter, zufall);
    const nochmal = darfWiederholen({ method, status, typ, hatSchluessel: Boolean(idempotencyKey), netz })
      && jetzt() + warte <= frist;
    if (!nochmal) {
      const rueckmeldung = rueckmeldungFuer({ status, netz, method });
      if (rueckmeldung === RUECKMELDUNG.AUSGANG_UNKLAR) {
        const basis = wurf ? `Board nicht erreichbar (${host}): ${wurf.message}` : `Board-Fehler: ${grund}`;
        throw new CliError(`${basis}\n${unklarHinweis(method, path, idempotencyKey, umgebung.argv)}`, rueckmeldung);
      }
      if (wurf) throw new CliError(`Board nicht erreichbar (${host}): ${wurf.message}`, rueckmeldung);
      return res;
    }

    // Eine Zeile je Wiederholung, nicht je Aufruf: Wer zusieht, soll Warten von
    // Haengen unterscheiden koennen.
    melde(`tbx: ${method} ${path} — Versuch ${versuch} endete mit ${grund}, erneut in ${warte} ms `
      + `(Frist ${Math.round(BUDGET_MS / 1000)} s)`);
    await schlaf(warte);
  }
}

/**
 * Fuehrt einen PAT-authentifizierten Request gegen `${config.host}${path}` aus.
 * Wirft AuthError mit reason 'not_logged_in', wenn kein Login vorliegt.
 */
export async function apiFetch(path, options = {}, { fetchImpl = fetch, baseDir, uhr, argv } = {}) {
  const config = readJsonFile(configPath(baseDir));
  const tokens = readJsonFile(tokensPath(baseDir));
  if (!config || !config.host || !tokens || !tokens.token) {
    throw new AuthError('Nicht angemeldet. Bitte zuerst: tbx auth login', 'not_logged_in');
  }
  return tokenFetch(config.host, tokens.token, path, options, fetchImpl, { uhr, argv });
}

/** Was ein Kommando aus `io` an den API-Zugriff weiterreicht. */
function verbindung(io) {
  return { fetchImpl: io.fetchImpl, baseDir: io.baseDir, uhr: io.uhr, argv: io.argv };
}

/** Liest `--idempotency-key`; ein nackter Schalter waere sonst der Schluessel "true". */
function idempotenzSchluessel(flags) {
  if (flags['idempotency-key'] === undefined) return randomUUID();
  return requireStringFlag(flags, 'idempotency-key');
}

// --- Status-Mapping (Kit-Status <-> Backend-Spalte) ---------------------------

export const STATUS_TO_COLUMN = {
  backlog: 'BACKLOG',
  ready: 'READY',
  in_progress: 'IN_PROGRESS',
  in_review: 'IN_REVIEW',
  done: 'DONE',
};

export const COLUMN_TO_STATUS = Object.fromEntries(
  Object.entries(STATUS_TO_COLUMN).map(([status, column]) => [column, status]),
);

export const VALID_STATUSES = Object.keys(STATUS_TO_COLUMN);

export function toColumn(status) {
  const column = STATUS_TO_COLUMN[status];
  if (!column) {
    throw new CliError(`Ungültiger Status '${status}'. Gültig: ${VALID_STATUSES.join(', ')}`);
  }
  return column;
}

export function toStatus(column) {
  return COLUMN_TO_STATUS[column] || column;
}

// --- Board-Zugriff -------------------------------------------------------------

/** Wirft bei 401 einen anmelde-spezifischen Fehler, sonst bei Nicht-2xx die Server-Message. */
async function ensureOk(res) {
  if (res.status === 401) {
    throw new CliError('Token ungültig oder widerrufen. Bitte neu anmelden: tbx auth login');
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    const fieldErrors = body.fieldErrors
      ? ` (${Object.entries(body.fieldErrors)
          .map(([field, msg]) => `${field}: ${msg}`)
          .join(', ')})`
      : '';
    throw new CliError(`${body.message || `HTTP ${res.status}`}${fieldErrors}`);
  }
  return res;
}

/** Liest das gruppierte Board und liefert eine flache, mit `status` angereicherte Liste. */
export async function fetchBoardItems(io) {
  const res = await apiFetch('/api/kanban/items', {}, verbindung(io));
  await ensureOk(res);
  const grouped = await res.json();
  return Object.values(grouped)
    .flat()
    .map((item) => ({ ...item, status: toStatus(item.column) }));
}

export function findItemByNumber(items, number) {
  return items.find((i) => i.number === number) || null;
}

/** Parst ein CLI-Argument als Issue-Nummer; wirft bei nicht-numerischer Eingabe
 * einen klaren CliError statt ein ungeprueftes NaN durchzureichen. */
export function parseIssueNumber(numberArg) {
  const number = Number(numberArg);
  if (Number.isNaN(number)) {
    throw new CliError(`Ungültige Issue-Nummer: '${numberArg}'`);
  }
  return number;
}

async function resolveItemByNumber(number, io) {
  const items = await fetchBoardItems(io);
  const item = findItemByNumber(items, number);
  if (!item) {
    throw new CliError(`Issue ${number} nicht gefunden`);
  }
  return item;
}

function toGenericIssue(item) {
  return { id: item.number, title: item.title, body: item.body, status: item.status };
}

/**
 * Liest die Kommentare einer Karte (`GET /api/kanban/items/{id}/comments`).
 *
 * Bewusst ohne `ensureOk`: eine aeltere Instanz kennt den Endpoint noch nicht und
 * antwortet mit 404/405 — das darf `issue get` nicht abbrechen lassen. Jede
 * Nicht-2xx-Antwort und jeder unerwartete Body ergeben daher eine leere Liste.
 * Ein ungueltiger Token faellt schon vorher beim Board-Abruf auf (401 dort).
 * Die Felder werden explizit gemappt, damit die CLI-Ausgabe stabil bleibt, wenn
 * das Backend das DTO spaeter erweitert.
 */
export async function fetchItemComments(itemId, io) {
  const res = await apiFetch(
    `/api/kanban/items/${itemId}/comments`,
    {},
    verbindung(io),
  );
  if (!res.ok) return [];
  const body = await res.json().catch(() => null);
  if (!Array.isArray(body)) return [];
  return body.map((c) => ({ author: c.author, body: c.body, createdAt: c.createdAt }));
}

// --- Kommandos: issue -----------------------------------------------------------

async function cmdIssueCreate(flags, io) {
  if (!flags.title) throw new CliError('--title ist erforderlich');
  const idempotencyKey = idempotenzSchluessel(flags);
  const config = readJsonFile(configPath(io.baseDir));
  const res = await apiFetch(
    '/api/kanban/items',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: flags.title, body: flags.body || '', column: 'BACKLOG' }),
      idempotencyKey,
    },
    verbindung(io),
  );
  await ensureOk(res);
  const created = await res.json();
  io.stdout(JSON.stringify({ id: created.number, url: `${config.host}/kanban` }, null, 2) + '\n');
}

async function cmdIssueGet(numberArg, io) {
  const number = parseIssueNumber(numberArg);
  const item = await resolveItemByNumber(number, io);
  const comments = await fetchItemComments(item.id, io);
  io.stdout(JSON.stringify({ ...toGenericIssue(item), comments }, null, 2) + '\n');
}

async function cmdIssueList(flags, io) {
  if (flags.status && !VALID_STATUSES.includes(flags.status)) {
    throw new CliError(`Ungültiger Status '${flags.status}'. Gültig: ${VALID_STATUSES.join(', ')}`);
  }
  const items = await fetchBoardItems(io);
  const filtered = (flags.status ? items.filter((i) => i.status === flags.status) : items)
    .slice()
    .sort((a, b) => a.number - b.number);
  io.stdout(JSON.stringify(filtered.map(toGenericIssue), null, 2) + '\n');
}

async function cmdIssueMove(numberArg, statusArg, io) {
  const number = parseIssueNumber(numberArg);
  const column = toColumn(statusArg);
  const items = await fetchBoardItems(io);
  const item = findItemByNumber(items, number);
  if (!item) throw new CliError(`Issue ${number} nicht gefunden`);

  const targetPosition =
    item.column === column ? item.position : items.filter((i) => i.column === column).length;
  const res = await apiFetch(
    `/api/kanban/items/${item.id}/move`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ column, position: targetPosition }),
    },
    verbindung(io),
  );
  await ensureOk(res);
  io.stdout(JSON.stringify({ ok: true, id: number, status: statusArg }, null, 2) + '\n');
}

async function cmdIssueComment(numberArg, flags, io) {
  if (!flags.text) throw new CliError('--text ist erforderlich');
  const idempotencyKey = idempotenzSchluessel(flags);
  const number = parseIssueNumber(numberArg);
  const item = await resolveItemByNumber(number, io);
  const res = await apiFetch(
    `/api/kanban/items/${item.id}/comments`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ body: flags.text }),
      idempotencyKey,
    },
    verbindung(io),
  );
  await ensureOk(res);
  io.stdout(JSON.stringify({ ok: true, id: number }, null, 2) + '\n');
}

// --- Kommandos: auth -----------------------------------------------------------

async function cmdLogin(flags, io) {
  const storedConfig = readJsonFile(configPath(io.baseDir));
  const host = resolveHost(flags, storedConfig);
  const token = await acquireToken(flags, io);

  // Token gegen die API validieren, bevor er gespeichert wird.
  const res = await tokenFetch(host, token, '/api/kanban/items', {}, io.fetchImpl, verbindung(io));
  if (res.status === 401) {
    throw new AuthError('Token ungültig oder widerrufen.', 'invalid_token');
  }
  if (!res.ok) {
    throw new AuthError(`Token-Validierung fehlgeschlagen (HTTP ${res.status}).`, 'validation_failed');
  }

  writeJsonFileSecure(configPath(io.baseDir), { host });
  writeJsonFileSecure(tokensPath(io.baseDir), { token });
  io.stdout(JSON.stringify({ ok: true, host }, null, 2) + '\n');
}

async function cmdStatus(io) {
  const config = readJsonFile(configPath(io.baseDir));
  const tokens = readJsonFile(tokensPath(io.baseDir));
  if (!config || !config.host || !tokens || !tokens.token) {
    io.stderr('Nicht angemeldet. Bitte zuerst: tbx auth login\n');
    return 1;
  }
  const res = await tokenFetch(config.host, tokens.token, '/api/kanban/items', {}, io.fetchImpl, verbindung(io));
  const valid = res.ok;
  io.stdout(JSON.stringify({ host: config.host, valid }, null, 2) + '\n');
  return valid ? 0 : 1;
}

function cmdLogout(io) {
  // Der Token wird serverseitig in der Web-UI widerrufen (Einstellungen -> Kanban-Tokens);
  // hier wird nur die lokale Kopie entfernt.
  deleteFile(tokensPath(io.baseDir));
  io.stdout(JSON.stringify({ ok: true }, null, 2) + '\n');
  return 0;
}

// --- Dispatch --------------------------------------------------------------

const defaultIo = () => ({
  stdout: (s) => process.stdout.write(s),
  stderr: (s) => process.stderr.write(s),
  fetchImpl: fetch,
  env: process.env,
  readStdin: readStdinToEnd,
  baseDir: undefined,
});

/** Liest stdin vollstaendig (fuer 'auth login' ohne --token/TBX_TOKEN). */
async function readStdinToEnd() {
  if (process.stdin.isTTY) return '';
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString('utf-8');
}

/** Gibt den Exit-Code zurueck, statt process.exit() aufzurufen — testbar. */
export async function main(argv, io = defaultIo()) {
  if (argv.length === 0 || argv[0] === '--help' || argv[0] === '-h') {
    io.stdout(HELP);
    return 0;
  }

  const [axis, command, ...rest] = argv;
  const flags = parseArgs(rest);
  // Der Befehl reist mit, damit "Ausgang unklar" das vollstaendige Wiederholkommando nennt.
  io = { ...io, argv };

  if (axis === 'auth') {
    try {
      switch (command) {
        case 'login':
          await cmdLogin(flags, io);
          return 0;
        case 'status':
          return await cmdStatus(io);
        case 'logout':
          return cmdLogout(io);
        default:
          io.stdout(HELP);
          io.stderr(`Fehler: Unbekannter auth-Befehl: '${command}'\n`);
          return 1;
      }
    } catch (e) {
      io.stderr(`Fehler: ${e.message}\n`);
      return 1;
    }
  }

  if (axis === 'issue') {
    try {
      switch (command) {
        case 'create':
          await cmdIssueCreate(flags, io);
          return 0;
        case 'get':
          await cmdIssueGet(rest[0], io);
          return 0;
        case 'list':
          await cmdIssueList(flags, io);
          return 0;
        case 'move':
          await cmdIssueMove(rest[0], rest[1], io);
          return 0;
        case 'comment':
          await cmdIssueComment(rest[0], flags, io);
          return 0;
        default:
          io.stdout(HELP);
          io.stderr(`Fehler: Unbekannter issue-Befehl: '${command}'\n`);
          return 1;
      }
    } catch (e) {
      io.stderr(`Fehler: ${e.message}\n`);
      return 1;
    }
  }

  io.stdout(HELP);
  io.stderr(`Fehler: Unbekannte Achse: '${axis}'. Erwartet: auth, issue\n`);
  return 1;
}

/**
 * realpathSync noetig, da import.meta.url immer den aufgeloesten Pfad traegt —
 * ein Aufruf ueber einen symbolischen Link (z.B. macOS /tmp -> /private/tmp,
 * oder ein `~/bin/tbx`-Symlink) wuerde sonst nie erkannt und main() nie laufen.
 */
export function resolveIsMainModule(argv1, metaUrl) {
  if (!argv1) return false;
  try {
    return metaUrl === pathToFileURL(realpathSync(argv1)).href;
  } catch (err) {
    if (err && ['ENOENT', 'EACCES', 'ELOOP', 'ENOTDIR'].includes(err.code)) {
      return false;
    }
    throw err;
  }
}

const isMainModule = resolveIsMainModule(process.argv[1], import.meta.url);
if (isMainModule) {
  main(process.argv.slice(2)).then((code) => process.exit(code));
}
