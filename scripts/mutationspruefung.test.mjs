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

function sammelLauf(argv, wurzel, gitAntworten = {}) {
  const zeilen = [];
  const code = laufen(argv, {
    cwd: wurzel,
    git: gitDoppel({
      'merge-base HEAD origin/main': OK('1a2b3c4\n'),
      'diff --name-status -z 1a2b3c4': OK(''),
      'status --porcelain -z --untracked-files=all': OK(''),
      ...gitAntworten,
    }),
    ausgabe: (text) => zeilen.push(text),
  });
  return { code, text: zeilen.join('') };
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

test('laufen: beruehrte Dateien enden ungleich 0, solange die Auswertung fehlt', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      'diff --name-status -z 1a2b3c4': OK('M\0frontend/src/lib/a.ts\0'),
    }),
  );
  assert.notEqual(code, 0);
  assert.match(text, /frontend\/src\/lib\/a\.ts/);
  assert.match(text, /noch nicht umgesetzt/);
});

test('laufen: unaufloesbarer Anker fuehrt zum vollen Umfang und sagt das', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      'merge-base HEAD origin/main': { status: 128, stdout: '', stderr: 'no upstream' },
    }),
  );
  assert.notEqual(code, 0);
  assert.match(text, /volle Umfang/);
  assert.match(text, /Umfang: die ganze Seite/);
});
