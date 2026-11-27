// Strukturtest des Release-Workflows (Issue #2292): Jede eingebundene Action ist an einen vollen
// Commit-SHA gebunden, nicht an einen Tag. Der Job `abbilder` traegt `packages: write`,
// `id-token: write` und `attestations: write`; ein Tag — auch ein voller Versions-Tag wie `v4.6.0` —
// kann vom Eigentuemer des Action-Repositorys jederzeit auf anderen Code verschoben werden, und
// dieser liefe dann ohne Aenderung an diesem Repository mit genau diesen Rechten.
//
// Der Test stellt den Angriff nach: Ein Modell der Aufloesung von `uses: <repo>@<ref>` (ein SHA
// trifft genau seinen Commit, ein Tag zeigt dorthin, wohin das Action-Repository ihn gerade
// stellt) wird vor und nach dem Verschieben aller Tags durch den Angreifer befragt. Gebunden ist
// eine Fundstelle erst, wenn sie in beiden Lagen denselben Commit laedt.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const RELEASE_YML = fileURLToPath(new URL('../.github/workflows/release-images.yml', import.meta.url));

const SHA = /^[0-9a-f]{40}$/;
const VERSIONSKOMMENTAR = /^#\s*v\d+\.\d+\.\d+\s*$/;

/** Alle `uses:`-Fundstellen mit Zeilennummer, Repository, Ref und Kommentar dahinter. */
function aktionsBezuege(yaml) {
  const bezuege = [];
  yaml.split('\n').forEach((zeile, index) => {
    const treffer = /^\s*(?:-\s+)?uses:\s*([^\s@#]+)@([^\s#]+)\s*(#.*)?$/.exec(zeile);
    if (treffer) {
      bezuege.push({ zeile: index + 1, repo: treffer[1], ref: treffer[2], kommentar: treffer[3] ?? '' });
    }
  });
  return bezuege;
}

/**
 * Modell der Aufloesung durch den Actions-Runner: Ein voller SHA laedt genau diesen Commit,
 * jeder andere Ref (Tag, Branch) den Commit, auf den das Action-Repository ihn gerade stellt.
 */
function aufloesen({ repo, ref }, actionRepos) {
  if (SHA.test(ref)) return ref;
  const ziel = actionRepos[repo]?.[ref];
  assert.ok(ziel, `${repo}@${ref} ist im Modell nicht bekannt`);
  return ziel;
}

/** Ausgangslage: Jeder Ref zeigt auf einen redlichen Commit; ein SHA-Ref auf sich selbst. */
function redlicheRepos(bezuege) {
  const repos = {};
  for (const { repo, ref } of bezuege) {
    repos[repo] ??= {};
    repos[repo][ref] = SHA.test(ref) ? ref : 'a'.repeat(40);
  }
  return repos;
}

/** Der Angreifer kontrolliert jedes Action-Repository und stellt jeden Ref auf seinen Commit. */
const BOESER_COMMIT = 'b'.repeat(40);
function tagsVerschoben(repos) {
  return Object.fromEntries(
    Object.entries(repos).map(([repo, refs]) => [
      repo,
      Object.fromEntries(Object.keys(refs).map((ref) => [ref, BOESER_COMMIT])),
    ]),
  );
}

/** Fundstellen, die nach dem Verschieben der Tags anderen Code laden als davor. */
function verschiebbar(bezuege) {
  const vorher = redlicheRepos(bezuege);
  const nachher = tagsVerschoben(vorher);
  return bezuege.filter((b) => aufloesen(b, vorher) !== aufloesen(b, nachher));
}

/** Formbefunde: kein voller SHA oder kein Versionskommentar, an dem man die Fassung erkennt. */
function befunde(yaml) {
  const out = [];
  for (const b of aktionsBezuege(yaml)) {
    if (!SHA.test(b.ref)) out.push(`Zeile ${b.zeile}: ${b.repo}@${b.ref} ist kein voller Commit-SHA`);
    else if (!VERSIONSKOMMENTAR.test(b.kommentar)) {
      out.push(`Zeile ${b.zeile}: ${b.repo}@${b.ref} ohne Versionskommentar "# vX.Y.Z"`);
    }
  }
  return out;
}

const release = readFileSync(RELEASE_YML, 'utf-8');

test('Nachstellung: ein verschobener Tag laedt im Modell anderen Code (Gegenprobe des Angriffs)', () => {
  const ungebunden = aktionsBezuege(
    '      - name: An ghcr.io anmelden\n        uses: docker/login-action@v4.6.0\n',
  );
  assert.equal(ungebunden.length, 1);
  assert.deepEqual(
    verschiebbar(ungebunden).map((b) => `${b.repo}@${b.ref}`),
    ['docker/login-action@v4.6.0'],
  );
  const gebunden = aktionsBezuege(
    '        uses: docker/login-action@dbcb813823bdd20940b903addbd779551569679f # v4.6.0\n',
  );
  assert.deepEqual(verschiebbar(gebunden), []);
});

test('release-images.yml bindet Actions ueberhaupt ein (sonst liefe die Pruefung leer)', () => {
  const bezuege = aktionsBezuege(release);
  assert.ok(bezuege.length >= 10, `nur ${bezuege.length} uses-Zeilen gefunden`);
  const repos = new Set(bezuege.map((b) => b.repo));
  assert.ok(repos.has('docker/login-action'));
  assert.ok(repos.has('docker/build-push-action'));
});

test('Angriff: verschobene Tags aller Action-Repositorys aendern den Code des Release-Laufs nicht', () => {
  const offen = verschiebbar(aktionsBezuege(release)).map((b) => `Zeile ${b.zeile}: ${b.repo}@${b.ref}`);
  assert.deepEqual(offen, [], `diese Fundstellen laden nach einem verschobenen Tag fremden Code:\n${offen.join('\n')}`);
});

test('jede Action in release-images.yml traegt vollen Commit-SHA und Versionskommentar', () => {
  assert.deepEqual(befunde(release), []);
});

test('befunde erkennt Tag, Kurz-SHA und fehlenden Versionskommentar', () => {
  const yaml = [
    '      - uses: actions/checkout@v7.0.1',
    '        uses: docker/login-action@dbcb813 # v4.6.0',
    '        uses: docker/build-push-action@c3c9e263c25d99ce0380d002d59b67737d91b0dc',
    '        uses: actions/setup-node@820762786026740c76f36085b0efc47a31fe5020 # v7.0.0',
  ].join('\n');
  assert.deepEqual(befunde(yaml), [
    'Zeile 1: actions/checkout@v7.0.1 ist kein voller Commit-SHA',
    'Zeile 2: docker/login-action@dbcb813 ist kein voller Commit-SHA',
    'Zeile 3: docker/build-push-action@c3c9e263c25d99ce0380d002d59b67737d91b0dc ohne Versionskommentar "# vX.Y.Z"',
  ]);
});
