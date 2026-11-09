// Strukturtest der Abdeckungspruefung im Build-Dienst (Issue #2066, Plan #2062, E1, E8): Die harte
// Grenze `jacoco-check` in der pom.xml entfaellt, und der Job `backend` faehrt nach `mvn -B verify`
// denselben Treiber wie `push main`. Ein Hotfix faehrt ihn ueber `buildChecks` mit.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const CI_YML = fileURLToPath(new URL('../.github/workflows/ci.yml', import.meta.url));
const POM_XML = fileURLToPath(new URL('../pom.xml', import.meta.url));

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

const TREIBER = /run:\s*node scripts\/abdeckungspruefung\.mjs\s*$/m;
const TOKEN = /^\s+TBX_TOKEN:\s*\$\{\{\s*secrets\.KANBAN_KIT_TOKEN\s*\}\}\s*$/m;
const VERIFY = /run:\s*mvn -B verify\s*$/m;
const NUR_AUSSERHALB_HOTFIX = /^\s+if:\s*env\.HOTFIX != 'true'\s*$/m;
const NODE = /uses:\s*actions\/setup-node@/;
const BEDINGT = /^\s+if:/m;

/** Befunde zum Job `backend`; leer, wenn er der Regel folgt. */
function befunde(yaml) {
  const job = jobZeilen(yaml, 'backend');
  if (!job.length) return ['Job backend fehlt'];
  const liste = schritte(job);
  const treiber = liste.findIndex((s) => TREIBER.test(s));
  if (treiber < 0) return ['kein Schritt startet den Treiber'];
  const out = [];
  if (!TOKEN.test(liste[treiber])) out.push('Treiber-Schritt bekommt kein TBX_TOKEN aus secrets.KANBAN_KIT_TOKEN');
  if (!NUR_AUSSERHALB_HOTFIX.test(liste[treiber])) out.push('Treiber-Schritt läuft nicht nur außerhalb des Hotfix');
  const verify = liste.findIndex((s) => VERIFY.test(s));
  if (verify < 0 || verify > treiber) out.push('Treiber läuft nicht nach mvn -B verify');
  const node = liste.findIndex((s) => NODE.test(s) && !BEDINGT.test(s));
  if (node < 0 || node > treiber) out.push('Node ist vor dem Treiber nicht bedingungslos eingerichtet');
  return out;
}

const yaml = readFileSync(CI_YML, 'utf8');

const MVN_SCHRITT = "      - name: Build\n        if: env.HOTFIX != 'true'\n        run: mvn -B verify";
const NODE_SCHRITT = '      - name: Set up Node 22\n        uses: actions/setup-node@v4';
const TREIBER_SCHRITT = [
  '      - name: Abdeckungsprüfung',
  "        if: env.HOTFIX != 'true'",
  '        env:',
  '          TBX_TOKEN: ${{ secrets.KANBAN_KIT_TOKEN }}',
  '        run: node scripts/abdeckungspruefung.mjs',
].join('\n');

function jobMit(...liste) {
  return `jobs:\n  backend:\n    name: Backend (mvn verify)\n    steps:\n${liste.join('\n')}\n`;
}

test('Job backend fährt den Treiber nach mvn -B verify, mit Token und außerhalb des Hotfix', () => {
  assert.deepEqual(befunde(yaml), []);
});

test('der Job behält den Namen, den das Ruleset von production verlangt', () => {
  assert.match(jobZeilen(yaml, 'backend').join('\n'), /^ {4}name: Backend \(mvn verify\)$/m);
});

test('die Vorlage der Gegenproben gilt selbst als regelkonform', () => {
  assert.deepEqual(befunde(jobMit(MVN_SCHRITT, NODE_SCHRITT, TREIBER_SCHRITT)), []);
});

test('rot, wenn kein Schritt den Treiber startet', () => {
  assert.deepEqual(befunde(jobMit(MVN_SCHRITT, NODE_SCHRITT)), ['kein Schritt startet den Treiber']);
});

test('rot, wenn der Treiber vor mvn -B verify läuft', () => {
  assert.deepEqual(
    befunde(jobMit(NODE_SCHRITT, TREIBER_SCHRITT, MVN_SCHRITT)),
    ['Treiber läuft nicht nach mvn -B verify'],
  );
});

test('rot, wenn der Treiber-Schritt kein TBX_TOKEN bekommt', () => {
  const ohne = TREIBER_SCHRITT.replace(TOKEN, '          FOO: bar');
  assert.deepEqual(
    befunde(jobMit(MVN_SCHRITT, NODE_SCHRITT, ohne)),
    ['Treiber-Schritt bekommt kein TBX_TOKEN aus secrets.KANBAN_KIT_TOKEN'],
  );
});

test('rot, wenn das Token aus einem anderen Secret kommt', () => {
  const fremd = TREIBER_SCHRITT.replace('KANBAN_KIT_TOKEN', 'KANBAN_SONAR_TOKEN');
  assert.deepEqual(
    befunde(jobMit(MVN_SCHRITT, NODE_SCHRITT, fremd)),
    ['Treiber-Schritt bekommt kein TBX_TOKEN aus secrets.KANBAN_KIT_TOKEN'],
  );
});

test('rot, wenn der Treiber auch im Hotfix läuft', () => {
  const immer = TREIBER_SCHRITT.replace(NUR_AUSSERHALB_HOTFIX, '').replace(/\n\n/, '\n');
  assert.deepEqual(
    befunde(jobMit(MVN_SCHRITT, NODE_SCHRITT, immer)),
    ['Treiber-Schritt läuft nicht nur außerhalb des Hotfix'],
  );
});

test('rot, wenn Node weiter nur für den Hotfix eingerichtet wird', () => {
  const nurHotfix = "      - name: Set up Node 22 (Hotfix)\n        if: env.HOTFIX == 'true'\n        uses: actions/setup-node@v4";
  assert.deepEqual(
    befunde(jobMit(MVN_SCHRITT, nurHotfix, TREIBER_SCHRITT)),
    ['Node ist vor dem Treiber nicht bedingungslos eingerichtet'],
  );
});

test('rot, wenn der Job backend fehlt', () => {
  assert.deepEqual(befunde('jobs:\n  frontend:\n    steps: []\n'), ['Job backend fehlt']);
});

test('die pom.xml trägt die harte Grenze nicht mehr, den Bericht weiterhin', () => {
  const pom = readFileSync(POM_XML, 'utf8');
  assert.ok(!pom.includes('jacoco-check'), 'pom.xml enthält noch jacoco-check');
  assert.ok(pom.includes('jacoco-report'), 'pom.xml enthält jacoco-report nicht mehr');
});
