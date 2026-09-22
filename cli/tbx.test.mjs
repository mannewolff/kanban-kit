import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, statSync, readFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

import {
  parseArgs,
  resolveHost,
  acquireToken,
  writeJsonFileSecure,
  readJsonFile,
  configPath,
  tokensPath,
  tokenFetch,
  apiFetch,
  main,
  AuthError,
  toColumn,
  toStatus,
  findItemByNumber,
  fetchItemComments,
  parseIssueNumber,
  CliError,
  resolveIsMainModule,
  RUECKMELDUNG,
  BUDGET_MS,
  UEBERLAST_TYPE,
  darfWiederholen,
  rueckmeldungFuer,
  wartezeitMs,
  netzfehlerArt,
  wiederholKommando,
} from './tbx.mjs';

const THIS_FILE = fileURLToPath(import.meta.url);
const TBX_PATH = join(dirname(THIS_FILE), 'tbx.mjs');

async function withTempConfigDir(fn) {
  const dir = mkdtempSync(join(tmpdir(), 'tbx-test-'));
  try {
    return await fn(dir);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

function jsonResponse(status, body, headers = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    headers: { get: (name) => headers[name.toLowerCase()] ?? null },
  };
}

/** Gesteuerte Uhr: Schlafen rueckt nur die Zeit vor, es wird nie echt gewartet. */
function fakeUhr(zeilen = []) {
  let t = 0;
  const schlaefe = [];
  return {
    jetzt: () => t,
    schlaf: async (ms) => {
      schlaefe.push(ms);
      t += ms;
    },
    zufall: () => 0,
    melde: (zeile) => zeilen.push(zeile),
    schlaefe,
    zeit: () => t,
  };
}

function io(overrides = {}) {
  const stdoutLines = [];
  const stderrLines = [];
  return {
    stdout: (s) => stdoutLines.push(s),
    stderr: (s) => stderrLines.push(s),
    fetchImpl: overrides.fetchImpl,
    env: overrides.env || {},
    readStdin: overrides.readStdin || (async () => ''),
    baseDir: overrides.baseDir,
    uhr: overrides.uhr || fakeUhr(stderrLines),
    stdoutLines,
    stderrLines,
  };
}

/** Legt einen gültigen Login-Zustand (config + tokens) im baseDir an. */
function seedLogin(baseDir, host = 'http://localhost:8080', token = 'tk_seed') {
  writeJsonFileSecure(configPath(baseDir), { host });
  writeJsonFileSecure(tokensPath(baseDir), { token });
}

// --- 1. Argument-Parser + Dispatch ------------------------------------------

test('parseArgs: liest Flags mit Werten und Positional-Argumente', () => {
  const result = parseArgs(['--host', 'http://x', 'foo', '--flag-only']);
  assert.equal(result.host, 'http://x');
  assert.equal(result['flag-only'], true);
  assert.deepEqual(result._, ['foo']);
});

test('main: ohne Argumente zeigt Hilfe und liefert Exit-Code 0', async () => {
  const i = io();
  assert.equal(await main([], i), 0);
  assert.match(i.stdoutLines.join(''), /tbx —/);
});

test('main: unbekannter auth-Befehl -> Exit 1 + Hilfe', async () => {
  const i = io();
  assert.equal(await main(['auth', 'nonsense'], i), 1);
  assert.match(i.stderrLines.join(''), /Unbekannter auth-Befehl/);
});

test('main: unbekannte Achse -> Exit 1', async () => {
  const i = io();
  assert.equal(await main(['bogus', 'list'], i), 1);
  assert.match(i.stderrLines.join(''), /Unbekannte Achse/);
});

test('main: unbekannter issue-Befehl -> Exit 1', async () => {
  const i = io();
  assert.equal(await main(['issue', 'nonsense'], i), 1);
  assert.match(i.stderrLines.join(''), /Unbekannter issue-Befehl/);
});

// --- 2. Host-Aufloesung ------------------------------------------------------

test('resolveHost: Flag schlaegt gespeicherte Config und Default', () => {
  assert.equal(resolveHost({ host: 'http://flag' }, { host: 'http://stored' }), 'http://flag');
});

test('resolveHost: faellt auf gespeicherte Config zurueck', () => {
  assert.equal(resolveHost({}, { host: 'http://stored' }), 'http://stored');
});

test('resolveHost: Default (Produktion) ohne Flag/Config', () => {
  assert.equal(resolveHost({}, null), 'https://kanban.mwolff.org');
});

test('resolveHost: leeres --host wird abgelehnt', () => {
  assert.throws(() => resolveHost({ host: '' }, null), CliError);
});

test('resolveHost: --host ohne Wert wird abgelehnt', () => {
  assert.throws(() => resolveHost({ host: true }, null), CliError);
});

// --- 3. Token-Beschaffung ----------------------------------------------------

test('acquireToken: --token hat Vorrang', async () => {
  const t = await acquireToken({ token: 'tk_flag' }, io({ env: { TBX_TOKEN: 'tk_env' } }));
  assert.equal(t, 'tk_flag');
});

test('acquireToken: faellt auf TBX_TOKEN zurueck', async () => {
  const t = await acquireToken({}, io({ env: { TBX_TOKEN: 'tk_env' } }));
  assert.equal(t, 'tk_env');
});

test('acquireToken: faellt auf stdin zurueck', async () => {
  const t = await acquireToken({}, io({ readStdin: async () => '  tk_stdin\n' }));
  assert.equal(t, 'tk_stdin');
});

test('acquireToken: ohne Quelle -> CliError', async () => {
  await assert.rejects(() => acquireToken({}, io()), CliError);
});

test('acquireToken: --token ohne Wert -> CliError', async () => {
  await assert.rejects(() => acquireToken({ token: true }, io()), CliError);
});

// --- 4. Sichere Speicherung --------------------------------------------------

test('writeJsonFileSecure: legt Datei mit 0600 an', async () => {
  await withTempConfigDir((dir) => {
    const p = tokensPath(dir);
    writeJsonFileSecure(p, { token: 'tk_x' });
    assert.equal(statSync(p).mode & 0o777, 0o600);
    assert.deepEqual(readJsonFile(p), { token: 'tk_x' });
  });
});

test('readJsonFile: null bei fehlender/korrupter Datei', () => {
  assert.equal(readJsonFile('/nonexistent/x.json'), null);
});

// --- 5. Status-Mapping -------------------------------------------------------

test('toColumn/toStatus: Rundlauf', () => {
  assert.equal(toColumn('in_review'), 'IN_REVIEW');
  assert.equal(toStatus('IN_REVIEW'), 'in_review');
});

test('toColumn: ungueltiger Status -> CliError', () => {
  assert.throws(() => toColumn('bogus'), CliError);
});

test('parseIssueNumber: nicht-numerisch -> CliError', () => {
  assert.throws(() => parseIssueNumber('abc'), CliError);
});

test('findItemByNumber: findet/verfehlt', () => {
  const items = [{ number: 1 }, { number: 2 }];
  assert.equal(findItemByNumber(items, 2).number, 2);
  assert.equal(findItemByNumber(items, 99), null);
});

// --- 6. apiFetch / tokenFetch ------------------------------------------------

test('tokenFetch: setzt X-Kanban-Token-Header', async () => {
  let captured;
  const fetchImpl = async (url, opts) => {
    captured = { url, opts };
    return jsonResponse(200, {});
  };
  await tokenFetch('http://h', 'tk_abc', '/api/kanban/items', {}, fetchImpl);
  assert.equal(captured.url, 'http://h/api/kanban/items');
  assert.equal(captured.opts.headers['X-Kanban-Token'], 'tk_abc');
});

test('apiFetch: ohne Login -> AuthError not_logged_in', async () => {
  await withTempConfigDir(async (dir) => {
    await assert.rejects(
      () => apiFetch('/api/kanban/items', {}, { baseDir: dir, fetchImpl: async () => jsonResponse(200, {}) }),
      (e) => e instanceof AuthError && e.reason === 'not_logged_in',
    );
  });
});

test('apiFetch: sendet gespeicherten Token', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir, 'http://h', 'tk_stored');
    let captured;
    const fetchImpl = async (url, opts) => {
      captured = { url, opts };
      return jsonResponse(200, {});
    };
    await apiFetch('/api/kanban/items', {}, { baseDir: dir, fetchImpl });
    assert.equal(captured.url, 'http://h/api/kanban/items');
    assert.equal(captured.opts.headers['X-Kanban-Token'], 'tk_stored');
  });
});

// --- 7. auth login -----------------------------------------------------------

test('auth login: gueltiger Token wird validiert und 0600 gespeichert', async () => {
  await withTempConfigDir(async (dir) => {
    let validated;
    const fetchImpl = async (url, opts) => {
      validated = { url, token: opts.headers['X-Kanban-Token'] };
      return jsonResponse(200, {});
    };
    const i = io({ baseDir: dir, fetchImpl, env: { TBX_TOKEN: 'tk_valid' } });
    const code = await main(['auth', 'login', '--host', 'http://localhost:8080'], i);

    assert.equal(code, 0);
    assert.equal(validated.url, 'http://localhost:8080/api/kanban/items');
    assert.equal(validated.token, 'tk_valid');
    assert.deepEqual(readJsonFile(configPath(dir)), { host: 'http://localhost:8080' });
    assert.deepEqual(readJsonFile(tokensPath(dir)), { token: 'tk_valid' });
    assert.equal(statSync(tokensPath(dir)).mode & 0o777, 0o600);
  });
});

test('auth login: ungueltiger Token (401) speichert nichts', async () => {
  await withTempConfigDir(async (dir) => {
    const fetchImpl = async () => jsonResponse(401, {});
    const i = io({ baseDir: dir, fetchImpl, env: { TBX_TOKEN: 'tk_bad' } });
    const code = await main(['auth', 'login', '--host', 'http://h'], i);

    assert.equal(code, 1);
    assert.match(i.stderrLines.join(''), /ungültig|widerrufen/i);
    assert.equal(existsSync(tokensPath(dir)), false);
  });
});

test('auth login: Server-Fehler (500) speichert nichts', async () => {
  await withTempConfigDir(async (dir) => {
    const fetchImpl = async () => jsonResponse(500, {});
    const i = io({ baseDir: dir, fetchImpl, env: { TBX_TOKEN: 'tk_x' } });
    const code = await main(['auth', 'login', '--host', 'http://h'], i);
    assert.equal(code, 1);
    assert.equal(existsSync(tokensPath(dir)), false);
  });
});

// --- 8. auth status ----------------------------------------------------------

test('auth status: nicht angemeldet -> Exit 1', async () => {
  await withTempConfigDir(async (dir) => {
    const i = io({ baseDir: dir });
    assert.equal(await main(['auth', 'status'], i), 1);
    assert.match(i.stderrLines.join(''), /Nicht angemeldet/);
  });
});

test('auth status: gueltiger Token -> valid true, Exit 0', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir, 'http://h', 'tk_ok');
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(200, {}) });
    assert.equal(await main(['auth', 'status'], i), 0);
    assert.match(i.stdoutLines.join(''), /"valid": true/);
  });
});

test('auth status: widerrufener Token -> valid false, Exit 1', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir, 'http://h', 'tk_revoked');
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(401, {}) });
    assert.equal(await main(['auth', 'status'], i), 1);
    assert.match(i.stdoutLines.join(''), /"valid": false/);
  });
});

// --- 9. auth logout ----------------------------------------------------------

test('auth logout: loescht lokale Token-Datei', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const i = io({ baseDir: dir });
    assert.equal(await main(['auth', 'logout'], i), 0);
    assert.equal(existsSync(tokensPath(dir)), false);
  });
});

// --- 10. issue-Kommandos -----------------------------------------------------

const BOARD = {
  BACKLOG: [{ id: 10, number: 1, title: 'A', body: 'a', column: 'BACKLOG', position: 0 }],
  READY: [{ id: 11, number: 2, title: 'B', body: 'b', column: 'READY', position: 0 }],
};

test('issue list: flach, nach Nummer sortiert, mit Status', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(200, BOARD) });
    assert.equal(await main(['issue', 'list'], i), 0);
    const out = JSON.parse(i.stdoutLines.join(''));
    assert.deepEqual(out.map((x) => x.id), [1, 2]);
    assert.equal(out[1].status, 'ready');
  });
});

test('issue list: Status-Filter', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(200, BOARD) });
    await main(['issue', 'list', '--status', 'ready'], i);
    const out = JSON.parse(i.stdoutLines.join(''));
    assert.equal(out.length, 1);
    assert.equal(out[0].id, 2);
  });
});

test('issue list: ungueltiger Status -> Exit 1', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(200, BOARD) });
    assert.equal(await main(['issue', 'list', '--status', 'bogus'], i), 1);
  });
});

test('issue get: liefert generisches Issue inkl. Kommentaren', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const calls = [];
    const fetchImpl = async (url) => {
      calls.push(url);
      if (url.endsWith('/comments')) {
        return jsonResponse(200, [
          { author: 'Manne', body: 'Erster', createdAt: '2026-07-28T10:00:00Z' },
        ]);
      }
      return jsonResponse(200, BOARD);
    };
    const i = io({ baseDir: dir, fetchImpl });
    await main(['issue', 'get', '2'], i);
    // Board-Nummer 2 -> DB-id 11
    assert.ok(calls.some((u) => u.endsWith('/api/kanban/items/11/comments')));
    assert.deepEqual(JSON.parse(i.stdoutLines.join('')), {
      id: 2,
      title: 'B',
      body: 'b',
      status: 'ready',
      comments: [{ author: 'Manne', body: 'Erster', createdAt: '2026-07-28T10:00:00Z' }],
    });
  });
});

test('issue get: Backend ohne Kommentar-Endpoint (404) -> leere Liste, Exit 0', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const fetchImpl = async (url) =>
      url.endsWith('/comments') ? jsonResponse(404, {}) : jsonResponse(200, BOARD);
    const i = io({ baseDir: dir, fetchImpl });
    assert.equal(await main(['issue', 'get', '2'], i), 0);
    assert.deepEqual(JSON.parse(i.stdoutLines.join('')).comments, []);
  });
});

test('issue get: unerwartete Kommentar-Antwort (kein Array) -> leere Liste', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const fetchImpl = async (url) =>
      url.endsWith('/comments') ? jsonResponse(200, { unerwartet: true }) : jsonResponse(200, BOARD);
    const i = io({ baseDir: dir, fetchImpl });
    assert.equal(await main(['issue', 'get', '2'], i), 0);
    assert.deepEqual(JSON.parse(i.stdoutLines.join('')).comments, []);
  });
});

test('fetchItemComments: nicht parsebarer Body -> leere Liste', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const fetchImpl = async () => ({
      ok: true,
      status: 200,
      json: async () => {
        throw new Error('kein JSON');
      },
    });
    const comments = await fetchItemComments(11, io({ baseDir: dir, fetchImpl }));
    assert.deepEqual(comments, []);
  });
});

test('issue get: unbekannte Nummer -> Exit 1', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(200, BOARD) });
    assert.equal(await main(['issue', 'get', '99'], i), 1);
  });
});

test('issue create: POST BACKLOG, liefert Nummer + url', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir, 'http://h', 'tk_c');
    let captured;
    const fetchImpl = async (url, opts) => {
      captured = { url, opts };
      return jsonResponse(201, { number: 5 });
    };
    const i = io({ baseDir: dir, fetchImpl });
    await main(['issue', 'create', '--title', 'Neu', '--body', 'X'], i);
    assert.equal(captured.url, 'http://h/api/kanban/items');
    assert.deepEqual(JSON.parse(captured.opts.body), { title: 'Neu', body: 'X', column: 'BACKLOG' });
    assert.deepEqual(JSON.parse(i.stdoutLines.join('')), { id: 5, url: 'http://h/kanban' });
  });
});

test('issue create: ohne --title -> Exit 1', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(201, {}) });
    assert.equal(await main(['issue', 'create', '--body', 'X'], i), 1);
  });
});

test('issue move: loest DB-id auf und PUT move', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const calls = [];
    const fetchImpl = async (url, opts) => {
      calls.push({ url, opts });
      if (url.endsWith('/api/kanban/items')) return jsonResponse(200, BOARD);
      return jsonResponse(200, {});
    };
    const i = io({ baseDir: dir, fetchImpl });
    assert.equal(await main(['issue', 'move', '1', 'ready'], i), 0);
    const move = calls.find((c) => c.url.includes('/move'));
    // Board-Nummer 1 -> DB-id 10
    assert.match(move.url, /\/api\/kanban\/items\/10\/move$/);
    const body = JSON.parse(move.opts.body);
    assert.equal(body.column, 'READY');
    // Zielposition = Anzahl bestehender READY-Items (1)
    assert.equal(body.position, 1);
  });
});

test('issue comment: loest DB-id auf und POST comment', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const calls = [];
    const fetchImpl = async (url, opts) => {
      calls.push({ url, opts });
      if (url.endsWith('/api/kanban/items')) return jsonResponse(200, BOARD);
      return jsonResponse(201, {});
    };
    const i = io({ baseDir: dir, fetchImpl });
    assert.equal(await main(['issue', 'comment', '2', '--text', 'Hallo'], i), 0);
    const comment = calls.find((c) => c.url.includes('/comments'));
    assert.match(comment.url, /\/api\/kanban\/items\/11\/comments$/);
    assert.deepEqual(JSON.parse(comment.opts.body), { body: 'Hallo' });
  });
});

test('issue comment: ohne --text -> Exit 1', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(200, BOARD) });
    assert.equal(await main(['issue', 'comment', '2'], i), 1);
  });
});

test('issue list: 401 -> anmelde-spezifischer Fehler, Exit 1', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(401, {}) });
    assert.equal(await main(['issue', 'list'], i), 1);
    assert.match(i.stderrLines.join(''), /anmelden/i);
  });
});

// --- 11. Hauptmodul-Erkennung + Portabilitaet --------------------------------

test('resolveIsMainModule: false bei fehlendem argv1', () => {
  assert.equal(resolveIsMainModule(undefined, 'file:///x'), false);
});

test('Portabilitaet: tbx.mjs laeuft als isolierte Kopie (zero deps)', () => {
  // Aus einem fremden cwd (tmp) starten, damit kein Repo-Kontext mitspielt.
  const out = execFileSync('node', [TBX_PATH, '--help'], {
    cwd: tmpdir(),
    encoding: 'utf-8',
  });
  assert.match(out, /tbx —/);
});

test('Portabilitaet: tbx.mjs importiert nur node:-Builtins', () => {
  const src = readFileSync(TBX_PATH, 'utf-8');
  const imports = [...src.matchAll(/^import\s+.*?from\s+'([^']+)';/gm)].map((m) => m[1]);
  for (const spec of imports) {
    assert.ok(spec.startsWith('node:'), `Nicht-Builtin-Import gefunden: ${spec}`);
  }
});

// --- 12. Wiederholung gegen Ueberlast (Issue #1005) ---------------------------

const UEBERLAST = { type: UEBERLAST_TYPE, message: 'Zu viele Befehle' };

/** fetch, das der Reihe nach die gegebenen Antworten liefert und jeden Aufruf mitschreibt. */
function folge(...antworten) {
  const aufrufe = [];
  const impl = async (url, opts) => {
    aufrufe.push({ url, opts });
    const a = antworten[Math.min(aufrufe.length - 1, antworten.length - 1)];
    if (a instanceof Error) throw a;
    return a;
  };
  impl.aufrufe = aufrufe;
  return impl;
}

function netzfehler(code) {
  const e = new TypeError('fetch failed');
  e.cause = { code };
  return e;
}

test('Wiederholung: 429 mit Ueberlast-type wird wiederholt, auch bei POST, Schluessel bleibt gleich', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const fetchImpl = folge(
      jsonResponse(429, UEBERLAST, { 'retry-after': '2' }),
      jsonResponse(201, { number: 7 }),
    );
    const i = io({ baseDir: dir, fetchImpl });
    assert.equal(await main(['issue', 'create', '--title', 'T'], i), 0);
    assert.equal(fetchImpl.aufrufe.length, 2);
    const [k1, k2] = fetchImpl.aufrufe.map((a) => a.opts.headers['Idempotency-Key']);
    assert.ok(k1);
    assert.equal(k1, k2);
    assert.deepEqual(i.uhr.schlaefe, [2000]);
    assert.match(i.stderrLines.join(''), /POST \/api\/kanban\/items — Versuch 1 endete mit HTTP 429/);
  });
});

test('Wiederholung: 429 ohne Ueberlast-type wird nicht wiederholt', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const fetchImpl = folge(jsonResponse(429, { message: 'fremde Bremse' }), jsonResponse(201, { number: 7 }));
    const i = io({ baseDir: dir, fetchImpl });
    assert.equal(await main(['issue', 'create', '--title', 'T'], i), 1);
    assert.equal(fetchImpl.aufrufe.length, 1);
    assert.match(i.stderrLines.join(''), /Fehler: fremde Bremse/);
  });
});

for (const method of ['GET', 'PUT', 'DELETE']) {
  test(`Wiederholung: 5xx bei ${method} wird wiederholt`, async () => {
    const fetchImpl = folge(jsonResponse(502, {}), jsonResponse(200, { ok: true }));
    const res = await tokenFetch('http://h', 'tk', '/p', { method }, fetchImpl, { uhr: fakeUhr() });
    assert.equal(res.status, 200);
    assert.equal(fetchImpl.aufrufe.length, 2);
  });
}

test('Wiederholung: 5xx bei POST mit Schluessel wird wiederholt', async () => {
  const fetchImpl = folge(jsonResponse(503, {}), jsonResponse(201, {}));
  const res = await tokenFetch('http://h', 'tk', '/p', { method: 'POST', idempotencyKey: 'k1' }, fetchImpl, {
    uhr: fakeUhr(),
  });
  assert.equal(res.status, 201);
  assert.deepEqual(fetchImpl.aufrufe.map((a) => a.opts.headers['Idempotency-Key']), ['k1', 'k1']);
});

test('Wiederholung: 5xx bei POST ohne Schluessel wird nicht wiederholt, Ausgang unklar ohne Schluessel', async () => {
  const fetchImpl = folge(jsonResponse(502, {}), jsonResponse(201, {}));
  await assert.rejects(
    () => tokenFetch('http://h', 'tk', '/labels', { method: 'POST' }, fetchImpl, { uhr: fakeUhr() }),
    (e) =>
      e instanceof CliError
      && e.rueckmeldung === RUECKMELDUNG.AUSGANG_UNKLAR
      && /ohne Idempotenz-Schluessel/.test(e.message)
      && /Erst am Board nachsehen/.test(e.message),
  );
  assert.equal(fetchImpl.aufrufe.length, 1);
});

for (const status of [403, 404, 409, 401]) {
  test(`Wiederholung: ${status} wird sofort zurueckgegeben`, async () => {
    const fetchImpl = folge(jsonResponse(status, { type: UEBERLAST_TYPE }), jsonResponse(200, {}));
    const res = await tokenFetch('http://h', 'tk', '/p', { method: 'PUT' }, fetchImpl, { uhr: fakeUhr() });
    assert.equal(res.status, status);
    assert.equal(fetchImpl.aufrufe.length, 1);
  });
}

test('Wiederholung: 401 bleibt beim Befehl die bisherige Anmelde-Meldung', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const fetchImpl = folge(jsonResponse(401, {}));
    const i = io({ baseDir: dir, fetchImpl });
    assert.equal(await main(['issue', 'list'], i), 1);
    assert.equal(fetchImpl.aufrufe.length, 1);
    assert.match(i.stderrLines.join(''), /Token ungültig oder widerrufen\. Bitte neu anmelden: tbx auth login/);
  });
});

test('Schluessel: --idempotency-key erreicht den Aufruf unveraendert (create und comment)', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const createFetch = folge(jsonResponse(201, { number: 3 }));
    assert.equal(
      await main(['issue', 'create', '--title', 'T', '--idempotency-key', 'mein-schluessel'], io({ baseDir: dir, fetchImpl: createFetch })),
      0,
    );
    assert.equal(createFetch.aufrufe[0].opts.headers['Idempotency-Key'], 'mein-schluessel');

    const commentFetch = folge(jsonResponse(200, BOARD), jsonResponse(201, {}));
    assert.equal(
      await main(['issue', 'comment', '2', '--text', 'x', '--idempotency-key', 'k-2'], io({ baseDir: dir, fetchImpl: commentFetch })),
      0,
    );
    assert.equal(commentFetch.aufrufe[1].opts.headers['Idempotency-Key'], 'k-2');
    assert.equal(commentFetch.aufrufe[0].opts.headers['Idempotency-Key'], undefined);
  });
});

test('Schluessel: ohne Schalter erzeugt comment einen eigenen, nackter Schalter wird abgewiesen', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const fetchImpl = folge(jsonResponse(200, BOARD), jsonResponse(201, {}));
    assert.equal(await main(['issue', 'comment', '2', '--text', 'x'], io({ baseDir: dir, fetchImpl })), 0);
    assert.match(fetchImpl.aufrufe[1].opts.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/);

    const nackt = io({ baseDir: dir, fetchImpl: folge(jsonResponse(201, {})) });
    assert.equal(await main(['issue', 'create', '--title', 'T', '--idempotency-key'], nackt), 1);
    assert.match(nackt.stderrLines.join(''), /--idempotency-key erwartet einen Wert/);
  });
});

test('Ausgang unklar: Meldung nennt Schluessel und vollstaendiges Wiederholkommando', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const fetchImpl = folge(jsonResponse(503, { message: 'kaputt' }));
    const i = io({ baseDir: dir, fetchImpl });
    assert.equal(await main(['issue', 'create', '--title', 'Mein Titel', '--idempotency-key', 'k-9'], i), 1);
    const err = i.stderrLines.join('');
    assert.match(err, /Ausgang unklar: POST \/api\/kanban\/items/);
    assert.match(err, /Schluessel: k-9\./);
    assert.ok(err.includes("tbx issue create --title 'Mein Titel' --idempotency-key k-9"), err);
    assert.ok(fetchImpl.aufrufe.length > 1);
  });
});

test('Rueckmeldungen: ausgefuehrt, nicht ausgefuehrt, Ausgang unklar an je einem erzeugten Fall', async () => {
  assert.equal(rueckmeldungFuer({ ok: true }), RUECKMELDUNG.AUSGEFUEHRT);
  const ok = await tokenFetch('http://h', 'tk', '/p', { method: 'POST' }, folge(jsonResponse(201, {})), {
    uhr: fakeUhr(),
  });
  assert.equal(rueckmeldungFuer({ ok: ok.ok, status: ok.status, method: 'POST' }), RUECKMELDUNG.AUSGEFUEHRT);

  await assert.rejects(
    () => tokenFetch('http://h', 'tk', '/p', { method: 'POST' }, folge(netzfehler('ECONNREFUSED')), { uhr: fakeUhr() }),
    (e) => e instanceof CliError && e.rueckmeldung === RUECKMELDUNG.NICHT_AUSGEFUEHRT && /nicht erreichbar/.test(e.message),
  );

  await assert.rejects(
    () =>
      tokenFetch('http://h', 'tk', '/p', { method: 'POST', idempotencyKey: 'k' }, folge(netzfehler('ECONNRESET')), {
        uhr: fakeUhr(),
        argv: ['issue', 'comment', '2', '--text', 'x'],
      }),
    (e) =>
      e.rueckmeldung === RUECKMELDUNG.AUSGANG_UNKLAR
      && e.message.includes('tbx issue comment 2 --text x --idempotency-key k'),
  );
});

test('Rueckmeldungen: lesender Abbruch nach Budget ist nicht ausgefuehrt', async () => {
  await assert.rejects(
    () => tokenFetch('http://h', 'tk', '/p', {}, folge(netzfehler('ECONNRESET')), { uhr: fakeUhr() }),
    (e) => e.rueckmeldung === RUECKMELDUNG.NICHT_AUSGEFUEHRT,
  );
});

test('Budget: fest 30 Sekunden, auch mit gesetztem KIT_AGENT_MODEL', async () => {
  const vorher = process.env.KIT_AGENT_MODEL;
  process.env.KIT_AGENT_MODEL = 'claude-opus-5';
  try {
    assert.equal(BUDGET_MS, 30_000);
    const uhr = fakeUhr();
    const fetchImpl = folge(jsonResponse(429, UEBERLAST));
    const res = await tokenFetch('http://h', 'tk', '/p', {}, fetchImpl, { uhr });
    assert.equal(res.status, 429);
    assert.ok(uhr.zeit() <= 30_000, `gewartet: ${uhr.zeit()}`);
    assert.ok(uhr.zeit() > 20_000, `gewartet: ${uhr.zeit()}`);
    assert.equal(fetchImpl.aufrufe.length, uhr.schlaefe.length + 1);
  } finally {
    if (vorher === undefined) delete process.env.KIT_AGENT_MODEL;
    else process.env.KIT_AGENT_MODEL = vorher;
  }
});

test('Zeitgrenze: jeder Versuch traegt ein eigenes Abbruchsignal', async () => {
  const fetchImpl = folge(jsonResponse(200, {}));
  await tokenFetch('http://h', 'tk', '/p', {}, fetchImpl, { uhr: fakeUhr() });
  assert.ok(fetchImpl.aufrufe[0].opts.signal instanceof AbortSignal);
});

test('Zeitablauf eines Versuchs wird wiederholt', async () => {
  const zeitablauf = new DOMException('timed out', 'TimeoutError');
  const fetchImpl = folge(zeitablauf, jsonResponse(200, {}));
  const res = await tokenFetch('http://h', 'tk', '/p', {}, fetchImpl, { uhr: fakeUhr() });
  assert.equal(res.status, 200);
});

test('Ohne injizierte Uhr: Standard-Meldespur und echtes Schlafen', async () => {
  const geschrieben = [];
  const original = process.stderr.write;
  process.stderr.write = (s) => geschrieben.push(s);
  try {
    const fetchImpl = folge(jsonResponse(429, UEBERLAST, { 'retry-after': '0' }), jsonResponse(200, {}));
    const res = await tokenFetch('http://h', 'tk', '/p', {}, fetchImpl);
    assert.equal(res.status, 200);
  } finally {
    process.stderr.write = original;
  }
  assert.match(geschrieben.join(''), /Versuch 1 endete mit HTTP 429: Zu viele Befehle, erneut in 100 ms/);
});

test('Regeln: darfWiederholen, wartezeitMs, netzfehlerArt', () => {
  assert.equal(darfWiederholen({ method: 'GET', netz: 'endgueltig' }), false);
  assert.equal(darfWiederholen({ method: 'POST', netz: 'abbruch' }), true);
  assert.equal(darfWiederholen({ method: 'GET', status: 400 }), false);
  assert.equal(darfWiederholen({}), false);
  assert.equal(darfWiederholen({ status: 500 }), true);

  assert.equal(wartezeitMs(1, null, () => 0), 500);
  assert.equal(wartezeitMs(2, null, () => 0), 1000);
  assert.equal(wartezeitMs(10, null, () => 0), 8000);
  assert.equal(wartezeitMs(1, null, () => 1), 625);
  assert.equal(wartezeitMs(1, 3, () => 0), 3000);
  assert.equal(wartezeitMs(1, 0, () => 0), 100);
  assert.equal(wartezeitMs(1, 'abc', () => 0), 500);
  assert.equal(wartezeitMs(1, -1, () => 0), 500);
  assert.ok(wartezeitMs(1) >= 500);

  assert.equal(netzfehlerArt({ name: 'AbortError' }), 'zeitablauf');
  assert.equal(netzfehlerArt({ code: 'ENOTFOUND' }), 'endgueltig');
  assert.equal(netzfehlerArt(netzfehler('ECONNRESET')), 'abbruch');
  assert.equal(netzfehlerArt(undefined), 'abbruch');

  assert.equal(rueckmeldungFuer({ method: 'POST', status: 409 }), RUECKMELDUNG.NICHT_AUSGEFUEHRT);
  assert.equal(rueckmeldungFuer({ method: undefined }), RUECKMELDUNG.NICHT_AUSGEFUEHRT);
  assert.equal(rueckmeldungFuer({}), RUECKMELDUNG.NICHT_AUSGEFUEHRT);
});

test('wiederholKommando: ersetzt einen vorhandenen Schluessel und zitiert nur, wo noetig', () => {
  assert.equal(
    wiederholKommando('neu', ['issue', 'comment', '2', '--idempotency-key', 'alt', '--text', "it's"]),
    "tbx issue comment 2 --text 'it'\\''s' --idempotency-key neu",
  );
  assert.equal(wiederholKommando('k'), 'tbx --idempotency-key k');
});

test('Fehlerlage: Antwort ohne JSON und ohne Retry-After', async () => {
  const ohneJson = {
    ok: false,
    status: 503,
    json: async () => {
      throw new SyntaxError('kein JSON');
    },
  };
  const fetchImpl = folge(ohneJson, jsonResponse(200, {}));
  const zeilen = [];
  const res = await tokenFetch('http://h', 'tk', '/p', {}, fetchImpl, { uhr: fakeUhr(zeilen) });
  assert.equal(res.status, 200);
  assert.match(zeilen[0], /Versuch 1 endete mit HTTP 503, erneut in 500 ms \(Frist 30 s\)/);
});

test('Fehlerlage: der Rumpf bleibt fuer den Aufrufer lesbar (clone)', async () => {
  let gelesen = 0;
  const antwort = {
    ok: false,
    status: 404,
    json: async () => {
      gelesen++;
      return { message: 'weg' };
    },
    clone() {
      return { json: async () => ({ message: 'weg' }) };
    },
  };
  const res = await tokenFetch('http://h', 'tk', '/p', {}, folge(antwort), { uhr: fakeUhr() });
  assert.equal(gelesen, 0);
  assert.deepEqual(await res.json(), { message: 'weg' });
});
