import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, symlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  kartenTitel,
  kartenBody,
  stellenAusBody,
  ergaenzen,
  ueberfaellig,
  offeneKarten,
  anlagedatum,
  liegezeit,
  schreiben,
  zugangLesen,
  ausfuehren,
} from './mutationskarten.mjs';

const SKRIPT = join(dirname(fileURLToPath(import.meta.url)), 'mutationskarten.mjs');
const TAG_MS = 24 * 60 * 60 * 1000;

const stelle = (zeile, mutator = 'ConditionalExpression', ersetzung = 'false', datei = 'src/a.ts') => ({
  datei, zeile, mutator, ersetzung,
});

// --- reine Funktionen -------------------------------------------------------------------

test('kartenTitel: genau "Mutations-Überlebende in <Pfad>"', () => {
  assert.equal(kartenTitel('frontend/src/lib/boardOps.ts'), 'Mutations-Überlebende in frontend/src/lib/boardOps.ts');
});

test('kartenBody: Vier-Abschnitt-Format mit der Stellenliste unter ## Aufgabe', () => {
  const body = kartenBody({ pfad: 'src/a.ts', stellen: [stelle(12), stelle(3, 'StringLiteral', '""')], herkunft: 'Änderungsprüfung frontend' });
  const abschnitte = [...body.matchAll(/^## (.+)$/gm)].map((m) => m[1]);
  assert.deepEqual(abschnitte, ['Kontext', 'Aufgabe', 'Akzeptanzkriterium', 'Abhängigkeiten']);
  const aufgabe = body.slice(body.indexOf('## Aufgabe'), body.indexOf('## Akzeptanzkriterium'));
  assert.match(aufgabe, /- `src\/a\.ts` Zeile 3, StringLiteral: ""/);
  assert.match(aufgabe, /- `src\/a\.ts` Zeile 12, ConditionalExpression: false/);
  assert.ok(aufgabe.indexOf('Zeile 3,') < aufgabe.indexOf('Zeile 12,'), 'nach Zeile sortiert');
  assert.match(body, /Änderungsprüfung frontend/);
  assert.match(body, /`src\/a\.ts`/);
});

test('kartenBody: mehrzeilige Ersetzung wird auf eine Zeile gefaltet', () => {
  const body = kartenBody({ pfad: 'src/a.ts', stellen: [stelle(1, 'BlockStatement', '{\n  x();\n}')], herkunft: 'h' });
  assert.match(body, /Zeile 1, BlockStatement: \{ x\(\); \}$/m);
});

test('stellenAusBody liest die Stellen, die kartenBody schreibt', () => {
  const stellen = [stelle(12), stelle(3, 'StringLiteral', '""'), stelle(7, 'BlockStatement', '{}')];
  const body = kartenBody({ pfad: 'src/a.ts', stellen, herkunft: 'h' });
  const gelesen = stellenAusBody(body);
  assert.deepEqual(
    gelesen.map((s) => [s.datei, s.zeile, s.mutator, s.ersetzung]),
    [['src/a.ts', 3, 'StringLiteral', '""'], ['src/a.ts', 7, 'BlockStatement', '{}'], ['src/a.ts', 12, 'ConditionalExpression', 'false']],
  );
});

test('stellenAusBody: leerer oder fremder Body ergibt keine Stellen', () => {
  assert.deepEqual(stellenAusBody(''), []);
  assert.deepEqual(stellenAusBody(null), []);
  assert.deepEqual(stellenAusBody('## Aufgabe\n- irgendwas anderes'), []);
});

test('ergaenzen haengt nur neue Stellen an, ohne Dubletten', () => {
  const body = kartenBody({ pfad: 'src/a.ts', stellen: [stelle(12), stelle(3)], herkunft: 'h' });
  const { body: neu, neue } = ergaenzen(body, [stelle(12), stelle(3), stelle(12, 'EqualityOperator', 'a !== b'), stelle(12)]);
  assert.equal(neue.length, 1);
  assert.equal(neue[0].mutator, 'EqualityOperator');
  const gelesen = stellenAusBody(neu);
  assert.equal(gelesen.length, 3);
  assert.equal(new Set(gelesen.map((s) => `${s.zeile}|${s.mutator}|${s.ersetzung}`)).size, 3);
  // Die neue Stelle steht in der Liste unter ## Aufgabe, nicht hinter den folgenden Abschnitten.
  const aufgabe = neu.slice(neu.indexOf('## Aufgabe'), neu.indexOf('## Akzeptanzkriterium'));
  assert.match(aufgabe, /EqualityOperator: a !== b/);
});

test('ergaenzen: ohne neue Stellen bleibt der Body unveraendert', () => {
  const body = kartenBody({ pfad: 'src/a.ts', stellen: [stelle(12)], herkunft: 'h' });
  const ergebnis = ergaenzen(body, [stelle(12)]);
  assert.equal(ergebnis.body, body);
  assert.deepEqual(ergebnis.neue, []);
});

test('ergaenzen: gleiche Stelle mit anderer Ersetzung ist eine neue Stelle', () => {
  const body = kartenBody({ pfad: 'src/a.ts', stellen: [stelle(12, 'StringLiteral', '""')], herkunft: 'h' });
  assert.equal(ergaenzen(body, [stelle(12, 'StringLiteral', '"x"')]).neue.length, 1);
});

test('ergaenzen: ein Body ohne ## Aufgabe bekommt die Stellen angehaengt', () => {
  const { body, neue } = ergaenzen('von Hand umgeschrieben', [stelle(5)]);
  assert.equal(neue.length, 1);
  assert.equal(stellenAusBody(body).length, 1);
  assert.ok(body.startsWith('von Hand umgeschrieben'));
});

test('ueberfaellig: genau 7 Tage ist nicht ueberfaellig, 7 Tage plus eine Sekunde schon', () => {
  const angelegt = '2026-10-01T00:00:00Z';
  const t0 = Date.parse(angelegt);
  assert.equal(ueberfaellig({ angelegt, jetzt: new Date(t0 + 7 * TAG_MS), tage: 7 }), false);
  assert.equal(ueberfaellig({ angelegt, jetzt: new Date(t0 + 7 * TAG_MS + 1000), tage: 7 }), true);
  assert.equal(ueberfaellig({ angelegt, jetzt: new Date(t0 + TAG_MS), tage: 7 }), false);
});

// --- Board-Teil gegen ein Fake-fetch --------------------------------------------------

const HOST = 'https://board.test';
const antwort = (status, body) => new Response(body === undefined ? null : JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});

function karte(id, number, title, column = 'BACKLOG', extra = {}) {
  return { id, number, title, body: '', column, position: 0, type: 'card', labels: [], ...extra };
}

/** Fake-Board: beantwortet GET /items, GET /activity, POST und PUT und schreibt jeden Aufruf mit. */
function fakeBoard({ spalten = {}, aktivitaet = {}, status = {} } = {}) {
  const aufrufe = [];
  let naechsteNummer = 900;
  const fetchImpl = async (url, opts = {}) => {
    const pfad = url.slice(HOST.length);
    const method = (opts.method || 'GET').toUpperCase();
    aufrufe.push({ method, pfad, headers: opts.headers, body: opts.body ? JSON.parse(opts.body) : undefined });
    const fest = status[`${method} ${pfad}`];
    if (fest) return antwort(fest, { message: 'nein' });
    if (method === 'GET' && pfad === '/api/kanban/items') return antwort(200, spalten);
    const akt = pfad.match(/^\/api\/kanban\/items\/(\d+)\/activity$/);
    if (method === 'GET' && akt) return antwort(200, aktivitaet[akt[1]] ?? []);
    if (method === 'POST' && pfad === '/api/kanban/items') {
      naechsteNummer += 1;
      return antwort(201, { id: naechsteNummer * 10, number: naechsteNummer, created: true });
    }
    if (method === 'PUT' && /^\/api\/kanban\/items\/\d+$/.test(pfad)) return antwort(200, {});
    return antwort(404, { message: 'unbekannt' });
  };
  return { fetchImpl, aufrufe };
}

const zugang = (fetchImpl) => ({ host: HOST, token: 'tk_test', fetchImpl });

test('offeneKarten: liest alle Spalten ausser DONE, nur Arbeitspakete mit passendem Titel', async () => {
  const titel = kartenTitel('src/a.ts');
  const { fetchImpl, aufrufe } = fakeBoard({
    spalten: {
      BACKLOG: [karte(1, 11, titel), karte(2, 12, 'Etwas anderes')],
      IN_REVIEW: [karte(3, 13, kartenTitel('src/b.ts'))],
      DONE: [karte(4, 14, kartenTitel('src/c.ts'))],
      SONDERSPALTE: [karte(5, 15, titel, 'SONDERSPALTE', { type: 'epic' })],
    },
  });
  const karten = await offeneKarten(zugang(fetchImpl));
  assert.deepEqual(karten.map((k) => k.number).sort(), [11, 13]);
  assert.equal(aufrufe[0].headers['X-Kanban-Token'], 'tk_test');
});

test('anlagedatum: der CREATED-Eintrag des Verlaufs', async () => {
  const { fetchImpl } = fakeBoard({
    aktivitaet: { 7: [
      { type: 'MOVED', createdAt: '2026-10-03T10:00:00Z' },
      { type: 'CREATED', createdAt: '2026-10-01T09:00:00Z' },
    ] },
  });
  assert.equal(await anlagedatum(zugang(fetchImpl), 7), '2026-10-01T09:00:00Z');
});

test('anlagedatum: ohne CREATED-Eintrag ein Fehler', async () => {
  const { fetchImpl } = fakeBoard({ aktivitaet: { 7: [{ type: 'MOVED', createdAt: '2026-10-03T10:00:00Z' }] } });
  await assert.rejects(anlagedatum(zugang(fetchImpl), 7), /CREATED/);
});

test('liegezeit: liefert nur offene Karten, die laenger als 7 Tage liegen', async () => {
  const { fetchImpl } = fakeBoard({
    spalten: {
      BACKLOG: [karte(1, 11, kartenTitel('src/a.ts')), karte(2, 12, kartenTitel('src/b.ts')), karte(5, 15, 'fremd')],
      DONE: [karte(3, 13, kartenTitel('src/c.ts'))],
    },
    aktivitaet: {
      1: [{ type: 'CREATED', createdAt: '2026-09-01T00:00:00Z' }],
      2: [{ type: 'CREATED', createdAt: '2026-10-06T00:00:00Z' }],
      3: [{ type: 'CREATED', createdAt: '2026-01-01T00:00:00Z' }],
    },
  });
  const ergebnis = await liegezeit({ ...zugang(fetchImpl), jetzt: new Date('2026-10-07T00:00:00Z') });
  assert.deepEqual(ergebnis, {
    ueberfaellig: [{ nummer: 11, titel: kartenTitel('src/a.ts'), angelegt: '2026-09-01T00:00:00Z' }],
  });
});

test('schreiben: legt je Datei ohne offene Karte eine neue in BACKLOG an, mit zufaelligem Idempotency-Key', async () => {
  const { fetchImpl, aufrufe } = fakeBoard({ spalten: { BACKLOG: [] } });
  const ergebnis = await schreiben({
    ...zugang(fetchImpl),
    herkunft: 'Änderungsprüfung backend',
    stellen: [stelle(3, 'M', 'x', 'src/a.ts'), stelle(4, 'M', 'y', 'src/b.ts'), stelle(5, 'M', 'z', 'src/a.ts')],
  });
  const posts = aufrufe.filter((a) => a.method === 'POST');
  assert.equal(posts.length, 2);
  for (const post of posts) {
    assert.equal(post.body.column, 'BACKLOG');
    assert.match(post.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/);
  }
  assert.notEqual(posts[0].headers['Idempotency-Key'], posts[1].headers['Idempotency-Key']);
  const a = posts.find((p) => p.body.title === kartenTitel('src/a.ts'));
  assert.equal(stellenAusBody(a.body.body).length, 2);
  assert.deepEqual(ergebnis, { karten: [
    { pfad: 'src/a.ts', art: 'neu', nummer: 901, neueStellen: 2 },
    { pfad: 'src/b.ts', art: 'neu', nummer: 902, neueStellen: 1 },
  ] });
});

test('schreiben: ergaenzt eine offene Karte per PUT mit unveraendertem Titel und nur neuen Stellen', async () => {
  const titel = kartenTitel('src/a.ts');
  const alterBody = kartenBody({ pfad: 'src/a.ts', stellen: [stelle(3)], herkunft: 'frueher' });
  const { fetchImpl, aufrufe } = fakeBoard({
    spalten: { IN_PROGRESS: [karte(40, 77, titel, 'IN_PROGRESS', { body: alterBody })] },
  });
  const ergebnis = await schreiben({ ...zugang(fetchImpl), herkunft: 'jetzt', stellen: [stelle(3), stelle(9)] });
  const puts = aufrufe.filter((a) => a.method === 'PUT');
  assert.equal(puts.length, 1);
  assert.equal(puts[0].pfad, '/api/kanban/items/40');
  assert.equal(puts[0].body.title, titel);
  assert.deepEqual(stellenAusBody(puts[0].body.body).map((s) => s.zeile), [3, 9]);
  assert.ok(puts[0].body.body.startsWith(alterBody.split('\n## Aufgabe')[0]), 'Kontext bleibt');
  assert.equal(aufrufe.filter((a) => a.method === 'POST').length, 0);
  assert.deepEqual(ergebnis, { karten: [{ pfad: 'src/a.ts', art: 'ergaenzt', nummer: 77, neueStellen: 1 }] });
});

test('schreiben: eine offene Karte ohne neue Stellen wird nicht beschrieben', async () => {
  const titel = kartenTitel('src/a.ts');
  const body = kartenBody({ pfad: 'src/a.ts', stellen: [stelle(3)], herkunft: 'h' });
  const { fetchImpl, aufrufe } = fakeBoard({ spalten: { BACKLOG: [karte(40, 77, titel, 'BACKLOG', { body })] } });
  const ergebnis = await schreiben({ ...zugang(fetchImpl), herkunft: 'h', stellen: [stelle(3)] });
  assert.equal(aufrufe.filter((a) => a.method !== 'GET').length, 0);
  assert.deepEqual(ergebnis, { karten: [{ pfad: 'src/a.ts', art: 'ergaenzt', nummer: 77, neueStellen: 0 }] });
});

test('schreiben: eine Karte in DONE zaehlt nicht als offen — es entsteht eine neue', async () => {
  const { fetchImpl, aufrufe } = fakeBoard({ spalten: { DONE: [karte(40, 77, kartenTitel('src/a.ts'), 'DONE')] } });
  const ergebnis = await schreiben({ ...zugang(fetchImpl), herkunft: 'h', stellen: [stelle(3)] });
  assert.equal(aufrufe.filter((a) => a.method === 'POST').length, 1);
  assert.equal(ergebnis.karten[0].art, 'neu');
});

test('schreiben: ein Vorhaben mit gleichem Titel zaehlt nicht — es entsteht eine neue Karte', async () => {
  const { fetchImpl, aufrufe } = fakeBoard({
    spalten: { BACKLOG: [karte(40, 77, kartenTitel('src/a.ts'), 'BACKLOG', { type: 'epic' })] },
  });
  const ergebnis = await schreiben({ ...zugang(fetchImpl), herkunft: 'h', stellen: [stelle(3)] });
  assert.equal(aufrufe.filter((a) => a.method === 'PUT').length, 0);
  assert.equal(ergebnis.karten[0].art, 'neu');
});

test('Board-Fehler werden geworfen und nennen den Status', async () => {
  const { fetchImpl } = fakeBoard({ status: { 'GET /api/kanban/items': 401 } });
  await assert.rejects(offeneKarten(zugang(fetchImpl)), /401/);
  const zweit = fakeBoard({ spalten: { BACKLOG: [] }, status: { 'POST /api/kanban/items': 400 } });
  await assert.rejects(schreiben({ ...zugang(zweit.fetchImpl), herkunft: 'h', stellen: [stelle(3)] }), /400/);
});

// --- Zugang und Auftraege -------------------------------------------------------------

function loginVerzeichnis({ host, token } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'mutationskarten-'));
  if (host) writeFileSync(join(dir, 'config.json'), JSON.stringify({ host }));
  if (token) writeFileSync(join(dir, 'tokens.json'), JSON.stringify({ token }));
  return dir;
}

function projektVerzeichnis({ toolbox, tokenDatei } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'mutationskarten-projekt-'));
  if (toolbox) {
    mkdirSync(join(dir, '.claude'), { recursive: true });
    writeFileSync(join(dir, '.claude', 'workflow.config.json'), JSON.stringify({ toolbox }));
  }
  if (tokenDatei !== undefined) {
    mkdirSync(join(dir, '.claude', 'toolbox.cli'), { recursive: true });
    writeFileSync(join(dir, '.claude', 'toolbox.cli', 'tbx.token'), tokenDatei);
  }
  return dir;
}

const MIT_TOKENDATEI = { host: 'https://projekt.test', tokenFile: '.claude/toolbox.cli/tbx.token' };

test('zugangLesen: TBX_TOKEN vor dem tbx-Login, Host aus dessen config.json', () => {
  const dir = loginVerzeichnis({ host: 'https://eigen.test', token: 'tk_login' });
  const projekt = projektVerzeichnis();
  try {
    assert.deepEqual(zugangLesen({ env: { TBX_TOKEN: ' tk_env ' }, baseDir: dir, projektDir: projekt }), { host: 'https://eigen.test', token: 'tk_env' });
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: projekt }), { host: 'https://eigen.test', token: 'tk_login' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('zugangLesen: TBX_TOKEN gewinnt auch vor toolbox.tokenFile, Host aus toolbox.host', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const projekt = projektVerzeichnis({ toolbox: MIT_TOKENDATEI, tokenDatei: 'tk_projekt\n' });
  try {
    assert.deepEqual(zugangLesen({ env: { TBX_TOKEN: 'tk_env' }, baseDir: dir, projektDir: projekt }), { host: 'https://projekt.test', token: 'tk_env' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('zugangLesen: ohne TBX_TOKEN gewinnt toolbox.tokenFile vor dem globalen Login', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const projekt = projektVerzeichnis({ toolbox: MIT_TOKENDATEI, tokenDatei: '  tk_projekt\n' });
  try {
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: projekt }), { host: 'https://projekt.test', token: 'tk_projekt' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('zugangLesen: ohne toolbox.host kommt der Host aus dem tbx-Login', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const projekt = projektVerzeichnis({ toolbox: { tokenFile: '.claude/toolbox.cli/tbx.token' }, tokenDatei: 'tk_projekt' });
  try {
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: projekt }), { host: 'https://login.test', token: 'tk_projekt' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('zugangLesen: leere oder fehlende toolbox.tokenFile ist ein Fehler, kein Rueckfall', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const leer = projektVerzeichnis({ toolbox: MIT_TOKENDATEI, tokenDatei: '  \n' });
  const fehlt = projektVerzeichnis({ toolbox: MIT_TOKENDATEI });
  try {
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: leer }), /toolbox\.tokenFile.*leer/s);
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: fehlt }), /toolbox\.tokenFile.*nicht lesbar/s);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(leer, { recursive: true, force: true });
    rmSync(fehlt, { recursive: true, force: true });
  }
});

/**
 * Hauptkopie mit eingecheckter Config und ein verknuepfter Worktree; Token-Dateien wahlweise an beiden Orten.
 * `worktreeHost` aendert `toolbox.host` in der Config des Worktrees (uncommittet, wie ein gepruefter Stand),
 * `worktreeTokenFile` ebenso `toolbox.tokenFile`.
 */
function worktreePaar({ hauptToken, worktreeToken, worktreeHost, worktreeTokenFile } = {}) {
  const wurzel = mkdtempSync(join(tmpdir(), 'mutationskarten-worktree-'));
  const haupt = join(wurzel, 'haupt');
  const worktree = join(wurzel, 'worktree');
  const git = (cwd, ...args) => {
    const lauf = spawnSync('git', ['-c', 'user.name=Test', '-c', 'user.email=test@example.org', '-c', 'commit.gpgsign=false', ...args], { cwd, encoding: 'utf-8' });
    assert.equal(lauf.status, 0, lauf.stderr);
  };
  mkdirSync(join(haupt, '.claude'), { recursive: true });
  writeFileSync(join(haupt, '.claude', 'workflow.config.json'), JSON.stringify({ toolbox: MIT_TOKENDATEI }));
  git(haupt, 'init', '-q');
  git(haupt, 'add', '.claude/workflow.config.json');
  git(haupt, 'commit', '-q', '-m', 'Config');
  git(haupt, 'worktree', 'add', '-q', '--detach', worktree);
  for (const [dir, token] of [[haupt, hauptToken], [worktree, worktreeToken]]) {
    if (token === undefined) continue;
    mkdirSync(join(dir, '.claude', 'toolbox.cli'), { recursive: true });
    writeFileSync(join(dir, '.claude', 'toolbox.cli', 'tbx.token'), token);
  }
  if (worktreeHost !== undefined || worktreeTokenFile !== undefined) {
    const toolbox = { ...MIT_TOKENDATEI, ...(worktreeHost !== undefined && { host: worktreeHost }), ...(worktreeTokenFile !== undefined && { tokenFile: worktreeTokenFile }) };
    writeFileSync(join(worktree, '.claude', 'workflow.config.json'), JSON.stringify({ toolbox }));
  }
  return { wurzel, haupt, worktree };
}

test('zugangLesen: im verknuepften Worktree ohne Token-Datei kommt das Token aus der Hauptkopie', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt\n' });
  try {
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), { host: 'https://projekt.test', token: 'tk_haupt' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: die eigene Token-Datei des Worktrees gewinnt vor der Hauptkopie', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeToken: 'tk_worktree' });
  try {
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), { host: 'https://projekt.test', token: 'tk_worktree' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: fehlt die Token-Datei auch in der Hauptkopie, bleibt es ein Fehler, kein Rueckfall', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar();
  try {
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), /toolbox\.tokenFile.*nicht lesbar/s);
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: paar.haupt }), /toolbox\.tokenFile.*nicht lesbar/s);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: Token der Hauptkopie bei gleichem Host, Host der Hauptkopie (Issue #2054)', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeHost: 'https://projekt.test' });
  try {
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), { host: 'https://projekt.test', token: 'tk_haupt' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: Token der Hauptkopie und fremder toolbox.host im Worktree sind ein Fehler mit beiden Hosts (Issue #2054)', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeHost: 'https://fremd.test' });
  try {
    assert.throws(
      () => zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }),
      (e) => e instanceof Error && e.message.includes('https://fremd.test') && e.message.includes('https://projekt.test'),
    );
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: Token der Hauptkopie und fehlender toolbox.host im Worktree sind ebenfalls ein Fehler (Issue #2054)', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeHost: '' });
  try {
    assert.throws(
      () => zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }),
      (e) => e.message.includes('https://login.test') && e.message.includes('https://projekt.test'),
    );
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: eigene Token-Datei des Worktrees bindet nicht an den Host der Hauptkopie (Issue #2054)', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeToken: 'tk_worktree', worktreeHost: 'https://fremd.test' });
  try {
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), { host: 'https://fremd.test', token: 'tk_worktree' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('ausfuehren: fremder toolbox.host im Worktree ohne eigene Token-Datei endet mit fehler, ohne Aufruf (Issue #2054)', async () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeHost: 'https://fremd.test' });
  let aufrufe = 0;
  const fetchImpl = async () => { aufrufe += 1; return new Response('[]', { status: 200 }); };
  try {
    const lauf = await ausfuehren('{"auftrag":"liegezeit"}', { env: {}, baseDir: dir, projektDir: paar.worktree, fetchImpl });
    assert.equal(lauf.code, 1);
    assert.match(lauf.ausgabe.fehler, /https:\/\/fremd\.test/);
    assert.match(lauf.ausgabe.fehler, /https:\/\/projekt\.test/);
    assert.equal(aufrufe, 0);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

const HAUPT_TOKEN_RELATIV = '../haupt/.claude/toolbox.cli/tbx.token';

test('ausfuehren: toolbox.tokenFile mit ../ auf die Hauptkopie schickt deren Token nicht an einen fremden Host (Issue #2058)', async () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeHost: 'https://fremd.test', worktreeTokenFile: HAUPT_TOKEN_RELATIV });
  let aufrufe = 0;
  const fetchImpl = async () => { aufrufe += 1; return new Response('[]', { status: 200 }); };
  try {
    const lauf = await ausfuehren('{"auftrag":"liegezeit"}', { env: {}, baseDir: dir, projektDir: paar.worktree, fetchImpl });
    assert.equal(lauf.code, 1);
    assert.match(lauf.ausgabe.fehler, /toolbox\.tokenFile.*ausserhalb/s);
    assert.equal(aufrufe, 0);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: ein Symlink im Worktree auf die Token-Datei der Hauptkopie gilt als Ausbruch (Issue #2058)', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeHost: 'https://fremd.test' });
  try {
    mkdirSync(join(paar.worktree, '.claude', 'toolbox.cli'), { recursive: true });
    symlinkSync(join(paar.haupt, '.claude', 'toolbox.cli', 'tbx.token'), join(paar.worktree, '.claude', 'toolbox.cli', 'tbx.token'));
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), /toolbox\.tokenFile.*ausserhalb/s);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: ein symlinktes Verzeichnis im Pfad der Token-Datei gilt als Ausbruch (Issue #2058)', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeHost: 'https://fremd.test' });
  try {
    symlinkSync(join(paar.haupt, '.claude', 'toolbox.cli'), join(paar.worktree, '.claude', 'toolbox.cli'));
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), /toolbox\.tokenFile.*ausserhalb/s);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: ein Nachbarverzeichnis mit gleichem Namensanfang liegt ausserhalb (Issue #2058)', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ worktreeTokenFile: '../worktree-nachbar/tbx.token' });
  try {
    mkdirSync(join(paar.wurzel, 'worktree-nachbar'));
    writeFileSync(join(paar.wurzel, 'worktree-nachbar', 'tbx.token'), 'tk_nachbar');
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), /toolbox\.tokenFile.*ausserhalb/s);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: auch die Hauptkopie selbst liest keine Token-Datei ausserhalb ihres Verzeichnisses (Issue #2058)', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const projekt = projektVerzeichnis({ toolbox: { ...MIT_TOKENDATEI, tokenFile: '../aussen.token' } });
  const aussen = join(dirname(projekt), 'aussen.token');
  try {
    writeFileSync(aussen, 'tk_aussen');
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: projekt }), /toolbox\.tokenFile.*ausserhalb/s);
  } finally {
    rmSync(aussen, { force: true });
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('zugangLesen: den Rueckfallpfad in die Hauptkopie bestimmt deren Config, nicht der Worktree (Issue #2058)', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt', worktreeTokenFile: '.claude/anderes-geheimnis' });
  try {
    writeFileSync(join(paar.haupt, '.claude', 'anderes-geheimnis'), 'tk_geheimnis');
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), { host: 'https://projekt.test', token: 'tk_haupt' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('zugangLesen: nennt die Config der Hauptkopie keine Token-Datei, gibt es keinen Rueckfall (Issue #2058)', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const paar = worktreePaar({ hauptToken: 'tk_haupt' });
  try {
    writeFileSync(join(paar.haupt, '.claude', 'workflow.config.json'), JSON.stringify({ toolbox: { host: 'https://projekt.test' } }));
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: paar.worktree }), /toolbox\.tokenFile.*nicht lesbar/s);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(paar.wurzel, { recursive: true, force: true });
  }
});

test('ausfuehren: entfernte toolbox.tokenFile und fremder toolbox.host schicken das Login-Token nicht ab (Issue #2059)', async () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const projekt = projektVerzeichnis({ toolbox: { host: 'https://fremd.test' } });
  const aufrufe = [];
  const fetchImpl = async (url, init) => { aufrufe.push({ url, init }); return new Response('[]', { status: 200 }); };
  try {
    const lauf = await ausfuehren('{"auftrag":"liegezeit"}', { env: {}, baseDir: dir, projektDir: projekt, fetchImpl });
    assert.equal(lauf.code, 1);
    assert.match(lauf.ausgabe.fehler, /https:\/\/fremd\.test/);
    assert.match(lauf.ausgabe.fehler, /https:\/\/login\.test/);
    assert.doesNotMatch(lauf.ausgabe.fehler, /tk_login/);
    assert.deepEqual(aufrufe, []);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('zugangLesen: leere toolbox.tokenFile hebt die Bindung des Login-Tokens nicht auf (Issue #2059)', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const projekt = projektVerzeichnis({ toolbox: { host: 'https://fremd.test', tokenFile: '' } });
  try {
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: projekt }), /https:\/\/fremd\.test.*https:\/\/login\.test/s);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('zugangLesen: Login ohne gespeicherten Host gilt nur fuer den Standard-Host (Issue #2059)', () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const fremd = projektVerzeichnis({ toolbox: { host: 'https://fremd.test' } });
  const standard = projektVerzeichnis({ toolbox: { host: 'https://kanban.mwolff.org' } });
  try {
    assert.throws(() => zugangLesen({ env: {}, baseDir: dir, projektDir: fremd }), /https:\/\/fremd\.test.*https:\/\/kanban\.mwolff\.org/s);
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: standard }), { host: 'https://kanban.mwolff.org', token: 'tk_login' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(fremd, { recursive: true, force: true });
    rmSync(standard, { recursive: true, force: true });
  }
});

test('zugangLesen: Login-Token geht an den eigenen Host, auch wenn toolbox.host ihn nennt (Issue #2059)', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const projekt = projektVerzeichnis({ toolbox: { host: 'https://login.test' } });
  try {
    assert.deepEqual(zugangLesen({ env: {}, baseDir: dir, projektDir: projekt }), { host: 'https://login.test', token: 'tk_login' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('zugangLesen: TBX_TOKEN bleibt ungebunden, auch ohne toolbox.tokenFile (Issue #2059)', () => {
  const dir = loginVerzeichnis({ host: 'https://login.test', token: 'tk_login' });
  const projekt = projektVerzeichnis({ toolbox: { host: 'https://projekt.test' } });
  try {
    assert.deepEqual(zugangLesen({ env: { TBX_TOKEN: 'tk_env' }, baseDir: dir, projektDir: projekt }), { host: 'https://projekt.test', token: 'tk_env' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('zugangLesen: ohne Token ein Fehler, der beide Wege nennt', () => {
  const dir = loginVerzeichnis();
  const projekt = projektVerzeichnis();
  try {
    assert.throws(() => zugangLesen({ env: { TBX_TOKEN: '' }, baseDir: dir, projektDir: projekt }), /TBX_TOKEN.*tbx auth login/s);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('ausfuehren: leere toolbox.tokenFile endet mit fehler und 1', async () => {
  const dir = loginVerzeichnis({ token: 'tk_login' });
  const projekt = projektVerzeichnis({ toolbox: MIT_TOKENDATEI, tokenDatei: '' });
  try {
    const lauf = await ausfuehren('{"auftrag":"liegezeit"}', { env: {}, baseDir: dir, projektDir: projekt });
    assert.equal(lauf.code, 1);
    assert.match(lauf.ausgabe.fehler, /toolbox\.tokenFile/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
    rmSync(projekt, { recursive: true, force: true });
  }
});

test('ausfuehren: unbekannter Auftrag und kaputte Eingabe enden mit fehler und 1', async () => {
  const dir = loginVerzeichnis({ token: 'tk' });
  try {
    const unbekannt = await ausfuehren('{"auftrag":"loeschen"}', { env: {}, baseDir: dir, projektDir: dir });
    assert.equal(unbekannt.code, 1);
    assert.match(unbekannt.ausgabe.fehler, /loeschen/);
    const kaputt = await ausfuehren('kein json', { env: {}, baseDir: dir, projektDir: dir });
    assert.equal(kaputt.code, 1);
    assert.ok(kaputt.ausgabe.fehler);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('ausfuehren: liegezeit und schreiben laufen ueber den Zugang', async () => {
  const dir = loginVerzeichnis({ host: HOST, token: 'tk_test' });
  try {
    const { fetchImpl } = fakeBoard({ spalten: { BACKLOG: [] } });
    const lz = await ausfuehren('{"auftrag":"liegezeit"}', { env: {}, baseDir: dir, projektDir: dir, fetchImpl });
    assert.deepEqual(lz, { code: 0, ausgabe: { ueberfaellig: [] } });
    const sw = await ausfuehren(JSON.stringify({ auftrag: 'schreiben', herkunft: 'h', stellen: [stelle(1)] }), { env: {}, baseDir: dir, projektDir: dir, fetchImpl });
    assert.equal(sw.code, 0);
    assert.equal(sw.ausgabe.karten[0].art, 'neu');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('Kommandozeile: ohne Token endet liegezeit mit 1 und einer JSON-Ausgabe mit fehler', () => {
  const home = mkdtempSync(join(tmpdir(), 'mutationskarten-home-'));
  try {
    mkdirSync(join(home, '.config'), { recursive: true });
    const env = { ...process.env, TBX_TOKEN: '', HOME: home };
    delete env.TBX_CONFIG_DIR;
    const lauf = spawnSync(process.execPath, [SKRIPT], { input: '{"auftrag":"liegezeit"}', env, cwd: home, encoding: 'utf-8' });
    assert.equal(lauf.status, 1);
    assert.ok(JSON.parse(lauf.stdout).fehler);
  } finally {
    rmSync(home, { recursive: true, force: true });
  }
});
