import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';

import {
  LEBENSZEICHEN_MS,
  LEBENSZEICHEN_SKRIPT,
  mitLebenszeichen,
} from './mutationspruefung.mjs';

/*
 * Lebenszeichen des Mutationstreibers (Issue #2263). `checks.mjs` bricht eine Mutationspruefung
 * nach 5 Minuten ohne Ausgabe als haengend ab; der Treiber wartet aber synchron auf Stryker bzw.
 * PIT und reicht deren Ausgabe bewusst nicht durch. Ein Begleitprozess schreibt darum in festem
 * Takt eine Zeile, solange das Werkzeug laeuft.
 */

test('Lebenszeichen: der Takt liegt deutlich unter der erlaubten Stille von 5 Minuten', () => {
  assert.ok(LEBENSZEICHEN_MS > 0);
  assert.ok(LEBENSZEICHEN_MS <= 60_000);
});

test('Lebenszeichen: der Begleiter startet vor dem Werkzeug und endet danach, das Ergebnis bleibt', () => {
  const folge = [];
  const begleiter = (name) => {
    folge.push(`start ${name}`);
    return () => folge.push(`ende ${name}`);
  };
  const ergebnis = { status: 0, stdout: 'x', stderr: '' };
  const starte = mitLebenszeichen((befehl, args, optionen) => {
    folge.push(`werkzeug ${befehl} ${args.join(' ')} ${optionen.cwd}`);
    return ergebnis;
  }, begleiter);

  assert.equal(starte('/pfad/zu/stryker', ['run'], { cwd: 'frontend' }), ergebnis);
  assert.deepEqual(folge, ['start stryker', 'werkzeug /pfad/zu/stryker run frontend', 'ende stryker']);
});

test('Lebenszeichen: wirft das Werkzeug, endet der Begleiter trotzdem', () => {
  let beendet = false;
  const starte = mitLebenszeichen(() => {
    throw new Error('kaputt');
  }, () => () => {
    beendet = true;
  });
  assert.throws(() => starte('mvn', [], {}), /kaputt/);
  assert.equal(beendet, true);
});

/*
 * Liest die Ausgabe, bis `anzahl` Zeilen da sind, und beendet den Prozess dann selbst. Eine feste
 * Laufzeit wie frueher (400 ms) reichte im vollen Prueflauf nicht: Unter Last brauchte schon der
 * Start von Node laenger, und die Ausgabe blieb leer (Issue #2269). Die Obergrenze faengt nur ein
 * Skript ab, das gar nicht schreibt.
 */
function zeilenAbwarten(args, anzahl, obergrenzeMs) {
  return new Promise((resolve) => {
    const kind = spawn(process.execPath, args, { stdio: ['ignore', 'pipe', 'ignore'] });
    let ausgabe = '';
    const ende = () => {
      clearTimeout(frist);
      kind.kill();
      resolve(ausgabe);
    };
    const frist = setTimeout(ende, obergrenzeMs);
    kind.stdout.setEncoding('utf-8');
    kind.stdout.on('data', (stueck) => {
      ausgabe += stueck;
      if (ausgabe.split('\n').filter(Boolean).length >= anzahl) ende();
    });
    kind.on('exit', ende);
  });
}

test('Lebenszeichen: das Skript schreibt im Takt eine Zeile mit dem Werkzeug, ohne Fehlermerkmal', async () => {
  const ausgabe = await zeilenAbwarten(['-e', LEBENSZEICHEN_SKRIPT, '40', String(process.pid), 'mvn'], 2, 10_000);
  const zeilen = ausgabe.trim().split('\n').filter(Boolean);
  assert.ok(zeilen.length >= 2, `zu wenige Zeilen: ${JSON.stringify(ausgabe)}`);
  for (const zeile of zeilen) {
    assert.match(zeile, /^Mutationswerkzeug mvn läuft noch/);
    assert.doesNotMatch(zeile, /\[ERROR\]|BUILD FAILURE/);
  }
});

test('Lebenszeichen: lebt der Treiber nicht mehr, endet das Skript von selbst und schreibt nichts', () => {
  const tot = spawnSync(process.execPath, ['-e', '']).pid;
  const res = spawnSync(process.execPath, ['-e', LEBENSZEICHEN_SKRIPT, '20', String(tot), 'mvn'], {
    encoding: 'utf-8',
    timeout: 5_000,
  });
  assert.equal(res.status, 0);
  assert.equal(res.signal, null);
  assert.equal(res.stdout, '');
});
