#!/usr/bin/env node
/**
 * mutationspruefung.mjs — Treiber der Mutationspruefung fuer beide Seiten (Issue #1212,
 * Plan #1210, fachliche Quelle #1104).
 *
 * Nutzung:
 *   node scripts/mutationspruefung.mjs aenderung frontend|backend
 *   node scripts/mutationspruefung.mjs vollauf   frontend|backend
 *
 * Dieses Paket legt die GEMEINSAMEN Teile an: Anker, Dateilisten, Pruefbereich beider Seiten,
 * Test-zu-Quelle-Zuordnung und die Ausgabeform. Die seitenspezifische Auswertung und der Vollauf
 * folgen in eigenen Paketen; hier laeuft noch kein Mutationswerkzeug.
 *
 * Zwei Festlegungen, die sich aus dem Bestand ergeben:
 *
 * 1. Der Umfang wird IMMER gegen den Hauptzweig bestimmt (`git merge-base HEAD origin/<main>`),
 *    auch wenn `checks.mjs` an der Paketstufe gegen `HEAD` auswaehlt: Ein Push traegt oft mehrere
 *    Pakete, und zwei Pakete koennen dieselbe Datei nacheinander anfassen (Kriterium 1). Laesst
 *    sich der Anker nicht aufloesen, gilt der VOLLE Umfang — die Irrtumsrichtung ist "mehr
 *    pruefen, nie weniger".
 * 2. Die Ausgabe des Mutationswerkzeugs wird nie durchgereicht, sondern zusammengefasst:
 *    `checks.mjs` faerbt einen Lauf schon an `[ERROR]` oder `BUILD FAILURE` im Text rot, und
 *    Mavens Log traegt beides auch in gruenen Laeufen (abstuerzende PIT-Minions auf aarch64).
 *
 * Eigene Glob- und Diff-Auswertung statt eines Imports aus `.claude/kit/checks.mjs`: Das Kit ist
 * nicht versioniert (`.gitignore`: `.claude/*`), ein versioniertes Werkzeug darf nicht von
 * lokalem Zustand abhaengen. Die Semantik ist bewusst dieselbe.
 */

import { existsSync, readFileSync, realpathSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

export const KOMMANDOS = ['aenderung', 'vollauf'];
export const SEITEN = ['frontend', 'backend'];

const HAUPTZWEIG_VORGABE = 'main';

// --- Muster ----------------------------------------------------------------

const REGEX_SONDERZEICHEN = /[.+?^${}()|[\]\\]/;

function maskiere(zeichen) {
  return REGEX_SONDERZEICHEN.test(zeichen) ? `\\${zeichen}` : zeichen;
}

/**
 * Minimal-Glob fuer Pfadmuster: '*' innerhalb eines Segments, '**' ueber Segmentgrenzen.
 * Ein '**' samt folgendem Trenner darf ganz verschwinden, damit `src/lib/**\/*.ts` auch
 * `src/lib/a.ts` trifft und nicht erst eine Datei im Unterverzeichnis — dieselbe Auslegung,
 * die Stryker seinen `mutate`-Mustern gibt.
 */
export function globZuRegex(muster) {
  let quelle = '';
  let i = 0;
  while (i < muster.length) {
    const zeichen = muster[i];
    if (zeichen !== '*') {
      quelle += maskiere(zeichen);
      i += 1;
    } else if (muster[i + 1] === '*') {
      const mitTrenner = muster[i + 2] === '/';
      quelle += mitTrenner ? '(?:.*/)?' : '.*';
      i += mitTrenner ? 3 : 2;
    } else {
      quelle += '[^/]*';
      i += 1;
    }
  }
  return new RegExp(`^${quelle}$`);
}

/**
 * Klassenmuster von PIT: '*' steht fuer beliebige Zeichen — auch fuer Punkte, weshalb
 * `org.mwolff.manban.*.application.*` auch tiefere Pakete erfasst. '$' trennt innere Klassen
 * und ist kein Regex-Anker.
 */
export function pitGlobZuRegex(muster) {
  let quelle = '';
  for (const zeichen of muster) {
    quelle += zeichen === '*' ? '.*' : maskiere(zeichen);
  }
  return new RegExp(`^${quelle}$`);
}

/** `src/main/java/org/x/Foo.java` -> `org.x.Foo`; alles andere gehoert keiner Klasse. */
export function javaPfadZuKlasse(pfad) {
  const praefix = 'src/main/java/';
  if (!pfad.startsWith(praefix) || !pfad.endsWith('.java')) return null;
  return pfad.slice(praefix.length, -'.java'.length).replaceAll('/', '.');
}

// --- Pruefbereich je Seite --------------------------------------------------

const STRYKER_PFAD = join('frontend', 'stryker.config.json');

/**
 * Der Frontend-Bereich kommt aus `mutate` in `frontend/stryker.config.json` — dieselbe Quelle,
 * aus der schon der Frontend-Testumfang abgeleitet wird. Nie duplizieren: Ab der ersten
 * Abweichung pruefte der Treiber einen anderen Bereich als das Werkzeug.
 */
export function frontendBereich(strykerConfig) {
  const woertlich = strykerConfig?.mutate ?? [];
  const ein = [];
  const aus = [];
  for (const muster of woertlich) {
    const negiert = muster.startsWith('!');
    const roh = negiert ? muster.slice(1) : muster;
    (negiert ? aus : ein).push(globZuRegex(`frontend/${roh}`));
  }
  return {
    seite: 'frontend',
    quelle: `${STRYKER_PFAD} (mutate)`,
    woertlich,
    zeilen: woertlich,
    trifft: (pfad) => ein.some((r) => r.test(pfad)) && !aus.some((r) => r.test(pfad)),
    istSeitenTest: (pfad) => pfad.startsWith('frontend/') && /\.test\.tsx?$/.test(pfad),
  };
}

function ohneKommentare(xml) {
  return xml.replaceAll(/<!--[\s\S]*?-->/g, '');
}

function profilPit(pomText) {
  const start = pomText.indexOf('<id>pit</id>');
  if (start < 0) return '';
  const ende = pomText.indexOf('</profile>', start);
  return ende < 0 ? pomText.slice(start) : pomText.slice(start, ende);
}

function paramWerte(block) {
  return [...block.matchAll(/<param>([^<]*)<\/param>/g)]
    .map((treffer) => treffer[1].trim())
    .filter((wert) => wert.length > 0 && !wert.startsWith('${'));
}

function elementBlock(text, name) {
  const muster = new RegExp(`<${name}>([\\s\\S]*?)</${name}>`);
  return muster.exec(text)?.[1] ?? '';
}

/**
 * Backend-Bereich aus `pom.xml`, Profil `pit`: bevorzugt die Property `pit.targetClasses`
 * (kommagetrennt), weil der Aenderungslauf sie spaeter umschaltet. Solange sie fehlt — sie kommt
 * mit dem Backend-Paket —, faellt das Lesen auf die `<targetClasses>`-Elemente zurueck.
 */
export function pitBereichLesen(pomText) {
  const profil = ohneKommentare(profilPit(pomText));
  const property = /<pit\.targetClasses>([^<]*)<\/pit\.targetClasses>/.exec(profil)?.[1];
  const ausProperty = (property ?? '')
    .split(',')
    .map((wert) => wert.trim())
    .filter((wert) => wert.length > 0);
  const ziel = ausProperty.length > 0 ? ausProperty : paramWerte(elementBlock(profil, 'targetClasses'));
  return {
    quelle: ausProperty.length > 0 ? 'property' : 'elemente',
    ziel,
    aus: paramWerte(elementBlock(profil, 'excludedClasses')),
  };
}

/** `excludedClasses` gewinnt in PIT gegen `targetClasses`, unabhaengig davon, welches Muster genauer ist. */
export function backendBereich(gelesen) {
  const ein = gelesen.ziel.map((m) => pitGlobZuRegex(m));
  const aus = gelesen.aus.map((m) => pitGlobZuRegex(m));
  const quelle = gelesen.quelle === 'property'
    ? 'pom.xml, Profil pit, Property pit.targetClasses + excludedClasses'
    : 'pom.xml, Profil pit, targetClasses + excludedClasses';
  return {
    seite: 'backend',
    quelle,
    woertlich: gelesen,
    zeilen: [
      ...gelesen.ziel.map((m) => `eingeschlossen: ${m}`),
      ...gelesen.aus.map((m) => `ausgenommen:    ${m}`),
    ],
    trifft: (pfad) => {
      const klasse = javaPfadZuKlasse(pfad);
      if (klasse === null) return false;
      return ein.some((r) => r.test(klasse)) && !aus.some((r) => r.test(klasse));
    },
    istSeitenTest: (pfad) => pfad.startsWith('src/test/java/') && /(Test|IT)\.java$/.test(pfad),
  };
}

// --- Test-zu-Quelle-Zuordnung ----------------------------------------------

export function istTestdatei(pfad) {
  if (/\.test\.tsx?$/.test(pfad)) return true;
  return pfad.startsWith('src/test/java/') && /(Test|IT)\.java$/.test(pfad);
}

/**
 * Namenskonvention beider Seiten: `x.test.ts` -> `x.ts`, `x.test.tsx` -> `x.tsx`,
 * `FooTest.java` -> `Foo.java`, `FooIT.java` -> `Foo.java` (dabei wechselt der Quellbaum
 * von `src/test/java` nach `src/main/java`).
 */
export function konventionsQuelle(testPfad) {
  const js = /^(.*)\.test\.(tsx?)$/.exec(testPfad);
  if (js) return `${js[1]}.${js[2]}`;
  const java = /^src\/test\/java\/(.*?)(Test|IT)\.java$/.exec(testPfad);
  if (java) return `src/main/java/${java[1]}.java`;
  return null;
}

/**
 * Die Umkehrung von `konventionsQuelle`: die Testdateien, die eine Quelle laut Namenskonvention
 * pruefen. Sie ergaenzen die Deckungsangabe des Berichts, die eine ENTFERNTE Testdatei nicht mehr
 * kennt — und genau die Luecke schliesst die dritte Bedingung des Altlast-Vermerks.
 */
export function konventionsTests(quellPfad) {
  const js = /^(.*)\.(tsx?)$/.exec(quellPfad);
  if (js && !/\.test$/.test(js[1])) return [`${js[1]}.test.${js[2]}`];
  const java = /^src\/main\/java\/(.*)\.java$/.exec(quellPfad);
  if (java) return [`src/test/java/${java[1]}Test.java`, `src/test/java/${java[1]}IT.java`];
  return [];
}

const JAVA_TESTKLASSE = /([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*\.[A-Z][\w$]*(?:Test|IT))\b/;

/**
 * Eine Testangabe aus einem Vollauf-Bericht als Pfad. Stryker nennt Testdateien bereits mit Pfad;
 * PIT nennt in `killingTest` eine Testklasse samt Methode (`org.x.FooTest.bar(org.x.FooTest)`).
 */
export function testAngabeZuPfad(angabe) {
  if (typeof angabe !== 'string' || angabe.length === 0) return null;
  if (angabe.includes('/')) return angabe;
  const klasse = JAVA_TESTKLASSE.exec(angabe)?.[1];
  if (!klasse) return null;
  return `src/test/java/${klasse.replaceAll('.', '/')}.java`;
}

/**
 * Umkehrung des letzten Vollauf-Berichts: Test -> gepruefte Quelldateien. Die Mutantenliste der
 * Gedaechtnisdatei traegt je Mutant die Quelldatei und die Tests, die ihn beruehren — im Frontend
 * aus `coveredBy`/`testFiles`, im Backend aus dem ERSTEN toetenden Test (`killingTest`), weil das
 * Profil `fullMutationMatrix` nicht setzt. Im Backend ist die Namenskonvention deshalb die
 * Hauptquelle, `succeedingTests` gibt es dort nicht.
 */
export function zuordnungAusVollauf(inhalt) {
  const zuordnung = new Map();
  for (const mutant of inhalt?.mutanten ?? []) {
    if (!mutant?.datei) continue;
    for (const angabe of mutant.tests ?? []) {
      const testPfad = testAngabeZuPfad(angabe);
      if (!testPfad) continue;
      if (!zuordnung.has(testPfad)) zuordnung.set(testPfad, new Set());
      zuordnung.get(testPfad).add(mutant.datei);
    }
  }
  return zuordnung;
}

// --- Beruehrte Dateien im Bereich -------------------------------------------

/**
 * Schnittmenge aus geaenderten Dateien und Pruefbereich, ergaenzt um die Quellen geaenderter
 * Tests: Wer einen Test schwaecht, faellt damit schon in der Aenderungspruefung auf (Kriterium 1).
 * Bleibt ein geaenderter Test der eigenen Seite ohne Zuordnung, gilt die GANZE Seite als
 * beruehrt — lieber zu viel pruefen als eine Luecke uebersehen.
 */
export function beruehrung({ geaendert, bereich, zuordnung, existiert }) {
  const dateien = new Set();
  const ohneZuordnung = [];
  for (const pfad of geaendert) {
    if (bereich.trifft(pfad)) dateien.add(pfad);
    if (!istTestdatei(pfad) || !bereich.istSeitenTest(pfad)) continue;

    const quellen = new Set(zuordnung.get(pfad) ?? []);
    const konvention = konventionsQuelle(pfad);
    if (konvention && existiert(konvention)) quellen.add(konvention);
    if (quellen.size === 0) {
      ohneZuordnung.push(pfad);
      continue;
    }
    for (const quelle of quellen) {
      if (bereich.trifft(quelle)) dateien.add(quelle);
    }
  }
  return {
    dateien: [...dateien].sort(),
    ganzeSeite: ohneZuordnung.length > 0,
    ohneZuordnung,
  };
}

// --- Stryker-Bericht --------------------------------------------------------

/**
 * `Timeout` zaehlt als getoetet: Eine Endlosschleife des Mutanten ist ein erkanntes
 * Fehlverhalten, kein unbemerkter Fehler. `Ignored` traegt KEINE der beiden Mengen — das ist der
 * Zustand, den Strykers eigene Ausnahme `// Stryker disable next-line <mutator>: <Grund>` erzeugt
 * (Kriterium 5). Ein so ausgenommener Mutant faellt damit aus der Zaehlung heraus, statt sie als
 * getoeteter zu schoenen. `CompileError` und `RuntimeError` bleiben aus demselben Grund draussen:
 * Sie sagen nichts ueber die Tests aus.
 */
export const ZUSTAND_UEBERLEBT = new Set(['Survived', 'NoCoverage']);
export const ZUSTAND_GETOETET = new Set(['Killed', 'Timeout']);

const FRONTEND_PRAEFIX = 'frontend/';

/**
 * Strykers json-Bericht in die eine Mutantenform beider Seiten. Die Pfade im Bericht sind relativ
 * zum Arbeitsverzeichnis des Laufs (`frontend`) — hier bekommen sie das Praefix, damit sie zu den
 * Pfaden aus `git` passen. `coveredBy` traegt Test-IDs; `testFiles` loest sie zu Pfaden auf.
 *
 * Mit `{ mitQuellen: true }` kommt zusaetzlich der Quelltext je Datei zurueck. Er steht im Bericht
 * selbst (`files[*].source`) und ist damit genau der Stand, den der Lauf mutiert hat — die Datei
 * spaeter erneut zu lesen, koennte einen anderen treffen.
 */
export function strykerMutanten(bericht, { mitQuellen = false } = {}) {
  const testDateiJeId = new Map();
  for (const [pfad, eintrag] of Object.entries(bericht?.testFiles ?? {})) {
    for (const test of eintrag?.tests ?? []) {
      if (test?.id !== undefined) testDateiJeId.set(String(test.id), `${FRONTEND_PRAEFIX}${pfad}`);
    }
  }

  const mutanten = [];
  const quellen = new Map();
  for (const [pfad, eintrag] of Object.entries(bericht?.files ?? {})) {
    const datei = `${FRONTEND_PRAEFIX}${pfad}`;
    if (mitQuellen) quellen.set(datei, eintrag?.source ?? '');
    for (const roh of eintrag?.mutants ?? []) {
      const deckende = new Set();
      for (const id of roh?.coveredBy ?? []) {
        const testPfad = testDateiJeId.get(String(id));
        if (testPfad) deckende.add(testPfad);
      }
      mutanten.push({
        datei,
        zeile: roh?.location?.start?.line ?? 0,
        mutator: roh?.mutatorName ?? 'unbekannt',
        ersetzung: roh?.replacement ?? '',
        zustand: roh?.status ?? 'unbekannt',
        deckendeTests: [...deckende].sort(),
      });
    }
  }
  return mitQuellen ? { mutanten, quellen } : mutanten;
}

/**
 * Der Aufruf des Werkzeugs. Verengt wird AUSSCHLIESSLICH `mutate` (`-m`) — der Testumfang bleibt
 * der bestehende: Strykers Deckungsanalyse waehlt je Mutant ohnehin die deckenden Tests, eine
 * zweite Verengung riskierte falsch Ueberlebende. `--incremental` faellt aus demselben Grund weg
 * wie die Historie im Backend: Bei wechselndem Umfang mischte eine inkrementelle Datei Staende.
 */
export function strykerArgumente(dateien) {
  const relativ = dateien.map((pfad) => (pfad.startsWith(FRONTEND_PRAEFIX) ? pfad.slice(FRONTEND_PRAEFIX.length) : pfad));
  return [
    'run',
    ...(relativ.length > 0 ? ['-m', relativ.join(',')] : []),
    '--reporters',
    'json,html,clear-text',
  ];
}

// --- Altlast-Vermerk (Kriterium 6) ------------------------------------------

/**
 * `// Mutations-Altlast: <Grund> (#<Issue>, <JJJJ-MM-TT>)`. Die Begruendung ist Pflicht und darf
 * selbst Klammern tragen — `(.+)` ist gierig und laesst dem Anhang nur die LETZTE Klammer.
 */
export const ALTLAST_MUSTER = /\/\/\s*Mutations-Altlast:\s*(\S.*)\s*\(#(\d+),\s*(\d{4}-\d{2}-\d{2})\)/;

/**
 * Der Vermerk gilt auf der Zeile des Mutanten oder unmittelbar darueber — nicht weiter weg: Sonst
 * deckte ein Vermerk am Blockanfang stillschweigend alles darunter ab.
 */
export function altlastVermerkAn(quelle, zeile) {
  const zeilen = String(quelle ?? '').split('\n');
  for (const kandidat of [zeile, zeile - 1]) {
    const treffer = ALTLAST_MUSTER.exec(zeilen[kandidat - 1] ?? '');
    if (treffer) return { grund: treffer[1].trim(), issue: treffer[2], datum: treffer[3] };
  }
  return null;
}

/** Die Zeilennummern, die ein Diff auf der NEUEN Seite antastet. `+a,0` ist eine reine Loeschung. */
export function hunkZeilen(diffText) {
  const zeilen = new Set();
  for (const treffer of String(diffText ?? '').matchAll(/^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@/gm)) {
    const start = Number(treffer[1]);
    const anzahl = treffer[2] === undefined ? 1 : Number(treffer[2]);
    for (let n = 0; n < anzahl; n += 1) zeilen.add(start + n);
  }
  return zeilen;
}

/** Die Stelle eines Mutanten, wie die Gedaechtnisdatei des Vollaufs sie fuehrt (Issue #1215). */
export function vollaufSchluessel(datei, zeile, mutator) {
  return `${datei}|${zeile}|${mutator}`;
}

// --- Auswertung -------------------------------------------------------------

/**
 * Die Entscheidung ueber Anhalten oder Durchlassen, fuer beide Seiten gleich.
 *
 * - Ueberlebende in BERUEHRTEN Dateien halten an (Kriterium 2).
 * - Ueberlebende anderswo halten nicht an und erscheinen nicht als Grund (Kriterium 4); sie
 *   stehen nur als Zahl in der Zaehlung, damit ein verengter Lauf nicht wie ein vollstaendiger
 *   aussieht.
 * - Ein Altlast-Vermerk gibt die Aenderungspruefung frei, ZAEHLT ABER WEITER MIT (Kriterium 6) —
 *   sonst verschwaende die Schuld aus der Statistik. Er greift nur, wenn ALLE vier Bedingungen
 *   zugleich erfuellt sind; die verletzte steht als `vermerkGrund` am haltenden Ueberlebenden,
 *   damit die Meldung sagen kann, warum der Vermerk diesmal nicht trug.
 */
export function auswerten({ mutanten, quellen, istBeruehrt, zeileGeaendert, dateiGeaendert, vollauf }) {
  const vollaufStellen = vollauf
    ? new Set((vollauf.mutanten ?? []).map((m) => vollaufSchluessel(m.datei, m.zeile, m.mutator)))
    : null;

  const zaehlung = { geprueft: 0, getoetet: 0, ueberlebt: 0, ausgenommen: 0, ausserhalb: 0 };
  const ueberlebende = [];
  const haltende = [];

  for (const mutant of mutanten) {
    if (mutant.zustand === 'Ignored') {
      zaehlung.ausgenommen += 1;
      continue;
    }
    if (ZUSTAND_GETOETET.has(mutant.zustand)) {
      zaehlung.geprueft += 1;
      zaehlung.getoetet += 1;
      continue;
    }
    if (!ZUSTAND_UEBERLEBT.has(mutant.zustand)) continue;

    zaehlung.geprueft += 1;
    if (!istBeruehrt(mutant.datei)) {
      zaehlung.ausserhalb += 1;
      continue;
    }
    zaehlung.ueberlebt += 1;

    const vermerk = altlastVermerkAn(quellen.get(mutant.datei), mutant.zeile);
    const stelle = { ...mutant, altlast: null, vermerkGrund: null };
    ueberlebende.push(stelle);

    if (!vermerk) {
      stelle.vermerkGrund = 'kein Altlast-Vermerk an dieser Stelle';
      haltende.push(stelle);
      continue;
    }
    if (zeileGeaendert(mutant.datei, mutant.zeile)) {
      stelle.vermerkGrund = 'die Zeile des Mutanten ist gegenüber dem Anker geändert';
      haltende.push(stelle);
      continue;
    }
    const deckend = [...new Set([...mutant.deckendeTests, ...konventionsTests(mutant.datei)])];
    const geaenderterTest = deckend.find((pfad) => dateiGeaendert(pfad));
    if (geaenderterTest) {
      stelle.vermerkGrund = `die deckende Testdatei ${geaenderterTest} hat sich geändert`;
      haltende.push(stelle);
      continue;
    }
    if (vollaufStellen && !vollaufStellen.has(vollaufSchluessel(mutant.datei, mutant.zeile, mutant.mutator))) {
      stelle.vermerkGrund = 'im letzten Vollauf hat dieser Mutant nicht überlebt — er ist keine Altlast';
      haltende.push(stelle);
      continue;
    }
    stelle.altlast = vermerk;
  }

  return { zaehlung, ueberlebende, haltende };
}

// --- Anker und Dateilisten --------------------------------------------------

/**
 * Zerlegt die NUL-getrennte Ausgabe von `git diff --name-status -z`. Eine Umbenennung (R) und
 * eine Kopie (C) tragen zwei Pfade — beide zaehlen, denn eine verschobene Datei aendert an
 * beiden Enden etwas.
 */
export function* diffPfade(roh) {
  const felder = roh.split('\0');
  let i = 0;
  while (i < felder.length) {
    const status = felder[i];
    i += 1;
    if (!status) continue;
    const anzahl = status.startsWith('R') || status.startsWith('C') ? 2 : 1;
    for (let n = 0; n < anzahl && i < felder.length; n += 1, i += 1) {
      if (felder[i]) yield felder[i];
    }
  }
}

export function* untracktePfade(roh) {
  for (const eintrag of roh.split('\0')) {
    if (eintrag.startsWith('?? ')) yield eintrag.slice(3);
  }
}

const alsPfad = (roh) => roh.replaceAll('\\', '/');

/**
 * Die geaenderten Dateien, und getrennt davon die UNGETRACKTEN: Fuer eine ungetrackte Datei gibt
 * `git diff` nichts her, sie gilt darum Zeile fuer Zeile als geaendert — sonst greifte dort ein
 * Altlast-Vermerk auf einer Zeile, die es im Anker gar nicht gab.
 */
export function geaenderteDateien(git, anker) {
  const dateien = new Set();
  const ungetrackt = new Set();
  const diff = git('diff', '--name-status', '-z', anker);
  if (diff.status === 0) {
    for (const pfad of diffPfade(diff.stdout)) dateien.add(alsPfad(pfad));
  }
  const status = git('status', '--porcelain', '-z', '--untracked-files=all');
  if (status.status === 0) {
    for (const pfad of untracktePfade(status.stdout)) {
      dateien.add(alsPfad(pfad));
      ungetrackt.add(alsPfad(pfad));
    }
  }
  return { alle: [...dateien].sort(), ungetrackt };
}

/**
 * Ein Leser fuer die Frage "ist diese Zeile gegenueber dem Anker geaendert?" (zweite Bedingung des
 * Altlast-Vermerks). Je Datei genau ein `git diff`, gemerkt. Laesst sich der Diff nicht bilden —
 * ungetrackte Datei, fehlgeschlagener Aufruf —, gilt die ganze Datei als geaendert: Die
 * Irrtumsrichtung ist "mehr pruefen, nie weniger".
 */
export function zeileGeaendertLeser(git, anker, ungetrackt) {
  const gemerkt = new Map();
  return (datei, zeile) => {
    if (anker === null || ungetrackt.has(datei)) return true;
    if (!gemerkt.has(datei)) {
      const res = git('diff', '-U0', anker, '--', datei);
      gemerkt.set(datei, res.status === 0 ? hunkZeilen(res.stdout) : null);
    }
    const zeilen = gemerkt.get(datei);
    return zeilen === null || zeilen.has(zeile);
  };
}

/**
 * Der Anker ist immer der gemeinsame Vorfahr mit dem Hauptzweig. Faellt er aus — kein `origin`,
 * kein gemeinsamer Vorfahr —, gilt der volle Umfang, und die Ausgabe sagt das in einem Satz.
 */
export function ankerBestimmen(git, hauptzweig) {
  const ref = `origin/${hauptzweig}`;
  const res = git('merge-base', 'HEAD', ref);
  const anker = res.status === 0 ? res.stdout.trim() : '';
  if (!anker) {
    return {
      anker: null,
      vollerUmfang: true,
      satz: `Anker ${ref} nicht auflösbar — geprüft wird der volle Umfang der Seite.`,
    };
  }
  return { anker, vollerUmfang: false, satz: `Anker: ${anker} (git merge-base HEAD ${ref})` };
}

// --- Stufe und Formate ------------------------------------------------------

/** Die Stufe, auf der die Pruefung dieser Seite laut Config gerade haengt (Kriterium 12). */
export function stufeAus(config, kommandoText) {
  for (const eintrag of config?.buildChecks ?? []) {
    const cmd = typeof eintrag === 'string' ? eintrag : eintrag?.cmd;
    if (typeof cmd === 'string' && cmd.includes(kommandoText)) {
      return (typeof eintrag === 'string' ? null : eintrag.stufe) ?? 'paket';
    }
  }
  return null;
}

export function dauerText(ms) {
  const sekundenGesamt = ms / 1000;
  if (sekundenGesamt < 60) return `${sekundenGesamt.toFixed(1).replace('.', ',')} s`;
  const minuten = Math.floor(sekundenGesamt / 60);
  const sekunden = Math.round(sekundenGesamt - minuten * 60);
  return `${minuten} min ${sekunden} s`;
}

function vollaufZeile(seite, inhalt) {
  if (!inhalt) {
    return `Letzter Vollauf ${seite}: es gibt noch keinen Vollauf — allein für diese Anzeige wird keiner gestartet.`;
  }
  const dauer = typeof inhalt.dauerMs === 'number' ? dauerText(inhalt.dauerMs) : 'Dauer nicht vermerkt';
  const datum = typeof inhalt.datum === 'string' ? inhalt.datum.slice(0, 10) : 'Datum nicht vermerkt';
  const quote = typeof inhalt.quote === 'number' ? `, Quote ${String(inhalt.quote).replace('.', ',')} %` : '';
  return `Letzter Vollauf ${seite}: ${dauer} am ${datum}${quote}.`;
}

/**
 * Die eine Ausgabeform fuer beide Seiten und beide Unterkommandos. Sie nennt den geprueften
 * Bereich woertlich (Kriterium 7), jede ueberlebende Stelle mit Datei, Zeile, Mutator und
 * Ersetzung (Kriterium 3), die eigene Dauer neben der des letzten Vollaufs (Kriterium 9) und die
 * Stufe (Kriterium 12). Die Liste der Ueberlebenden ist in diesem Paket noch leer; sie kommt aus
 * den Auswertungspaketen.
 */
export function meldungBauen({
  kommando,
  seite,
  bereich,
  ankerSatz,
  dateien = [],
  ganzeSeite = false,
  ohneZuordnung = [],
  ueberlebende = [],
  zaehlung = null,
  dauerMs,
  vollauf,
  stufe,
  schluss,
}) {
  const zeilen = [];
  const titel = kommando === 'aenderung' ? 'Änderungsprüfung' : 'Vollauf';
  zeilen.push(`Mutationsprüfung — ${titel} ${seite}`);
  zeilen.push('');
  zeilen.push(`Geprüfter Bereich (${bereich.quelle}):`);
  for (const zeile of bereich.zeilen) zeilen.push(`  ${zeile}`);
  zeilen.push('');
  zeilen.push(ankerSatz);

  if (kommando === 'aenderung') {
    if (ganzeSeite) {
      zeilen.push('Umfang: die ganze Seite.');
      for (const pfad of ohneZuordnung) {
        zeilen.push(`  geänderter Test ohne zuordenbare Quelle: ${pfad}`);
      }
    } else if (dateien.length === 0) {
      zeilen.push('keine berührte Datei im Prüfbereich.');
    } else {
      zeilen.push(`Berührte Dateien im Prüfbereich (${dateien.length}):`);
      for (const pfad of dateien) zeilen.push(`  ${pfad}`);
    }
  }

  zeilen.push('');
  if (ueberlebende.length === 0) {
    zeilen.push('Überlebende Stellen: keine.');
  } else {
    const altlasten = ueberlebende.filter((stelle) => stelle.altlast).length;
    const zusatz = altlasten > 0
      ? `, davon ${altlasten} mit Altlast-Vermerk — sie zählen mit, halten aber nicht an`
      : '';
    zeilen.push(`Überlebende Stellen (${ueberlebende.length}${zusatz}):`);
    for (const stelle of ueberlebende) {
      zeilen.push(`  ${stelle.datei}:${stelle.zeile} — ${stelle.mutator}: ${stelle.ersetzung} überlebt`);
      if (stelle.altlast) {
        zeilen.push(`      Altlast-Vermerk (#${stelle.altlast.issue}, ${stelle.altlast.datum}): ${stelle.altlast.grund}`);
      } else if (stelle.vermerkGrund) {
        zeilen.push(`      hält an — ${stelle.vermerkGrund}`);
      }
    }
  }

  if (zaehlung) {
    zeilen.push('');
    zeilen.push(`Mutanten: ${zaehlung.geprueft} geprüft, ${zaehlung.getoetet} getötet, `
      + `${zaehlung.ueberlebt} überlebt, ${zaehlung.ausgenommen} ausgenommen (Stryker-Ausnahme je Stelle).`);
    if (zaehlung.ausserhalb > 0) {
      zeilen.push(`Dazu ${zaehlung.ausserhalb} Überlebende außerhalb der berührten Dateien — sie halten nicht an (Kriterium 4).`);
    }
  }

  zeilen.push('');
  zeilen.push(`Dauer: ${dauerText(dauerMs)}. ${vollaufZeile(seite, vollauf)}`);
  zeilen.push(stufe ? `Stufe: ${stufe}` : 'Stufe: noch nicht eingetragen');
  if (seite === 'backend') {
    zeilen.push('Ausnahme: im Backend gilt sie je Einheit (Typ, Methode, Konstruktor), nicht je Stelle.');
  }
  if (schluss) {
    zeilen.push('');
    zeilen.push(schluss);
  }
  return `${zeilen.join('\n')}\n`;
}

// --- Lauf -------------------------------------------------------------------

function jsonLesen(pfad) {
  if (!existsSync(pfad)) return null;
  try {
    return JSON.parse(readFileSync(pfad, 'utf-8'));
  } catch {
    return null;
  }
}

function bereichLesen(seite, wurzel) {
  if (seite === 'frontend') {
    const config = jsonLesen(join(wurzel, 'frontend', 'stryker.config.json'));
    if (!config) throw new Error(`${STRYKER_PFAD} fehlt oder ist kein gültiges JSON — ohne sie ist der Prüfbereich unbekannt.`);
    return frontendBereich(config);
  }
  const pomPfad = join(wurzel, 'pom.xml');
  if (!existsSync(pomPfad)) throw new Error('pom.xml fehlt — ohne sie ist der Prüfbereich unbekannt.');
  return backendBereich(pitBereichLesen(readFileSync(pomPfad, 'utf-8')));
}

const NOCH_NICHT = 'Die Auswertung der Backend-Seite ist noch nicht umgesetzt (Issue #1214) — dieser Lauf hat nichts geprüft und endet deshalb ungleich 0.';

/** Der Bericht liegt unter `.claude/` — dort ist er durch `.claude/*` ignoriert und verschmutzt
 * den Arbeitsbaum nicht. Ein Berichtsrest unter `frontend/reports/` brachte sonst den harten Stopp
 * des Nacht-Runners auf `git status --porcelain` zum Greifen, und `checks.mjs` zaehlte ihn ueber
 * `--untracked-files=all` als Aenderung im Bereich `frontend` — jeder Lauf beruehrte dann den
 * Bereich, der ihn ausgeloest hat. */
const BERICHT_TEILE = ['.claude', 'stryker', 'mutation.json'];

/**
 * Ein Stryker-Lauf ueber die beruehrten Dateien. Der alte Bericht faellt VOR dem Lauf weg: Sonst
 * laese ein abgebrochener Lauf den Stand des vorigen und meldete ihn als seinen eigenen.
 */
function strykerLaufen({ wurzel, starte, dateien }) {
  const berichtsPfad = join(wurzel, ...BERICHT_TEILE);
  rmSync(berichtsPfad, { force: true });
  const binaer = join(wurzel, 'frontend', 'node_modules', '.bin', 'stryker');
  const ergebnis = starte(binaer, strykerArgumente(dateien), {
    cwd: join(wurzel, 'frontend'),
    encoding: 'utf-8',
  });
  return { ergebnis, bericht: jsonLesen(berichtsPfad) };
}

/**
 * Ein Lauf, vollstaendig ueber `umgebung` steuerbar: `cwd` (Projektwurzel), `git` (Aufruf mit
 * Rueckgabe wie spawnSync), `starte` (Mutationswerkzeug), `ausgabe` (Schreiben) und `jetzt` (Uhr).
 * Rueckgabe ist der Exitcode.
 */
export function laufen(argv, umgebung = {}) {
  const wurzel = umgebung.cwd ?? process.cwd();
  const ausgabe = umgebung.ausgabe ?? ((text) => process.stdout.write(text));
  const jetzt = umgebung.jetzt ?? (() => Date.now());
  const existiert = umgebung.existiert ?? ((pfad) => existsSync(join(wurzel, pfad)));
  const git = umgebung.git ?? ((...args) => spawnSync('git', args, { cwd: wurzel, encoding: 'utf-8' }));
  const starte = umgebung.starte
    ?? ((befehl, args, optionen) => spawnSync(befehl, args, { encoding: 'utf-8', ...optionen }));
  const beginn = jetzt();

  const [kommando, seite] = argv;
  if (!KOMMANDOS.includes(kommando)) {
    ausgabe(`Unbekanntes Unterkommando '${kommando ?? ''}'. Erlaubt: ${KOMMANDOS.join(', ')}.\n`
      + `Aufruf: node scripts/mutationspruefung.mjs <${KOMMANDOS.join('|')}> <${SEITEN.join('|')}>\n`);
    return 2;
  }
  if (!SEITEN.includes(seite)) {
    ausgabe(`Unbekannte Seite '${seite ?? ''}'. Erlaubt: ${SEITEN.join(', ')}.\n`
      + `Aufruf: node scripts/mutationspruefung.mjs <${KOMMANDOS.join('|')}> <${SEITEN.join('|')}>\n`);
    return 2;
  }

  let bereich;
  try {
    bereich = bereichLesen(seite, wurzel);
  } catch (err) {
    ausgabe(`${err.message}\n`);
    return 1;
  }

  const config = jsonLesen(join(wurzel, '.claude', 'workflow.config.json')) ?? {};
  const vollauf = jsonLesen(join(wurzel, '.claude', `mutationsvollauf-${seite}.json`));
  const stufe = stufeAus(config, `mutationspruefung.mjs ${kommando} ${seite}`);

  if (kommando === 'vollauf') {
    ausgabe(meldungBauen({
      kommando,
      seite,
      bereich,
      ankerSatz: 'Umfang: die ganze Seite.',
      dauerMs: jetzt() - beginn,
      vollauf,
      stufe,
      schluss: 'Der Vollauf ist noch nicht umgesetzt (Issue #1215) — er folgt in einem eigenen Arbeitspaket.',
    }));
    return 1;
  }

  const { anker, vollerUmfang, satz } = ankerBestimmen(git, config.mainBranch ?? HAUPTZWEIG_VORGABE);
  const zuordnung = zuordnungAusVollauf(vollauf);
  const stand = vollerUmfang ? { alle: [], ungetrackt: new Set() } : geaenderteDateien(git, anker);
  const gemessen = vollerUmfang
    ? { dateien: [], ganzeSeite: true, ohneZuordnung: [] }
    : beruehrung({ geaendert: stand.alle, bereich, zuordnung, existiert });

  const grundmeldung = {
    kommando,
    seite,
    bereich,
    ankerSatz: satz,
    dateien: gemessen.dateien,
    ganzeSeite: gemessen.ganzeSeite,
    ohneZuordnung: gemessen.ohneZuordnung,
    vollauf,
    stufe,
  };

  if (!gemessen.ganzeSeite && gemessen.dateien.length === 0) {
    ausgabe(meldungBauen({ ...grundmeldung, dauerMs: jetzt() - beginn }));
    return 0;
  }
  if (seite === 'backend') {
    ausgabe(meldungBauen({ ...grundmeldung, dauerMs: jetzt() - beginn, schluss: NOCH_NICHT }));
    return 1;
  }

  // Bei ganzer Seite gilt jede Datei des Pruefbereichs als beruehrt: Der Umfang ist genau dann
  // die ganze Seite, wenn der Anker fehlt oder ein geaenderter Test keiner Quelle zuzuordnen war —
  // in beiden Faellen ist unbekannt, was verschont bleiben duerfte.
  const beruehrt = new Set(gemessen.dateien);
  const geaendert = new Set(stand.alle);
  const { ergebnis, bericht } = strykerLaufen({
    wurzel,
    starte,
    dateien: gemessen.ganzeSeite ? [] : gemessen.dateien,
  });

  if (!bericht) {
    ausgabe(`Der Stryker-Lauf hat keinen Bericht unter ${BERICHT_TEILE.join('/')} hinterlassen `
      + `(Rückgabewert ${ergebnis?.status ?? 'unbekannt'}). Geprüft wurde deshalb nichts.\n`
      + `${(ergebnis?.stderr ?? '').trim().split('\n').slice(-5).join('\n')}\n`);
    return 1;
  }

  const { mutanten, quellen } = strykerMutanten(bericht, { mitQuellen: true });
  const ausgewertet = auswerten({
    mutanten,
    quellen,
    istBeruehrt: (datei) => gemessen.ganzeSeite || beruehrt.has(datei),
    zeileGeaendert: zeileGeaendertLeser(git, vollerUmfang ? null : anker, stand.ungetrackt),
    dateiGeaendert: (pfad) => vollerUmfang || geaendert.has(pfad),
    vollauf,
  });

  ausgabe(meldungBauen({
    ...grundmeldung,
    ueberlebende: ausgewertet.ueberlebende,
    zaehlung: ausgewertet.zaehlung,
    dauerMs: jetzt() - beginn,
  }));
  return ausgewertet.haltende.length > 0 ? 1 : 0;
}

const direktAufgerufen = process.argv[1]
  && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url));
if (direktAufgerufen) {
  process.exitCode = laufen(process.argv.slice(2));
}
