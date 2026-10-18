import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  globToRegExp,
  excludeByExclusions,
  buildIssuesSearchUrl,
  mergeIssueLists,
  SEVERITY_SELECTION,
  TYPE_SELECTION,
} from './sync-sonar-issues-to-board.mjs';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

test('docs-site/** trifft beliebig tiefe Pfade im Ordner, nicht den Nachbarordner', () => {
  const re = globToRegExp('docs-site/**');
  assert.ok(re.test('docs-site/a/b/c.html'));
  assert.ok(re.test('docs-site/x.html'));
  assert.ok(!re.test('docs-sitex/a.html'));
});

test('frontend/dist/** trifft tief verschachtelte Dateien', () => {
  assert.ok(globToRegExp('frontend/dist/**').test('frontend/dist/assets/x/y.js'));
});

test('**/ in der Mitte darf null Ordner bedeuten', () => {
  const re = globToRegExp('src/**/X.java');
  assert.ok(re.test('src/X.java'));
  assert.ok(re.test('src/a/b/X.java'));
});

test('* bleibt innerhalb einer Ebene', () => {
  const re = globToRegExp('*.js');
  assert.ok(re.test('a.js'));
  assert.ok(!re.test('a/b.js'));
});

test('Sonderzeichen im Muster sind wörtlich', () => {
  assert.ok(!globToRegExp('a.b').test('axb'));
  assert.ok(globToRegExp('a.b').test('a.b'));
});

test('excludeByExclusions mit den Mustern aus sonar-project.properties', () => {
  const props = readFileSync(join(REPO_ROOT, 'sonar-project.properties'), 'utf-8');
  const exclusions = props.split('\n').find((l) => l.startsWith('sonar.exclusions=')).slice('sonar.exclusions='.length);
  const befund = (pfad) => ({ key: pfad, component: `proj:${pfad}` });
  const issues = [
    befund('docs-site/a/b/c.html'),
    befund('frontend/dist/assets/x/y.js'),
    befund('frontend/coverage/lcov-report/a/b.html'),
    befund('target/classes/org/x/Y.class'),
    befund('src/main/java/org/mwolff/manban/X.java'),
  ];
  assert.deepEqual(
    excludeByExclusions(issues, exclusions).map((i) => i.key),
    ['src/main/java/org/mwolff/manban/X.java'],
  );
});

const abfrage = (selection) =>
  new URL(
    buildIssuesSearchUrl({ projectKey: 'proj', organization: 'org', pageSize: 500, page: 2, selection }),
  );

test('Abfrage mit Schweregrad-Auswahl filtert nach Schweregrad, nicht nach Typ', () => {
  const url = abfrage(SEVERITY_SELECTION);
  assert.equal(url.pathname, '/api/issues/search');
  assert.equal(url.searchParams.get('severities'), 'MAJOR,CRITICAL,BLOCKER');
  assert.equal(url.searchParams.has('types'), false);
  assert.equal(url.searchParams.get('componentKeys'), 'proj');
  assert.equal(url.searchParams.get('organization'), 'org');
  assert.equal(url.searchParams.get('resolved'), 'false');
  assert.equal(url.searchParams.get('ps'), '500');
  assert.equal(url.searchParams.get('p'), '2');
});

test('Abfrage mit Typ-Auswahl holt Bugs und Vulnerabilities ohne Schweregrad-Filter', () => {
  const url = abfrage(TYPE_SELECTION);
  assert.equal(url.searchParams.get('types'), 'BUG,VULNERABILITY');
  assert.equal(url.searchParams.has('severities'), false);
  assert.equal(url.searchParams.get('resolved'), 'false');
});

test('mergeIssueLists entdoppelt ueber issue.key und behaelt die Reihenfolge der ersten Liste', () => {
  const befund = (key, liste) => ({ key, liste });
  const merged = mergeIssueLists(
    [befund('a', 1), befund('b', 1), befund('c', 1)],
    [befund('b', 2), befund('d', 2), befund('a', 2)],
  );
  assert.deepEqual(
    merged.map((i) => `${i.key}${i.liste}`),
    ['a1', 'b1', 'c1', 'd2'],
  );
});

test('Ein MINOR-Bug ueberlebt die Zusammenfuehrung, ein MINOR-Code-Smell nicht', () => {
  // Bestand auf SonarCloud; die Auswahl wirkt wie die API: Parameter je Abfrage mit UND verknuepft.
  const bestand = [
    { key: 'minor-bug', type: 'BUG', severity: 'MINOR' },
    { key: 'minor-smell', type: 'CODE_SMELL', severity: 'MINOR' },
    { key: 'major-bug', type: 'BUG', severity: 'MAJOR' },
    { key: 'info-vuln', type: 'VULNERABILITY', severity: 'INFO' },
    { key: 'major-smell', type: 'CODE_SMELL', severity: 'MAJOR' },
  ];
  const antwort = (selection) => {
    const params = abfrage(selection).searchParams;
    const severities = params.get('severities')?.split(',');
    const types = params.get('types')?.split(',');
    return bestand.filter(
      (i) => (!severities || severities.includes(i.severity)) && (!types || types.includes(i.type)),
    );
  };
  const merged = mergeIssueLists(antwort(SEVERITY_SELECTION), antwort(TYPE_SELECTION));
  assert.deepEqual(merged.map((i) => i.key), ['major-bug', 'major-smell', 'minor-bug', 'info-vuln']);
});
