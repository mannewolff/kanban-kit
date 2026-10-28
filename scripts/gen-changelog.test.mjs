import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { KEEP_A_CHANGELOG_HEADER, collectEntries, previousVersionTag, versionBlock } from './gen-changelog.mjs';

const HIER = dirname(fileURLToPath(import.meta.url));
const SKRIPT = join(HIER, 'gen-changelog.mjs');
const CHANGELOG = join(HIER, '..', 'CHANGELOG.md');
const URL_REPO = 'https://github.com/beispiel/repo';

/** Ein Ersatz für `git log`, der die Titel so liefert, wie git sie ausgäbe: neueste zuerst. */
function gitMit(titel) {
  return () => `${titel.join('\n')}\n`;
}

test('API:-Titel stehen vor den übrigen Einträgen und tragen das Kennzeichen', () => {
  const eintraege = collectEntries(
    'a',
    'b',
    URL_REPO,
    gitMit([
      'Karten-Suche beschleunigen (Issue #10)',
      'API: GET /api/kanban/items — neu verlässlich (Issue #11)',
      'Spaltenbreite merken (Issue #12)',
    ]),
  );

  assert.deepEqual(eintraege, [
    `**API-Änderung:** GET /api/kanban/items — neu verlässlich ([#11](${URL_REPO}/issues/11))`,
    `Karten-Suche beschleunigen ([#10](${URL_REPO}/issues/10))`,
    `Spaltenbreite merken ([#12](${URL_REPO}/issues/12))`,
  ]);
  assert.match(
    versionBlock('2.21.0', '2026-10-05', eintraege),
    /^## \[2\.21\.0\] – 2026-10-05\n\n- \*\*API-Änderung:\*\* GET \/api\/kanban\/items/,
  );
});

test('Titel ohne Präfix bleiben unverändert und in ihrer Reihenfolge', () => {
  const eintraege = collectEntries(
    'a',
    'b',
    URL_REPO,
    gitMit(['Dritter Titel', 'Release: Version 2.20.0', 'Zweiter Titel', 'Erster Titel']),
  );

  assert.deepEqual(eintraege, ['Dritter Titel', 'Zweiter Titel', 'Erster Titel']);
  assert.equal(
    versionBlock('2.21.0', '2026-10-05', eintraege),
    '## [2.21.0] – 2026-10-05\n\n- Dritter Titel\n- Zweiter Titel\n- Erster Titel\n',
  );
});

test('ein API:-Titel abweichender Form wird übernommen, nicht verworfen', () => {
  const eintraege = collectEntries(
    'a',
    'b',
    URL_REPO,
    gitMit(['Normaler Titel', 'API:Kartenlabels umgebaut', 'API: zweiter freier Text']),
  );

  assert.deepEqual(eintraege, [
    '**API-Änderung:** Kartenlabels umgebaut',
    '**API-Änderung:** zweiter freier Text',
    'Normaler Titel',
  ]);
});

test('der Import des Moduls startet main nicht', () => {
  const vorher = readFileSync(CHANGELOG, 'utf-8');

  const ausgabe = execFileSync(
    process.execPath,
    ['-e', `import(${JSON.stringify(pathToFileURL(SKRIPT).href)})`],
    { encoding: 'utf-8' },
  );

  assert.equal(ausgabe, '');
  assert.equal(readFileSync(CHANGELOG, 'utf-8'), vorher);
});

test('der Kopf nennt die Kennzeichnung der API-Änderungen vor den Upgrade-Hinweisen', () => {
  assert.match(KEEP_A_CHANGELOG_HEADER, /\*\*API-Änderung:\*\*/);
  assert.ok(
    KEEP_A_CHANGELOG_HEADER.indexOf('API-Änderung') <
      KEEP_A_CHANGELOG_HEADER.indexOf('## Hinweise zum Upgrade'),
  );
});

// --- Hotfix (Issue #1975) --------------------------------------------------

test('ein Hotfix-Block trägt den Vermerk „verkürzt geprüft“ vor den Einträgen', () => {
  const block = versionBlock('3.8.0-hotfix.1', '2026-10-28', ['Ansicht als: Schleife beendet']);

  assert.equal(
    block,
    '## [3.8.0-hotfix.1] – 2026-10-28\n\n' +
      '**verkürzt geprüft** — Hotfix auf 3.8.0; vor dem Merge lief die verkürzte Prüfung, nicht die volle.\n\n' +
      '- Ansicht als: Schleife beendet\n',
  );
});

test('ein regulärer Block trägt keinen Vermerk', () => {
  assert.ok(!versionBlock('3.12.0', '2026-10-28', ['x']).includes('verkürzt geprüft'));
});

test('der Hotfix grenzt gegen das Release ab, auf dem er sitzt', () => {
  assert.equal(previousVersionTag('3.8.0-hotfix.1', ['v3.7.0', 'v3.8.0', 'v3.9.0']), 'v3.8.0');
});

test('der zweite Hotfix grenzt gegen den ersten ab', () => {
  assert.equal(
    previousVersionTag('3.8.0-hotfix.2', ['v3.8.0', 'v3.8.0-hotfix.1', 'v3.8.0-hotfix.3']),
    'v3.8.0-hotfix.1',
  );
});

test('ein reguläres Release übergeht die Hotfix-Tags', () => {
  assert.equal(previousVersionTag('3.12.0', ['v3.10.0', 'v3.11.0', 'v3.11.0-hotfix.1']), 'v3.11.0');
});
