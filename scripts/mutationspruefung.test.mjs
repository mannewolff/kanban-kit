import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import {
  SEITEN,
  KOMMANDOS,
  globZuRegex,
  pitGlobZuRegex,
  javaPfadZuKlasse,
  frontendBereich,
  pitBereichLesen,
  backendBereich,
  istTestdatei,
  konventionsQuelle,
  testAngabeZuPfad,
  zuordnungAusVollauf,
  beruehrung,
  diffPfade,
  untracktePfade,
  ankerBestimmen,
  stufeAus,
  dauerText,
  laufen,
  ZUSTAND_UEBERLEBT,
  ZUSTAND_GETOETET,
  konventionsTests,
  strykerMutanten,
  strykerArgumente,
  altlastVermerkAn,
  hunkZeilen,
  vollaufSchluessel,
  auswerten,
} from './mutationspruefung.mjs';

const STRYKER = {
  mutate: ['src/lib/**/*.ts', 'src/api/**/*.ts', '!src/**/*.test.ts', '!src/**/*.test.tsx'],
};

const POM_MIT_PROPERTY = `<project>
  <profiles>
    <profile>
      <id>pit</id>
      <properties>
        <jacoco.skip>true</jacoco.skip>
        <pit.targetClasses>org.mwolff.manban.*.application.*,org.mwolff.manban.*.domain.*</pit.targetClasses>
      </properties>
      <build><plugins><plugin>
        <configuration>
          <targetClasses>
            <param>\${pit.targetClasses}</param>
          </targetClasses>
          <excludedClasses>
            <param>org.mwolff.manban.*.infrastructure.*</param>
            <param>org.mwolff.manban.ManbanApplication</param>
          </excludedClasses>
        </configuration>
      </plugin></plugins></build>
    </profile>
  </profiles>
</project>`;

const POM_OHNE_PROPERTY = `<project>
  <profiles>
    <profile>
      <id>pit</id>
      <properties>
        <jacoco.skip>true</jacoco.skip>
      </properties>
      <build><plugins><plugin>
        <configuration>
          <targetClasses>
            <param>org.mwolff.manban.*.application.*</param>
            <param>org.mwolff.manban.*.domain.*</param>
          </targetClasses>
          <excludedClasses>
            <param>org.mwolff.manban.*.infrastructure.*</param>
            <param>org.mwolff.manban.ManbanApplication</param>
          </excludedClasses>
        </configuration>
      </plugin></plugins></build>
    </profile>
  </profiles>
</project>`;

/** git-Doppel: liefert je Argumentfolge ein vorbereitetes Ergebnis. */
function gitDoppel(antworten) {
  return (...args) => {
    const schluessel = args.join(' ');
    const treffer = antworten[schluessel];
    if (treffer) return treffer;
    return { status: 1, stdout: '', stderr: `unerwartet: git ${schluessel}` };
  };
}

const OK = (stdout) => ({ status: 0, stdout, stderr: '' });

// --- Muster ----------------------------------------------------------------

test('globZuRegex: ** erfasst beliebige Tiefe einschliesslich keiner', () => {
  const regex = globZuRegex('src/lib/**/*.ts');
  assert.equal(regex.test('src/lib/a.ts'), true);
  assert.equal(regex.test('src/lib/tief/a.ts'), true);
  assert.equal(regex.test('src/api/a.ts'), false);
  assert.equal(regex.test('src/lib/a.tsx'), false);
});

test('globZuRegex: * bleibt im Pfadsegment', () => {
  const regex = globZuRegex('src/*.ts');
  assert.equal(regex.test('src/a.ts'), true);
  assert.equal(regex.test('src/tief/a.ts'), false);
});

test('pitGlobZuRegex: * erfasst auch Punkte, $ wird nicht als Regex gelesen', () => {
  assert.equal(pitGlobZuRegex('org.mwolff.manban.*.application.*').test('org.mwolff.manban.card.application.CardService'), true);
  assert.equal(pitGlobZuRegex('org.mwolff.manban.*.application.*').test('org.mwolff.manban.card.web.CardController'), false);
  assert.equal(pitGlobZuRegex('org.mwolff.manban.config.*Config$*').test('org.mwolff.manban.config.SpaWebConfig$1'), true);
  assert.equal(pitGlobZuRegex('org.mwolff.manban.config.*Config$*').test('org.mwolff.manban.config.SpaWebConfigX'), false);
});

test('javaPfadZuKlasse: nur Hauptquellen unter src/main/java', () => {
  assert.equal(
    javaPfadZuKlasse('src/main/java/org/mwolff/manban/card/application/CardService.java'),
    'org.mwolff.manban.card.application.CardService',
  );
  assert.equal(javaPfadZuKlasse('src/test/java/org/mwolff/manban/card/application/CardServiceTest.java'), null);
  assert.equal(javaPfadZuKlasse('src/main/resources/application.yml'), null);
});

// --- Pruefbereich je Seite --------------------------------------------------

test('frontendBereich: Musterschnitt aus mutate, Negationen schneiden heraus', () => {
  const bereich = frontendBereich(STRYKER);
  assert.equal(bereich.trifft('frontend/src/lib/statusColors.ts'), true);
  assert.equal(bereich.trifft('frontend/src/api/cards.ts'), true);
  assert.equal(bereich.trifft('frontend/src/lib/statusColors.test.ts'), false);
  assert.equal(bereich.trifft('frontend/src/components/BoardView.tsx'), false);
  assert.equal(bereich.trifft('src/main/java/org/mwolff/manban/card/application/CardService.java'), false);
  assert.deepEqual(bereich.woertlich, STRYKER.mutate);
});

test('pitBereichLesen: Property gewinnt, wenn sie vorhanden ist', () => {
  const gelesen = pitBereichLesen(POM_MIT_PROPERTY);
  assert.equal(gelesen.quelle, 'property');
  assert.deepEqual(gelesen.ziel, ['org.mwolff.manban.*.application.*', 'org.mwolff.manban.*.domain.*']);
  assert.deepEqual(gelesen.aus, ['org.mwolff.manban.*.infrastructure.*', 'org.mwolff.manban.ManbanApplication']);
});

test('pitBereichLesen: ohne Property faellt das Lesen auf die targetClasses-Elemente zurueck', () => {
  const gelesen = pitBereichLesen(POM_OHNE_PROPERTY);
  assert.equal(gelesen.quelle, 'elemente');
  assert.deepEqual(gelesen.ziel, ['org.mwolff.manban.*.application.*', 'org.mwolff.manban.*.domain.*']);
});

test('backendBereich: Musterschnitt ueber Klassennamen, excludedClasses gewinnt', () => {
  const bereich = backendBereich(pitBereichLesen(POM_MIT_PROPERTY));
  assert.equal(bereich.trifft('src/main/java/org/mwolff/manban/card/application/CardService.java'), true);
  assert.equal(bereich.trifft('src/main/java/org/mwolff/manban/card/domain/Card.java'), true);
  assert.equal(bereich.trifft('src/main/java/org/mwolff/manban/card/web/CardController.java'), false);
  assert.equal(bereich.trifft('src/main/java/org/mwolff/manban/card/infrastructure/CardEntity.java'), false);
  assert.equal(bereich.trifft('frontend/src/lib/statusColors.ts'), false);
});

// --- Test-zu-Quelle-Zuordnung ----------------------------------------------

test('istTestdatei erkennt beide Seiten', () => {
  assert.equal(istTestdatei('frontend/src/lib/a.test.ts'), true);
  assert.equal(istTestdatei('frontend/src/pages/A.test.tsx'), true);
  assert.equal(istTestdatei('src/test/java/org/mwolff/manban/card/application/CardServiceTest.java'), true);
  assert.equal(istTestdatei('src/test/java/org/mwolff/manban/card/CardFlowIT.java'), true);
  assert.equal(istTestdatei('frontend/src/lib/a.ts'), false);
  assert.equal(istTestdatei('src/main/java/org/mwolff/manban/card/application/CardService.java'), false);
});

test('konventionsQuelle: Namenskonvention beider Seiten', () => {
  assert.equal(konventionsQuelle('frontend/src/lib/a.test.ts'), 'frontend/src/lib/a.ts');
  assert.equal(konventionsQuelle('frontend/src/pages/A.test.tsx'), 'frontend/src/pages/A.tsx');
  assert.equal(
    konventionsQuelle('src/test/java/org/mwolff/manban/card/application/CardServiceTest.java'),
    'src/main/java/org/mwolff/manban/card/application/CardService.java',
  );
  assert.equal(
    konventionsQuelle('src/test/java/org/mwolff/manban/card/application/CardFlowIT.java'),
    'src/main/java/org/mwolff/manban/card/application/CardFlow.java',
  );
  assert.equal(konventionsQuelle('frontend/src/lib/a.ts'), null);
});

test('testAngabeZuPfad: Pfad bleibt, PIT-Testklasse wird zum Pfad', () => {
  assert.equal(testAngabeZuPfad('frontend/src/lib/a.test.ts'), 'frontend/src/lib/a.test.ts');
  assert.equal(
    testAngabeZuPfad('org.mwolff.manban.card.application.CardServiceTest.zaehltKarten(org.mwolff.manban.card.application.CardServiceTest)'),
    'src/test/java/org/mwolff/manban/card/application/CardServiceTest.java',
  );
  assert.equal(testAngabeZuPfad('irgendwas ohne klasse'), null);
});

test('zuordnungAusVollauf: kehrt die Mutantenliste zu Test -> Quellen um', () => {
  const zuordnung = zuordnungAusVollauf({
    mutanten: [
      { datei: 'frontend/src/lib/a.ts', tests: ['frontend/src/lib/andersHeissend.test.ts'] },
      { datei: 'frontend/src/lib/b.ts', tests: ['frontend/src/lib/andersHeissend.test.ts'] },
      { datei: 'src/main/java/org/mwolff/manban/card/application/CardService.java', tests: ['org.mwolff.manban.card.CardFlowTest.laeuft(org.mwolff.manban.card.CardFlowTest)'] },
      { datei: 'frontend/src/lib/c.ts' },
    ],
  });
  assert.deepEqual([...zuordnung.get('frontend/src/lib/andersHeissend.test.ts')].sort(), [
    'frontend/src/lib/a.ts',
    'frontend/src/lib/b.ts',
  ]);
  assert.deepEqual([...zuordnung.get('src/test/java/org/mwolff/manban/card/CardFlowTest.java')], [
    'src/main/java/org/mwolff/manban/card/application/CardService.java',
  ]);
});

test('zuordnungAusVollauf: ohne Mutantenliste bleibt die Zuordnung leer', () => {
  assert.equal(zuordnungAusVollauf(null).size, 0);
  assert.equal(zuordnungAusVollauf({}).size, 0);
});

// --- Beruehrung -------------------------------------------------------------

test('beruehrung: geaenderte Quelldatei im Bereich zaehlt, ausserhalb nicht', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/a.ts', 'frontend/src/components/B.tsx', 'README.md'],
    bereich: frontendBereich(STRYKER),
    zuordnung: new Map(),
    existiert: () => true,
  });
  assert.deepEqual(ergebnis.dateien, ['frontend/src/lib/a.ts']);
  assert.equal(ergebnis.ganzeSeite, false);
});

test('beruehrung: geaenderter Test zieht seine Quelle ueber die Namenskonvention mit', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/a.test.ts'],
    bereich: frontendBereich(STRYKER),
    zuordnung: new Map(),
    existiert: (pfad) => pfad === 'frontend/src/lib/a.ts',
  });
  assert.deepEqual(ergebnis.dateien, ['frontend/src/lib/a.ts']);
  assert.equal(ergebnis.ganzeSeite, false);
});

test('beruehrung: geaenderter Test zieht seine Quelle ueber die Berichtsumkehrung mit', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/andersHeissend.test.ts'],
    bereich: frontendBereich(STRYKER),
    zuordnung: new Map([['frontend/src/lib/andersHeissend.test.ts', new Set(['frontend/src/lib/a.ts'])]]),
    existiert: () => false,
  });
  assert.deepEqual(ergebnis.dateien, ['frontend/src/lib/a.ts']);
  assert.equal(ergebnis.ganzeSeite, false);
});

test('beruehrung: Test ohne Zuordnung macht die ganze Seite beruehrt', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/ohnePartner.test.ts'],
    bereich: frontendBereich(STRYKER),
    zuordnung: new Map(),
    existiert: () => false,
  });
  assert.equal(ergebnis.ganzeSeite, true);
  assert.deepEqual(ergebnis.ohneZuordnung, ['frontend/src/lib/ohnePartner.test.ts']);
});

test('beruehrung: ein Test der anderen Seite loest die ganze Seite nicht aus', () => {
  const ergebnis = beruehrung({
    geaendert: ['src/test/java/org/mwolff/manban/card/OhnePartnerTest.java'],
    bereich: frontendBereich(STRYKER),
    zuordnung: new Map(),
    existiert: () => false,
  });
  assert.equal(ergebnis.ganzeSeite, false);
  assert.deepEqual(ergebnis.dateien, []);
});

// --- Anker und Dateilisten --------------------------------------------------

test('diffPfade: Umbenennung traegt beide Pfade', () => {
  const roh = 'M\0a.ts\0R100\0alt.ts\0neu.ts\0';
  assert.deepEqual([...diffPfade(roh)], ['a.ts', 'alt.ts', 'neu.ts']);
});

test('untracktePfade: nur ungetrackte Eintraege', () => {
  const roh = ' M a.ts\0?? neu.ts\0';
  assert.deepEqual([...untracktePfade(roh)], ['neu.ts']);
});

test('ankerBestimmen: merge-base loest auf', () => {
  const git = gitDoppel({
    'merge-base HEAD origin/main': OK('2a5fa1465bc75e746a466214d719eef80b9216be\n'),
  });
  const ergebnis = ankerBestimmen(git, 'main');
  assert.equal(ergebnis.anker, '2a5fa1465bc75e746a466214d719eef80b9216be');
  assert.equal(ergebnis.vollerUmfang, false);
});

test('ankerBestimmen: leere Ausgabe gilt als unaufloesbar und fuehrt zum vollen Umfang', () => {
  const git = gitDoppel({ 'merge-base HEAD origin/main': OK('\n') });
  const ergebnis = ankerBestimmen(git, 'main');
  assert.equal(ergebnis.anker, null);
  assert.equal(ergebnis.vollerUmfang, true);
  assert.match(ergebnis.satz, /volle Umfang/);
});

test('ankerBestimmen: fehlendes origin fuehrt zum vollen Umfang', () => {
  const git = gitDoppel({});
  const ergebnis = ankerBestimmen(git, 'main');
  assert.equal(ergebnis.anker, null);
  assert.equal(ergebnis.vollerUmfang, true);
  assert.match(ergebnis.satz, /origin\/main/);
});

// --- Stufe und Formate ------------------------------------------------------

test('stufeAus: ohne passenden buildChecks-Eintrag gibt es keine Stufe', () => {
  assert.equal(stufeAus({ buildChecks: ['mvn verify'] }, 'node scripts/mutationspruefung.mjs aenderung frontend'), null);
});

test('stufeAus: Eintrag ohne stufe gilt als Paketstufe', () => {
  const config = { buildChecks: [{ cmd: 'node scripts/mutationspruefung.mjs aenderung frontend' }] };
  assert.equal(stufeAus(config, 'node scripts/mutationspruefung.mjs aenderung frontend'), 'paket');
});

test('stufeAus: eingetragene Stufe wird uebernommen', () => {
  const config = { buildChecks: [{ cmd: 'node scripts/mutationspruefung.mjs aenderung backend', stufe: 'push' }] };
  assert.equal(stufeAus(config, 'node scripts/mutationspruefung.mjs aenderung backend'), 'push');
});

test('dauerText: Sekunden und Minuten', () => {
  assert.equal(dauerText(1500), '1,5 s');
  assert.equal(dauerText(65000), '1 min 5 s');
});

// --- Lauf -------------------------------------------------------------------

function mitProjekt(fn, { stryker = STRYKER, pom = POM_MIT_PROPERTY, config = { mainBranch: 'main', buildChecks: [] }, vollauf = null } = {}) {
  const wurzel = mkdtempSync(join(tmpdir(), 'mutpruef-'));
  try {
    mkdirSync(join(wurzel, '.claude'), { recursive: true });
    mkdirSync(join(wurzel, 'frontend'), { recursive: true });
    writeFileSync(join(wurzel, '.claude', 'workflow.config.json'), JSON.stringify(config));
    writeFileSync(join(wurzel, 'frontend', 'stryker.config.json'), JSON.stringify(stryker));
    writeFileSync(join(wurzel, 'pom.xml'), pom);
    if (vollauf) {
      for (const [seite, inhalt] of Object.entries(vollauf)) {
        writeFileSync(join(wurzel, '.claude', `mutationsvollauf-${seite}.json`), JSON.stringify(inhalt));
      }
    }
    return fn(wurzel);
  } finally {
    rmSync(wurzel, { recursive: true, force: true });
  }
}

function sammelLauf(argv, wurzel, gitAntworten = {}, starte = undefined) {
  const zeilen = [];
  const aufrufe = [];
  const code = laufen(argv, {
    cwd: wurzel,
    git: gitDoppel({
      'merge-base HEAD origin/main': OK('1a2b3c4\n'),
      'diff --name-status -z 1a2b3c4': OK(''),
      'status --porcelain -z --untracked-files=all': OK(''),
      ...gitAntworten,
    }),
    starte: (befehl, args, optionen) => {
      aufrufe.push({ befehl, args, optionen });
      return starte ? starte(befehl, args, optionen) : { status: 0, stdout: '', stderr: '' };
    },
    ausgabe: (text) => zeilen.push(text),
  });
  return { code, text: zeilen.join(''), aufrufe };
}

/** Ein `starte`-Doppel, das den json-Bericht an seinen vereinbarten Ort schreibt. */
function strykerDoppel(wurzel, bericht, status = 0) {
  return () => {
    mkdirSync(join(wurzel, '.claude', 'stryker'), { recursive: true });
    if (bericht) {
      writeFileSync(join(wurzel, '.claude', 'stryker', 'mutation.json'), JSON.stringify(bericht));
    }
    return { status, stdout: '', stderr: '' };
  };
}

test('laufen: ohne Argumente nennt Unterkommandos und Seiten und endet ungleich 0', () => {
  const { code, text } = mitProjekt((wurzel) => sammelLauf([], wurzel));
  assert.notEqual(code, 0);
  for (const wort of [...KOMMANDOS, ...SEITEN]) assert.match(text, new RegExp(wort));
});

test('laufen: unbekanntes Unterkommando endet ungleich 0 und nennt die erlaubten Werte', () => {
  const { code, text } = mitProjekt((wurzel) => sammelLauf(['pruefe', 'frontend'], wurzel));
  assert.notEqual(code, 0);
  assert.match(text, /aenderung/);
  assert.match(text, /vollauf/);
});

test('laufen: unbekannte Seite endet ungleich 0 und nennt die erlaubten Werte', () => {
  const { code, text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'mitte'], wurzel));
  assert.notEqual(code, 0);
  assert.match(text, /frontend/);
  assert.match(text, /backend/);
});

test('laufen: vollauf ist in diesem Paket noch nicht umgesetzt', () => {
  const { code, text } = mitProjekt((wurzel) => sammelLauf(['vollauf', 'frontend'], wurzel));
  assert.notEqual(code, 0);
  assert.match(text, /noch nicht umgesetzt/);
});

test('laufen: leere Beruehrungsmenge endet gruen, nennt Bereich und Satz', () => {
  const { code, text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel));
  assert.equal(code, 0);
  assert.ok(text.includes('keine berührte Datei im Prüfbereich'));
  for (const muster of STRYKER.mutate) assert.ok(text.includes(muster), `Muster fehlt woertlich: ${muster}`);
});

test('laufen: ohne buildChecks-Eintrag steht die Stufe woertlich als nicht eingetragen', () => {
  const { text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel));
  assert.ok(text.includes('Stufe: noch nicht eingetragen'));
});

test('laufen: eingetragene Stufe erscheint statt des Platzhalters', () => {
  const config = {
    mainBranch: 'main',
    buildChecks: [{ cmd: 'node scripts/mutationspruefung.mjs aenderung frontend', stufe: 'push' }],
  };
  const { text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel), { config });
  assert.ok(!text.includes('Stufe: noch nicht eingetragen'));
  assert.match(text, /Stufe: push/);
});

test('laufen: ohne abgelegten Vollauf steht der Satz statt einer Dauer', () => {
  const { text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel));
  assert.match(text, /noch keinen Vollauf/);
});

test('laufen: abgelegter Vollauf erscheint mit Dauer und Datum', () => {
  const vollauf = { frontend: { datum: '2026-09-24T11:36:00.000Z', dauerMs: 1789000, quote: 84.7, mutanten: [] } };
  const { text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel), { vollauf });
  assert.match(text, /29 min 49 s/);
  assert.match(text, /2026-09-24/);
});

test('laufen: das Backend nennt die groebere Ausnahme je Einheit', () => {
  const { code, text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'backend'], wurzel));
  assert.equal(code, 0);
  assert.match(text, /je Einheit/);
  assert.ok(text.includes('org.mwolff.manban.*.application.*'));
});

test('laufen: ein gruener Lauf traegt keine Fehlermerkmale', () => {
  const { code, text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel));
  assert.equal(code, 0);
  assert.ok(!text.includes('[ERROR]'));
  assert.ok(!text.includes('BUILD FAILURE'));
});

test('laufen: das Backend wartet weiter auf seine Auswertung (Issue #1214)', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'backend'], wurzel, {
      'diff --name-status -z 1a2b3c4': OK('M\0src/main/java/org/mwolff/manban/card/domain/Card.java\0'),
    }),
  );
  assert.notEqual(code, 0);
  assert.match(text, /noch nicht umgesetzt/);
});

// --- Stryker-Bericht --------------------------------------------------------

const QUELLE_A = [
  'export function f(n: number): number {', // 1
  '  return n > 0 ? 1 : 2', //                 2
  '}', //                                      3
].join('\n');

function mutant(zeile, mutator, zustand, { ersetzung = 'true', coveredBy = ['t1'], id = String(zeile) } = {}) {
  return {
    id,
    mutatorName: mutator,
    replacement: ersetzung,
    status: zustand,
    location: { start: { line: zeile, column: 10 }, end: { line: zeile, column: 20 } },
    coveredBy,
  };
}

function bericht(dateien, testDateien = { 'src/lib/andersHeissend.test.ts': { tests: [{ id: 't1', name: 'f' }] } }) {
  return { schemaVersion: '1.0', files: dateien, testFiles: testDateien };
}

const BERICHT_A = bericht({
  'src/lib/a.ts': {
    language: 'typescript',
    source: QUELLE_A,
    mutants: [
      mutant(2, 'ConditionalExpression', 'Survived'),
      mutant(2, 'EqualityOperator', 'Killed', { ersetzung: 'n >= 0', id: '2b' }),
    ],
  },
});

test('strykerMutanten: Pfade bekommen das frontend-Praefix, Ort und Mutator werden gelesen', () => {
  const mutanten = strykerMutanten(BERICHT_A);
  assert.equal(mutanten.length, 2);
  assert.deepEqual(
    { datei: mutanten[0].datei, zeile: mutanten[0].zeile, mutator: mutanten[0].mutator, ersetzung: mutanten[0].ersetzung },
    { datei: 'frontend/src/lib/a.ts', zeile: 2, mutator: 'ConditionalExpression', ersetzung: 'true' },
  );
});

test('strykerMutanten: coveredBy wird zu den Pfaden der deckenden Testdateien', () => {
  const mutanten = strykerMutanten(BERICHT_A);
  assert.deepEqual(mutanten[0].deckendeTests, ['frontend/src/lib/andersHeissend.test.ts']);
});

test('strykerMutanten: ohne testFiles bleibt die Deckung leer statt zu raten', () => {
  const ohneTestdateien = bericht(
    { 'src/lib/a.ts': { source: QUELLE_A, mutants: [mutant(2, 'ConditionalExpression', 'Survived')] } },
    {},
  );
  assert.deepEqual(strykerMutanten(ohneTestdateien)[0].deckendeTests, []);
});

test('strykerMutanten: Quelltext je Datei kommt aus dem Bericht', () => {
  const { quellen } = strykerMutanten(BERICHT_A, { mitQuellen: true });
  assert.equal(quellen.get('frontend/src/lib/a.ts'), QUELLE_A);
});

test('Zustaende: Timeout zaehlt als getoetet, NoCoverage als ueberlebend, Ignored als keines von beidem', () => {
  assert.equal(ZUSTAND_GETOETET.has('Timeout'), true);
  assert.equal(ZUSTAND_GETOETET.has('Killed'), true);
  assert.equal(ZUSTAND_UEBERLEBT.has('Survived'), true);
  assert.equal(ZUSTAND_UEBERLEBT.has('NoCoverage'), true);
  assert.equal(ZUSTAND_UEBERLEBT.has('Ignored'), false);
  assert.equal(ZUSTAND_GETOETET.has('Ignored'), false);
});

test('strykerArgumente: beruehrte Dateien verengen nur mutate, der Bericht traegt json', () => {
  const args = strykerArgumente(['frontend/src/lib/a.ts', 'frontend/src/api/cards.ts']);
  assert.deepEqual(args, ['run', '-m', 'src/lib/a.ts,src/api/cards.ts', '--reporters', 'json,html,clear-text']);
  assert.equal(args.includes('--incremental'), false);
  assert.equal(args.some((a) => a.includes('testFilter') || a === '--testFilter'), false);
});

test('strykerArgumente: ohne Dateiliste bleibt der volle mutate-Bereich stehen', () => {
  assert.deepEqual(strykerArgumente([]), ['run', '--reporters', 'json,html,clear-text']);
});

// --- Marker -----------------------------------------------------------------

test('konventionsTests: Umkehrung der Namenskonvention beider Seiten', () => {
  assert.deepEqual(konventionsTests('frontend/src/lib/a.ts'), ['frontend/src/lib/a.test.ts']);
  assert.deepEqual(konventionsTests('frontend/src/pages/A.tsx'), ['frontend/src/pages/A.test.tsx']);
  assert.deepEqual(konventionsTests('src/main/java/org/mwolff/manban/card/domain/Card.java'), [
    'src/test/java/org/mwolff/manban/card/domain/CardTest.java',
    'src/test/java/org/mwolff/manban/card/domain/CardIT.java',
  ]);
  assert.deepEqual(konventionsTests('README.md'), []);
});

test('altlastVermerkAn: auf der Zeile des Mutanten', () => {
  const quelle = ['a', '  return 1 // Mutations-Altlast: Grenzfall ohne Test (#1213, 2026-09-25)', 'b'].join('\n');
  assert.deepEqual(altlastVermerkAn(quelle, 2), { grund: 'Grenzfall ohne Test', issue: '1213', datum: '2026-09-25' });
});

test('altlastVermerkAn: unmittelbar ueber der Zeile des Mutanten', () => {
  const quelle = ['// Mutations-Altlast: Grenzfall ohne Test (#1213, 2026-09-25)', '  return 1', 'b'].join('\n');
  assert.equal(altlastVermerkAn(quelle, 2)?.issue, '1213');
});

test('altlastVermerkAn: zwei Zeilen darueber greift nicht', () => {
  const quelle = ['// Mutations-Altlast: Grenzfall ohne Test (#1213, 2026-09-25)', 'leer', '  return 1'].join('\n');
  assert.equal(altlastVermerkAn(quelle, 3), null);
});

test('altlastVermerkAn: ohne Begruendung greift der Vermerk nicht', () => {
  const quelle = ['  return 1 // Mutations-Altlast: (#1213, 2026-09-25)'].join('\n');
  assert.equal(altlastVermerkAn(quelle, 1), null);
});

test('altlastVermerkAn: ohne Issue oder Datum greift der Vermerk nicht', () => {
  assert.equal(altlastVermerkAn('  return 1 // Mutations-Altlast: ohne Anhang', 1), null);
  assert.equal(altlastVermerkAn('  return 1 // Mutations-Altlast: halb (#1213)', 1), null);
});

test('altlastVermerkAn: eine Begruendung darf Klammern tragen', () => {
  const quelle = '  return 1 // Mutations-Altlast: Grenzfall (siehe oben) (#1213, 2026-09-25)';
  assert.equal(altlastVermerkAn(quelle, 1)?.grund, 'Grenzfall (siehe oben)');
});

test('hunkZeilen: neue Zeilen aus den Hunk-Koepfen, reine Loeschung traegt keine', () => {
  const diff = [
    'diff --git a/x b/x',
    '@@ -1 +1 @@',
    '@@ -10,0 +11,2 @@',
    '@@ -20,3 +22,0 @@',
  ].join('\n');
  assert.deepEqual([...hunkZeilen(diff)].sort((a, b) => a - b), [1, 11, 12]);
});

test('vollaufSchluessel: Datei, Zeile und Mutator bilden die Stelle', () => {
  assert.equal(vollaufSchluessel('frontend/src/lib/a.ts', 2, 'ConditionalExpression'), 'frontend/src/lib/a.ts|2|ConditionalExpression');
});

// --- Auswertung -------------------------------------------------------------

const VERMERKTE_QUELLE = [
  'export function f(n: number): number {', //                                    1
  '  // Mutations-Altlast: Grenzfall ohne Test (#1213, 2026-09-25)', //           2
  '  return n > 0 ? 1 : 2', //                                                    3
  '}', //                                                                         4
].join('\n');

const VERMERKTER_BERICHT = bericht({
  'src/lib/a.ts': {
    source: VERMERKTE_QUELLE,
    mutants: [mutant(3, 'ConditionalExpression', 'Survived')],
  },
});

/** Die Vorgabe: Vermerk da, Zeile unveraendert, kein Test geaendert, im Vollauf ueberlebt. */
function auswertenMit(uebersteuert = {}) {
  return auswerten({
    ...strykerMutanten(VERMERKTER_BERICHT, { mitQuellen: true }),
    istBeruehrt: () => true,
    zeileGeaendert: () => false,
    dateiGeaendert: () => false,
    vollauf: { mutanten: [{ datei: 'frontend/src/lib/a.ts', zeile: 3, mutator: 'ConditionalExpression' }] },
    ...uebersteuert,
  });
}

test('auswerten: ein Ueberlebender in einer beruehrten Datei haelt an (Kriterium 2)', () => {
  const ergebnis = auswerten({
    ...strykerMutanten(BERICHT_A, { mitQuellen: true }),
    istBeruehrt: (datei) => datei === 'frontend/src/lib/a.ts',
    zeileGeaendert: () => true,
    dateiGeaendert: () => false,
    vollauf: null,
  });
  assert.equal(ergebnis.haltende.length, 1);
  assert.deepEqual(
    { datei: ergebnis.haltende[0].datei, zeile: ergebnis.haltende[0].zeile, mutator: ergebnis.haltende[0].mutator },
    { datei: 'frontend/src/lib/a.ts', zeile: 2, mutator: 'ConditionalExpression' },
  );
  assert.deepEqual(ergebnis.zaehlung, { geprueft: 2, getoetet: 1, ueberlebt: 1, ausgenommen: 0, ausserhalb: 0 });
});

test('auswerten: ein Ueberlebender ausserhalb der Beruehrung haelt nicht an und ist kein Grund (Kriterium 4)', () => {
  const ergebnis = auswerten({
    ...strykerMutanten(BERICHT_A, { mitQuellen: true }),
    istBeruehrt: () => false,
    zeileGeaendert: () => true,
    dateiGeaendert: () => false,
    vollauf: null,
  });
  assert.deepEqual(ergebnis.haltende, []);
  assert.deepEqual(ergebnis.ueberlebende, []);
  assert.equal(ergebnis.zaehlung.ausserhalb, 1);
});

test('auswerten: ein per Stryker ausgenommener Mutant zaehlt weder als getoetet noch als ueberlebend (Kriterium 5)', () => {
  const ergebnis = auswerten({
    ...strykerMutanten(bericht({
      'src/lib/a.ts': { source: QUELLE_A, mutants: [mutant(2, 'ConditionalExpression', 'Ignored')] },
    }), { mitQuellen: true }),
    istBeruehrt: () => true,
    zeileGeaendert: () => true,
    dateiGeaendert: () => false,
    vollauf: null,
  });
  assert.deepEqual(ergebnis.haltende, []);
  assert.equal(ergebnis.zaehlung.ausgenommen, 1);
  assert.equal(ergebnis.zaehlung.ueberlebt, 0);
});

test('auswerten: alle vier Bedingungen erfuellt — der Vermerk greift, zaehlt aber mit (Kriterium 6)', () => {
  const ergebnis = auswertenMit();
  assert.deepEqual(ergebnis.haltende, []);
  assert.equal(ergebnis.ueberlebende.length, 1);
  assert.equal(ergebnis.ueberlebende[0].altlast.grund, 'Grenzfall ohne Test');
  assert.equal(ergebnis.zaehlung.ueberlebt, 1);
});

test('auswerten: Bedingung 1 verletzt — ohne Vermerk an der Stelle haelt der Ueberlebende an', () => {
  const ergebnis = auswerten({
    ...strykerMutanten(bericht({
      'src/lib/a.ts': { source: QUELLE_A, mutants: [mutant(2, 'ConditionalExpression', 'Survived')] },
    }), { mitQuellen: true }),
    istBeruehrt: () => true,
    zeileGeaendert: () => false,
    dateiGeaendert: () => false,
    vollauf: { mutanten: [{ datei: 'frontend/src/lib/a.ts', zeile: 2, mutator: 'ConditionalExpression' }] },
  });
  assert.equal(ergebnis.haltende.length, 1);
  assert.equal(ergebnis.ueberlebende[0].altlast, null);
});

test('auswerten: Bedingung 2 verletzt — geaenderte Zeile laesst den Vermerk nicht greifen', () => {
  const ergebnis = auswertenMit({ zeileGeaendert: (datei, zeile) => datei === 'frontend/src/lib/a.ts' && zeile === 3 });
  assert.equal(ergebnis.haltende.length, 1);
  assert.match(ergebnis.haltende[0].vermerkGrund, /Zeile/);
});

test('auswerten: Bedingung 3 verletzt — eine geaenderte deckende Testdatei laesst den Vermerk nicht greifen', () => {
  const ergebnis = auswertenMit({ dateiGeaendert: (pfad) => pfad === 'frontend/src/lib/andersHeissend.test.ts' });
  assert.equal(ergebnis.haltende.length, 1);
  assert.match(ergebnis.haltende[0].vermerkGrund, /Testdatei/);
});

test('auswerten: Bedingung 3 greift auch fuer die Testdatei nach Namenskonvention', () => {
  const ergebnis = auswertenMit({ dateiGeaendert: (pfad) => pfad === 'frontend/src/lib/a.test.ts' });
  assert.equal(ergebnis.haltende.length, 1);
  assert.match(ergebnis.haltende[0].vermerkGrund, /Testdatei/);
});

test('auswerten: Bedingung 4 verletzt — im Vollauf nicht ueberlebt, also keine Altlast', () => {
  const ergebnis = auswertenMit({ vollauf: { mutanten: [{ datei: 'frontend/src/lib/b.ts', zeile: 9, mutator: 'X' }] } });
  assert.equal(ergebnis.haltende.length, 1);
  assert.match(ergebnis.haltende[0].vermerkGrund, /Vollauf/);
});

test('auswerten: ohne Vollauf-Bericht entfaellt die vierte Bedingung', () => {
  const ergebnis = auswertenMit({ vollauf: null });
  assert.deepEqual(ergebnis.haltende, []);
  assert.equal(ergebnis.ueberlebende[0].altlast.issue, '1213');
});

// --- Lauf mit Stryker -------------------------------------------------------

const GEAENDERT_A = { 'diff --name-status -z 1a2b3c4': OK('M\0frontend/src/lib/a.ts\0') };

test('laufen: der Stryker-Aufruf verengt mutate auf die beruehrten Dateien, im Arbeitsverzeichnis frontend', () => {
  const { aufrufe } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      ...GEAENDERT_A,
      'diff -U0 1a2b3c4 -- frontend/src/lib/a.ts': OK('@@ -2 +2 @@\n'),
    }, strykerDoppel(wurzel, BERICHT_A)),
  );
  assert.equal(aufrufe.length, 1);
  assert.match(aufrufe[0].befehl, /node_modules[/\\]\.bin[/\\]stryker$/);
  assert.deepEqual(aufrufe[0].args, ['run', '-m', 'src/lib/a.ts', '--reporters', 'json,html,clear-text']);
  assert.match(aufrufe[0].optionen.cwd, /frontend$/);
});

test('laufen: ein Ueberlebender in einer beruehrten Datei endet ungleich 0 und nennt Datei, Zeile und Mutator', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      ...GEAENDERT_A,
      'diff -U0 1a2b3c4 -- frontend/src/lib/a.ts': OK('@@ -2 +2 @@\n'),
    }, strykerDoppel(wurzel, BERICHT_A)),
  );
  assert.notEqual(code, 0);
  assert.match(text, /frontend\/src\/lib\/a\.ts:2 — ConditionalExpression/);
  assert.ok(!text.includes('noch nicht umgesetzt'));
});

test('laufen: ein Ueberlebender in einer nicht beruehrten Datei endet gruen und erscheint nicht als Grund', () => {
  const fremd = bericht({
    'src/lib/a.ts': { source: QUELLE_A, mutants: [mutant(2, 'EqualityOperator', 'Killed')] },
    'src/lib/fremd.ts': { source: QUELLE_A, mutants: [mutant(2, 'ConditionalExpression', 'Survived')] },
  });
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      ...GEAENDERT_A,
      'diff -U0 1a2b3c4 -- frontend/src/lib/a.ts': OK('@@ -2 +2 @@\n'),
    }, strykerDoppel(wurzel, fremd)),
  );
  assert.equal(code, 0);
  assert.ok(!text.includes('fremd.ts'));
});

test('laufen: ein greifender Altlast-Vermerk laesst gruen enden und steht trotzdem in der Zaehlung', () => {
  const vollauf = {
    frontend: {
      datum: '2026-09-24T11:36:00.000Z',
      dauerMs: 1000,
      quote: 84.7,
      mutanten: [{ datei: 'frontend/src/lib/a.ts', zeile: 3, mutator: 'ConditionalExpression' }],
    },
  };
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      ...GEAENDERT_A,
      'diff -U0 1a2b3c4 -- frontend/src/lib/a.ts': OK('@@ -1 +1 @@\n'),
    }, strykerDoppel(wurzel, VERMERKTER_BERICHT)),
  { vollauf });
  assert.equal(code, 0);
  assert.match(text, /Altlast-Vermerk/);
  assert.match(text, /frontend\/src\/lib\/a\.ts:3/);
  assert.match(text, /1 überlebt/);
});

test('laufen: eine geaenderte deckende Testdatei nimmt demselben Vermerk die Wirkung', () => {
  const vollauf = {
    frontend: {
      datum: '2026-09-24T11:36:00.000Z',
      dauerMs: 1000,
      mutanten: [{ datei: 'frontend/src/lib/a.ts', zeile: 3, mutator: 'ConditionalExpression' }],
    },
  };
  const { code } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      'diff --name-status -z 1a2b3c4': OK('M\0frontend/src/lib/a.ts\0M\0frontend/src/lib/andersHeissend.test.ts\0'),
      'diff -U0 1a2b3c4 -- frontend/src/lib/a.ts': OK('@@ -1 +1 @@\n'),
    }, strykerDoppel(wurzel, VERMERKTER_BERICHT)),
  { vollauf });
  assert.notEqual(code, 0);
});

test('laufen: eine ungetrackte Datei gilt ganz als geaendert — kein Vermerk greift dort', () => {
  const { code } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      'status --porcelain -z --untracked-files=all': OK('?? frontend/src/lib/a.ts\0'),
    }, strykerDoppel(wurzel, VERMERKTER_BERICHT)),
  );
  assert.notEqual(code, 0);
});

test('laufen: ein abgebrochener Stryker-Lauf ohne Bericht endet ungleich 0 und sagt warum', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, GEAENDERT_A, () => ({ status: 1, stdout: '', stderr: 'boom' })),
  );
  assert.notEqual(code, 0);
  assert.match(text, /Bericht/);
});

test('laufen: ein alter Bericht wird vor dem Lauf verworfen und nicht als neuer gelesen', () => {
  const { code, text } = mitProjekt((wurzel) => {
    mkdirSync(join(wurzel, '.claude', 'stryker'), { recursive: true });
    writeFileSync(join(wurzel, '.claude', 'stryker', 'mutation.json'), JSON.stringify(BERICHT_A));
    return sammelLauf(['aenderung', 'frontend'], wurzel, GEAENDERT_A, () => ({ status: 1, stdout: '', stderr: 'boom' }));
  });
  assert.notEqual(code, 0);
  assert.match(text, /Bericht/);
  assert.ok(!text.includes('ConditionalExpression'));
});

test('laufen: unaufloesbarer Anker fuehrt zum vollen Umfang und sagt das', () => {
  const { code, text, aufrufe } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      'merge-base HEAD origin/main': { status: 128, stdout: '', stderr: 'no upstream' },
    }, strykerDoppel(wurzel, BERICHT_A)),
  );
  assert.notEqual(code, 0);
  assert.match(text, /volle Umfang/);
  assert.match(text, /Umfang: die ganze Seite/);
  assert.deepEqual(aufrufe[0].args, ['run', '--reporters', 'json,html,clear-text']);
});
