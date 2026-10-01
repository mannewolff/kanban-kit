import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  STUECKLISTEN_ABBILDER,
  laufWaehlen,
  stuecklistenAssets,
  stuecklistenArtefakt,
  trockenlaufText,
} from './gh-release.mjs';

// Fixtures in der Form von `gh run list --json databaseId,status,conclusion` (neuester Lauf zuerst).
const ERFOLG = [{ databaseId: 4711, status: 'completed', conclusion: 'success' }];
const LAEUFT = [{ databaseId: 4712, status: 'in_progress', conclusion: '' }];
const GESCHEITERT = [{ databaseId: 4713, status: 'completed', conclusion: 'failure' }];

test('erfolgreicher Lauf liefert seine Id und beide Stuecklisten-Assets', () => {
  const ergebnis = laufWaehlen(ERFOLG, 'v2.17.0');
  assert.deepEqual(ergebnis, { laufId: 4711 });
  assert.deepEqual(stuecklistenAssets('/tmp/x'), [
    '/tmp/x/stueckliste-kanban-kit.cdx.json',
    '/tmp/x/stueckliste-kanban-kit-backup.cdx.json',
  ]);
});

test('der neueste Lauf entscheidet, auch wenn ein aelterer erfolgreich war', () => {
  const ergebnis = laufWaehlen([...LAEUFT, ...ERFOLG], 'v2.17.0');
  assert.match(ergebnis.fehler, /läuft noch/);
});

test('ein laufender Lauf fuehrt zum Abbruch', () => {
  const ergebnis = laufWaehlen(LAEUFT, 'v2.17.0');
  assert.equal(ergebnis.laufId, undefined);
  assert.match(ergebnis.fehler, /4712/);
  assert.match(ergebnis.fehler, /läuft noch/);
});

test('ein fehlgeschlagener Lauf fuehrt zum Abbruch', () => {
  const ergebnis = laufWaehlen(GESCHEITERT, 'v2.17.0');
  assert.equal(ergebnis.laufId, undefined);
  assert.match(ergebnis.fehler, /4713/);
  assert.match(ergebnis.fehler, /failure/);
});

test('kein Lauf zum Tag fuehrt zum Abbruch', () => {
  const ergebnis = laufWaehlen([], 'v2.17.0');
  assert.equal(ergebnis.laufId, undefined);
  assert.match(ergebnis.fehler, /release-images\.yml/);
  assert.match(ergebnis.fehler, /v2\.17\.0/);
});

test('die Artefaktnamen folgen den beiden Abbildern', () => {
  assert.deepEqual(STUECKLISTEN_ABBILDER, ['kanban-kit', 'kanban-kit-backup']);
  assert.equal(stuecklistenArtefakt('kanban-kit-backup'), 'stueckliste-kanban-kit-backup');
});

// Ueber die reine Funktion statt ueber einen Kindprozess: Der Skriptlauf verlangt den Tag lokal, und
// der CI-Checkout holt keine Tags.
test('der Trockenlauf nennt beide Stuecklisten-Assets', () => {
  const text = trockenlaufText('v2.17.0', 'Notizen');
  assert.match(text, /gh run download/);
  assert.match(text, /stueckliste-kanban-kit\.cdx\.json/);
  assert.match(text, /stueckliste-kanban-kit-backup\.cdx\.json/);
  assert.match(text, /--- Release-Notes \(v2\.17\.0\) ---\nNotizen/);
});

test('der Trockenlauf ohne Changelog-Block nennt --generate-notes', () => {
  assert.match(trockenlaufText('v2.17.0', null), /--generate-notes/);
});
