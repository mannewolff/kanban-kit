import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { globToRegExp, excludeByExclusions } from './sync-sonar-issues-to-board.mjs';

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
