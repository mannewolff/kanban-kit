#!/usr/bin/env node
/**
 * mutationspruefung.mjs — Treiber der Mutationspruefung fuer beide Seiten (Issues #1212, #1213,
 * #1214, Plan #1210, fachliche Quelle #1104).
 *
 * Nutzung:
 *   node scripts/mutationspruefung.mjs aenderung frontend|backend [--stufe paket|push]
 *   node scripts/mutationspruefung.mjs vollauf   frontend|backend
 *   node scripts/mutationspruefung.mjs zuordnung frontend|backend
 *
 * Die Aenderungspruefung steht fuer beide Seiten: Anker, Dateilisten, Pruefbereich,
 * Test-zu-Quelle-Zuordnung, der Lauf (Stryker bzw. PIT), die Auswertung seines Berichts und die
 * gemeinsame Ausgabeform. Der Vollauf faehrt beide Seiten in ihrem vollen Umfang, prueft die
 * Schwelle je Seite und hinterlaesst die Gedaechtnisdatei, aus der die Aenderungspruefung Dauer,
 * Datum und die Mutantenliste des letzten Vollaufs liest (Issue #1215). Die Zuordnungspruefung ist
 * die Paketpruefung beim Kartenabschluss (Issue #1522, Plan #1521): Sie misst gegen den Anker
 * `HEAD` nur, ob jeder geaenderte Test einer Quelle zuzuordnen ist, und startet kein Werkzeug.
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

import { existsSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

import { aufgenommene, kandidat as kandidatVon, mutateFuer } from '../frontend/mutationsbereich.mjs';

export const KOMMANDOS = ['aenderung', 'vollauf', 'zuordnung'];
export const SEITEN = ['frontend', 'backend'];

/**
 * Die Schwellen des Vollaufs (Kriterium 8) — an EINER Stelle, nicht verstreut: Zwei Fundstellen
 * derselben Zahl gingen beim ersten Anheben auseinander. Backend 100, weil das Profil `pit` seine
 * Marke schon auf 100 fuehrt; Frontend 80 als der heute haltbare Stand (84,70 % am 2026-09-24).
 */
export const SCHWELLEN = { frontend: 80, backend: 100 };

/**
 * Haelt die Aenderungspruefung bei einem Test ohne Zuordnung sofort an (true) oder weicht sie auf
 * die ganze Seite aus (false)? Je Seite, weil der Preis des Ausweichens verschieden ist (Issue
 * #1308): Stryker ueber das ganze Frontend dauert rund 80 min (#1275), PIT ueber das ganze Backend
 * rund 2 min — und Backend-Tests sind oft nach Aspekten geteilt (`CardServiceEpicTreeTest`), ein
 * Halt traefe dort fast jedes Paket.
 */
export const HALT_OHNE_ZUORDNUNG = { frontend: true, backend: false };

/**
 * Ab dieser Quote schlaegt der Vollauf den Kandidaten zur Aufnahme vor (Plan #1270, AK 4). Zwei
 * Punkte ueber der Schwelle, damit ein frisch aufgenommener Ausschnitt nicht beim ersten
 * schwankenden Lauf wieder darunter faellt. Der Vorschlag ist eine Zeile im Bericht, mehr nicht.
 */
export const VORSCHLAGSSCHWELLE = 82;

const HAUPTZWEIG_VORGABE = 'main';

/** Die beiden Stufen, auf denen die Aenderungspruefung je Seite in der Config steht (Issue #1280). */
export const STUFEN = ['paket', 'push'];

/** Liegt der Median der protokollierten Laeufe darueber, gehoert die Aenderungspruefung an `push`. */
export const STUFENGRENZE_MS = 10 * 60 * 1000;

/**
 * So viele Laeufe haelt das Dauerprotokoll. Fuenf, weil der Median dann einen einzelnen Ausreisser
 * nach oben wie nach unten schluckt, ein anhaltender Anstieg aber nach drei Laeufen durchschlaegt.
 */
export const DAUER_PROTOKOLL_LAENGE = 5;

// --- Muster ----------------------------------------------------------------

const REGEX_SONDERZEICHEN = /[.+?^${}()|[\]\\]/;

function maskiere(zeichen) {
  return REGEX_SONDERZEICHEN.test(zeichen) ? `\\${zeichen}` : zeichen;
}

/**
 * Minimal-Glob fuer Pfadmuster: '*' innerhalb eines Segments, '**' ueber Segmentgrenzen.
 * Ein '**' samt folgendem Trenner darf ganz verschwinden, damit `src/lib/**\/*.ts` auch
 * `src/lib/a.ts` trifft und nicht erst eine Datei im Unterverzeichnis — dieselbe Auslegung,
 * die Stryker seinen `mutate`-Mustern gibt. Dazu Alternativen ohne Schachtelung,
 * `*.{ts,tsx}`, wie sie der Stufenplan traegt (#1276).
 */
export function globZuRegex(muster) {
  let quelle = '';
  let i = 0;
  while (i < muster.length) {
    const zeichen = muster[i];
    if (zeichen === '{' && muster.indexOf('}', i) > i) {
      const ende = muster.indexOf('}', i);
      const zweige = muster.slice(i + 1, ende).split(',');
      quelle += `(?:${zweige.map((zweig) => [...zweig].map(maskiere).join('')).join('|')})`;
      i = ende + 1;
    } else if (zeichen !== '*') {
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
 * Loest eine Klammer-Alternative `{a,b}` in zwei Muster auf. Noetig fuer `-m`: Stryker trennt die
 * Liste an jedem Komma (`stryker-cli.js`, `createSplitter(',')`), `*.{ts,tsx}` zerfiele dort in
 * zwei unsinnige Muster. Ohne Schachtelung, wie `globZuRegex`.
 */
export function klammernAufloesen(muster) {
  const treffer = /\{([^{}]*)\}/.exec(muster);
  if (!treffer) return [muster];
  const vorn = muster.slice(0, treffer.index);
  const hinten = muster.slice(treffer.index + treffer[0].length);
  return treffer[1].split(',').flatMap((zweig) => klammernAufloesen(`${vorn}${zweig}${hinten}`));
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

const STRYKER_PFAD = join('frontend', 'mutationsstufen.json');

function istMusterliste(wert) {
  return Array.isArray(wert) && wert.length > 0 && wert.every((m) => typeof m === 'string' && m.length > 0);
}

/**
 * Prueft die Form des Stufenplans, bevor ein Bereich daraus entsteht: Ein Ausschnitt ohne Muster
 * fiele sonst still aus dem Pruefbereich, und der Bericht saehe vollstaendig aus (#1073).
 * Rueckgabe ist der erste Fehler als Satz oder `null`.
 */
export function stufenplanPruefen(plan) {
  if (plan === null || typeof plan !== 'object') return 'der Stufenplan ist kein Objekt';
  if (!Array.isArray(plan.ausnahmen)) return '`ausnahmen` fehlt oder ist keine Liste';
  if (!Array.isArray(plan.ausschnitte)) return '`ausschnitte` fehlt oder ist keine Liste';
  for (const ausschnitt of plan.ausschnitte) {
    if (typeof ausschnitt?.name !== 'string' || ausschnitt.name.length === 0) {
      return 'ein Ausschnitt traegt keinen `name`';
    }
    if (!istMusterliste(ausschnitt.muster)) {
      return `Ausschnitt ${ausschnitt.name}: \`muster\` fehlt oder ist keine nicht-leere Liste`;
    }
  }
  return null;
}

/** Ein- und Ausschluss eines `mutate`-Musters als Regex, relativ zur Repo-Wurzel. */
function frontendRegexe(mutate) {
  const ein = [];
  const aus = [];
  for (const muster of mutate) {
    const negiert = muster.startsWith('!');
    const roh = negiert ? muster.slice(1) : muster;
    (negiert ? aus : ein).push(globZuRegex(`frontend/${roh}`));
  }
  return { ein, aus };
}

/**
 * Der Frontend-Bereich kommt aus dem Stufenplan `frontend/mutationsstufen.json` — dieselbe Quelle,
 * aus der `stryker.config.mjs` ihr `mutate` und der Mutationslauf seinen Testumfang ableiten. Nie
 * duplizieren: Ab der ersten Abweichung pruefte der Treiber einen anderen Bereich als das Werkzeug.
 *
 * Neben dem Pruefbereich liefert er ALLE Ausschnitte des Plans mit ihrer Trefferfunktion — auch
 * die noch nicht aufgenommenen, damit sich jede Datei ihrem Ausschnitt zuordnen laesst. Die
 * Ausschluesse (Testdateien, Ausnahmen) gelten fuer jeden Ausschnitt.
 */
export function frontendBereich(plan) {
  const woertlich = mutateFuer(aufgenommene(plan).map((a) => a.name), plan);
  const { ein, aus } = frontendRegexe(woertlich);
  const ausgeschlossen = (pfad) => aus.some((r) => r.test(pfad));
  const naechster = kandidatVon(plan);
  const ausschnitte = plan.ausschnitte.map((ausschnitt) => {
    const eigene = ausschnitt.muster.map((m) => globZuRegex(`frontend/${m}`));
    const tests = (ausschnitt.testMuster ?? []).map((m) => globZuRegex(`frontend/${m}`));
    return {
      name: ausschnitt.name,
      muster: ausschnitt.muster,
      aufgenommen: typeof ausschnitt.aufgenommen === 'string',
      aufgenommenAm: typeof ausschnitt.aufgenommen === 'string' ? ausschnitt.aufgenommen : false,
      gemeinsameSchwelle: ausschnitt.gemeinsameSchwelle === true,
      kandidat: ausschnitt === naechster,
      trifft: (pfad) => eigene.some((r) => r.test(pfad)) && !ausgeschlossen(pfad),
      umfasstTest: (pfad) => eigene.some((r) => r.test(pfad)) || tests.some((r) => r.test(pfad)),
    };
  });
  // Der Vollauf misst die aufgenommenen Ausschnitte plus genau den Kandidaten (E6). Sein `-m`
  // traegt die Ausschluesse und Ausnahmen woertlich mit, weil `--mutate` die Liste der
  // Konfiguration ersetzt — sonst mutierte er die Testdateien mit.
  const gemessen = ausschnitte.filter((a) => a.aufgenommen || a.kandidat).map((a) => a.name);
  return {
    seite: 'frontend',
    quelle: `${STRYKER_PFAD} (aufgenommene Ausschnitte)`,
    woertlich,
    zeilen: woertlich,
    ausschnitte,
    gemessen,
    vollaufMutate: mutateFuer(gemessen, plan),
    trifft: (pfad) => ein.some((r) => r.test(pfad)) && !ausgeschlossen(pfad),
    istSeitenTest: (pfad) => pfad.startsWith('frontend/') && /\.test\.tsx?$/.test(pfad),
    // Ein Test, der nur zu noch nicht aufgenommenen Ausschnitten gehoert (Muster oder `testMuster`),
    // liegt ausserhalb des Pruefbereichs (Issue #1279). Ohne Zuordnung haelt er deshalb nicht an —
    // sonst braeche der Altbestand ueber die Pfadfinderregel herein, bevor sein Ausschnitt
    // aufgenommen ist. Ein Test ausserhalb jedes Ausschnitts bleibt unbekannt und haelt weiter an.
    testAusserhalb: (pfad) => {
      const eigene = ausschnitte.filter((a) => a.umfasstTest(pfad));
      return eigene.length > 0 && eigene.every((a) => !a.aufgenommen);
    },
  };
}

/** Der Ausschnitt, in dem eine Datei liegt, oder `null` — fuer beide Seiten dieselbe Frage. */
export function ausschnittVon(bereich, pfad) {
  return bereich.ausschnitte.find((ausschnitt) => ausschnitt.trifft(pfad)) ?? null;
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
  const trifft = (pfad) => {
    const klasse = javaPfadZuKlasse(pfad);
    if (klasse === null) return false;
    return ein.some((r) => r.test(klasse)) && !aus.some((r) => r.test(klasse));
  };
  return {
    seite: 'backend',
    quelle,
    woertlich: gelesen,
    zeilen: [
      ...gelesen.ziel.map((m) => `eingeschlossen: ${m}`),
      ...gelesen.aus.map((m) => `ausgenommen:    ${m}`),
    ],
    // Genau ein Ausschnitt ueber den ganzen Pruefbereich (E11): Die Ausschnittslogik ist damit fuer
    // beide Seiten dieselbe, statt zwei Auswertungspfade zu tragen, die auseinanderlaufen.
    ausschnitte: [{ name: 'backend', aufgenommen: true, trifft }],
    gemessen: ['backend'],
    trifft,
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
 *
 * Die Klassenform wird ZUERST geprueft: Unter JUnit 5 traegt `killingTest` einen Descriptor, der
 * selbst Schraegstriche enthaelt (`org.x.FooTest.[engine:junit-jupiter]/[class:org.x.FooTest]/…`).
 * Ein Test auf den Schraegstrich zuerst haette ihn faelschlich fuer einen Pfad gehalten. Geprueft
 * wird nur der Teil vor der ersten eckigen Klammer, damit der Descriptor nicht mitspricht.
 */
export function testAngabeZuPfad(angabe) {
  if (typeof angabe !== 'string' || angabe.length === 0) return null;
  const klasse = JAVA_TESTKLASSE.exec(angabe.split('[')[0])?.[1];
  if (klasse) return `src/test/java/${klasse.replaceAll('.', '/')}.java`;
  return angabe.includes('/') ? angabe : null;
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
 *
 * Der dritte Weg neben Konvention und Berichtsumkehrung ist die feste Zuordnung aus
 * `scripts/mutationszuordnung.json` (Issue #1287): fuer Tests, deren Name keine Quelle trifft.
 * Zeigt sie nur auf Quellen ausserhalb des Bereichs, ist der Test zugeordnet und steuert nichts bei.
 * Ein Test eines noch nicht aufgenommenen Ausschnitts ohne Zuordnung steuert ebenfalls nichts bei
 * (Issue #1279): Pruefbereich sind die aufgenommenen Ausschnitte, alles andere ist aussen vor.
 */
export function beruehrung({ geaendert, bereich, zuordnung, festeZuordnung = {}, existiert }) {
  const dateien = new Set();
  const ohneZuordnung = [];
  for (const pfad of geaendert) {
    if (bereich.trifft(pfad)) dateien.add(pfad);
    if (!istTestdatei(pfad) || !bereich.istSeitenTest(pfad)) continue;

    const quellen = new Set([...(zuordnung.get(pfad) ?? []), ...(festeZuordnung[pfad] ?? [])]);
    const konvention = konventionsQuelle(pfad);
    if (konvention && existiert(konvention)) quellen.add(konvention);
    if (quellen.size === 0) {
      if (bereich.testAusserhalb?.(pfad)) continue;
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
        grund: roh?.statusReason ?? null,
        deckendeTests: [...deckende].sort(),
      });
    }
  }
  return mitQuellen ? { mutanten, quellen } : mutanten;
}

// --- PIT-Bericht ------------------------------------------------------------

/**
 * Die Zustaende von PIT in dasselbe Vokabular wie die von Stryker. Ein unbekannter Zustand bleibt
 * woertlich stehen und faellt damit in KEINE der beiden Mengen — `NON_VIABLE`, `RUN_ERROR` und
 * `MEMORY_ERROR` sagen nichts ueber die Tests aus, genau wie `CompileError` im Frontend.
 *
 * Einen Gegenpart zu `Ignored` gibt es nicht: Die Backend-Ausnahme (FANN ueber
 * `@ExcludeFromJacocoGeneratedReport`) wirkt schon bei der Erzeugung, solche Mutanten stehen gar
 * nicht erst im Bericht.
 */
const PIT_ZUSTAENDE = new Map([
  ['KILLED', 'Killed'],
  ['TIMED_OUT', 'Timeout'],
  ['SURVIVED', 'Survived'],
  ['NO_COVERAGE', 'NoCoverage'],
]);

export function pitZustand(status) {
  return PIT_ZUSTAENDE.get(status) ?? status;
}

const ENTITAETEN = new Map([['amp', '&'], ['lt', '<'], ['gt', '>'], ['quot', '"'], ['apos', "'"]]);

function xmlText(roh) {
  return String(roh ?? '').replaceAll(/&(#\d+|#x[\da-fA-F]+|\w+);/g, (ganz, name) => {
    if (name.startsWith('#x') || name.startsWith('#X')) return String.fromCodePoint(Number.parseInt(name.slice(2), 16));
    if (name.startsWith('#')) return String.fromCodePoint(Number(name.slice(1)));
    return ENTITAETEN.get(name) ?? ganz;
  });
}

/**
 * Die Quelldatei eines Mutanten. `sourceFile` traegt nur den Dateinamen, `mutatedClass` das Paket —
 * zusammen ergeben sie den Pfad. Der Umweg ueber `sourceFile` statt ueber den Klassennamen allein
 * ist der genauere: Eine innere Klasse (`Card$Zustand`) und eine im selben Datei-Kopf deklarierte
 * Nebenklasse landen so bei ihrer wirklichen Datei statt bei einer erfundenen.
 */
export function pitQuellPfad(mutatedClass, sourceFile) {
  if (!mutatedClass || !sourceFile) return null;
  const punkt = mutatedClass.lastIndexOf('.');
  const paket = punkt < 0 ? '' : `${mutatedClass.slice(0, punkt).replaceAll('.', '/')}/`;
  return `src/main/java/${paket}${sourceFile}`;
}

function xmlFeld(block, name) {
  const treffer = new RegExp(`<${name}>([\\s\\S]*?)</${name}>`).exec(block);
  return treffer ? xmlText(treffer[1]) : '';
}

/**
 * `target/pit-reports/mutations.xml` in dieselbe Mutantenform wie der Stryker-Bericht. Gelesen wird
 * je Mutant Datei, Zeile, Mutator, Ersetzung (`description`) und Zustand.
 *
 * Als deckender Test steht nur der ERSTE toetende (`killingTest`) zur Verfuegung: Das Profil setzt
 * `fullMutationMatrix` nicht, und `succeedingTests` gibt es in diesem Bericht gar nicht. Bei einem
 * Ueberlebenden ist das Element leer oder fehlt — dort traegt die Namenskonvention die dritte
 * Bedingung des Altlast-Vermerks allein.
 */
export function pitMutanten(xml) {
  const mutanten = [];
  for (const treffer of String(xml ?? '').matchAll(/<mutation\b([^>]*)>([\s\S]*?)<\/mutation>/g)) {
    const [, kopf, block] = treffer;
    const datei = pitQuellPfad(xmlFeld(block, 'mutatedClass'), xmlFeld(block, 'sourceFile'));
    if (!datei) continue;
    const toetend = testAngabeZuPfad(xmlFeld(block, 'killingTest'));
    mutanten.push({
      datei,
      zeile: Number(xmlFeld(block, 'lineNumber')) || 0,
      mutator: xmlFeld(block, 'mutator').split('.').pop(),
      ersetzung: xmlFeld(block, 'description'),
      zustand: pitZustand(/status=['"]([^'"]*)['"]/.exec(kopf)?.[1] ?? ''),
      deckendeTests: toetend ? [toetend] : [],
    });
  }
  return mutanten;
}

/**
 * Der Aufruf des Werkzeugs. Verengt wird der Pruefbereich ueber `-Dpit.targetClasses`, damit die
 * Einschraenkung schon IM Lauf wirkt und nicht erst bei der Auswertung. Je beruehrter Datei zwei
 * Muster: die Klasse selbst und `$*` fuer ihre inneren und anonymen Typen — PIT vergleicht gegen
 * den Klassennamen, und `Foo` traefe `Foo$1` nicht.
 *
 * `-Dpit.marke=0` schaltet die Werkzeugschwelle ab: Der Halt kommt aus dem Rueckgabewert dieses
 * Treibers, weil ein Altlast-Vermerk nicht anhalten, aber mitzaehlen soll. Keine Historie — das
 * Bestandspaket `pitest-entry-1.25.7` bringt keine `HistoryFactory` mit, ein Lauf mit
 * `withHistory` braeche ab.
 *
 * `-B` (Batch-Modus), weil dieser Lauf programmatisch startet: Seine Ausgabe wird nie
 * durchgereicht, sondern zusammengefasst — ANSI-Steuerzeichen darin waeren nur Rauschen —, und
 * Maven darf nichts erfragen, worauf niemand antwortet.
 */
export function pitArgumente(dateien) {
  const klassen = [];
  for (const pfad of dateien) {
    const klasse = javaPfadZuKlasse(pfad);
    if (klasse) klassen.push(klasse, `${klasse}$*`);
  }
  return [
    '-B',
    '-Ppit',
    '-Dskip.frontend=true',
    '-Dpit.marke=0',
    ...(klassen.length > 0 ? [`-Dpit.targetClasses=${klassen.join(',')}`] : []),
    'test',
  ];
}

/**
 * Der Vollauf des Backends faehrt das Profil `pit` mit seinem Default-Umfang UND seiner
 * Default-Marke: Anders als die Aenderungspruefung schaltet er `pit.marke` nicht ab — dort musste
 * die Werkzeugschwelle weichen, weil ein Altlast-Vermerk nicht anhalten, aber mitzaehlen soll; hier
 * gibt es keine Vermerke, und die Schwelle dieses Treibers (100) sagt dasselbe wie die des Profils.
 */
export function pitVollaufArgumente() {
  return ['-B', '-Ppit', '-Dskip.frontend=true', 'test'];
}

/**
 * Der Aufruf des Werkzeugs, fuer beide Unterkommandos: Verengt wird AUSSCHLIESSLICH `mutate`
 * (`-m`) — der Testumfang bleibt der bestehende, denn Strykers Deckungsanalyse waehlt je Mutant
 * ohnehin die deckenden Tests, und eine zweite Verengung riskierte falsch Ueberlebende. Ohne
 * Dateiliste — also im Vollauf — bleibt der volle `mutate`-Bereich stehen.
 *
 * Kein gespeicherter Zwischenstand von Lauf zu Lauf, aus demselben Grund wie die fehlende Historie
 * im Backend: Bei wechselndem Umfang mischte eine solche Datei Staende, und der Vollauf ist der
 * Nachweis (Kriterium 11) — er darf auf keinem uebernommenen Urteil ruhen.
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

/** Der Grund, den der Darstellungs-Ignorer (#1277) jedem seiner Mutanten mitgibt, beginnt so. */
const DARSTELLUNG_PRAEFIX = 'Darstellung:';

/**
 * Die Zaehlung je Ausschnitt. Neben den Mengen der Gesamtzaehlung fuehrt sie zwei Teilmengen: die
 * vom Darstellungs-Ignorer ausgenommenen Mutanten (ein Teil von `ausgenommen`), damit eine
 * Verschiebung im Bestand auffaellt, und die ungedeckten (`NoCoverage`, ein Teil von `ueberlebt`),
 * an denen ein Kandidat ohne Tests im Umfang zu erkennen ist.
 */
function ausschnittsZaehlung() {
  return { geprueft: 0, getoetet: 0, ueberlebt: 0, ausgenommen: 0, darstellung: 0, ohneDeckung: 0 };
}

/**
 * Die Auswertung des Vollaufs. Sie kennt weder Beruehrung noch Altlast-Vermerk: Im Vollauf gilt die
 * ganze Seite, und angehalten wird allein an der Schwelle (Kriterium 8) — ein Vermerk gibt die
 * AENDERUNGSpruefung frei, nicht die Gesamtquote. Gezaehlt wird wie dort, damit beide Ausgaben
 * dieselbe Zahl gleich meinen.
 *
 * Mit `ausschnitte` (Issue #1278) ordnet sie jeden Mutanten seinem Ausschnitt zu und zaehlt je
 * Ausschnitt. Gesamtzaehlung und Ueberlebendenliste bleiben beim Pruefbereich, also bei den
 * AUFGENOMMENEN Ausschnitten: Der Kandidat wird nur gemessen, und seine Ueberlebenden fluteten sonst
 * Bericht und Gedaechtnisdatei. Ein Mutant, der in keinen Ausschnitt faellt, landet in
 * `ohneAusschnitt` — Stufenplan und Mutationsumfang passen dann nicht zusammen, und das wird
 * gemeldet statt verschluckt. Bei genau einem Ausschnitt (Backend, E11) ist er der ganze
 * Pruefbereich: Jeder Mutant, den das Werkzeug erzeugt hat, gehoert zu ihm.
 */
export function vollaufAuswerten(mutanten, ausschnitte = null) {
  const zaehlung = { geprueft: 0, getoetet: 0, ueberlebt: 0, ausgenommen: 0, ausserhalb: 0 };
  const ueberlebende = [];
  const ohneAusschnitt = [];
  const je = new Map((ausschnitte ?? []).map((a) => [a, ausschnittsZaehlung()]));

  for (const mutant of mutanten) {
    let ausschnitt = null;
    if (ausschnitte !== null) {
      ausschnitt = ausschnitte.length === 1 ? ausschnitte[0] : ausschnitte.find((a) => a.trifft(mutant.datei));
      if (!ausschnitt) {
        ohneAusschnitt.push(mutant);
        continue;
      }
    }
    const eigene = ausschnitt ? je.get(ausschnitt) : ausschnittsZaehlung();
    const gesamt = ausschnitt === null || ausschnitt.aufgenommen;
    if (mutant.zustand === 'Ignored') {
      eigene.ausgenommen += 1;
      if (String(mutant.grund ?? '').startsWith(DARSTELLUNG_PRAEFIX)) eigene.darstellung += 1;
      if (gesamt) zaehlung.ausgenommen += 1;
      continue;
    }
    if (ZUSTAND_GETOETET.has(mutant.zustand)) {
      eigene.geprueft += 1;
      eigene.getoetet += 1;
      if (gesamt) {
        zaehlung.geprueft += 1;
        zaehlung.getoetet += 1;
      }
      continue;
    }
    if (!ZUSTAND_UEBERLEBT.has(mutant.zustand)) continue;
    eigene.geprueft += 1;
    eigene.ueberlebt += 1;
    if (mutant.zustand === 'NoCoverage') eigene.ohneDeckung += 1;
    if (gesamt) {
      zaehlung.geprueft += 1;
      zaehlung.ueberlebt += 1;
      ueberlebende.push({ ...mutant, altlast: null, vermerkGrund: null });
    }
  }

  const jeAusschnitt = [...je].map(([ausschnitt, eigene]) => ({
    name: ausschnitt.name,
    muster: ausschnitt.muster ?? [],
    aufgenommen: ausschnitt.aufgenommen,
    aufgenommenAm: ausschnitt.aufgenommenAm ?? ausschnitt.aufgenommen,
    gemeinsameSchwelle: ausschnitt.gemeinsameSchwelle === true,
    kandidat: ausschnitt.kandidat === true,
    zaehlung: eigene,
    quote: quoteAus(eigene),
  }));
  return { zaehlung, ueberlebende, ausschnitte: jeAusschnitt, ohneAusschnitt };
}

/**
 * Der Halt je Ausschnitt (AK 3, AK 6) samt der Bestandsregel (E7).
 *
 * Jeder aufgenommene Ausschnitt unter der Schwelle haelt an und wird genannt, auch wenn alle
 * anderen darueber liegen; der Kandidat haelt nie an. Die Bestandsausschnitte
 * (`gemeinsameSchwelle`) halten zunaechst nicht einzeln an, fuer sie zaehlt ihre gemeinsame Quote.
 * Das Datum, an dem ein Bestandsausschnitt erstmals die Schwelle erreichte (`erstmals80`), kommt
 * aus der Gedaechtnisdatei des vorigen Vollaufs und wird hier fortgeschrieben; sobald jeder
 * Bestandsausschnitt eines traegt, gilt fuer sie die getrennte Schwelle. Der Treiber schreibt dazu
 * nie in den versionierten Stufenplan — nur in die Gedaechtnisdatei unter `.claude/`.
 */
export function haltBestimmen({ ausschnitte, schwelle, erstmalsVorher = {}, heute }) {
  const bestand = ausschnitte.filter((a) => a.aufgenommen && a.gemeinsameSchwelle);
  const erstmals80 = {};
  for (const a of bestand) {
    const vorher = erstmalsVorher[a.name];
    erstmals80[a.name] = typeof vorher === 'string' ? vorher : (a.quote >= schwelle ? heute : null);
  }
  const getrennt = bestand.length > 0 && bestand.every((a) => erstmals80[a.name] !== null);
  const gemeinsamZaehlung = bestand.reduce(
    (summe, a) => ({ geprueft: summe.geprueft + a.zaehlung.geprueft, getoetet: summe.getoetet + a.zaehlung.getoetet }),
    { geprueft: 0, getoetet: 0 },
  );
  const gemeinsam = bestand.length > 0 && !getrennt
    ? { namen: bestand.map((a) => a.name), quote: quoteAus(gemeinsamZaehlung) }
    : null;

  const haltende = [];
  if (gemeinsam && gemeinsam.quote < schwelle) haltende.push(`Bestand gemeinsam (${gemeinsam.namen.join(', ')})`);
  for (const a of ausschnitte) {
    if (!a.aufgenommen) continue;
    if (gemeinsam && a.gemeinsameSchwelle) continue;
    if (a.quote < schwelle) haltende.push(a.name);
  }
  const karten = gemeinsam ? bestand.filter((a) => a.quote < schwelle).map((a) => a.name) : [];
  return { haltende, gemeinsam, karten, erstmals80 };
}

/**
 * Die Quote in Prozent, auf zwei Stellen gerundet — dieselbe Rechnung, die Stryker seinen
 * Mutation-Score nennt. Ohne einen einzigen gepruefsten Mutanten gilt sie als erfuellt: Eine Seite
 * ohne Mutanten hat nichts unterschritten, und eine 0 dort liesse jeden Vollauf anhalten, bevor es
 * etwas zu messen gibt.
 */
export function quoteAus(zaehlung) {
  if (zaehlung.geprueft === 0) return 100;
  return Math.round((zaehlung.getoetet / zaehlung.geprueft) * 10000) / 100;
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

/**
 * Die Stufe, auf der die Pruefung dieser Seite laut Config gerade haengt (Kriterium 12). Mit
 * `stufeArgument` zaehlt nur der Eintrag, dessen `cmd` dasselbe `--stufe`-Argument traegt: Mit zwei
 * Eintraegen je Seite faende der blosse Teilstring sonst in beiden Laeufen den ersten (Issue #1280).
 */
export function stufeAus(config, kommandoText, stufeArgument = null) {
  const argumentMuster = stufeArgument ? new RegExp(`--stufe\\s+${stufeArgument}(\\s|$)`) : null;
  for (const eintrag of config?.buildChecks ?? []) {
    const cmd = typeof eintrag === 'string' ? eintrag : eintrag?.cmd;
    if (typeof cmd === 'string' && cmd.includes(kommandoText) && (!argumentMuster || argumentMuster.test(cmd))) {
      return (typeof eintrag === 'string' ? null : eintrag.stufe) ?? 'paket';
    }
  }
  return null;
}

export function median(werte) {
  if (werte.length === 0) return null;
  const sortiert = [...werte].sort((a, b) => a - b);
  const mitte = Math.floor(sortiert.length / 2);
  return sortiert.length % 2 === 1 ? sortiert[mitte] : (sortiert[mitte - 1] + sortiert[mitte]) / 2;
}

/**
 * Die Stufe, auf der die Aenderungspruefung gilt, aus dem Dauerprotokoll. Ohne Protokoll gilt
 * `paket` — etwa in einer frischen Arbeitskopie, denn `.claude/*` ist nicht versioniert; die
 * Irrtumsrichtung heisst "mehr pruefen, nie weniger".
 */
export function geltendeStufe(protokoll) {
  const dauern = (protokoll?.laeufe ?? []).map((lauf) => lauf?.dauerMs).filter(Number.isFinite);
  const mitte = median(dauern);
  return { mitte, anzahl: dauern.length, stufe: mitte !== null && mitte > STUFENGRENZE_MS ? 'push' : 'paket' };
}

export function dauerText(ms) {
  const sekundenGesamt = ms / 1000;
  if (sekundenGesamt < 60) return `${sekundenGesamt.toFixed(1).replace('.', ',')} s`;
  const minuten = Math.floor(sekundenGesamt / 60);
  const sekunden = Math.round(sekundenGesamt - minuten * 60);
  return `${minuten} min ${sekunden} s`;
}

/** Prozentangaben in derselben Schreibweise wie die Dauer: Komma statt Punkt. */
export function prozentText(wert) {
  return String(wert).replace('.', ',');
}

/**
 * Die Zeile zur Schwelle (Kriterium 8). Sie nennt den gemessenen Wert UND die Schwelle in beiden
 * Richtungen: Ein gruener Lauf, der nur "erfuellt" sagte, liesse offen, wie knapp es war.
 */
export function schwellenZeile(quote, schwelle) {
  const gemessen = `Quote: ${prozentText(quote.toFixed(2))} %`;
  return quote >= schwelle
    ? `${gemessen} — Schwelle ${prozentText(schwelle)} % erfüllt.`
    : `${gemessen} — unter der Schwelle ${prozentText(schwelle)} %. Der Vollauf hält an.`;
}

function ausschnittsQuoteText(a, schwelle) {
  if (a.zaehlung.geprueft === 0) return 'keine Mutanten — bestanden.';
  const gemessen = `${prozentText(a.quote.toFixed(2))} %`;
  return a.quote >= schwelle
    ? `${gemessen} — Schwelle ${prozentText(schwelle)} % erfüllt`
    : `${gemessen} — unter der Schwelle ${prozentText(schwelle)} %`;
}

function zaehlungsText(z) {
  return `${z.geprueft} geprüft, ${z.getoetet} getötet, ${z.ueberlebt} überlebt, `
    + `${z.ausgenommen} ausgenommen, davon ${z.darstellung} Darstellung.`;
}

function kandidatText(a) {
  const vorschlag = `zur Aufnahme vorgeschlagen. Die Aufnahme trägt der Mensch in ${STRYKER_PFAD} ein.`;
  if (a.zaehlung.geprueft === 0) return `keine Mutanten — bestanden, ${vorschlag}`;
  if (a.zaehlung.ohneDeckung === a.zaehlung.geprueft) return '0 % — keine Tests im Umfang.';
  const gemessen = `${prozentText(a.quote.toFixed(2))} %`;
  return a.quote >= VORSCHLAGSSCHWELLE
    ? `${gemessen} — erreicht ${VORSCHLAGSSCHWELLE} %, ${vorschlag}`
    : `${gemessen} — unter ${VORSCHLAGSSCHWELLE} %, nicht zur Aufnahme vorgeschlagen.`;
}

/**
 * Der Bericht je Ausschnitt (AK 5): je aufgenommenem Ausschnitt der gemessene Wert gegen die
 * Schwelle, die Zaehlung samt der Darstellungsmutanten des Ignorers, dann die Bestandsregel (E7)
 * und zuletzt der Kandidat gegen die Vorschlagsschwelle. Die Gesamtquote steht danach als Wert,
 * nicht mehr als Schwellenzeile — gehalten wird je Ausschnitt. Bei genau einem Ausschnitt entfaellt
 * der ganze Block (E11), die Meldung bleibt dann zeichengleich mit der vor Issue #1278.
 */
export function ausschnittsZeilen({ ausschnitte, halt, schwelle, quote, ohneAusschnitt = [] }) {
  const zeilen = [`Ausschnitte (Schwelle ${prozentText(schwelle)} % je aufgenommenem Ausschnitt):`];
  const imBestand = new Set(halt.gemeinsam?.namen ?? []);
  for (const a of ausschnitte.filter((x) => x.aufgenommen)) {
    const text = ausschnittsQuoteText(a, schwelle);
    const zusatz = imBestand.has(a.name) ? ' (gemeinsame Schwelle des Bestands).' : '.';
    zeilen.push(`  ${a.name}: ${text.endsWith('.') ? text : `${text}${zusatz}`}`);
    zeilen.push(`      ${zaehlungsText(a.zaehlung)}`);
  }
  if (halt.gemeinsam) {
    const { namen, quote: gemeinsam } = halt.gemeinsam;
    const stand = namen.map((name) => `${name}: ${halt.erstmals80[name] ?? 'noch nicht'}`).join(', ');
    const urteil = gemeinsam >= schwelle ? 'erfüllt' : 'nicht erfüllt';
    zeilen.push(`Bestand gemeinsam (${namen.join(', ')}): ${prozentText(gemeinsam.toFixed(2))} % — `
      + `Schwelle ${prozentText(schwelle)} % ${urteil}. Die getrennte Schwelle gilt, sobald jeder `
      + `Bestandsausschnitt erstmals ${prozentText(schwelle)} % erreicht hat (${stand}).`);
    for (const name of halt.karten) {
      zeilen.push(`  ${name} unter ${prozentText(schwelle)} % — Karte für die fehlenden Tests anlegen.`);
    }
  }
  const naechster = ausschnitte.find((a) => a.kandidat);
  if (naechster) {
    zeilen.push(`Kandidat ${naechster.name}: ${kandidatText(naechster)}`);
    zeilen.push(`      ${zaehlungsText(naechster.zaehlung)}`);
  } else {
    zeilen.push('Kandidat: keiner — alle Ausschnitte sind aufgenommen.');
  }
  if (ohneAusschnitt.length > 0) {
    const dateien = [...new Set(ohneAusschnitt.map((m) => m.datei))].sort();
    zeilen.push(`Mutanten ohne Ausschnitt (${ohneAusschnitt.length}) — Stufenplan und Mutationsumfang passen nicht zusammen:`);
    for (const datei of dateien) zeilen.push(`  ${datei}`);
  }
  zeilen.push('');
  zeilen.push(`Quote: ${prozentText(quote.toFixed(2))} % gesamt über die aufgenommenen Ausschnitte.`);
  zeilen.push(halt.haltende.length > 0
    ? `Der Vollauf hält an: ${halt.haltende.join(', ')} unter der Schwelle ${prozentText(schwelle)} %.`
    : `Kein Halt: Schwelle ${prozentText(schwelle)} % in jedem aufgenommenen Ausschnitt erfüllt`
      + `${halt.gemeinsam ? ', im Bestand gemeinsam' : ''}.`);
  return zeilen;
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
  quote = null,
  schwelle = null,
  ausschnittsBericht = null,
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
    // Die Form der Ausnahme steht an der Zahl, weil die beiden Seiten verschieden ausnehmen:
    // Stryker markiert je Stelle und meldet den Mutanten als `Ignored`; im Backend nimmt FANN
    // ihn schon bei der Erzeugung heraus, er erscheint gar nicht erst im Bericht.
    const ausnahme = seite === 'backend'
      ? '@ExcludeFromJacocoGeneratedReport je Einheit — solche Mutanten entstehen gar nicht erst'
      : 'Stryker-Ausnahme je Stelle';
    zeilen.push('');
    zeilen.push(`Mutanten: ${zaehlung.geprueft} geprüft, ${zaehlung.getoetet} getötet, `
      + `${zaehlung.ueberlebt} überlebt, ${zaehlung.ausgenommen} ausgenommen (${ausnahme}).`);
    if (zaehlung.ausserhalb > 0) {
      zeilen.push(`Dazu ${zaehlung.ausserhalb} Überlebende außerhalb der berührten Dateien — sie halten nicht an (Kriterium 4).`);
    }
  }

  if (ausschnittsBericht) {
    zeilen.push('');
    zeilen.push(...ausschnittsBericht);
  } else if (quote !== null && schwelle !== null) {
    zeilen.push('');
    zeilen.push(schwellenZeile(quote, schwelle));
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

export const ZUORDNUNG_PFAD = 'scripts/mutationszuordnung.json';

function ohneZuordnungMeldung(tests, kopfzeile, begruendung) {
  const zeilen = [kopfzeile, ''];
  for (const pfad of tests) zeilen.push(`  geänderter Test ohne zuordenbare Quelle: ${pfad}`);
  zeilen.push('');
  zeilen.push(`Eintrag in ${ZUORDNUNG_PFAD} ergänzen oder den Test nach der Quelle benennen.`);
  zeilen.push(begruendung);
  zeilen.push('-> rot');
  return `${zeilen.join('\n')}\n`;
}

/** Die feste Test-zu-Quelle-Zuordnung (Issue #1287); fehlt die Datei, ist sie leer. */
export function festeZuordnungLesen(wurzel) {
  return jsonLesen(join(wurzel, ZUORDNUNG_PFAD)) ?? {};
}

function bereichLesen(seite, wurzel) {
  if (seite === 'frontend') {
    const plan = jsonLesen(join(wurzel, STRYKER_PFAD));
    if (!plan) throw new Error(`${STRYKER_PFAD} fehlt oder ist kein gültiges JSON — ohne ihn ist der Prüfbereich unbekannt.`);
    const fehler = stufenplanPruefen(plan);
    if (fehler) throw new Error(`${STRYKER_PFAD} ist ungültig: ${fehler} — ohne ihn ist der Prüfbereich unbekannt.`);
    return frontendBereich(plan);
  }
  const pomPfad = join(wurzel, 'pom.xml');
  if (!existsSync(pomPfad)) throw new Error('pom.xml fehlt — ohne sie ist der Prüfbereich unbekannt.');
  return backendBereich(pitBereichLesen(readFileSync(pomPfad, 'utf-8')));
}

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
function strykerLaufen({ wurzel, starte, args }) {
  const berichtsPfad = join(wurzel, ...BERICHT_TEILE);
  rmSync(berichtsPfad, { force: true });
  const binaer = join(wurzel, 'frontend', 'node_modules', '.bin', 'stryker');
  const ergebnis = starte(binaer, args, {
    cwd: join(wurzel, 'frontend'),
    encoding: 'utf-8',
  });
  return { ergebnis, bericht: jsonLesen(berichtsPfad) };
}

/** PIT schreibt seinen XML-Bericht unversioniert nach `target/` — es gibt ihn also fertig. */
const PIT_BERICHT_TEILE = ['target', 'pit-reports', 'mutations.xml'];

/**
 * Ein PIT-Lauf ueber die beruehrten Klassen. Wie im Frontend faellt der alte Bericht VOR dem Lauf
 * weg, sonst laese ein abgebrochener Lauf den Stand des vorigen und meldete ihn als seinen eigenen.
 */
function pitLaufen({ wurzel, starte, args }) {
  const berichtsPfad = join(wurzel, ...PIT_BERICHT_TEILE);
  rmSync(berichtsPfad, { force: true });
  const ergebnis = starte('mvn', args, { cwd: wurzel, encoding: 'utf-8' });
  return { ergebnis, xml: existsSync(berichtsPfad) ? readFileSync(berichtsPfad, 'utf-8') : null };
}

/**
 * Anders als Stryker traegt der PIT-Bericht keinen Quelltext. Er kommt darum von der Platte — je
 * Datei genau einmal, und eine unlesbare Datei gilt als leer: Dort greift dann kein
 * Altlast-Vermerk, was die richtige Irrtumsrichtung ist.
 */
function quellenLesen(mutanten, lies) {
  const quellen = new Map();
  for (const mutant of mutanten) {
    if (!quellen.has(mutant.datei)) quellen.set(mutant.datei, lies(mutant.datei));
  }
  return quellen;
}

/**
 * Die Gedaechtnisdatei des Vollaufs (Kriterium 9). Sie liegt unter `.claude/` und ist damit durch
 * den bestehenden `.claude/*`-Eintrag ignoriert: Eine versionierte Datei verschmutzte den
 * Arbeitsbaum genau im Veroeffentlichungslauf und liefe gegen den harten Stopp auf dirty.
 */
function vollaufPfad(wurzel, seite) {
  return join(wurzel, '.claude', `mutationsvollauf-${seite}.json`);
}

/** Das Dauerprotokoll der Aenderungspruefung, unversioniert wie die Gedaechtnisdatei (Issue #1280). */
function dauerPfad(wurzel, seite) {
  return join(wurzel, '.claude', `mutationsdauer-${seite}.json`);
}

/**
 * Haengt die Dauer eines Laufs mit Werkzeugstart an und kuerzt auf die letzten Laeufe. Ein
 * gescheitertes Schreiben aendert das Urteil des Laufs nicht — es kostet nur eine Messung —, wird
 * aber genannt.
 */
function dauerProtokollieren({ wurzel, seite, datum, dauerMs, ausgabe }) {
  const bisher = jsonLesen(dauerPfad(wurzel, seite));
  const laeufe = Array.isArray(bisher?.laeufe) ? bisher.laeufe : [];
  const neu = { laeufe: [...laeufe, { datum, dauerMs }].slice(-DAUER_PROTOKOLL_LAENGE) };
  try {
    writeFileSync(dauerPfad(wurzel, seite), `${JSON.stringify(neu, null, 2)}\n`);
  } catch (err) {
    ausgabe(`Das Dauerprotokoll ${dauerPfad(wurzel, seite)} ließ sich nicht schreiben: ${err.message}\n`);
  }
}

function ausstiegsSatz(seite, stufeArgument, { mitte, anzahl, stufe }) {
  const medianText = mitte === null
    ? 'Median: keiner, es gibt noch kein Dauerprotokoll'
    : `Median der letzten ${anzahl} Läufe ${dauerText(mitte)}`;
  return `Änderungsprüfung ${seite} auf Stufe ${stufeArgument} ausgelassen — ${medianText}, `
    + `Grenze 10 min, geltende Stufe: ${stufe}.\n`;
}

/**
 * Der Stand, gegen den der Vollauf gemessen hat. Faellt `rev-parse` aus — kein Repository, kein
 * Commit —, steht `null` in der Datei: lieber kein Stand als ein erfundener, denn an ihm haengt
 * spaeter die Frage, ob ein Ueberlebender derselbe ist.
 */
function standLesen(git) {
  const res = git('rev-parse', 'HEAD');
  const roh = res.status === 0 ? res.stdout.trim() : '';
  return roh.length > 0 ? roh : null;
}

/**
 * Schreibt die Gedaechtnisdatei und gibt im Fehlerfall den Satz zurueck, der in die Meldung gehoert.
 * Ein gescheitertes Schreiben faerbt den Lauf rot: Die Datei IST der Auftrag dieses Unterkommandos,
 * und ein gruener Lauf ohne sie liesse die naechste Aenderungspruefung ohne Vergleich zurueck.
 */
function gedaechtnisSchreiben(wurzel, seite, inhalt) {
  try {
    writeFileSync(vollaufPfad(wurzel, seite), `${JSON.stringify(inhalt, null, 2)}\n`);
    return null;
  } catch (err) {
    return `Die Gedächtnisdatei ${vollaufPfad(wurzel, seite)} ließ sich nicht schreiben: ${err.message}`;
  }
}

function letzteZeilen(roh, anzahl) {
  const text = String(roh ?? '').trim();
  return text.length === 0 ? [] : text.split('\n').slice(-anzahl);
}

/**
 * Die einzige Stelle, an der die Ausgabe des Werkzeugs doch durchgereicht wird — und dann aus
 * BEIDEN Kanälen: Maven meldet den Grund eines Fehlschlags auf stdout, auf stderr steht oft nur
 * Rauschen der JVM. Nur stderr zu zeigen liesse jeden gescheiterten Lauf grundlos aussehen. Dass
 * ein `[ERROR]` aus dem Tail `checks.mjs` rot faerbt, ist hier richtig: Der Lauf IST rot.
 */
function fehlenderBericht(teile, ergebnis) {
  const tail = [...letzteZeilen(ergebnis?.stdout, 15), ...letzteZeilen(ergebnis?.stderr, 5)];
  return `Der Mutationslauf hat keinen Bericht unter ${teile.join('/')} hinterlassen `
    + `(Rückgabewert ${ergebnis?.status ?? 'unbekannt'}). Geprüft wurde deshalb nichts.\n`
    + `${tail.join('\n')}\n`;
}

/**
 * Der Vollauf einer Seite: der volle Umfang des Werkzeugs, die Schwelle je Seite und die
 * Gedaechtnisdatei. Der Rueckgabewert des Werkzeugs selbst wird wie in der Aenderungspruefung nicht
 * uebernommen — geurteilt wird ueber den Bericht: Im Backend haelt das Profil an seiner eigenen
 * Marke schon an, und ein durchgereichter Exitcode sagte dann zweimal dasselbe, aber ohne Zahl.
 */
function vollaufLaufen({ wurzel, seite, bereich, starte, git, ausgabe, jetzt, beginn, vollauf, stufe, schwelle }) {
  let mutanten;
  if (seite === 'frontend') {
    const mutate = bereich.vollaufMutate.flatMap(klammernAufloesen);
    const { ergebnis, bericht } = strykerLaufen({ wurzel, starte, args: strykerArgumente(mutate) });
    if (!bericht) {
      ausgabe(fehlenderBericht(BERICHT_TEILE, ergebnis));
      return 1;
    }
    mutanten = strykerMutanten(bericht);
  } else {
    const { ergebnis, xml } = pitLaufen({ wurzel, starte, args: pitVollaufArgumente() });
    if (xml === null) {
      ausgabe(fehlenderBericht(PIT_BERICHT_TEILE, ergebnis));
      return 1;
    }
    mutanten = pitMutanten(xml);
  }

  const gemessen = bereich.ausschnitte.filter((a) => bereich.gemessen.includes(a.name));
  const ausgewertet = vollaufAuswerten(mutanten, gemessen);
  const { zaehlung, ueberlebende } = ausgewertet;
  const quote = quoteAus(zaehlung);
  const dauerMs = jetzt() - beginn;
  const mehrere = gemessen.length > 1;
  const erstmalsVorher = Object.fromEntries((Array.isArray(vollauf?.ausschnitte) ? vollauf.ausschnitte : [])
    .filter((a) => typeof a?.name === 'string')
    .map((a) => [a.name, a.erstmals80]));
  const halt = mehrere
    ? haltBestimmen({
      ausschnitte: ausgewertet.ausschnitte, schwelle, erstmalsVorher, heute: new Date(jetzt()).toISOString().slice(0, 10),
    })
    : { haltende: quote >= schwelle ? [] : [seite] };
  // Auch ein an der Schwelle gescheiterter Lauf hinterlaesst die Datei: Sein Ergebnis ist der
  // Stand, gegen den die naechste Aenderungspruefung vergleicht — ihn wegzuwerfen, weil er rot ist,
  // nahm der vierten Bedingung des Altlast-Vermerks genau dann die Grundlage, wenn sie gebraucht wird.
  const fehler = gedaechtnisSchreiben(wurzel, seite, {
    stand: standLesen(git),
    datum: new Date(jetzt()).toISOString(),
    dauerMs,
    umfang: bereich.zeilen,
    quote,
    mutanten: ueberlebende.map((m) => ({
      datei: m.datei,
      zeile: m.zeile,
      mutator: m.mutator,
      tests: m.deckendeTests ?? [],
    })),
    // Nur bei mehreren Ausschnitten (E11): Die Backend-Datei bleibt, wie sie war.
    ...(mehrere ? { ausschnitte: ausgewertet.ausschnitte.map((a) => gedaechtnisAusschnitt(a, halt)) } : {}),
  });

  ausgabe(meldungBauen({
    kommando: 'vollauf',
    seite,
    bereich,
    ankerSatz: 'Umfang: die ganze Seite.',
    ueberlebende,
    zaehlung,
    dauerMs,
    vollauf,
    stufe,
    quote,
    schwelle,
    ausschnittsBericht: mehrere
      ? ausschnittsZeilen({ ausschnitte: ausgewertet.ausschnitte, halt, schwelle, quote, ohneAusschnitt: ausgewertet.ohneAusschnitt })
      : null,
    schluss: fehler ?? undefined,
  }));
  return halt.haltende.length === 0 && ausgewertet.ohneAusschnitt.length === 0 && !fehler ? 0 : 1;
}

/** Ein Ausschnitt in der Gedaechtnisdatei; `erstmals80` traegt nur ein Bestandsausschnitt (E7). */
function gedaechtnisAusschnitt(a, halt) {
  return {
    name: a.name,
    muster: a.muster,
    aufgenommen: a.aufgenommenAm,
    quote: a.quote,
    zaehlung: a.zaehlung,
    ...(a.name in halt.erstmals80 ? { erstmals80: halt.erstmals80[a.name] } : {}),
  };
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
  const liesDatei = umgebung.liesDatei ?? ((pfad) => {
    try {
      return readFileSync(join(wurzel, pfad), 'utf-8');
    } catch {
      return '';
    }
  });
  const git = umgebung.git ?? ((...args) => spawnSync('git', args, { cwd: wurzel, encoding: 'utf-8' }));
  const starte = umgebung.starte
    ?? ((befehl, args, optionen) => spawnSync(befehl, args, { encoding: 'utf-8', ...optionen }));
  const beginn = jetzt();

  const [kommando, seite, ...rest] = argv;
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
  const stufenIndex = rest.indexOf('--stufe');
  const stufeArgument = stufenIndex === -1 ? null : (rest[stufenIndex + 1] ?? '');
  if (stufeArgument !== null && (kommando !== 'aenderung' || !STUFEN.includes(stufeArgument))) {
    ausgabe(`Ungültiges --stufe '${stufeArgument}'. Erlaubt nur an der Änderungsprüfung: `
      + `--stufe ${STUFEN.join('|')}.\n`);
    return 2;
  }
  if (stufeArgument !== null) {
    const geltend = geltendeStufe(jsonLesen(dauerPfad(wurzel, seite)));
    if (geltend.stufe !== stufeArgument) {
      ausgabe(ausstiegsSatz(seite, stufeArgument, geltend));
      return 0;
    }
  }

  let bereich;
  try {
    bereich = bereichLesen(seite, wurzel);
  } catch (err) {
    ausgabe(`${err.message}\n`);
    return 1;
  }

  const config = jsonLesen(join(wurzel, '.claude', 'workflow.config.json')) ?? {};
  const vollauf = jsonLesen(vollaufPfad(wurzel, seite));
  const stufe = stufeAus(config, `mutationspruefung.mjs ${kommando} ${seite}`, stufeArgument);
  // Ueber `umgebung.schwellen` ueberschreibbar wie `git` und `starte`: Ein Nachweis an einer
  // kuenstlich angehobenen Schwelle braucht sonst einen zweiten halbstuendigen Vollauf.
  const schwelle = (umgebung.schwellen ?? SCHWELLEN)[seite];

  if (kommando === 'vollauf') {
    return vollaufLaufen({
      wurzel, seite, bereich, starte, git, ausgabe, jetzt, beginn, vollauf, stufe, schwelle,
    });
  }
  if (kommando === 'zuordnung') {
    return zuordnungPruefen({ wurzel, seite, bereich, git, ausgabe, existiert, vollauf });
  }

  const { anker, vollerUmfang, satz } = ankerBestimmen(git, config.mainBranch ?? HAUPTZWEIG_VORGABE);
  const zuordnung = zuordnungAusVollauf(vollauf);
  const stand = vollerUmfang ? { alle: [], ungetrackt: new Set() } : geaenderteDateien(git, anker);
  const gemessen = vollerUmfang
    ? { dateien: [], ganzeSeite: true, ohneZuordnung: [] }
    : beruehrung({
      geaendert: stand.alle, bereich, zuordnung, festeZuordnung: festeZuordnungLesen(wurzel), existiert,
    });

  // Ein Test ohne Zuordnung faehrt das Frontend nicht mehr ganz (Issue #1287): Der Lauf dauert
  // weit laenger als eine Paketrunde (80 min am 2026-09-28, #1275), die Abhilfe ist eine Zeile.
  // Das Backend weicht auf die ganze Seite aus (Issue #1308, HALT_OHNE_ZUORDNUNG).
  if (gemessen.ohneZuordnung.length > 0 && HALT_OHNE_ZUORDNUNG[seite]) {
    ausgabe(ohneZuordnungMeldung(
      gemessen.ohneZuordnung,
      'Mutationsprüfung — Änderungsprüfung angehalten, kein Werkzeuglauf.',
      'Ohne Zuordnung müsste die ganze Seite laufen, und das dauert länger als eine Paketrunde.',
    ));
    return 1;
  }

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
  // Bei ganzer Seite gilt jede Datei des Pruefbereichs als beruehrt: Der Umfang ist genau dann
  // die ganze Seite, wenn der Anker fehlt oder ein geaenderter Test keiner Quelle zuzuordnen war —
  // in beiden Faellen ist unbekannt, was verschont bleiben duerfte.
  const beruehrt = new Set(gemessen.dateien);
  const geaendert = new Set(stand.alle);
  const dateienDesLaufs = gemessen.ganzeSeite ? [] : gemessen.dateien;

  // Protokolliert wird nur ein Lauf, der das Werkzeug wirklich gestartet hat: Leerlaeufe und
  // Ausstiege dauern Sekunden, und der Median maesse sonst sie statt der Laeufe (Issue #1280).
  const code = aenderungMitWerkzeug({
    wurzel, seite, starte, git, ausgabe, liesDatei, jetzt, beginn, anker, vollerUmfang, stand, gemessen,
    beruehrt, geaendert, dateienDesLaufs, vollauf, grundmeldung,
  });
  dauerProtokollieren({
    wurzel, seite, datum: new Date(beginn).toISOString(), dauerMs: jetzt() - beginn, ausgabe,
  });
  return code;
}

/**
 * Die Zuordnungspruefung beim Kartenabschluss (Issue #1522, Plan #1521 E4–E6): Anker ist `HEAD`,
 * also genau die noch nicht committete Aenderung der Karte samt ungetrackter Dateien. Sie haelt auf
 * BEIDEN Seiten an und liest `HALT_OHNE_ZUORDNUNG` nicht — die Konstante steuert nur das Ausweichen
 * der Aenderungspruefung an der Push-Stufe, und dort bleibt das Backend unveraendert (AK 3). Kein
 * Werkzeuglauf, kein Dauerprotokoll, keine Gedaechtnisdatei.
 */
function zuordnungPruefen({ wurzel, seite, bereich, git, ausgabe, existiert, vollauf }) {
  const geaendert = geaenderteDateien(git, 'HEAD').alle;
  const gemessen = beruehrung({
    geaendert,
    bereich,
    zuordnung: zuordnungAusVollauf(vollauf),
    festeZuordnung: festeZuordnungLesen(wurzel),
    existiert,
  });
  if (gemessen.ohneZuordnung.length > 0) {
    ausgabe(ohneZuordnungMeldung(
      gemessen.ohneZuordnung,
      `Mutationsprüfung — Zuordnungsprüfung ${seite}`,
      'Beim Abschluss einer Karte muss jeder geänderte Test einer Quelle zuzuordnen sein (Issue #1515).',
    ));
    return 1;
  }
  const tests = geaendert.filter((pfad) => istTestdatei(pfad) && bereich.istSeitenTest(pfad)
    && !bereich.testAusserhalb?.(pfad));
  ausgabe(`Mutationsprüfung — Zuordnungsprüfung ${seite}: ${tests.length} geänderte Testdateien geprüft, `
    + 'jede ist einer Quelle zugeordnet.\n');
  return 0;
}

function aenderungMitWerkzeug({
  wurzel, seite, starte, git, ausgabe, liesDatei, jetzt, beginn, anker, vollerUmfang, stand, gemessen,
  beruehrt, geaendert, dateienDesLaufs, vollauf, grundmeldung,
}) {
  let mutanten;
  let quellen;
  if (seite === 'frontend') {
    const { ergebnis, bericht } = strykerLaufen({ wurzel, starte, args: strykerArgumente(dateienDesLaufs) });
    if (!bericht) {
      ausgabe(fehlenderBericht(BERICHT_TEILE, ergebnis));
      return 1;
    }
    ({ mutanten, quellen } = strykerMutanten(bericht, { mitQuellen: true }));
  } else {
    const { ergebnis, xml } = pitLaufen({ wurzel, starte, args: pitArgumente(dateienDesLaufs) });
    if (xml === null) {
      ausgabe(fehlenderBericht(PIT_BERICHT_TEILE, ergebnis));
      return 1;
    }
    mutanten = pitMutanten(xml);
    quellen = quellenLesen(mutanten, liesDatei);
  }

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
