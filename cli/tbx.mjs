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

  tbx issue create --title <text> [--body <text>]
  tbx issue get <nummer>
  tbx issue list [--status <status>]
  tbx issue move <nummer> <status>
  tbx issue comment <nummer> --text <text>

Status-Werte: backlog, ready, in_progress, in_review, done

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

/** Allgemeiner CLI-Fehler (Validierung, Not-Found, API-Fehler) — main() faengt ihn wie jeden Error. */
export class CliError extends Error {}

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

// --- API-Zugriff -------------------------------------------------------------

/**
 * Roher, token-authentifizierter Request gegen `${host}${path}` — ohne Zugriff auf
 * gespeicherte Dateien (fuer die Login-Validierung, bevor gespeichert wird).
 */
export function tokenFetch(host, token, path, options, fetchImpl = fetch) {
  return fetchImpl(`${host}${path}`, {
    ...options,
    headers: { ...(options?.headers || {}), [TOKEN_HEADER]: token },
  });
}

/**
 * Fuehrt einen PAT-authentifizierten Request gegen `${config.host}${path}` aus.
 * Wirft AuthError mit reason 'not_logged_in', wenn kein Login vorliegt.
 */
export async function apiFetch(path, options = {}, { fetchImpl = fetch, baseDir } = {}) {
  const config = readJsonFile(configPath(baseDir));
  const tokens = readJsonFile(tokensPath(baseDir));
  if (!config || !config.host || !tokens || !tokens.token) {
    throw new AuthError('Nicht angemeldet. Bitte zuerst: tbx auth login', 'not_logged_in');
  }
  return tokenFetch(config.host, tokens.token, path, options, fetchImpl);
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
  const res = await apiFetch('/api/kanban/items', {}, { fetchImpl: io.fetchImpl, baseDir: io.baseDir });
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

// --- Kommandos: issue -----------------------------------------------------------

async function cmdIssueCreate(flags, io) {
  if (!flags.title) throw new CliError('--title ist erforderlich');
  const config = readJsonFile(configPath(io.baseDir));
  const res = await apiFetch(
    '/api/kanban/items',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: flags.title, body: flags.body || '', column: 'BACKLOG' }),
    },
    { fetchImpl: io.fetchImpl, baseDir: io.baseDir },
  );
  await ensureOk(res);
  const created = await res.json();
  io.stdout(JSON.stringify({ id: created.number, url: `${config.host}/kanban` }, null, 2) + '\n');
}

async function cmdIssueGet(numberArg, io) {
  const number = parseIssueNumber(numberArg);
  const item = await resolveItemByNumber(number, io);
  io.stdout(JSON.stringify(toGenericIssue(item), null, 2) + '\n');
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
    { fetchImpl: io.fetchImpl, baseDir: io.baseDir },
  );
  await ensureOk(res);
  io.stdout(JSON.stringify({ ok: true, id: number, status: statusArg }, null, 2) + '\n');
}

async function cmdIssueComment(numberArg, flags, io) {
  if (!flags.text) throw new CliError('--text ist erforderlich');
  const number = parseIssueNumber(numberArg);
  const item = await resolveItemByNumber(number, io);
  const res = await apiFetch(
    `/api/kanban/items/${item.id}/comments`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ body: flags.text }),
    },
    { fetchImpl: io.fetchImpl, baseDir: io.baseDir },
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
  const res = await tokenFetch(host, token, '/api/kanban/items', {}, io.fetchImpl);
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
  const res = await tokenFetch(config.host, tokens.token, '/api/kanban/items', {}, io.fetchImpl);
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
