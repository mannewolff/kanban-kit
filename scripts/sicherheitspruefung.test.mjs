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
  FRIST_UEBERNOMMEN_TAGE,
  STANDARD_SPERRDATEIEN,
  HILFE,
  argumenteZerlegen,
  zieleBilden,
  referenzBezugsstelle,
  registryTagsHolen,
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
  assert.equal(gesperrt[0], 'CVE-2026-1001 (CRITICAL) in libssl3 [postgres:16.15 (debian 12.11)] — Korrektur in 3.0.16-1');
  assert.ok(urteil.sperrend.every((e) => e.ziel === 'postgres:16.15'));
});

test('jeder Befund nennt seinen Bestandteil — gleiche Kennung in zwei Binaerdateien ergibt zwei Zeilen', () => {
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-go-binaerdateien.json')], KEINE_AUSNAHMEN, { heute: HEUTE });
  const gesperrt = texte(urteil.sperrend);
  assert.deepEqual(gesperrt, [
    'CVE-2026-2001 (HIGH) in stdlib [usr/local/bin/gosu] — Korrektur in 1.24.8',
    'CVE-2026-2001 (HIGH) in stdlib [usr/local/bin/rclone] — Korrektur in 1.24.8',
    'CVE-2026-2002 (CRITICAL) in org.example:lib [Java: app/app.jar/BOOT-INF/lib/lib-1.0.jar] — Korrektur in 1.1',
  ]);
});

test('ein PkgPath gleich dem Target wird nicht doppelt genannt', () => {
  const ausgabe = JSON.stringify({
    Results: [{ Target: 'usr/bin/x', Vulnerabilities: [
      { VulnerabilityID: 'CVE-1', PkgName: 'p', PkgPath: 'usr/bin/x', Severity: 'HIGH', FixedVersion: '2' },
    ] }],
  });
  const urteil = urteilen([{ ziel: ABBILD, befunde: trivyAusgabeLesen(ausgabe) }], KEINE_AUSNAHMEN, { heute: HEUTE });
  assert.deepEqual(texte(urteil.sperrend), ['CVE-1 (HIGH) in p [usr/bin/x] — Korrektur in 2']);
});

test('trivyAusgabeLesen uebernimmt Target als bestandteil und PkgPath, wo vorhanden', () => {
  const { schwachstellen } = trivyAusgabeLesen(fixture('abbild-go-binaerdateien.json'));
  assert.equal(schwachstellen[0].bestandteil, 'usr/local/bin/gosu');
  assert.equal(schwachstellen[0].pfad, null);
  assert.equal(schwachstellen[2].bestandteil, 'Java');
  assert.equal(schwachstellen[2].pfad, 'app/app.jar/BOOT-INF/lib/lib-1.0.jar');
});

test('Regel 2: schwer ohne Korrektur sperrt nicht und steht in der Zusammenfassung', () => {
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], KEINE_AUSNAHMEN, { heute: HEUTE });
  assert.ok(!texte(urteil.sperrend).some((t) => t.includes('CVE-2026-1003')));
  assert.equal(urteil.ohneKorrektur.length, 1);
  assert.match(urteil.ohneKorrektur[0].text, /CVE-2026-1003/);
  assert.match(urteil.ohneKorrektur[0].text, /perl-base/);
  assert.equal(urteil.ohneKorrektur[0].text, 'CVE-2026-1003 (HIGH) in perl-base [postgres:16.15 (debian 12.11)] — keine Korrektur verfuegbar');
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
  assert.deepEqual(befunde, { schwachstellen: [], geheimnisse: [], erstellt: null });
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

/** Tag-Abfrage ohne Netz: Jede Bezugsstelle kennt nur ihren eigenen Tag. */
const NUR_EIGENER_TAG = async (bezugsstelle) => [bezugsstelle.slice(bezugsstelle.lastIndexOf(':') + 1)];

test('Regel 12: aus der Bausteinliste nur ausgelieferte fremde Abbilder mit Pruefart bezug', async () => {
  const bausteine = bausteineLesen(join(TESTDATEN, 'bausteine.json'));
  const ziele = await zieleBilden(bausteine, {
    abbilder: [],
    sbom: null,
    sperrdateien: [],
    arbeitsbaum: null,
  }, { tagsHolen: NUR_EIGENER_TAG });
  assert.deepEqual(ziele, [
    {
      art: 'abbild',
      name: 'postgres:16.15',
      referenz: 'postgres:16.15@sha256:1a6ab3f5345eb6dbe04a1349529caabdb0ab09293a09590fad07b2246bfa4b54',
    },
    {
      art: 'abbild',
      rolle: 'referenz',
      name: 'postgres:16.15 (aktuell: 16.15)',
      referenz: 'postgres:16.15',
      gebunden: 'postgres:16.15',
    },
    { art: 'abbild', name: 'caddy:2.11.4', referenz: 'caddy:2.11.4', verwendung: 'bau' },
  ]);
});

test('Regel 12: dazu die per Argument uebergebenen Ziele', async () => {
  const bausteine = bausteineLesen(join(TESTDATEN, 'bausteine.json'));
  const ziele = await zieleBilden(bausteine, {
    abbilder: ['kanban-kit:pruefung', 'kanban-kit-backup:pruefung'],
    sbom: 'target/bom.json',
    sperrdateien: ['frontend/package-lock.json', 'docs-site/package-lock.json'],
    arbeitsbaum: '.',
  }, { tagsHolen: NUR_EIGENER_TAG });
  assert.deepEqual(
    ziele.map((z) => `${z.art} ${z.name}`),
    [
      'abbild postgres:16.15',
      'abbild postgres:16.15 (aktuell: 16.15)',
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

test('Regel 12: die echte Bausteinliste liefert kein Testabbild und kein eigenes Abbild', async () => {
  const bausteine = bausteineLesen(join(HIER, 'bausteine.json'));
  const namen = (await zieleBilden(bausteine, { abbilder: [], sbom: null, sperrdateien: [], arbeitsbaum: null }, { tagsHolen: NUR_EIGENER_TAG }))
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

// --- Referenzziele fuer uebernommene Bausteine (Issue #1356, Plan #1351 E2/E10/E11) ---

const REFERENZ = {
  art: 'abbild',
  rolle: 'referenz',
  name: 'postgres:16.15 (aktuell: 16.16)',
  referenz: 'postgres:16.16',
  gebunden: 'postgres:16.15',
};

test('Referenz: neuester Tag derselben Hauptlinie und Variante', () => {
  assert.equal(
    referenzBezugsstelle('postgres:16.15-bookworm', ['16.15-bookworm', '16.16-bookworm', '16.16', '17.1-bookworm']),
    'postgres:16.16-bookworm',
  );
  assert.equal(referenzBezugsstelle('chrislusf/seaweedfs:4.47', ['4.47', '4.48', '5.0']), 'chrislusf/seaweedfs:4.48');
});

test('Referenz: ist der eigene Tag der hoechste, ist die Referenz derselbe Tag — auch wenn die Liste ihn nicht nennt', () => {
  assert.equal(referenzBezugsstelle('caddy:2.11.4', ['2.11.3', '2.11.4', '2.11.4-alpine', '3.0']), 'caddy:2.11.4');
  assert.equal(referenzBezugsstelle('caddy:2.11.4', []), 'caddy:2.11.4');
});

test('Referenz: numerisch verglichen, nicht als Text', () => {
  assert.equal(referenzBezugsstelle('postgres:16.9', ['16.9', '16.10', '16.2']), 'postgres:16.10');
});

test('Referenz: die Baunummer nach Unterstrich gehoert nicht zur Variante', () => {
  assert.equal(
    referenzBezugsstelle('eclipse-temurin:25.0.4.1_1-jre', ['25.0.4.1_1-jre', '25.0.5_11-jre', '25.0.5_11-jdk', '26_35-jre']),
    'eclipse-temurin:25.0.5_11-jre',
  );
});

test('Referenz: hinter dem Unterstrich steht eine Build-Nummer — 25_36 ist Java 25.0.0, nicht 25.36 (Issue #1568)', () => {
  assert.equal(
    referenzBezugsstelle('eclipse-temurin:25.0.4.1_1-jre', [
      '25_36-jre',
      '25.0.4.1_1-jre',
      '25.0.4_7-jre',
      '25.0.5_3-jre',
      '25.0.5_3-jdk',
      '26_35-jre',
    ]),
    'eclipse-temurin:25.0.5_3-jre',
  );
});

test('Referenz: ohne neuere Fassung bleibt der gebundene Tag die Referenz, auch neben einem GA-Tag', () => {
  assert.equal(
    referenzBezugsstelle('eclipse-temurin:25.0.4.1_1-jre', ['25_36-jre', '25.0.4.1_1-jre', '25.0.4_7-jre', '25.0.4.1_1-jdk']),
    'eclipse-temurin:25.0.4.1_1-jre',
  );
});

test('Referenz: bei gleicher Fassung entscheidet die Build-Nummer, fehlende Stellen zaehlen als 0', () => {
  assert.equal(referenzBezugsstelle('eclipse-temurin:25.0.5_3-jre', ['25.0.5_11-jre', '25.0.5_2-jre']), 'eclipse-temurin:25.0.5_11-jre');
  assert.equal(referenzBezugsstelle('eclipse-temurin:25_36-jre', ['25.0.0_37-jre']), 'eclipse-temurin:25.0.0_37-jre');
  assert.equal(referenzBezugsstelle('eclipse-temurin:25.0.0_37-jre', ['25_36-jre']), 'eclipse-temurin:25.0.0_37-jre');
});

test('Referenz: Registry mit Port und Repository mit Pfad bleiben erhalten', () => {
  assert.equal(referenzBezugsstelle('localhost:5000/team/abbild:1.2', ['1.3']), 'localhost:5000/team/abbild:1.3');
});

test('Referenz: ein Tag ohne Ziffernanfang hat keine Referenz', () => {
  assert.equal(referenzBezugsstelle('debian:bookworm-20260918-slim', ['bookworm-20261001-slim']), null);
  assert.equal(referenzBezugsstelle('postgres', ['16']), null);
});

test('zieleBilden: fremdes Abbild mit Digest bekommt ein Referenzziel ohne Digest, Bau-Abbilder keines', async () => {
  const bausteine = bausteineLesen(join(TESTDATEN, 'bausteine.json'));
  const abgefragt = [];
  const ziele = await zieleBilden(bausteine, { abbilder: [], sbom: null, sperrdateien: [], arbeitsbaum: null }, {
    tagsHolen: async (bezugsstelle) => {
      abgefragt.push(bezugsstelle);
      return ['16.15', '16.16', '16.16-bookworm', '17.1'];
    },
  });
  assert.deepEqual(abgefragt, ['postgres:16.15']);
  assert.deepEqual(ziele.filter((z) => z.rolle === 'referenz'), [REFERENZ]);
  assert.ok(!ziele.some((z) => z.rolle === 'referenz' && z.gebunden === 'caddy:2.11.4'));
});

test('zieleBilden: ein fremdes Abbild ohne Digest bekommt kein Referenzziel', async () => {
  const bausteine = [{ name: 'X', art: 'abbild', bezugsstelle: 'caddy:2.11.4', pruefung: 'bezug', ausgeliefert: true }];
  const ziele = await zieleBilden(bausteine, { abbilder: [], sbom: null, sperrdateien: [], arbeitsbaum: null }, {
    tagsHolen: async () => assert.fail('keine Abfrage ohne Digest'),
  });
  assert.deepEqual(ziele.map((z) => z.name), ['caddy:2.11.4']);
});

test('zieleBilden: ein Fehler beim Abfragen der Tag-Liste wird zum Referenzziel mit Fehler', async () => {
  const bausteine = bausteineLesen(join(TESTDATEN, 'bausteine.json'));
  const ziele = await zieleBilden(bausteine, { abbilder: [], sbom: null, sperrdateien: [], arbeitsbaum: null }, {
    tagsHolen: async () => {
      throw new Error('Tag-Liste antwortete 503');
    },
  });
  const referenz = ziele.find((z) => z.rolle === 'referenz');
  assert.equal(referenz.name, 'postgres:16.15 (aktuell: unbekannt)');
  assert.equal(referenz.gebunden, 'postgres:16.15');
  assert.equal(referenz.fehler, 'Tag-Liste nicht abrufbar: Tag-Liste antwortete 503');
});

test('zieleBilden: ein Netzfehler nennt seine Ursache', async () => {
  const bausteine = bausteineLesen(join(TESTDATEN, 'bausteine.json'));
  const ziele = await zieleBilden(bausteine, { abbilder: [], sbom: null, sperrdateien: [], arbeitsbaum: null }, {
    tagsHolen: async () => {
      throw new Error('fetch failed', { cause: new Error('getaddrinfo ENOTFOUND registry-1.docker.io') });
    },
  });
  assert.equal(
    ziele.find((z) => z.rolle === 'referenz').fehler,
    'Tag-Liste nicht abrufbar: fetch failed: getaddrinfo ENOTFOUND registry-1.docker.io',
  );
});

test('zieleBilden: ein Tag ohne Ziffernanfang hat kein Referenzziel und erscheint als Hinweis', async () => {
  const bausteine = [{
    name: 'X', art: 'abbild', bezugsstelle: 'beispiel:stabil', pruefung: 'bezug', ausgeliefert: true, digest: 'sha256:ab',
  }];
  const ziele = await zieleBilden(bausteine, { abbilder: [], sbom: null, sperrdateien: [], arbeitsbaum: null }, {
    tagsHolen: async () => ['stabil'],
  });
  assert.equal(ziele.length, 1);
  assert.match(ziele[0].ohneReferenz, /beginnt nicht mit einer Ziffer/);
  const urteil = urteilen([{ ziel: ziele[0], befunde: { schwachstellen: [], geheimnisse: [], erstellt: null } }], KEINE_AUSNAHMEN, { heute: HEUTE });
  assert.deepEqual(urteil.sperrend, []);
  assert.equal(urteil.hinweise.length, 1);
  assert.equal(urteil.hinweise[0].ziel, 'beispiel:stabil');
  assert.match(urteil.hinweise[0].text, /kein Referenzziel/);
});

test('zieleBilden: ein eigenes Abbild traegt den Namen seines Basis-Ziels', async () => {
  const bausteine = bausteineLesen(join(TESTDATEN, 'bausteine.json'));
  const ziele = await zieleBilden(bausteine, {
    abbilder: ['anwendung:pruefung', 'unbekannt:pruefung'], sbom: null, sperrdateien: [], arbeitsbaum: null,
  }, { tagsHolen: NUR_EIGENER_TAG });
  assert.deepEqual(ziele.find((z) => z.name === 'anwendung:pruefung'), {
    art: 'abbild', name: 'anwendung:pruefung', referenz: 'anwendung:pruefung', basis: 'postgres:16.15',
  });
  assert.deepEqual(ziele.find((z) => z.name === 'unbekannt:pruefung'), {
    art: 'abbild', name: 'unbekannt:pruefung', referenz: 'unbekannt:pruefung',
  });
});

test('zieleBilden: die eigenen Abbilder der echten Liste tragen ihre Basis', async () => {
  const bausteine = bausteineLesen(join(HIER, 'bausteine.json'));
  const ziele = await zieleBilden(bausteine, {
    abbilder: ['kanban-kit:pruefung', 'kanban-kit-backup:pruefung'], sbom: null, sperrdateien: [], arbeitsbaum: null,
  }, { tagsHolen: NUR_EIGENER_TAG });
  const basis = (name) => ziele.find((z) => z.name === name).basis;
  assert.equal(basis('kanban-kit:pruefung'), 'eclipse-temurin:25.0.4.1_1-jre');
  assert.equal(basis('kanban-kit-backup:pruefung'), 'postgres:16.15-bookworm');
  for (const name of [basis('kanban-kit:pruefung'), basis('kanban-kit-backup:pruefung')]) {
    assert.ok(ziele.some((z) => z.name === name && !z.rolle), name);
  }
});

test('zieleBilden: die echte Liste bekommt je fremdem Betriebs-Abbild ein Referenzziel, Bauwerkzeuge keines', async () => {
  const bausteine = bausteineLesen(join(HIER, 'bausteine.json'));
  const ziele = await zieleBilden(bausteine, { abbilder: [], sbom: null, sperrdateien: [], arbeitsbaum: null }, {
    tagsHolen: NUR_EIGENER_TAG,
  });
  const gebunden = ziele.filter((z) => z.rolle === 'referenz').map((z) => z.gebunden);
  const erwartet = bausteine
    .filter((b) => b.art === 'abbild' && b.ausgeliefert && b.pruefung === 'bezug' && b.digest && b.verwendung !== 'bau')
    .map((b) => b.bezugsstelle);
  assert.ok(erwartet.length > 0);
  assert.deepEqual(gebunden, erwartet);
});

test('trivyAusgabeLesen: fassung je Schwachstelle und erstellt je Bericht', () => {
  const befunde = trivyAusgabeLesen(fixture('abbild-referenz.json'));
  assert.equal(befunde.erstellt, '2026-09-21T08:15:00Z');
  assert.equal(befunde.schwachstellen[0].fassung, '2.9.14');
  const ohne = trivyAusgabeLesen(fixture('abbild-befunde.json'));
  assert.equal(ohne.erstellt, null);
  assert.equal(ohne.schwachstellen[0].fassung, '3.0.15-1');
  const ohneFassung = trivyAusgabeLesen(JSON.stringify({
    Metadata: { ImageConfig: {} },
    Results: [{ Target: 't', Vulnerabilities: [{ VulnerabilityID: 'CVE-1', PkgName: 'p', Severity: 'HIGH' }] }],
  }));
  assert.equal(ohneFassung.erstellt, null);
  assert.equal(ohneFassung.schwachstellen[0].fassung, null);
});

test('urteilen: Referenzziele bekommen kein Urteil und keinen Ausnahmeabgleich', () => {
  const liste = ausnahmen([
    { kennung: 'CVE-2026-1002', ziel: REFERENZ.name, begruendung: 'Upstream', ablauf: '2026-12-31' },
  ]);
  const urteil = urteilen([ergebnis(REFERENZ, 'abbild-befunde.json')], liste, { heute: HEUTE });
  assert.deepEqual(urteil.sperrend, []);
  assert.deepEqual(urteil.ohneKorrektur, []);
  assert.deepEqual(urteil.genutzt, []);
  assert.deepEqual(urteil.hinweise.map((h) => h.ziel), [REFERENZ.name]);
});

test('urteilen: ein Fehler beim Pruefen des Referenzziels ist "Ziel nicht geprueft" und sperrt', () => {
  const urteil = urteilen([{ ziel: REFERENZ, fehler: 'trivy endete mit 1' }], KEINE_AUSNAHMEN, { heute: HEUTE });
  assert.deepEqual(urteil.sperrend, [{ ziel: REFERENZ.name, text: 'Ziel nicht geprueft: trivy endete mit 1' }]);
});

test('Zusammenfassung: Referenzziele stehen gekennzeichnet unter "Gepruefte Ziele"', () => {
  const text = zusammenfassung(urteilen([ergebnis(ABBILD, 'sperrdatei-sauber.json'), ergebnis(REFERENZ, 'abbild-referenz.json')], KEINE_AUSNAHMEN, { heute: HEUTE }));
  const ziele = text.slice(text.indexOf('### Gepruefte Ziele'));
  assert.match(ziele, /^- abbild: postgres:16\.15$/m);
  assert.match(ziele, /^- abbild \(Referenz\): postgres:16\.15 \(aktuell: 16\.16\)$/m);
  assert.match(text, /keine sperrenden Befunde/);
});

test('laufen: ein Referenzziel mit Fehler aus der Tag-Abfrage wird nicht geprueft und sperrt', async () => {
  const aufgerufen = [];
  const kaputt = { ...REFERENZ, name: 'postgres:16.15 (aktuell: unbekannt)', referenz: null, fehler: 'Tag-Liste nicht abrufbar: 503' };
  const { urteil, exitcode } = await laufen([kaputt], {
    ausfuehren: async (argumente) => {
      aufgerufen.push(argumente);
      return fixture('sperrdatei-sauber.json');
    },
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: () => {},
    umgebung: {},
  });
  assert.deepEqual(aufgerufen, []);
  assert.equal(exitcode, 1);
  assert.deepEqual(urteil.sperrend, [{ ziel: kaputt.name, text: 'Ziel nicht geprueft: Tag-Liste nicht abrufbar: 503' }]);
});

test('laufen: ein fehlgeschlagener Werkzeugaufruf am Referenzziel sperrt unter dessen Namen', async () => {
  const { urteil, exitcode } = await laufen([REFERENZ], {
    ausfuehren: async (argumente) => {
      assert.equal(argumente.at(-1), 'postgres:16.16');
      throw new Error('trivy endete mit 1: manifest unknown');
    },
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: () => {},
    umgebung: {},
  });
  assert.equal(exitcode, 1);
  assert.equal(urteil.sperrend[0].ziel, REFERENZ.name);
  assert.match(urteil.sperrend[0].text, /Ziel nicht geprueft: trivy endete mit 1/);
});

test('Gesamtfrist: 45 Minuten, weil Referenzziele dazukommen (E11)', () => {
  assert.equal(FRIST_GESAMT_MS, 2_700_000);
});

// --- Urteil nach Herkunft (Issue #1357, Plan #1351 E1/E3/E4/E10) -------------

// abbild-befunde.json ist der gebundene Stand (CVE-2026-1001, -1002 mit Korrektur), abbild-referenz.json
// der aktuelle Stand des Anbieters vom 2026-09-21, in dem nur noch CVE-2026-1002 steckt.
const REGEL = '(Regel: docs/betrieb.md, Abschnitt Sicherheitsprüfung)';
const TAG_10 = '2026-10-01';
const TAG_15 = '2026-10-06';
const EIGEN = { art: 'abbild', name: 'manban-backup:latest', referenz: 'manban-backup:latest', basis: 'postgres:16.15' };

function herkunftUrteilen(referenzDatei, heute, { referenz = REFERENZ, liste = KEINE_AUSNAHMEN, weitere = [] } = {}) {
  return urteilen([ergebnis(ABBILD, 'abbild-befunde.json'), ergebnis(referenz, referenzDatei), ...weitere], liste, { heute });
}

test('Frist fuer uebernommene Befunde: 14 Tage als Konstante', () => {
  assert.equal(FRIST_UEBERNOMMEN_TAGE, 14);
});

test('Herkunft: Befund im gebundenen und im Referenzziel wartet auf den Anbieter und sperrt nicht', () => {
  const urteil = herkunftUrteilen('abbild-referenz.json', TAG_15);
  assert.ok(!texte(urteil.sperrend).some((t) => t.includes('CVE-2026-1002')));
  assert.deepEqual(urteil.uebernommen.filter((e) => e.text.includes('CVE-2026-1002')), [{
    ziel: 'postgres:16.15',
    text: `CVE-2026-1002 (HIGH) in libxml2 [postgres:16.15 (debian 12.11)] — Korrektur in 2.9.14+dfsg-1.3 — Anbieter hat noch keine korrigierte Fassung ${REGEL}`,
  }]);
});

test('Herkunft: Befund nur im gebundenen Ziel, Referenz 10 Tage alt — sichtbar mit Frist, sperrt nicht', () => {
  const urteil = herkunftUrteilen('abbild-referenz.json', TAG_10);
  assert.deepEqual(urteil.sperrend, []);
  assert.deepEqual(urteil.uebernommen.filter((e) => e.text.includes('CVE-2026-1001')), [{
    ziel: 'postgres:16.15',
    text: `CVE-2026-1001 (CRITICAL) in libssl3 [postgres:16.15 (debian 12.11)] — Korrektur in 3.0.16-1 — korrigierte Fassung seit 2026-09-21, Frist bis 2026-10-05 ${REGEL}`,
  }]);
});

test('Herkunft: am letzten Fristtag sperrt der Befund noch nicht', () => {
  const urteil = herkunftUrteilen('abbild-referenz.json', '2026-10-05');
  assert.deepEqual(urteil.sperrend, []);
});

test('Herkunft: dasselbe mit Referenz 15 Tage alt sperrt und nennt Referenz, Fristende und Regel', () => {
  const urteil = herkunftUrteilen('abbild-referenz.json', TAG_15);
  assert.deepEqual(urteil.sperrend, [{
    ziel: 'postgres:16.15',
    text: `CVE-2026-1001 (CRITICAL) in libssl3 [postgres:16.15 (debian 12.11)] — Korrektur in 3.0.16-1 — korrigierte Fassung des Anbieters (postgres:16.16) seit 2026-09-21, 14-Tage-Frist abgelaufen am 2026-10-05 ${REGEL}`,
  }]);
  assert.ok(!urteil.uebernommen.some((e) => e.text.includes('CVE-2026-1001')));
});

test('Herkunft: ein Neubau desselben Tags zaehlt wie ein neuerer Tag', () => {
  const neubau = { ...REFERENZ, name: 'postgres:16.15 (aktuell: 16.15)', referenz: 'postgres:16.15' };
  const neuer = herkunftUrteilen('abbild-referenz.json', TAG_15);
  const gleich = herkunftUrteilen('abbild-referenz.json', TAG_15, { referenz: neubau });
  assert.deepEqual(texte(gleich.uebernommen), texte(neuer.uebernommen));
  assert.deepEqual(texte(gleich.sperrend), texte(neuer.sperrend).map((t) => t.replace('(postgres:16.16)', '(postgres:16.15)')));
});

test('Herkunft: fehlt das Erstellungsdatum der Referenz, sperrt der Befund mit benanntem Grund', () => {
  const urteil = herkunftUrteilen('abbild-referenz-ohne-erstellt.json', TAG_10);
  assert.deepEqual(texte(urteil.sperrend), [
    `CVE-2026-1001 (CRITICAL) in libssl3 [postgres:16.15 (debian 12.11)] — Korrektur in 3.0.16-1 — Erscheinungstag der Anbieter-Fassung unbekannt ${REGEL}`,
    `CVE-2026-1002 (HIGH) in libxml2 [postgres:16.15 (debian 12.11)] — Korrektur in 2.9.14+dfsg-1.3 — Erscheinungstag der Anbieter-Fassung unbekannt ${REGEL}`,
  ]);
});

test('Herkunft: ein Erstellungsdatum vor 2000-01-01 gilt als unbekannt', () => {
  const urteil = herkunftUrteilen('abbild-referenz-1970.json', TAG_10);
  assert.equal(urteil.sperrend.length, 2);
  assert.ok(texte(urteil.sperrend).every((t) => t.includes('Erscheinungstag der Anbieter-Fassung unbekannt')));
});

test('Herkunft: ein sperrender uebernommener Befund laesst sich weiter ausnehmen', () => {
  const liste = ausnahmen([{ kennung: 'CVE-2026-1001', ziel: 'postgres:16.15', begruendung: 'Anbieter', ablauf: '2026-12-31' }]);
  const urteil = herkunftUrteilen('abbild-referenz.json', TAG_15, { liste });
  assert.deepEqual(urteil.sperrend, []);
  assert.deepEqual(texte(urteil.genutzt), ['CVE-2026-1001: Anbieter (gilt bis 2026-12-31)']);
});

test('Herkunft: eine Ausnahme mit dem Namen des Referenzziels trifft nichts', () => {
  const liste = ausnahmen([{ kennung: 'CVE-2026-1001', ziel: REFERENZ.name, begruendung: 'falsch', ablauf: '2026-12-31' }]);
  const urteil = herkunftUrteilen('abbild-referenz.json', TAG_15, { liste });
  assert.ok(texte(urteil.sperrend).some((t) => t.includes('CVE-2026-1001')));
  assert.deepEqual(urteil.genutzt, []);
  assert.deepEqual(urteil.hinweise.map((h) => h.ziel), [REFERENZ.name]);
  assert.ok(!urteil.sperrend.some((e) => e.ziel === REFERENZ.name));
});

test('Herkunft: ein Fehler beim Referenzziel sperrt als "Ziel nicht geprueft", der gebundene Stand sperrt wie bisher', () => {
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json'), { ziel: REFERENZ, fehler: 'trivy endete mit 1' }], KEINE_AUSNAHMEN, { heute: TAG_10 });
  assert.deepEqual(urteil.sperrend, [
    { ziel: 'postgres:16.15', text: 'CVE-2026-1001 (CRITICAL) in libssl3 [postgres:16.15 (debian 12.11)] — Korrektur in 3.0.16-1' },
    { ziel: 'postgres:16.15', text: 'CVE-2026-1002 (HIGH) in libxml2 [postgres:16.15 (debian 12.11)] — Korrektur in 2.9.14+dfsg-1.3' },
    { ziel: REFERENZ.name, text: 'Ziel nicht geprueft: trivy endete mit 1' },
  ]);
  assert.deepEqual(urteil.uebernommen, []);
});

test('Herkunft: ohne Referenzziel bleibt ein schwerer Befund mit Korrektur sperrend', () => {
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], KEINE_AUSNAHMEN, { heute: TAG_10 });
  assert.equal(urteil.sperrend.length, 2);
  assert.deepEqual(urteil.uebernommen, []);
});

test('Herkunft: schwer ohne Korrektur bleibt auch im uebernommenen Baustein nur sichtbar', () => {
  const urteil = herkunftUrteilen('abbild-referenz.json', TAG_15);
  assert.deepEqual(texte(urteil.ohneKorrektur), ['CVE-2026-1003 (HIGH) in perl-base [postgres:16.15 (debian 12.11)] — keine Korrektur verfuegbar']);
});

test('Herkunft: im eigenen Abbild gilt ein Befund mit gleicher Kennung, Paket und Fassung wie im Basis-Ziel als uebernommen', () => {
  const urteil = herkunftUrteilen('abbild-referenz.json', TAG_10, { weitere: [ergebnis(EIGEN, 'abbild-eigen.json')] });
  assert.deepEqual(urteil.uebernommen.filter((e) => e.ziel === EIGEN.name), [{
    ziel: EIGEN.name,
    text: `CVE-2026-1001 (CRITICAL) in libssl3 [manban-backup:latest (debian 12.11)] — Korrektur in 3.0.16-1 — korrigierte Fassung seit 2026-09-21, Frist bis 2026-10-05 ${REGEL}`,
  }]);
  assert.deepEqual(texte(urteil.sperrend.filter((e) => e.ziel === EIGEN.name)), [
    'CVE-2026-1002 (HIGH) in libxml2 [manban-backup:latest (debian 12.11)] — Korrektur in 2.9.14+dfsg-1.3',
    'CVE-2026-2001 (HIGH) in stdlib [usr/local/bin/rclone] — Korrektur in 1.24.8',
  ]);
});

test('Herkunft: nach der Frist sperrt der uebernommene Befund auch im eigenen Abbild — unter dessen Namen', () => {
  const urteil = herkunftUrteilen('abbild-referenz.json', TAG_15, { weitere: [ergebnis(EIGEN, 'abbild-eigen.json')] });
  assert.ok(urteil.sperrend.some((e) => e.ziel === EIGEN.name && e.text.includes('CVE-2026-1001') && e.text.includes('14-Tage-Frist abgelaufen am 2026-10-05')));
});

test('Herkunft: ein eigenes Abbild ohne geprueftes Basis-Ziel urteilt wie heute', () => {
  const urteil = urteilen([ergebnis(EIGEN, 'abbild-eigen.json')], KEINE_AUSNAHMEN, { heute: TAG_10 });
  assert.equal(urteil.sperrend.length, 3);
  assert.deepEqual(urteil.uebernommen, []);
});

test('Herkunft: eigenes Abbild mit Basis ohne Referenzziel urteilt wie heute', () => {
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json'), ergebnis(EIGEN, 'abbild-eigen.json')], KEINE_AUSNAHMEN, { heute: TAG_10 });
  assert.equal(urteil.sperrend.filter((e) => e.ziel === EIGEN.name).length, 3);
  assert.deepEqual(urteil.uebernommen, []);
});

test('Herkunft: Befunde in Programmen ausserhalb des Betriebssystems werden mit ihrem Bestandteil abgeglichen', () => {
  const bericht = (ziel, ziele, erstellt) => ({
    ziel,
    befunde: trivyAusgabeLesen(JSON.stringify({
      Metadata: { ImageConfig: { created: erstellt } },
      Results: ziele.map((target) => ({ Target: target, Class: 'lang-pkgs', Vulnerabilities: [
        { VulnerabilityID: 'CVE-2026-2001', PkgName: 'stdlib', InstalledVersion: 'v1.24.1', FixedVersion: '1.24.8', Severity: 'HIGH' },
      ] })),
    })),
  });
  const urteil = urteilen([
    bericht(ABBILD, ['usr/local/bin/gosu', 'usr/local/bin/rclone']),
    bericht(REFERENZ, ['usr/local/bin/rclone'], '2026-09-21T08:15:00Z'),
  ], KEINE_AUSNAHMEN, { heute: TAG_10 });
  assert.deepEqual(texte(urteil.uebernommen), [
    `CVE-2026-2001 (HIGH) in stdlib [usr/local/bin/gosu] — Korrektur in 1.24.8 — korrigierte Fassung seit 2026-09-21, Frist bis 2026-10-05 ${REGEL}`,
    `CVE-2026-2001 (HIGH) in stdlib [usr/local/bin/rclone] — Korrektur in 1.24.8 — Anbieter hat noch keine korrigierte Fassung ${REGEL}`,
  ]);
});

test('Zusammenfassung: Abschnitt fuer uebernommene Befunde steht nach "Schwer ohne Korrektur"', () => {
  const text = zusammenfassung(herkunftUrteilen('abbild-referenz.json', TAG_10));
  const kopf = '### Übernommen, wartet auf Anbieter oder Frist (sichtbar, sperrt nicht)';
  const stellen = [
    text.indexOf('### Schwer ohne Korrektur'),
    text.indexOf(kopf),
    text.indexOf('CVE-2026-1001'),
    text.indexOf('### Bauwerkzeuge'),
  ];
  assert.ok(stellen.every((s) => s >= 0), stellen.join(','));
  assert.deepEqual([...stellen].sort((a, b) => a - b), stellen);
  assert.match(text, /Frist bis 2026-10-05/);
  assert.match(text, /keine sperrenden Befunde/);
});

// --- Tag-Liste der Registry (ohne Netz: holen ist ersetzt) ------------------

function antwort(status, { json = null, kopf = {} } = {}) {
  const kleinKopf = Object.fromEntries(Object.entries(kopf).map(([k, v]) => [k.toLowerCase(), v]));
  return { ok: status >= 200 && status < 300, status, headers: { get: (n) => kleinKopf[n.toLowerCase()] ?? null }, json: async () => json };
}

test('registryTagsHolen: anonymes Token nach 401, dann alle Seiten der Tag-Liste', async () => {
  const aufrufe = [];
  const holen = async (url, optionen = {}) => {
    aufrufe.push({ url, autorisierung: optionen.headers?.Authorization ?? null });
    if (url.startsWith('https://auth.docker.io/')) return antwort(200, { json: { token: 'T' } });
    if (!optionen.headers?.Authorization) {
      return antwort(401, { kopf: { 'WWW-Authenticate': 'Bearer realm="https://auth.docker.io/token",service="registry.docker.io"' } });
    }
    if (url.includes('last=16.16')) return antwort(200, { json: { name: 'library/postgres', tags: ['17.1'] } });
    return antwort(200, {
      json: { name: 'library/postgres', tags: ['16.15', '16.16'] },
      kopf: { Link: '</v2/library/postgres/tags/list?last=16.16&n=1000>; rel="next"' },
    });
  };
  const tags = await registryTagsHolen('postgres:16.15', { holen });
  assert.deepEqual(tags, ['16.15', '16.16', '17.1']);
  assert.equal(aufrufe[0].url, 'https://registry-1.docker.io/v2/library/postgres/tags/list?n=1000');
  assert.equal(aufrufe[1].url, 'https://auth.docker.io/token?service=registry.docker.io&scope=repository%3Alibrary%2Fpostgres%3Apull');
  assert.equal(aufrufe[2].autorisierung, 'Bearer T');
  assert.equal(aufrufe[3].url, 'https://registry-1.docker.io/v2/library/postgres/tags/list?last=16.16&n=1000');
  assert.equal(aufrufe[3].autorisierung, 'Bearer T');
});

test('registryTagsHolen: andere v2-Registry ohne Anmeldung', async () => {
  const holen = async (url) => {
    assert.equal(url, 'https://ghcr.io/v2/team/abbild/tags/list?n=1000');
    return antwort(200, { json: { tags: ['1.0', '1.1'] } });
  };
  assert.deepEqual(await registryTagsHolen('ghcr.io/team/abbild:1.0', { holen }), ['1.0', '1.1']);
});

test('registryTagsHolen: jede Fehlantwort wirft mit Grund', async () => {
  await assert.rejects(registryTagsHolen('postgres:16.15', { holen: async () => antwort(503) }), /Tag-Liste antwortete 503/);
  await assert.rejects(
    registryTagsHolen('postgres:16.15', { holen: async () => antwort(401) }),
    /401 ohne verwertbaren WWW-Authenticate-Kopf/,
  );
  const tokenKaputt = async (url) => (url.startsWith('https://auth.')
    ? antwort(500)
    : antwort(401, { kopf: { 'WWW-Authenticate': 'Bearer realm="https://auth.docker.io/token"' } }));
  await assert.rejects(registryTagsHolen('postgres:16.15', { holen: tokenKaputt }), /Token-Abruf antwortete 500/);
  await assert.rejects(
    registryTagsHolen('ghcr.io/a/b:1', { holen: async () => antwort(200, { json: { tags: 'kaputt' } }) }),
    /ohne Tag-Liste/,
  );
});

test('registryTagsHolen: eine Seitenkette ohne Ende bricht ab', async () => {
  const holen = async () => antwort(200, { json: { tags: ['1'] }, kopf: { Link: '</v2/a/b/tags/list?last=1>; rel="next"' } });
  await assert.rejects(registryTagsHolen('ghcr.io/a/b:1', { holen }), /mehr als \d+ Seiten/);
});

// --- Regel 13: Ausgabe und Reihenfolge ------------------------------------

// --- Bauwerkzeuge (Issue #1355) --------------------------------------------

const BAUWERKZEUG = { ...ABBILD, verwendung: 'bau' };

test('Bauwerkzeuge: schwerer Befund mit Korrektur sperrt nicht und steht im eigenen Abschnitt', () => {
  const urteil = urteilen([ergebnis(BAUWERKZEUG, 'abbild-befunde.json')], KEINE_AUSNAHMEN, { heute: HEUTE });
  assert.deepEqual(urteil.sperrend, []);
  assert.deepEqual(urteil.ohneKorrektur, []);
  assert.ok(urteil.bauwerkzeuge.some((b) => b.text.includes('CVE-2026-1001') && b.text.includes('Korrektur in')));
  assert.ok(urteil.bauwerkzeuge.some((b) => b.text.includes('CVE-2026-1003') && b.text.includes('keine Korrektur')));
  const text = zusammenfassung(urteil);
  const abschnitt = text.slice(text.indexOf('### Bauwerkzeuge (informiert, sperrt nicht)'), text.indexOf('### Genutzte Ausnahmen'));
  assert.match(abschnitt, /CVE-2026-1001/);
  assert.match(text, /keine sperrenden Befunde/);
});

test('Bauwerkzeuge: derselbe Befund ohne verwendung sperrt wie bisher', () => {
  const urteil = urteilen([ergebnis(ABBILD, 'abbild-befunde.json')], KEINE_AUSNAHMEN, { heute: HEUTE });
  assert.ok(urteil.sperrend.some((b) => b.text.includes('CVE-2026-1001')));
  assert.deepEqual(urteil.bauwerkzeuge, []);
});

test('Regel 13: Reihenfolge sperrend, ohne Korrektur, uebernommen, Bauwerkzeuge, genutzte Ausnahmen, Hinweise', () => {
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
    text.indexOf('### Übernommen, wartet auf Anbieter oder Frist (sichtbar, sperrt nicht)'),
    text.indexOf('### Bauwerkzeuge (informiert, sperrt nicht)'),
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
  for (const kopf of ['### Sperrend', '### Schwer ohne Korrektur (sichtbar, sperrt nicht)', '### Übernommen, wartet auf Anbieter oder Frist (sichtbar, sperrt nicht)', '### Bauwerkzeuge (informiert, sperrt nicht)', '### Genutzte Ausnahmen', '### Hinweise']) {
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

test('Regel 13: mit GITHUB_STEP_SUMMARY steht jeder sperrende Befund zusaetzlich als Zeile im Protokoll', async () => {
  const verzeichnis = mkdtempSync(join(tmpdir(), 'sicherheit-'));
  try {
    const datei = join(verzeichnis, 'summary.md');
    const ausgegeben = [];
    const { exitcode } = await laufen([ABBILD], {
      ausfuehren: async () => fixture('abbild-befunde.json'),
      ausnahmen: KEINE_AUSNAHMEN,
      schreiben: (t) => ausgegeben.push(t),
      umgebung: { GITHUB_STEP_SUMMARY: datei },
    });
    assert.equal(exitcode, 1);
    assert.match(readFileSync(datei, 'utf-8'), /## Sicherheitspruefung/);
    const protokoll = ausgegeben.join('');
    assert.ok(!protokoll.includes('## Sicherheitspruefung'), 'die Zusammenfassung selbst bleibt in der Datei');
    assert.equal(
      protokoll,
      'Sicherheitspruefung: 2 sperrende Befunde\n'
        + 'sperrend: postgres:16.15: CVE-2026-1001 (CRITICAL) in libssl3 [postgres:16.15 (debian 12.11)] — Korrektur in 3.0.16-1\n'
        + 'sperrend: postgres:16.15: CVE-2026-1002 (HIGH) in libxml2 [postgres:16.15 (debian 12.11)] — Korrektur in 2.9.14+dfsg-1.3\n',
    );
  } finally {
    rmSync(verzeichnis, { recursive: true, force: true });
  }
});

test('Regel 13: ein mehrzeiliger Befund bleibt im Protokoll eine Zeile', async () => {
  const ausgegeben = [];
  await laufen([ABBILD], {
    ausfuehren: async () => {
      throw new Error('trivy endete mit 1: erste Zeile\nzweite Zeile');
    },
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: (t) => ausgegeben.push(t),
    umgebung: { GITHUB_STEP_SUMMARY: join(tmpdir(), `sicherheit-${process.pid}-summary.md`) },
  });
  rmSync(join(tmpdir(), `sicherheit-${process.pid}-summary.md`), { force: true });
  assert.deepEqual(ausgegeben.join('').split('\n'), [
    'Sicherheitspruefung: 1 sperrender Befund',
    'sperrend: postgres:16.15: Ziel nicht geprueft: trivy endete mit 1: erste Zeile zweite Zeile',
    '',
  ]);
});

test('Regel 13: mit GITHUB_STEP_SUMMARY und ohne sperrenden Befund bleibt das Protokoll leer', async () => {
  const datei = join(tmpdir(), `sicherheit-${process.pid}-gruen.md`);
  const ausgegeben = [];
  try {
    await laufen([SPERRDATEI], {
      ausfuehren: async () => fixture('sperrdatei-sauber.json'),
      ausnahmen: KEINE_AUSNAHMEN,
      schreiben: (t) => ausgegeben.push(t),
      umgebung: { GITHUB_STEP_SUMMARY: datei },
    });
  } finally {
    rmSync(datei, { force: true });
  }
  assert.deepEqual(ausgegeben, []);
});

test('Regel 13: ohne GITHUB_STEP_SUMMARY erscheinen die Befunde nur einmal, in der Zusammenfassung', async () => {
  const ausgegeben = [];
  await laufen([ABBILD], {
    ausfuehren: async () => fixture('abbild-befunde.json'),
    ausnahmen: KEINE_AUSNAHMEN,
    schreiben: (t) => ausgegeben.push(t),
    umgebung: {},
  });
  assert.equal(ausgegeben.length, 1);
  assert.ok(!ausgegeben[0].includes('sperrend: '));
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
