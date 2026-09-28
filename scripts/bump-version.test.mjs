import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  GHCR_ANWENDUNG,
  GHCR_SICHERUNG,
  betriebsdateienFaellig,
  betriebsdateienNachziehen,
} from './bump-version.mjs';

const HIER = dirname(fileURLToPath(import.meta.url));
const WURZEL = join(HIER, '..');

/**
 * Legt eine Wegwerf-Kopie der drei betroffenen Dateien an. Bewusst Kopien der ECHTEN Dateien und
 * keine Attrappen: Der Test soll merken, wenn eine Betriebsdatei ihre ghcr-Zeile umbaut — genau
 * dann zoege bump-version.mjs sie beim naechsten Release nicht mehr nach.
 */
function wegwerfkopie() {
  const verzeichnis = mkdtempSync(join(tmpdir(), 'bump-version-'));
  mkdirSync(join(verzeichnis, 'scripts'));
  cpSync(join(WURZEL, 'docker-compose.yml'), join(verzeichnis, 'docker-compose.yml'));
  cpSync(join(WURZEL, 'docker-compose.backup.yml'), join(verzeichnis, 'docker-compose.backup.yml'));
  cpSync(join(WURZEL, 'scripts', 'bausteine.json'), join(verzeichnis, 'scripts', 'bausteine.json'));
  return verzeichnis;
}

function lies(verzeichnis, ...teile) {
  return readFileSync(join(verzeichnis, ...teile), 'utf-8');
}

function bausteinMit(verzeichnis, praefix) {
  const { bausteine } = JSON.parse(lies(verzeichnis, 'scripts', 'bausteine.json'));
  return bausteine.find((baustein) => baustein.bezugsstelle.startsWith(`${praefix}:`));
}

// --- Faelligkeit -----------------------------------------------------------

test('betriebsdateienFaellig: minor und major ziehen nach, patch nicht', () => {
  assert.equal(betriebsdateienFaellig('minor'), true);
  assert.equal(betriebsdateienFaellig('major'), true);
  assert.equal(betriebsdateienFaellig('patch'), false);
});

// --- Nachziehen ------------------------------------------------------------

test('betriebsdateienNachziehen setzt beide Compose-Zeilen und beide Bausteine', () => {
  const verzeichnis = wegwerfkopie();
  try {
    betriebsdateienNachziehen(verzeichnis, '3.0.0');

    assert.match(lies(verzeichnis, 'docker-compose.yml'), /image: ghcr\.io\/mannewolff\/kanban-kit:3\.0\.0\n/);
    assert.match(
      lies(verzeichnis, 'docker-compose.backup.yml'),
      /image: ghcr\.io\/mannewolff\/kanban-kit-backup:3\.0\.0\n/,
    );
    assert.deepEqual(
      {
        anwendung: bausteinMit(verzeichnis, GHCR_ANWENDUNG),
        sicherung: bausteinMit(verzeichnis, GHCR_SICHERUNG),
      },
      {
        anwendung: {
          ...bausteinMit(verzeichnis, GHCR_ANWENDUNG),
          bezugsstelle: `${GHCR_ANWENDUNG}:3.0.0`,
          fassung: '3.0.0',
        },
        sicherung: {
          ...bausteinMit(verzeichnis, GHCR_SICHERUNG),
          bezugsstelle: `${GHCR_SICHERUNG}:3.0.0`,
          fassung: '3.0.0',
        },
      },
    );
  } finally {
    rmSync(verzeichnis, { recursive: true, force: true });
  }
});

test('betriebsdateienNachziehen laesst fremde Abbilder unberuehrt', () => {
  const verzeichnis = wegwerfkopie();
  try {
    const vorher = lies(verzeichnis, 'docker-compose.yml');
    betriebsdateienNachziehen(verzeichnis, '3.0.0');
    const nachher = lies(verzeichnis, 'docker-compose.yml');

    const fremd = (text) => text.split('\n').filter((zeile) => /image:/.test(zeile) && !zeile.includes('ghcr.io'));
    assert.deepEqual(fremd(nachher), fremd(vorher));
    assert.notEqual(nachher, vorher);
  } finally {
    rmSync(verzeichnis, { recursive: true, force: true });
  }
});

test('betriebsdateienNachziehen behaelt die Form von bausteine.json', () => {
  const verzeichnis = wegwerfkopie();
  try {
    const vorher = lies(verzeichnis, 'scripts', 'bausteine.json');
    betriebsdateienNachziehen(verzeichnis, '3.0.0');
    const nachher = lies(verzeichnis, 'scripts', 'bausteine.json');

    assert.equal(nachher, vorher.replaceAll('2.14.0', '3.0.0'));
  } finally {
    rmSync(verzeichnis, { recursive: true, force: true });
  }
});

test('betriebsdateienNachziehen zieht auch nach, wenn der Tag hinter VERSION zurueckliegt', () => {
  const verzeichnis = wegwerfkopie();
  try {
    // Zwischen zwei Releases heben mehrere `patch`-Bumps VERSION an, ohne die Betriebsdateien zu
    // beruehren. Der naechste `minor` trifft die Zeile also mit einer aelteren Fassung an.
    const pfad = join(verzeichnis, 'docker-compose.yml');
    writeFileSync(pfad, readFileSync(pfad, 'utf-8').replace(`${GHCR_ANWENDUNG}:2.14.0`, `${GHCR_ANWENDUNG}:2.9.7`));

    betriebsdateienNachziehen(verzeichnis, '3.0.0');

    assert.match(lies(verzeichnis, 'docker-compose.yml'), /image: ghcr\.io\/mannewolff\/kanban-kit:3\.0\.0\n/);
  } finally {
    rmSync(verzeichnis, { recursive: true, force: true });
  }
});

// --- Fehlende Stellen ------------------------------------------------------

for (const [was, verstuemmeln] of [
  [
    'die Anwendungs-Zeile in docker-compose.yml',
    (verzeichnis) => {
      const pfad = join(verzeichnis, 'docker-compose.yml');
      writeFileSync(pfad, readFileSync(pfad, 'utf-8').replace(`${GHCR_ANWENDUNG}:2.14.0`, 'manban:lokal'));
    },
  ],
  [
    'die Sicherungs-Zeile in docker-compose.backup.yml',
    (verzeichnis) => {
      const pfad = join(verzeichnis, 'docker-compose.backup.yml');
      writeFileSync(pfad, readFileSync(pfad, 'utf-8').replace(`${GHCR_SICHERUNG}:2.14.0`, 'manban-backup:lokal'));
    },
  ],
  [
    'der Anwendungs-Baustein in bausteine.json',
    (verzeichnis) => {
      const pfad = join(verzeichnis, 'scripts', 'bausteine.json');
      const daten = JSON.parse(readFileSync(pfad, 'utf-8'));
      daten.bausteine = daten.bausteine.filter((b) => !b.bezugsstelle.startsWith(`${GHCR_ANWENDUNG}:`));
      writeFileSync(pfad, `${JSON.stringify(daten, null, 2)}\n`);
    },
  ],
  [
    'der Sicherungs-Baustein in bausteine.json',
    (verzeichnis) => {
      const pfad = join(verzeichnis, 'scripts', 'bausteine.json');
      const daten = JSON.parse(readFileSync(pfad, 'utf-8'));
      daten.bausteine = daten.bausteine.filter((b) => !b.bezugsstelle.startsWith(`${GHCR_SICHERUNG}:`));
      writeFileSync(pfad, `${JSON.stringify(daten, null, 2)}\n`);
    },
  ],
]) {
  test(`betriebsdateienNachziehen scheitert, wenn ${was} fehlt`, () => {
    const verzeichnis = wegwerfkopie();
    try {
      verstuemmeln(verzeichnis);
      assert.throws(() => betriebsdateienNachziehen(verzeichnis, '3.0.0'), /nicht gefunden/);
    } finally {
      rmSync(verzeichnis, { recursive: true, force: true });
    }
  });
}
