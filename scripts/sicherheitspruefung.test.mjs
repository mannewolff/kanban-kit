import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

import { bausteineLesen } from './bezugspruefung.mjs';
import {
  SCHWERE,
  FRIST_ZIEL_MS,
  FRIST_GESAMT_MS,
  STANDARD_SPERRDATEIEN,
  HILFE,
  argumenteZerlegen,
  zieleBilden,
  trivyArgumente,
  trivyAusgabeLesen,
  ausnahmenLesen,
  ausnahmeAbgelaufen,
  urteilen,
  zusammenfassung,
  zusammenfassungAusgeben,
  trivyAusfuehren,
  laufen,
} from './sicherheitspruefung.mjs';

const HIER = dirname(fileURLToPath(import.meta.url));
const TESTDATEN = join(HIER, 'testdaten', 'sicherheit');

function fixture(name) {
  return readFileSync(join(TESTDATEN, name), 'utf-8');
}

const ABBILD = { art: 'abbild', name: 'postgres:16.15', referenz: 'postgres:16.15' };
const ARBEITSBAUM = { art: 'arbeitsbaum', name: '.', referenz: '.' };
const SPERRDATEI = { art: 'sperrdatei', name: 'frontend/package-lock.json', referenz: 'frontend/package-lock.json' };

const HEUTE = '2026-10-01';
const KEINE_AUSNAHMEN = { eintraege: [], formfehler: [] };

function ergebnis(ziel, datei) {
  return { ziel, befunde: trivyAusgabeLesen(fixture(datei)) };
}

function ausnahmen(liste) {
  return ausnahmenLesen(JSON.stringify({ ausnahmen: liste }));
}

function texte(eintraege) {
  return eintraege.map((e) => e.text);
}

// --- Grundwerte -----------------------------------------------------------

test('schwer sind genau die beiden hoechsten Schweregrade', () => {
  assert.deepEqual(SCHWERE, ['CRITICAL', 'HIGH']);
});

test('die Gesamtfrist ist groesser als die Frist je Ziel', () => {
  assert.ok(FRIST_GESAMT_MS > FRIST_ZIEL_MS);
});

// --- Regel 1–3: Schwachstellen --------------------------------------------

test('Regel 1: CRITICAL und HIGH mit Korrektur sperren', () => {
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], KEINE_AUSNAHMEN, { heute: HEUTE });
  const gesperrt = texte(urteil.sperrend);
  assert.equal(gesperrt.length, 2);
  assert.match(gesperrt[0], /CVE-2026-1001/);
  assert.match(gesperrt[0], /CRITICAL/);
  assert.match(gesperrt[0], /libssl3/);
  assert.match(gesperrt[0], /3\.0\.16-1/);
  assert.match(gesperrt[1], /CVE-2026-1002/);
  assert.match(gesperrt[1], /HIGH/);
  assert.ok(urteil.sperrend.every((e) => e.ziel === 'postgres:16.15'));
});

test('Regel 2: schwer ohne Korrektur sperrt nicht und steht in der Zusammenfassung', () => {
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], KEINE_AUSNAHMEN, { heute: HEUTE });
  assert.ok(!texte(urteil.sperrend).some((t) => t.includes('CVE-2026-1003')));
  assert.equal(urteil.ohneKorrektur.length, 1);
  assert.match(urteil.ohneKorrektur[0].text, /CVE-2026-1003/);
  assert.match(urteil.ohneKorrektur[0].text, /perl-base/);
  assert.match(zusammenfassung(urteil), /CVE-2026-1003/);
});

test('Regel 3: MEDIUM sperrt nicht, auch mit Korrektur', () => {
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], KEINE_AUSNAHMEN, { heute: HEUTE });
  assert.ok(!texte(urteil.sperrend).some((t) => t.includes('CVE-2026-1004')));
  assert.ok(!texte(urteil.ohneKorrektur).some((t) => t.includes('CVE-2026-1004')));
});

test('eine leere FixedVersion zaehlt als keine Korrektur', () => {
  const ausgabe = JSON.stringify({
    Results: [{ Target: 'x', Vulnerabilities: [
      { VulnerabilityID: 'CVE-1', PkgName: 'p', Severity: 'HIGH', FixedVersion: '' },
    ] }],
  });
  const urteil = urteilen([{ ziel: ABBILD, befunde: trivyAusgabeLesen(ausgabe) }], KEINE_AUSNAHMEN, { heute: HEUTE });
  assert.equal(urteil.sperrend.length, 0);
  assert.equal(urteil.ohneKorrektur.length, 1);
});

// --- Regel 4–5: Geheimnisse -----------------------------------------------

test('Regel 4: ein Geheimnis sperrt, benannt mit Regel und Datei', () => {
  const urteil = urteilen([ergebnis(ARBEITSBAUM, 'arbeitsbaum-geheimnisse.json')], KEINE_AUSNAHMEN, { heute: HEUTE });
  const gesperrt = texte(urteil.sperrend);
  assert.equal(gesperrt.length, 2);
  assert.ok(gesperrt.some((t) => t.includes('generic-password') && t.includes('.env.example')));
  assert.ok(gesperrt.some((t) => t.includes('aws-access-key-id') && t.includes('src/main/resources/geheim.properties')));
});

test('Regel 5: ein als fehlalarm eingetragenes Geheimnis sperrt nicht', () => {
  const liste = ausnahmen([
    { kennung: 'generic-password', ziel: '.env.example', begruendung: 'Beispielwert der Vorlage', art: 'fehlalarm' },
  ]);
  const urteil = urteilen([ergebnis(ARBEITSBAUM, 'arbeitsbaum-geheimnisse.json')], liste, { heute: HEUTE });
  assert.equal(urteil.sperrend.length, 1);
  assert.match(urteil.sperrend[0].text, /aws-access-key-id/);
  assert.equal(urteil.genutzt.length, 1);
  assert.match(urteil.genutzt[0].text, /Fehlalarm/);
});

test('ein Fehlalarm-Eintrag fuer eine andere Datei nimmt das Geheimnis nicht aus', () => {
  const liste = ausnahmen([
    { kennung: 'generic-password', ziel: 'anderswo.env', begruendung: 'Beispiel', art: 'fehlalarm' },
  ]);
  const urteil = urteilen([ergebnis(ARBEITSBAUM, 'arbeitsbaum-geheimnisse.json')], liste, { heute: HEUTE });
  assert.equal(urteil.sperrend.length, 2);
});

test('ein Schwachstellen-Eintrag nimmt ein Geheimnis nicht aus', () => {
  const liste = ausnahmen([
    { kennung: 'generic-password', ziel: '.env.example', begruendung: 'falsch eingetragen', ablauf: '2099-01-01' },
  ]);
  const urteil = urteilen([ergebnis(ARBEITSBAUM, 'arbeitsbaum-geheimnisse.json')], liste, { heute: HEUTE });
  assert.equal(urteil.sperrend.length, 2);
  assert.equal(urteil.hinweise.length, 1);
});

// --- Regel 6–7: Ausnahmen mit Ablauf --------------------------------------

test('Regel 6: eine gueltige Ausnahme sperrt nicht und erscheint mit Ablaufdatum', () => {
  const liste = ausnahmen([
    { kennung: 'CVE-2026-1001', ziel: 'postgres:16.15', begruendung: 'Upstream liefert naechste Woche', ablauf: '2026-10-15' },
  ]);
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], liste, { heute: HEUTE });
  assert.ok(!texte(urteil.sperrend).some((t) => t.includes('CVE-2026-1001')));
  assert.equal(urteil.genutzt.length, 1);
  assert.match(urteil.genutzt[0].text, /CVE-2026-1001/);
  assert.match(urteil.genutzt[0].text, /2026-10-15/);
  assert.match(urteil.genutzt[0].text, /Upstream liefert naechste Woche/);
  assert.match(zusammenfassung(urteil), /2026-10-15/);
});

test('eine Ausnahme gilt einschliesslich ihres Ablauftags', () => {
  assert.equal(ausnahmeAbgelaufen({ ablauf: '2026-10-01' }, '2026-10-01'), false);
  assert.equal(ausnahmeAbgelaufen({ ablauf: '2026-09-30' }, '2026-10-01'), true);
});

test('Regel 7: eine abgelaufene Ausnahme sperrt — massgeblich ist der uebergebene Pruefzeitpunkt', () => {
  const liste = ausnahmen([
    { kennung: 'CVE-2026-1001', ziel: 'postgres:16.15', begruendung: 'Upstream', ablauf: '2026-10-15' },
  ]);
  const vorher = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], liste, { heute: '2026-10-15' });
  const danach = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], liste, { heute: '2026-10-16' });
  assert.ok(!texte(vorher.sperrend).some((t) => t.includes('CVE-2026-1001')));
  const gesperrt = texte(danach.sperrend).find((t) => t.includes('CVE-2026-1001'));
  assert.ok(gesperrt);
  assert.match(gesperrt, /abgelaufen am 2026-10-15/);
  assert.equal(danach.genutzt.length, 0);
});

test('Regel 7 im Lauf: laufen leitet den Pruefzeitpunkt aus dem injizierten jetzt ab', async () => {
  const liste = ausnahmen([
    { kennung: 'CVE-2026-1001', ziel: 'postgres:16.15', begruendung: 'Upstream', ablauf: '2026-10-15' },
    { kennung: 'CVE-2026-1002', ziel: 'postgres:16.15', begruendung: 'Upstream', ablauf: '2026-12-31' },
  ]);
  const optionen = (iso) => ({
    ausfuehren: async () => fixture('abbild-befunde.json'),
    ausnahmen: liste,
    jetzt: () => Date.parse(iso),
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal((await laufen([ABBILD], optionen('2026-10-15T23:00:00Z'))).exitcode, 0);
  assert.equal((await laufen([ABBILD], optionen('2026-10-16T00:30:00Z'))).exitcode, 1);
});

test('eine Ausnahme fuer ein anderes Ziel greift nicht', () => {
  const liste = ausnahmen([
    { kennung: 'CVE-2026-1001', ziel: 'caddy:2.11.4', begruendung: 'anderes Abbild', ablauf: '2026-12-31' },
  ]);
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], liste, { heute: HEUTE });
  assert.ok(texte(urteil.sperrend).some((t) => t.includes('CVE-2026-1001')));
});

test('ein Fehlalarm-Eintrag nimmt keine Schwachstelle aus', () => {
  const liste = ausnahmen([
    { kennung: 'CVE-2026-1001', ziel: 'postgres:16.15', begruendung: 'kein Fehlalarm', art: 'fehlalarm' },
  ]);
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], liste, { heute: HEUTE });
  assert.ok(texte(urteil.sperrend).some((t) => t.includes('CVE-2026-1001')));
});

// --- Regel 8: Formfehler --------------------------------------------------

test('Regel 8: Schwachstellen-Eintrag ohne begruendung ist ein Formfehler und sperrt', () => {
  const liste = ausnahmen([{ kennung: 'CVE-2026-1001', ziel: 'postgres:16.15', ablauf: '2026-12-31' }]);
  assert.equal(liste.formfehler.length, 1);
  assert.match(liste.formfehler[0], /begruendung/);
  const urteil = urteilen([], liste, { heute: HEUTE });
  assert.ok(texte(urteil.sperrend).some((t) => t.includes('begruendung')));
});

test('Regel 8: Schwachstellen-Eintrag ohne ablauf ist ein Formfehler, und sein Befund sperrt weiter', () => {
  const liste = ausnahmen([{ kennung: 'CVE-2026-1001', ziel: 'postgres:16.15', begruendung: 'Grund' }]);
  assert.equal(liste.formfehler.length, 1);
  assert.match(liste.formfehler[0], /ablauf/);
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], liste, { heute: HEUTE });
  assert.ok(texte(urteil.sperrend).some((t) => t.includes('CVE-2026-1001')));
  assert.ok(texte(urteil.sperrend).some((t) => t.includes('Ausnahmeliste')));
});

test('Regel 8: ablauf ausserhalb von JJJJ-MM-TT ist ein Formfehler', () => {
  for (const ablauf of ['15.10.2026', '2026-13-01', '2026-02-30', 20261015]) {
    const liste = ausnahmen([{ kennung: 'CVE-1', ziel: 'x', begruendung: 'Grund', ablauf }]);
    assert.equal(liste.formfehler.length, 1, `ablauf ${ablauf}`);
  }
});

test('Regel 8: Geheimnis-Eintrag mit ablauf ist ein Formfehler und sperrt', () => {
  const liste = ausnahmen([
    { kennung: 'generic-password', ziel: '.env.example', begruendung: 'Beispiel', art: 'fehlalarm', ablauf: '2026-12-31' },
  ]);
  assert.equal(liste.formfehler.length, 1);
  assert.match(liste.formfehler[0], /ablauf/);
  const urteil = urteilen([ergebnis(ARBEITSBAUM, 'arbeitsbaum-geheimnisse.json')], liste, { heute: HEUTE });
  assert.ok(texte(urteil.sperrend).some((t) => t.includes('generic-password') && t.includes('.env.example')));
});

test('Regel 8: Geheimnis-Eintrag ohne begruendung ist ein Formfehler', () => {
  const liste = ausnahmen([{ kennung: 'generic-password', ziel: '.env.example', art: 'fehlalarm' }]);
  assert.equal(liste.formfehler.length, 1);
  assert.match(liste.formfehler[0], /begruendung/);
});

test('Eintrag ohne kennung, ohne ziel oder mit unbekannter art ist ein Formfehler', () => {
  const liste = ausnahmen([
    { ziel: 'x', begruendung: 'Grund', ablauf: '2026-12-31' },
    { kennung: 'CVE-1', begruendung: 'Grund', ablauf: '2026-12-31' },
    { kennung: 'CVE-1', ziel: 'x', begruendung: 'Grund', art: 'dauerhaft' },
    'kein Objekt',
  ]);
  assert.equal(liste.formfehler.length, 4);
  assert.equal(liste.eintraege.length, 0);
});

test('eine unlesbare Ausnahmeliste ist ein Formfehler und kein Absturz', () => {
  assert.equal(ausnahmenLesen('{ kaputt').formfehler.length, 1);
  assert.equal(ausnahmenLesen('{"ausnahmen": {}}').formfehler.length, 1);
  assert.equal(ausnahmenLesen('null').formfehler.length, 1);
});

// --- Regel 9: unbenutzte Eintraege ----------------------------------------

test('Regel 9: ein Eintrag ohne passenden Befund ist nur ein Hinweis', () => {
  const liste = ausnahmen([
    { kennung: 'CVE-2020-9999', ziel: 'postgres:16.15', begruendung: 'laengst behoben', ablauf: '2026-12-31' },
    { kennung: 'CVE-2020-9998', ziel: 'postgres:16.15', begruendung: 'auch abgelaufen ohne Befund', ablauf: '2020-01-01' },
  ]);
  const urteil = urteilen([ergebnis(SPERRDATEI, 'sperrdatei-sauber.json')], liste, { heute: HEUTE });
  assert.equal(urteil.sperrend.length, 0);
  assert.equal(urteil.hinweise.length, 2);
  assert.match(urteil.hinweise[0].text, /CVE-2020-9999/);
});

// --- Regel 10: Zusammenfassung auch bei Gruen ------------------------------

test('Regel 10: die Zusammenfassung wird auch bei gruenem Lauf geschrieben', async () => {
  const geschrieben = [];
  const { exitcode } = await laufen([SPERRDATEI], {
    ausfuehren: async () => fixture('sperrdatei-sauber.json'),
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: (text) => geschrieben.push(text),
    umgebung: {},
  });
  assert.equal(exitcode, 0);
  const text = geschrieben.join('\n');
  assert.match(text, /## Sicherheitspruefung/);
  assert.match(text, /keine sperrenden Befunde/);
  assert.match(text, /frontend\/package-lock\.json/);
});

test('eine Ausgabe ohne Results ist ein sauberes Ziel', () => {
  const befunde = trivyAusgabeLesen(fixture('sbom-ohne-ergebnisse.json'));
  assert.deepEqual(befunde, { schwachstellen: [], geheimnisse: [] });
});

test('eine Ausgabe, die kein Trivy-JSON ist, wird abgelehnt', () => {
  assert.throws(() => trivyAusgabeLesen('Fatal error: kaputt'));
  assert.throws(() => trivyAusgabeLesen('[]'), /kein Trivy-Bericht/);
  assert.throws(() => trivyAusgabeLesen('null'), /kein Trivy-Bericht/);
});

// --- Regel 11: Fristen und Fehlerpfade ------------------------------------

test('Regel 11: eine gerissene Frist je Ziel ist ein benannter, sperrender Befund', async () => {
  const { urteil, exitcode } = await laufen([ABBILD, SPERRDATEI], {
    ausfuehren: (argumente) =>
      argumente.includes('image') ? new Promise(() => {}) : Promise.resolve(fixture('sperrdatei-sauber.json')),
    ausnahmen: KEINE_AUSNAHMEN,
    fristMs: 20,
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal(exitcode, 1);
  assert.equal(urteil.sperrend.length, 1);
  assert.equal(urteil.sperrend[0].ziel, 'postgres:16.15');
  assert.match(urteil.sperrend[0].text, /nicht geprueft/);
  assert.match(urteil.sperrend[0].text, /Zeitueberschreitung/);
});

test('Regel 11: das Abbrechen-Signal erreicht den Werkzeugaufruf, wenn die Frist reisst', async () => {
  let signal;
  await laufen([ABBILD], {
    ausfuehren: (_argumente, optionen) => {
      signal = optionen.signal;
      return new Promise(() => {});
    },
    ausnahmen: KEINE_AUSNAHMEN,
    fristMs: 10,
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal(signal.aborted, true);
});

test('Regel 11: nach gerissener Gesamtfrist wird jedes weitere Ziel benannt und nicht mehr geprueft', async () => {
  let uhr = 0;
  const aufgerufen = [];
  const { urteil, exitcode } = await laufen([SPERRDATEI, ABBILD, ARBEITSBAUM], {
    ausfuehren: async (argumente) => {
      aufgerufen.push(argumente.at(-1));
      uhr += 1000;
      return fixture('sperrdatei-sauber.json');
    },
    ausnahmen: KEINE_AUSNAHMEN,
    gesamtFristMs: 500,
    jetzt: () => uhr,
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal(exitcode, 1);
  assert.deepEqual(aufgerufen, ['frontend/package-lock.json']);
  assert.deepEqual(urteil.sperrend.map((e) => e.ziel), ['postgres:16.15', '.']);
  assert.ok(urteil.sperrend.every((e) => /Gesamtfrist/.test(e.text)));
});

test('ein fehlgeschlagener Werkzeugaufruf ist ein sperrender Befund "nicht geprueft"', async () => {
  const { urteil, exitcode } = await laufen([ABBILD], {
    ausfuehren: async () => {
      throw new Error('trivy endete mit 1: unauthorized');
    },
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal(exitcode, 1);
  assert.match(urteil.sperrend[0].text, /nicht geprueft: trivy endete mit 1: unauthorized/);
});

test('eine unlesbare Werkzeugausgabe ist ein sperrender Befund "nicht geprueft"', async () => {
  const { urteil } = await laufen([ABBILD], {
    ausfuehren: async () => 'kein JSON',
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal(urteil.sperrend.length, 1);
  assert.match(urteil.sperrend[0].text, /nicht geprueft/);
});

test('ohne ein einziges Ziel sperrt der Lauf, statt Sicherheit zu behaupten', async () => {
  const { urteil, exitcode } = await laufen([], {
    ausfuehren: async () => '{}',
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal(exitcode, 1);
  assert.match(urteil.sperrend[0].text, /kein Ziel/);
});

test('trivyAusfuehren meldet ein fehlendes Werkzeug als Fehler', async () => {
  await assert.rejects(
    trivyAusfuehren(['--version'], { befehl: 'sicherheitspruefung-gibt-es-nicht' }),
    /sicherheitspruefung-gibt-es-nicht/,
  );
});

test('trivyAusfuehren liefert die Standardausgabe bei Rueckgabewert 0', async () => {
  const ausgabe = await trivyAusfuehren(['-e', 'process.stdout.write("{}")'], { befehl: process.execPath });
  assert.equal(ausgabe, '{}');
});

test('trivyAusfuehren meldet einen Rueckgabewert ungleich 0 mit der Fehlerausgabe', async () => {
  await assert.rejects(
    trivyAusfuehren(['-e', 'process.stderr.write("kaputt"); process.exit(3)'], { befehl: process.execPath }),
    /endete mit 3: kaputt/,
  );
});

test('trivyAusfuehren bricht auf das Signal hin ab', async () => {
  const abbruch = new AbortController();
  const lauf = trivyAusfuehren(['-e', 'setTimeout(() => {}, 60000)'], {
    befehl: process.execPath,
    signal: abbruch.signal,
  });
  abbruch.abort();
  await assert.rejects(lauf);
});

// --- Regel 12: Zielliste --------------------------------------------------

test('Regel 12: aus der Bausteinliste nur ausgelieferte fremde Abbilder mit Pruefart bezug', () => {
  const bausteine = bausteineLesen(join(TESTDATEN, 'bausteine.json'));
  const ziele = zieleBilden(bausteine, {
    abbilder: [],
    sbom: null,
    sperrdateien: [],
    arbeitsbaum: null,
  });
  assert.deepEqual(ziele, [
    {
      art: 'abbild',
      name: 'postgres:16.15',
      referenz: 'postgres:16.15@sha256:1a6ab3f5345eb6dbe04a1349529caabdb0ab09293a09590fad07b2246bfa4b54',
    },
    { art: 'abbild', name: 'caddy:2.11.4', referenz: 'caddy:2.11.4' },
  ]);
});

test('Regel 12: dazu die per Argument uebergebenen Ziele', () => {
  const bausteine = bausteineLesen(join(TESTDATEN, 'bausteine.json'));
  const ziele = zieleBilden(bausteine, {
    abbilder: ['kanban-kit:pruefung', 'kanban-kit-backup:pruefung'],
    sbom: 'target/bom.json',
    sperrdateien: ['frontend/package-lock.json', 'docs-site/package-lock.json'],
    arbeitsbaum: '.',
  });
  assert.deepEqual(
    ziele.map((z) => `${z.art} ${z.name}`),
    [
      'abbild postgres:16.15',
      'abbild caddy:2.11.4',
      'abbild kanban-kit:pruefung',
      'abbild kanban-kit-backup:pruefung',
      'sbom target/bom.json',
      'sperrdatei frontend/package-lock.json',
      'sperrdatei docs-site/package-lock.json',
      'arbeitsbaum .',
    ],
  );
});

test('Regel 12: die echte Bausteinliste liefert kein Testabbild und kein eigenes Abbild', () => {
  const bausteine = bausteineLesen(join(HIER, 'bausteine.json'));
  const namen = zieleBilden(bausteine, { abbilder: [], sbom: null, sperrdateien: [], arbeitsbaum: null })
    .map((z) => z.name);
  assert.ok(namen.length > 0);
  assert.ok(!namen.some((n) => n.includes('mailpit')));
  assert.ok(!namen.some((n) => n.startsWith('ghcr.io/mannewolff/')));
});

test('je Zielart der passende Trivy-Aufruf, immer JSON mit Rueckgabewert 0', () => {
  const gemeinsam = ['--format', 'json', '--exit-code', '0'];
  assert.deepEqual(trivyArgumente({ art: 'abbild', referenz: 'postgres:16.15@sha256:ab' }), [
    'image', '--scanners', 'vuln', ...gemeinsam, 'postgres:16.15@sha256:ab',
  ]);
  assert.deepEqual(trivyArgumente({ art: 'sbom', referenz: 'target/bom.json' }), [
    'sbom', ...gemeinsam, 'target/bom.json',
  ]);
  assert.deepEqual(trivyArgumente({ art: 'sperrdatei', referenz: 'frontend/package-lock.json' }), [
    'fs', '--scanners', 'vuln', ...gemeinsam, 'frontend/package-lock.json',
  ]);
  assert.deepEqual(trivyArgumente({ art: 'arbeitsbaum', referenz: '.' }), [
    'fs', '--scanners', 'secret', ...gemeinsam, '.',
  ]);
});

test('Argumente: Standard-Sperrdateien und Arbeitsbaum, eigene Abbilder und Stueckliste per Argument', () => {
  assert.deepEqual(argumenteZerlegen([]), {
    hilfe: false,
    abbilder: [],
    sbom: null,
    sperrdateien: STANDARD_SPERRDATEIEN,
    arbeitsbaum: '.',
    bausteine: null,
    ausnahmen: null,
    fehler: [],
  });
  const zerlegt = argumenteZerlegen([
    '--abbild', 'a:1', '--abbild', 'b:1', '--sbom', 'target/bom.json',
    '--sperrdatei', 'x/package-lock.json', '--arbeitsbaum', 'wurzel',
    '--bausteine', 'b.json', '--ausnahmen', 'a.json',
  ]);
  assert.deepEqual(zerlegt.abbilder, ['a:1', 'b:1']);
  assert.equal(zerlegt.sbom, 'target/bom.json');
  assert.deepEqual(zerlegt.sperrdateien, ['x/package-lock.json']);
  assert.equal(zerlegt.arbeitsbaum, 'wurzel');
  assert.equal(zerlegt.bausteine, 'b.json');
  assert.equal(zerlegt.ausnahmen, 'a.json');
  assert.deepEqual(zerlegt.fehler, []);
});

test('Argumente: Hilfe, unbekannte Schalter und fehlende Werte', () => {
  assert.equal(argumenteZerlegen(['--hilfe']).hilfe, true);
  assert.equal(argumenteZerlegen(['-h']).hilfe, true);
  assert.match(argumenteZerlegen(['--gibts-nicht']).fehler[0], /--gibts-nicht/);
  assert.match(argumenteZerlegen(['--sbom']).fehler[0], /--sbom ohne Wert/);
  assert.match(argumenteZerlegen(['--abbild', '--sbom', 'x']).fehler[0], /--abbild ohne Wert/);
});

test('--hilfe beschreibt die Argumente und endet mit 0', () => {
  const lauf = spawnSync(process.execPath, [join(HIER, 'sicherheitspruefung.mjs'), '--hilfe'], {
    encoding: 'utf-8',
  });
  assert.equal(lauf.status, 0);
  for (const schalter of ['--abbild', '--sbom', '--sperrdatei', '--arbeitsbaum', '--bausteine', '--ausnahmen']) {
    assert.ok(HILFE.includes(schalter), schalter);
    assert.ok(lauf.stdout.includes(schalter), schalter);
  }
});

test('ein unbekannter Schalter endet mit 2 und nennt die Hilfe', () => {
  const lauf = spawnSync(process.execPath, [join(HIER, 'sicherheitspruefung.mjs'), '--gibts-nicht'], {
    encoding: 'utf-8',
  });
  assert.equal(lauf.status, 2);
  assert.match(lauf.stderr, /--gibts-nicht/);
});

// --- Regel 13: Ausgabe und Reihenfolge ------------------------------------

test('Regel 13: Reihenfolge sperrend, ohne Korrektur, genutzte Ausnahmen, Hinweise', () => {
  const liste = ausnahmen([
    { kennung: 'CVE-2026-1002', ziel: 'postgres:16.15', begruendung: 'Upstream', ablauf: '2026-12-31' },
    { kennung: 'CVE-2020-9999', ziel: 'postgres:16.15', begruendung: 'alt', ablauf: '2026-12-31' },
  ]);
  const text = zusammenfassung(urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], liste, { heute: HEUTE }));
  const stellen = [
    text.indexOf('### Sperrend'),
    text.indexOf('CVE-2026-1001'),
    text.indexOf('### Schwer ohne Korrektur'),
    text.indexOf('CVE-2026-1003'),
    text.indexOf('### Genutzte Ausnahmen'),
    text.indexOf('CVE-2026-1002'),
    text.indexOf('### Hinweise'),
    text.indexOf('CVE-2020-9999'),
    text.indexOf('### Gepruefte Ziele'),
  ];
  assert.ok(stellen.every((s) => s >= 0), stellen.join(','));
  assert.deepEqual([...stellen].sort((a, b) => a - b), stellen);
  assert.match(text, /1 sperrender Befund/);
});

test('Regel 13: leere Abschnitte sagen "keine", statt zu fehlen', () => {
  const text = zusammenfassung(urteilen([ergebnis(SPERRDATEI, 'sperrdatei-sauber.json')], KEINE_AUSNAHMEN, { heute: HEUTE }));
  for (const kopf of ['### Sperrend', '### Schwer ohne Korrektur (sichtbar, sperrt nicht)', '### Genutzte Ausnahmen', '### Hinweise']) {
    assert.ok(text.includes(`${kopf}\n\n- keine`), kopf);
  }
});

test('Regel 13: mit GITHUB_STEP_SUMMARY wird an die Datei angehaengt', () => {
  const verzeichnis = mkdtempSync(join(tmpdir(), 'sicherheit-'));
  try {
    const datei = join(verzeichnis, 'summary.md');
    writeFileSync(datei, 'vorher\n');
    const ausgegeben = [];
    zusammenfassungAusgeben('## Inhalt\n', {
      umgebung: { GITHUB_STEP_SUMMARY: datei },
      schreiben: (t) => ausgegeben.push(t),
    });
    assert.equal(readFileSync(datei, 'utf-8'), 'vorher\n## Inhalt\n');
    assert.deepEqual(ausgegeben, []);
  } finally {
    rmSync(verzeichnis, { recursive: true, force: true });
  }
});

test('Regel 13: ohne GITHUB_STEP_SUMMARY geht die Zusammenfassung auf die Standardausgabe', () => {
  const ausgegeben = [];
  zusammenfassungAusgeben('## Inhalt\n', { umgebung: {}, schreiben: (t) => ausgegeben.push(t) });
  assert.deepEqual(ausgegeben, ['## Inhalt\n']);
});

test('Markdown-Steuerzeichen aus Werkzeugausgaben brechen die Zusammenfassung nicht', () => {
  const ausgabe = JSON.stringify({
    Results: [{ Target: 'x', Secrets: [{ RuleID: 'r|1', Target: 'a`b|c.txt' }] }],
  });
  const text = zusammenfassung(urteilen([{ ziel: ARBEITSBAUM, befunde: trivyAusgabeLesen(ausgabe) }], KEINE_AUSNAHMEN, { heute: HEUTE }));
  assert.ok(!text.includes('a`b'));
  assert.ok(text.includes('r\\|1'));
});

// --- Regel 14: Rueckgabewert ----------------------------------------------

test('Regel 14: Rueckgabewert 1 bei mindestens einem sperrenden Befund', async () => {
  const { exitcode } = await laufen([ABBILD], {
    ausfuehren: async () => fixture('abbild-befunde.json'),
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal(exitcode, 1);
});

test('Regel 14: Rueckgabewert 0 ohne sperrenden Befund, auch mit sichtbaren Befunden', async () => {
  const liste = ausnahmen([
    { kennung: 'CVE-2026-1001', ziel: 'postgres:16.15', begruendung: 'Upstream', ablauf: '2026-12-31' },
    { kennung: 'CVE-2026-1002', ziel: 'postgres:16.15', begruendung: 'Upstream', ablauf: '2026-12-31' },
  ]);
  const { exitcode, urteil } = await laufen([ABBILD], {
    ausfuehren: async () => fixture('abbild-befunde.json'),
    ausnahmen: liste,
    jetzt: () => Date.parse('2026-10-01T12:00:00Z'),
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal(exitcode, 0);
  assert.equal(urteil.ohneKorrektur.length, 1);
});

test('der Lauf ruft das Werkzeug je Ziel mit den Argumenten der Zielart auf', async () => {
  const aufrufe = [];
  await laufen([ABBILD, ARBEITSBAUM], {
    ausfuehren: async (argumente) => {
      aufrufe.push(argumente);
      return fixture('sperrdatei-sauber.json');
    },
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: () => {},
    umgebung: {},
  });
  assert.deepEqual(aufrufe, [trivyArgumente(ABBILD), trivyArgumente(ARBEITSBAUM)]);
});

// --- echte Ausnahmeliste --------------------------------------------------

test('die echte Ausnahmeliste ist gueltiges JSON und formgueltig', () => {
  const text = readFileSync(join(HIER, 'sicherheitsausnahmen.json'), 'utf-8');
  const roh = JSON.parse(text);
  assert.ok(Array.isArray(roh.ausnahmen));
  assert.ok(roh._hinweis);
  assert.deepEqual(ausnahmenLesen(text).formfehler, []);
});
