import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  BERICHT_PFAD,
  LIEGEZEIT_TAGE,
  SPERRSCHWELLE_GEAENDERT,
  SPERRSCHWELLE_GESAMT,
  ZIEL,
  berichtLesen,
  laufen,
  lueckenStellen,
} from './abdeckungspruefung.mjs';

const HIER = dirname(fileURLToPath(import.meta.url));
const BEISPIEL = readFileSync(join(HIER, 'testdaten', 'jacoco-beispiel.xml'), 'utf-8');

const KARTE = 'src/main/java/org/mwolff/manban/card/CardService.java';
const BOARD = 'src/main/java/org/mwolff/manban/board/BoardService.java';
/** Liegt vor der juengsten `sessioninfo`-Startzeit des Beispiels (1700000100000 ms). */
const COMMIT_SEKUNDEN = 1699999000;
const JETZT = Date.parse('2026-11-09T12:00:00Z');

/** Das Beispiel mit anderen Zaehlern auf Berichtsebene. */
function mitGesamt(xml, { zeilen, zweige }) {
  const grenze = xml.lastIndexOf('</package>');
  const ende = xml.slice(grenze)
    .replace(/<counter type="LINE"[^>]*\/>/, `<counter type="LINE" missed="${zeilen[0]}" covered="${zeilen[1]}"/>`)
    .replace(/<counter type="BRANCH"[^>]*\/>/, `<counter type="BRANCH" missed="${zweige[0]}" covered="${zweige[1]}"/>`);
  return xml.slice(0, grenze) + ende;
}

/** Das Beispiel ohne jede Luecke und mit vollen Zaehlern. */
function ohneLuecken() {
  const gedeckt = BEISPIEL.replace(/<line nr="(\d+)"[^>]*\/>/g, '<line nr="$1" mi="0" ci="4" mb="0" cb="0"/>');
  return mitGesamt(gedeckt, { zeilen: [0, 200], zweige: [0, 200] });
}

function jeDateiKarten(stellen) {
  return [...new Set(stellen.map((s) => s.datei))]
    .map((pfad, i) => ({ pfad, art: 'neu', nummer: 100 + i, neueStellen: stellen.filter((s) => s.datei === pfad).length }));
}

/** Git-Attrappe: `diffs` nennt je Datei den `git diff -U0`-Text. */
function gitAttrappe({ anker, commitSekunden, diffs, aufrufe }) {
  return (...args) => {
    aufrufe.push(args);
    if (args[0] === 'merge-base') return anker ? { status: 0, stdout: `${anker}\n` } : { status: 1, stdout: '' };
    if (args[0] === 'log') return commitSekunden === null ? { status: 128, stdout: '' } : { status: 0, stdout: `${commitSekunden}\n` };
    if (args[0] === 'rev-parse') return { status: 0, stdout: 'stand123\n' };
    if (args[0] === 'status') return { status: 0, stdout: '' };
    if (args[0] === 'diff' && args[1] === '-U0') return { status: 0, stdout: diffs[args.at(-1)] ?? '' };
    return { status: 0, stdout: '' };
  };
}

function lauf({
  xml = BEISPIEL,
  anker = 'anker1',
  commitSekunden = COMMIT_SEKUNDEN,
  diffs = {},
  karten = null,
  mitClaudeVerzeichnis = true,
} = {}) {
  const wurzel = mkdtempSync(join(tmpdir(), 'abdeckung-'));
  if (mitClaudeVerzeichnis) mkdirSync(join(wurzel, '.claude'));
  const texte = [];
  const auftraege = [];
  const gitAufrufe = [];
  const antwort = karten ?? ((auftrag) => (auftrag.auftrag === 'liegezeit'
    ? { ueberfaellig: [] }
    : { karten: jeDateiKarten(auftrag.stellen) }));
  try {
    const code = laufen({
      cwd: wurzel,
      ausgabe: (text) => texte.push(text),
      jetzt: () => JETZT,
      liesDatei: (pfad) => (pfad === BERICHT_PFAD ? xml : null),
      git: gitAttrappe({ anker, commitSekunden, diffs, aufrufe: gitAufrufe }),
      karten: (auftrag) => {
        auftraege.push(auftrag);
        return antwort(auftrag);
      },
    });
    let durchlass = null;
    try {
      durchlass = JSON.parse(readFileSync(join(wurzel, '.claude', 'abdeckungsdurchlass.json'), 'utf-8'));
    } catch {
      durchlass = null;
    }
    return { code, text: texte.join(''), auftraege, gitAufrufe, durchlass };
  } finally {
    rmSync(wurzel, { recursive: true, force: true });
  }
}

const geschrieben = (ergebnis) => ergebnis.auftraege.filter((a) => a.auftrag === 'schreiben');

test('Konstanten: Sperrschwellen, Ziel und Liegezeit', () => {
  assert.equal(SPERRSCHWELLE_GESAMT, 99);
  assert.equal(SPERRSCHWELLE_GEAENDERT, 90);
  assert.equal(ZIEL, 100);
  assert.equal(LIEGEZEIT_TAGE, 7);
});

test('berichtLesen: Pfade, Zeilen, Gesamtzaehler und juengste Startzeit', () => {
  const bericht = berichtLesen(BEISPIEL);
  assert.deepEqual(bericht.dateien.map((d) => d.pfad), [KARTE, BOARD]);
  assert.deepEqual(bericht.dateien[0].zeilen[2], { nr: 14, mi: 1, ci: 3, mb: 1, cb: 1 });
  assert.deepEqual(bericht.zeilen, { gedeckt: 199, gesamt: 200 });
  assert.deepEqual(bericht.zweige, { gedeckt: 199, gesamt: 200 });
  assert.equal(bericht.sitzungStart, 1700000100000);
});

test('berichtLesen: ohne Report-Ende, Sitzung oder Zeilenzaehler ist der Bericht nicht lesbar', () => {
  assert.equal(berichtLesen(''), null);
  assert.equal(berichtLesen(BEISPIEL.replace('</report>', '')), null);
  assert.equal(berichtLesen(BEISPIEL.replace(/<sessioninfo[^>]*\/>/g, '')), null);
  assert.equal(berichtLesen(BEISPIEL.replace('<counter type="LINE" missed="1" covered="199"/>', '')), null);
});

test('berichtLesen: ein Bericht ohne Zweige hat den Zweigzaehler 0 von 0', () => {
  const ohneZweige = BEISPIEL.replace('<counter type="BRANCH" missed="1" covered="199"/>', '');
  assert.deepEqual(berichtLesen(ohneZweige).zweige, { gedeckt: 0, gesamt: 0 });
});

test('lueckenStellen: ci = 0 ist eine Zeilen-Stelle, ci > 0 mit mb > 0 eine Zweig-Stelle', () => {
  assert.deepEqual(lueckenStellen(berichtLesen(BEISPIEL)), [
    { datei: KARTE, zeile: 12, mutator: 'Zeile', ersetzung: 'nicht gedeckt' },
    { datei: KARTE, zeile: 14, mutator: 'Zweig', ersetzung: 'nicht vollständig gedeckt' },
    { datei: KARTE, zeile: 16, mutator: 'Zeile', ersetzung: 'nicht gedeckt' },
  ]);
});

test('100 %: gruen, keine Karte, kein Durchlass', () => {
  const ergebnis = lauf({ xml: ohneLuecken() });
  assert.equal(ergebnis.code, 0);
  assert.equal(geschrieben(ergebnis).length, 0);
  assert.equal(ergebnis.durchlass, null);
  assert.match(ergebnis.text, /100,00 %/);
  assert.doesNotMatch(ergebnis.text, /Für den Abschlussbericht von push main:/);
});

test('99,5 %: gruen mit Karten je Datei und der Art abdeckung', () => {
  const ergebnis = lauf();
  assert.equal(ergebnis.code, 0);
  const [auftrag] = geschrieben(ergebnis);
  assert.equal(auftrag.pruefung, 'abdeckung');
  assert.deepEqual(auftrag.stellen, lueckenStellen(berichtLesen(BEISPIEL)));
  assert.match(ergebnis.text, /Zeilen: 99,50 % \(199 von 200\)/);
  assert.match(ergebnis.text, /Karte neu: #100 Abdeckungslücken in src\/main\/java\/org\/mwolff\/manban\/card\/CardService\.java/);
});

test('98,99 %: rot ohne Karte, ohne Durchlass und ungerundet', () => {
  for (const zaehler of [{ zeilen: [101, 9899], zweige: [0, 10000] }, { zeilen: [0, 10000], zweige: [101, 9899] }]) {
    const ergebnis = lauf({ xml: mitGesamt(BEISPIEL, zaehler) });
    assert.equal(ergebnis.code, 1);
    assert.equal(geschrieben(ergebnis).length, 0);
    assert.equal(ergebnis.durchlass, null);
    assert.match(ergebnis.text, /98,99 %/);
  }
});

test('Ungerundet: 98,996 % reicht nicht, und die Meldung rundet nicht auf 99,00 %', () => {
  const ergebnis = lauf({ xml: mitGesamt(BEISPIEL, { zeilen: [1004, 98996], zweige: [0, 100] }) });
  assert.equal(ergebnis.code, 1);
  assert.match(ergebnis.text, /Zeilen: 98,99 % \(98996 von 100000\)/);
});

test('Genau 99,00 % erreicht die Sperrschwelle', () => {
  const ergebnis = lauf({ xml: mitGesamt(BEISPIEL, { zeilen: [2, 198], zweige: [2, 198] }) });
  assert.equal(ergebnis.code, 0);
});

test('Geaenderte Zeilen unter 90 %: rot trotz Gesamtstand ueber 99 %, ohne Karte', () => {
  const ergebnis = lauf({ diffs: { [KARTE]: '@@ -12 +12 @@\n' } });
  assert.equal(ergebnis.code, 1);
  assert.equal(geschrieben(ergebnis).length, 0);
  assert.match(ergebnis.text, /Geänderte Zeilen: 0,00 % \(0 von 1\)/);
});

test('Geaenderte Zeilen: Zweige zaehlen nicht, gedeckt heisst ci > 0', () => {
  const ergebnis = lauf({ diffs: { [KARTE]: '@@ -14 +14 @@\n' } });
  assert.equal(ergebnis.code, 0);
  assert.match(ergebnis.text, /Geänderte Zeilen: 100,00 % \(1 von 1\)/);
});

test('Keine geaenderte ausfuehrbare Zeile: die Bedingung ist erfuellt', () => {
  const ergebnis = lauf({ diffs: { [KARTE]: '@@ -11 +11 @@\n' } });
  assert.equal(ergebnis.code, 0);
  assert.match(ergebnis.text, /Geänderte Zeilen: keine/);
});

test('Anker nicht aufloesbar: jede Zeile zaehlt als geaendert', () => {
  const ergebnis = lauf({ anker: null });
  assert.equal(ergebnis.code, 1);
  assert.match(ergebnis.text, /Geänderte Zeilen: 66,66 % \(4 von 6\)/);
  assert.match(ergebnis.text, /nicht auflösbar/);
  assert.equal(ergebnis.gitAufrufe.some((a) => a[0] === 'diff'), false);
});

test('Fehlender und unlesbarer Bericht: rot, ohne Board', () => {
  for (const xml of [null, '<report>']) {
    const ergebnis = lauf({ xml });
    assert.equal(ergebnis.code, 1);
    assert.deepEqual(ergebnis.auftraege, []);
    assert.match(ergebnis.text, /fehlt oder ist nicht lesbar/);
  }
});

test('Veralteter Bericht: rot, massgeblich ist die juengste Startzeit', () => {
  const veraltet = lauf({ commitSekunden: 1700000101 });
  assert.equal(veraltet.code, 1);
  assert.deepEqual(veraltet.auftraege, []);
  assert.match(veraltet.text, /älter als der geprüfte Stand/);
  assert.equal(lauf({ commitSekunden: 1700000100 }).code, 0);
  assert.equal(lauf({ commitSekunden: 1700000050 }).code, 0);
});

test('Commit-Zeit nicht lesbar: rot statt Durchlass', () => {
  const ergebnis = lauf({ commitSekunden: null });
  assert.equal(ergebnis.code, 1);
  assert.deepEqual(ergebnis.auftraege, []);
  assert.match(ergebnis.text, /Commit-Zeit/);
});

test('Liegezeit: Auftrag traegt pruefung und tage, ueberfaellige Karte ist rot und nennt Karte und Regel', () => {
  const ergebnis = lauf({
    karten: () => ({ ueberfaellig: [{ nummer: 77, titel: `Abdeckungslücken in ${KARTE}`, angelegt: '2026-10-01T08:00:00Z' }] }),
  });
  assert.equal(ergebnis.code, 1);
  assert.deepEqual(ergebnis.auftraege, [{ auftrag: 'liegezeit', pruefung: 'abdeckung', tage: 7 }]);
  assert.match(ergebnis.text, /#77 Abdeckungslücken/);
  assert.match(ergebnis.text, /länger als 7 Tage offen/);
});

test('Liegezeit nicht pruefbar: Board-Fehler und fehlende Liste sind rot', () => {
  const fehler = lauf({ karten: () => ({ fehler: 'HTTP 503' }) });
  assert.equal(fehler.code, 1);
  assert.equal(geschrieben(fehler).length, 0);
  assert.match(fehler.text, /Liegezeit nicht prüfbar.*HTTP 503/);
  assert.equal(lauf({ karten: () => ({}) }).code, 1);
  assert.equal(lauf({ karten: () => { throw new Error('kaputt'); } }).code, 1);
});

test('Board-Fehler beim Schreiben: rot, ohne Durchlass, die Stellen stehen in der Meldung', () => {
  const ergebnis = lauf({
    karten: (auftrag) => (auftrag.auftrag === 'liegezeit' ? { ueberfaellig: [] } : { fehler: 'HTTP 500' }),
  });
  assert.equal(ergebnis.code, 1);
  assert.equal(ergebnis.durchlass, null);
  assert.match(ergebnis.text, /Board-Fehler: HTTP 500/);
  assert.match(ergebnis.text, /CardService\.java:12/);
});

test('Antwort ohne Karte fuer eine Datei: rot', () => {
  const ergebnis = lauf({
    karten: (auftrag) => (auftrag.auftrag === 'liegezeit' ? { ueberfaellig: [] } : { karten: [] }),
  });
  assert.equal(ergebnis.code, 1);
  assert.match(ergebnis.text, /keine Karte/);
});

test('Durchlass: Block in der Ausgabe und dieselben Angaben in der Datei', () => {
  const ergebnis = lauf({ diffs: { [KARTE]: '@@ -14 +14 @@\n' } });
  assert.match(ergebnis.text, /Für den Abschlussbericht von push main:/);
  assert.deepEqual(ergebnis.durchlass, {
    stand: 'stand123',
    datum: new Date(JETZT).toISOString(),
    gesamt: { zeilen: 99.5, zweige: 99.5 },
    geaendert: 100,
    karten: [{ pfad: KARTE, art: 'neu', nummer: 100, neueStellen: 3 }],
  });
});

test('Durchlassdatei nicht schreibbar: Urteil bleibt gruen, der Block nennt es', () => {
  const ergebnis = lauf({ mitClaudeVerzeichnis: false });
  assert.equal(ergebnis.code, 0);
  assert.match(ergebnis.text, /ließ sich nicht schreiben/);
});

test('Der gebildete Pfad stimmt mit dem Pfad aus git diff ueberein (echtes Repository)', () => {
  const wurzel = mkdtempSync(join(tmpdir(), 'abdeckung-git-'));
  const git = (...args) => {
    const res = spawnSync('git', ['-c', 'user.name=t', '-c', 'user.email=t@t', '-c', 'commit.gpgsign=false', '-c', 'core.hooksPath=/dev/null', ...args], { cwd: wurzel, encoding: 'utf-8' });
    assert.equal(res.status, 0, res.stderr);
  };
  try {
    const datei = join(wurzel, KARTE);
    mkdirSync(dirname(datei), { recursive: true });
    const zeilen = Array.from({ length: 16 }, (_, i) => `zeile ${i + 1}`);
    writeFileSync(datei, `${zeilen.join('\n')}\n`);
    git('init', '-q');
    git('add', '.');
    git('commit', '-q', '-m', 'start');
    git('update-ref', 'refs/remotes/origin/main', 'HEAD');
    zeilen[11] = 'geaendert';
    writeFileSync(datei, `${zeilen.join('\n')}\n`);
    const xml = BEISPIEL.replace('start="1700000100000"', `start="${Date.now() + 86400000}"`);

    const texte = [];
    const code = laufen({
      cwd: wurzel,
      ausgabe: (text) => texte.push(text),
      liesDatei: (pfad) => (pfad === BERICHT_PFAD ? xml : null),
      karten: () => ({ ueberfaellig: [] }),
    });

    assert.equal(code, 1);
    assert.match(texte.join(''), /Geänderte Zeilen: 0,00 % \(0 von 1\)/);
  } finally {
    rmSync(wurzel, { recursive: true, force: true });
  }
});
