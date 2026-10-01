import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import { bausteineLesen, abbilderAusJava } from './bezugspruefung.mjs';

// Haelt renovate.json gegen den Bestand (Issue #1331, Plan #1295, E9/E24): Die beiden
// customManagers muessen jedes Abbild der Bausteinliste und der Testcontainers-Zeichenketten
// treffen, sonst hoebe ein Aktualisierungs-PR die Fundstellen eines Abbilds nicht gemeinsam an.

const HIER = dirname(fileURLToPath(import.meta.url));
const WURZEL = join(HIER, '..');
const renovate = JSON.parse(readFileSync(join(WURZEL, 'renovate.json'), 'utf-8'));
const bausteine = bausteineLesen(join(WURZEL, 'scripts', 'bausteine.json'));

/** `managerFilePatterns` in Regex-Form (`/…/`) als RegExp. */
function dateimuster(manager) {
  return manager.managerFilePatterns.map((muster) => {
    assert.match(muster, /^\/.+\/$/, `Muster ${muster} steht nicht in Regex-Form`);
    return new RegExp(muster.slice(1, -1));
  });
}

function alleDateien(verzeichnis) {
  const gefunden = [];
  for (const eintrag of readdirSync(verzeichnis)) {
    if (eintrag === 'node_modules' || eintrag === '.git' || eintrag === 'target') continue;
    const pfad = join(verzeichnis, eintrag);
    if (statSync(pfad).isDirectory()) gefunden.push(...alleDateien(pfad));
    else gefunden.push(relative(WURZEL, pfad).split(sep).join('/'));
  }
  return gefunden;
}

const DATEIEN = alleDateien(WURZEL);

/** Wendet einen customManager an wie Renovate: Dateien per Muster, Abhaengigkeiten per matchStrings. */
function treffer(manager) {
  const muster = dateimuster(manager);
  const dateien = DATEIEN.filter((datei) => muster.some((m) => m.test(datei)));
  const abhaengigkeiten = [];
  for (const datei of dateien) {
    const text = readFileSync(join(WURZEL, datei), 'utf-8');
    for (const quelle of manager.matchStrings) {
      for (const t of text.matchAll(new RegExp(quelle, 'g'))) {
        abhaengigkeiten.push({ datei, ganz: t[0], ...t.groups });
      }
    }
  }
  return { dateien, abhaengigkeiten };
}

function manager(datei) {
  const gefunden = renovate.customManagers.filter((m) =>
    dateimuster(m).some((muster) => muster.test(datei)),
  );
  assert.equal(gefunden.length, 1, `genau ein customManager fuer ${datei}`);
  return gefunden[0];
}

test('Grundschalter: kein Automerge, Dashboard, Digest-Bindung, Actions aus', () => {
  assert.equal(renovate.automerge, false);
  assert.equal(renovate.dependencyDashboard, true);
  assert.equal(renovate.pinDigests, true);
  assert.deepEqual(renovate['github-actions'], { enabled: false });
  assert.ok(renovate.ignorePaths.includes('docker-compose.altspeicher.yml'));
  assert.ok(renovate.ignorePaths.includes('**/node_modules/**'));
});

test('ignorePaths nimmt src/test nicht aus, sonst saehe der Java-Manager nichts', () => {
  for (const pfad of renovate.ignorePaths) {
    assert.ok(!/(^|\/)tests?\//.test(pfad), `${pfad} schloesse die Testcontainers-Abbilder aus`);
  }
});

test('genau vier Gruppen, jede hoechstens woechentlich', () => {
  const gruppen = renovate.packageRules.filter((r) => r.groupName);
  assert.deepEqual(gruppen.map((r) => r.groupName).sort(), [
    'Backend',
    'Basisabbilder',
    'Dokumentationsseite',
    'Frontend',
  ]);
  for (const gruppe of gruppen) {
    assert.deepEqual(gruppe.schedule, ['before 6am on monday'], gruppe.groupName);
  }
  assert.equal(renovate.timezone, 'Europe/Berlin');
});

test('die eigenen Abbilder sind ausgenommen, und die Regel steht hinter den Gruppen', () => {
  const index = renovate.packageRules.findIndex((r) => r.enabled === false);
  assert.ok(index >= 0, 'Regel fuer die eigenen Abbilder fehlt');
  const regel = renovate.packageRules[index];
  const muster = regel.matchPackageNames.map((n) => new RegExp(n.slice(1, -1)));
  for (const name of ['ghcr.io/mannewolff/kanban-kit', 'ghcr.io/mannewolff/kanban-kit-backup']) {
    assert.ok(muster.some((m) => m.test(name)), name);
  }
  assert.ok(!muster.some((m) => m.test('postgres')));
  const letzteGruppe = renovate.packageRules.findLastIndex((r) => r.groupName);
  assert.ok(index > letzteGruppe);
});

test('zwei customManagers: bausteine.json und src/test/**/*.java', () => {
  assert.equal(renovate.customManagers.length, 2);
  for (const m of renovate.customManagers) {
    assert.equal(m.customType, 'regex');
    assert.equal(m.datasourceTemplate, 'docker');
  }
  manager('scripts/bausteine.json');
  manager('src/test/java/org/mwolff/manban/AbstractIntegrationTest.java');
});

test('der JSON-Manager trifft jedes beziehbare Abbild der Bausteinliste samt Digest und Fassung', () => {
  const { abhaengigkeiten } = treffer(manager('scripts/bausteine.json'));
  const erwartet = bausteine.filter((b) => b.art === 'abbild' && b.pruefung === 'bezug');
  assert.deepEqual(
    abhaengigkeiten.map((a) => `${a.depName}:${a.currentValue}`).sort(),
    erwartet.map((b) => b.bezugsstelle).sort(),
  );
  for (const baustein of erwartet) {
    const a = abhaengigkeiten.find((x) => `${x.depName}:${x.currentValue}` === baustein.bezugsstelle);
    assert.equal(a.currentDigest, baustein.digest, baustein.name);
    // Renovate ersetzt currentValue im ganzen Treffer: Steht die Fassung darin, hebt derselbe
    // Ersatz bezugsstelle und fassung gemeinsam an.
    assert.equal(baustein.fassung, a.currentValue, baustein.name);
    assert.ok(a.ganz.includes(`"fassung": "${a.currentValue}"`), baustein.name);
  }
});

test('der Java-Manager trifft jedes Testcontainers-Abbild, und jedes steht in der Bausteinliste', () => {
  const javaManager = manager('src/test/java/org/mwolff/manban/AbstractIntegrationTest.java');
  const { dateien, abhaengigkeiten } = treffer(javaManager);
  const bestand = new Set();
  for (const datei of dateien) {
    for (const abbild of abbilderAusJava(readFileSync(join(WURZEL, datei), 'utf-8'))) {
      bestand.add(abbild);
    }
  }
  assert.ok(bestand.size > 0);
  const gefunden = new Set(abhaengigkeiten.map((a) => `${a.depName}:${a.currentValue}`));
  assert.deepEqual([...gefunden].sort(), [...bestand].sort());
  const eingetragen = new Set(bausteine.map((b) => b.bezugsstelle));
  for (const abbild of gefunden) assert.ok(eingetragen.has(abbild), abbild);
});
