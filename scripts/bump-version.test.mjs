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
 *
 * Mit `ausgangsfassung` stehen beide ghcr-Zeilen und beide ghcr-Bausteine danach auf dieser
 * Fassung. So laufen die Tests auch auf dem Stand nach einem `minor`, an dem sie beim
 * `merge production` gescheitert sind, als sie die Fassung noch fest einbauten (Issue #1316).
 */
function wegwerfkopie(ausgangsfassung) {
  const verzeichnis = mkdtempSync(join(tmpdir(), 'bump-version-'));
  mkdirSync(join(verzeichnis, 'scripts'));
  cpSync(join(WURZEL, 'docker-compose.yml'), join(verzeichnis, 'docker-compose.yml'));
  cpSync(join(WURZEL, 'docker-compose.backup.yml'), join(verzeichnis, 'docker-compose.backup.yml'));
  cpSync(join(WURZEL, 'scripts', 'bausteine.json'), join(verzeichnis, 'scripts', 'bausteine.json'));
  if (ausgangsfassung !== undefined) {
    fassungSetzen(verzeichnis, ausgangsfassung);
  }
  return verzeichnis;
}

/**
 * Setzt die Fassung der eigenen Abbilder in der Kopie ohne bump-version.mjs — sonst bereitete die
 * geprueften Funktion ihre eigene Pruefung vor.
 */
function fassungSetzen(verzeichnis, neu) {
  const alt = fassungIn(verzeichnis);
  for (const [datei, praefix] of [
    ['docker-compose.yml', GHCR_ANWENDUNG],
    ['docker-compose.backup.yml', GHCR_SICHERUNG],
  ]) {
    const pfad = join(verzeichnis, datei);
    ersetzeGenau(pfad, `${praefix}:${alt}\n`, `${praefix}:${neu}\n`);
  }
  const pfad = join(verzeichnis, 'scripts', 'bausteine.json');
  const daten = JSON.parse(readFileSync(pfad, 'utf-8'));
  for (const baustein of daten.bausteine) {
    for (const praefix of [GHCR_ANWENDUNG, GHCR_SICHERUNG]) {
      if (baustein.bezugsstelle === `${praefix}:${alt}`) {
        baustein.bezugsstelle = `${praefix}:${neu}`;
        baustein.fassung = neu;
      }
    }
  }
  writeFileSync(pfad, `${JSON.stringify(daten, null, 2)}\n`);
}

/**
 * Ersetzt `alt` durch `neu` und haelt fest, dass `alt` genau einmal vorkam. Ohne diese Probe liefe
 * eine Ersetzung still ins Leere, und der Test pruefte den Fall nicht mehr, den er vorbereiten
 * soll — so geschehen, als die Fassung noch fest im Test stand (Issue #1316).
 */
function ersetzeGenau(pfad, alt, neu) {
  const text = readFileSync(pfad, 'utf-8');
  assert.equal(text.split(alt).length - 1, 1, `'${alt.trim()}' kommt in ${pfad} nicht genau einmal vor`);
  writeFileSync(pfad, text.replace(alt, neu));
}

function lies(verzeichnis, ...teile) {
  return readFileSync(join(verzeichnis, ...teile), 'utf-8');
}

function bausteinMit(verzeichnis, praefix) {
  const { bausteine } = JSON.parse(lies(verzeichnis, 'scripts', 'bausteine.json'));
  return bausteine.find((baustein) => baustein.bezugsstelle.startsWith(`${praefix}:`));
}

/** Die Fassung, auf der die eigenen Abbilder in der Kopie gerade stehen. */
function fassungIn(verzeichnis) {
  return bausteinMit(verzeichnis, GHCR_ANWENDUNG).fassung;
}

/** Ein Ziel, das garantiert von der Ausgangsfassung verschieden ist: die naechste Hauptversion. */
function zielNach(fassung) {
  return `${Number(fassung.split('.')[0]) + 1}.0.0`;
}

/**
 * Die Ausgangsfassungen, ueber die die fassungsabhaengigen Tests laufen: der echte Stand der
 * Betriebsdateien und `2.15.0`, der Stand nach dem `minor`, an dem der Merge-Lauf am
 * 2026-09-30 scheiterte.
 */
const AUSGANGSFASSUNGEN = [
  ['echter Stand', undefined],
  ['Stand 2.15.0', '2.15.0'],
];

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
    const ziel = zielNach(fassungIn(verzeichnis));
    betriebsdateienNachziehen(verzeichnis, ziel);

    assert.ok(lies(verzeichnis, 'docker-compose.yml').includes(`image: ${GHCR_ANWENDUNG}:${ziel}\n`));
    assert.ok(lies(verzeichnis, 'docker-compose.backup.yml').includes(`image: ${GHCR_SICHERUNG}:${ziel}\n`));
    assert.deepEqual(
      {
        anwendung: bausteinMit(verzeichnis, GHCR_ANWENDUNG),
        sicherung: bausteinMit(verzeichnis, GHCR_SICHERUNG),
      },
      {
        anwendung: {
          ...bausteinMit(verzeichnis, GHCR_ANWENDUNG),
          bezugsstelle: `${GHCR_ANWENDUNG}:${ziel}`,
          fassung: ziel,
        },
        sicherung: {
          ...bausteinMit(verzeichnis, GHCR_SICHERUNG),
          bezugsstelle: `${GHCR_SICHERUNG}:${ziel}`,
          fassung: ziel,
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
    betriebsdateienNachziehen(verzeichnis, zielNach(fassungIn(verzeichnis)));
    const nachher = lies(verzeichnis, 'docker-compose.yml');

    const fremd = (text) => text.split('\n').filter((zeile) => /image:/.test(zeile) && !zeile.includes('ghcr.io'));
    assert.deepEqual(fremd(nachher), fremd(vorher));
    assert.notEqual(nachher, vorher);
  } finally {
    rmSync(verzeichnis, { recursive: true, force: true });
  }
});

for (const [stand, ausgangsfassung] of AUSGANGSFASSUNGEN) {
  test(`betriebsdateienNachziehen behaelt die Form von bausteine.json (${stand})`, () => {
    const verzeichnis = wegwerfkopie(ausgangsfassung);
    try {
      const fassung = fassungIn(verzeichnis);
      const ziel = zielNach(fassung);
      const vorher = lies(verzeichnis, 'scripts', 'bausteine.json');
      betriebsdateienNachziehen(verzeichnis, ziel);
      const nachher = lies(verzeichnis, 'scripts', 'bausteine.json');

      assert.equal(nachher, vorher.replaceAll(fassung, ziel));
    } finally {
      rmSync(verzeichnis, { recursive: true, force: true });
    }
  });
}

for (const [stand, ausgangsfassung] of AUSGANGSFASSUNGEN) {
  test(`betriebsdateienNachziehen zieht auch nach, wenn der Tag hinter VERSION zurueckliegt (${stand})`, () => {
    const verzeichnis = wegwerfkopie(ausgangsfassung);
    try {
      // Zwischen zwei Releases heben mehrere `patch`-Bumps VERSION an, ohne die Betriebsdateien zu
      // beruehren. Der naechste `minor` trifft die Zeile also mit einer aelteren Fassung an.
      const fassung = fassungIn(verzeichnis);
      const ziel = zielNach(fassung);
      ersetzeGenau(
        join(verzeichnis, 'docker-compose.yml'),
        `${GHCR_ANWENDUNG}:${fassung}\n`,
        `${GHCR_ANWENDUNG}:0.9.7\n`,
      );

      betriebsdateienNachziehen(verzeichnis, ziel);

      assert.ok(lies(verzeichnis, 'docker-compose.yml').includes(`image: ${GHCR_ANWENDUNG}:${ziel}\n`));
    } finally {
      rmSync(verzeichnis, { recursive: true, force: true });
    }
  });
}

// --- Fehlende Stellen ------------------------------------------------------

for (const [was, verstuemmeln] of [
  [
    'die Anwendungs-Zeile in docker-compose.yml',
    (verzeichnis) => {
      const fassung = fassungIn(verzeichnis);
      ersetzeGenau(join(verzeichnis, 'docker-compose.yml'), `${GHCR_ANWENDUNG}:${fassung}\n`, 'manban:lokal\n');
    },
  ],
  [
    'die Sicherungs-Zeile in docker-compose.backup.yml',
    (verzeichnis) => {
      const fassung = fassungIn(verzeichnis);
      ersetzeGenau(
        join(verzeichnis, 'docker-compose.backup.yml'),
        `${GHCR_SICHERUNG}:${fassung}\n`,
        'manban-backup:lokal\n',
      );
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
  for (const [stand, ausgangsfassung] of AUSGANGSFASSUNGEN) {
    test(`betriebsdateienNachziehen scheitert, wenn ${was} fehlt (${stand})`, () => {
      const verzeichnis = wegwerfkopie(ausgangsfassung);
      try {
        // Das Ziel vor dem Verstuemmeln: Danach fehlt womoeglich der Baustein, aus dem es kommt.
        const ziel = zielNach(fassungIn(verzeichnis));
        verstuemmeln(verzeichnis);
        assert.throws(() => betriebsdateienNachziehen(verzeichnis, ziel), /nicht gefunden/);
      } finally {
        rmSync(verzeichnis, { recursive: true, force: true });
      }
    });
  }
}
