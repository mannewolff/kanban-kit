// Strukturtest des CI-Jobs `mutation` (Issue #1567): Die CI urteilt über den Mutationstreiber mit
// Sperrschwelle 80 % und Liegezeit 7 Tage wie `push main` (Ausnahme von W3 in CLAUDE.md), nicht
// über PIT direkt mit der Vorgabe `pit.marke=100` aus der pom.xml.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const CI_YML = fileURLToPath(new URL('../.github/workflows/ci.yml', import.meta.url));

/** Zeilen des Jobs `name` unter `jobs:` — bis zum nächsten Eintrag derselben Einrückung. */
function jobZeilen(yaml, name) {
  const zeilen = yaml.split('\n');
  const start = zeilen.findIndex((z) => z === `  ${name}:`);
  if (start < 0) return [];
  const ende = zeilen.findIndex((z, i) => i > start && /^ {2}\S/.test(z));
  return zeilen.slice(start + 1, ende < 0 ? zeilen.length : ende);
}

/** Die Schritte eines Jobs als Textblöcke, je Schritt ab seinem `- ` unter `steps:`. */
function schritte(jobzeilen) {
  const bloecke = [];
  for (const zeile of jobzeilen) {
    if (/^ {6}- /.test(zeile)) bloecke.push([zeile]);
    else if (bloecke.length && /^ {8}/.test(zeile)) bloecke.at(-1).push(zeile);
  }
  return bloecke.map((b) => b.join('\n'));
}

const TREIBER = /run:\s*node scripts\/mutationspruefung\.mjs vollauf backend\s*$/m;
const TOKEN = /^\s+TBX_TOKEN:\s*\$\{\{\s*secrets\.KANBAN_KIT_TOKEN\s*\}\}\s*$/m;
const PIT_DIREKT = /\bmvn\b[^\n]*-Ppit/;

/** Befunde zum Job `mutation`; leer, wenn er der Regel folgt. */
function befunde(yaml) {
  const job = jobZeilen(yaml, 'mutation');
  if (!job.length) return ['Job mutation fehlt'];
  const liste = schritte(job);
  const out = [];
  const treiber = liste.filter((s) => TREIBER.test(s));
  if (!treiber.length) out.push('kein Schritt startet den Treiber im Vollauf der Backend-Seite');
  else if (!treiber.every((s) => TOKEN.test(s))) out.push('Treiber-Schritt bekommt kein TBX_TOKEN aus secrets.KANBAN_KIT_TOKEN');
  if (liste.some((s) => PIT_DIREKT.test(s))) out.push('ein Schritt ruft -Ppit direkt mit Maven auf');
  return out;
}

const yaml = readFileSync(CI_YML, 'utf8');

test('Job mutation urteilt über den Treiber mit Board-Token, ohne PIT direkt', () => {
  assert.deepEqual(befunde(yaml), []);
});

test('rot, wenn der Job wieder mvn -B -Ppit direkt aufruft', () => {
  const zurueck = yaml.replace(TREIBER, 'run: mvn -B -Ppit -Dskip.frontend=true test');
  assert.ok(befunde(zurueck).includes('ein Schritt ruft -Ppit direkt mit Maven auf'));
});

test('rot, wenn neben dem Treiber ein zusätzlicher Schritt PIT direkt fährt', () => {
  const zusatz = yaml.replace(
    TREIBER,
    (m) => `${m}\n      - name: PIT\n        run: mvn -B -Ppit -Dskip.frontend=true test`,
  );
  assert.deepEqual(befunde(zusatz), ['ein Schritt ruft -Ppit direkt mit Maven auf']);
});

test('rot, wenn der Treiber-Schritt kein TBX_TOKEN bekommt', () => {
  const ohne = yaml.replace(TOKEN, '');
  assert.deepEqual(befunde(ohne), ['Treiber-Schritt bekommt kein TBX_TOKEN aus secrets.KANBAN_KIT_TOKEN']);
});

test('rot, wenn das Token aus einem anderen Secret kommt', () => {
  const fremd = yaml.replace(TOKEN, '          TBX_TOKEN: ${{ secrets.KANBAN_SONAR_TOKEN }}');
  assert.deepEqual(befunde(fremd), ['Treiber-Schritt bekommt kein TBX_TOKEN aus secrets.KANBAN_KIT_TOKEN']);
});

test('rot, wenn der Job mutation fehlt', () => {
  assert.deepEqual(befunde('jobs:\n  backend:\n    steps: []\n'), ['Job mutation fehlt']);
});
