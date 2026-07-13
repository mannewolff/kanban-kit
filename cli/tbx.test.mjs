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
  parseIssueNumber,
  CliError,
  resolveIsMainModule,
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

function jsonResponse(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
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

test('issue get: liefert generisches Issue', async () => {
  await withTempConfigDir(async (dir) => {
    seedLogin(dir);
    const i = io({ baseDir: dir, fetchImpl: async () => jsonResponse(200, BOARD) });
    await main(['issue', 'get', '2'], i);
    assert.deepEqual(JSON.parse(i.stdoutLines.join('')), {
      id: 2,
      title: 'B',
      body: 'b',
      status: 'ready',
    });
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
