import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  SEITEN,
  KOMMANDOS,
  globZuRegex,
  pitGlobZuRegex,
  javaPfadZuKlasse,
  frontendBereich,
  stufenplanPruefen,
  ausschnittVon,
  pitBereichLesen,
  backendBereich,
  istTestdatei,
  konventionsQuelle,
  testAngabeZuPfad,
  zuordnungAusVollauf,
  beruehrung,
  festeZuordnungLesen,
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
  pitZustand,
  pitQuellPfad,
  pitMutanten,
  pitArgumente,
  pitVollaufArgumente,
  SCHWELLEN,
  quoteAus,
  vollaufAuswerten,
  klammernAufloesen,
} from './mutationspruefung.mjs';

const HIER = dirname(fileURLToPath(import.meta.url));
const BEISPIEL_XML = readFileSync(join(HIER, 'testdaten', 'mutations-beispiel.xml'), 'utf-8');

/** Ein Stufenplan, dessen aufgenommene Ausschnitte genau den frueheren `mutate`-Bereich ergeben. */
const PLAN = {
  ausnahmen: [],
  ausschnitte: [
    { name: 'hilfsfunktionen', muster: ['src/lib/**/*.ts'], aufgenommen: '2026-09-28', reihenfolge: 1 },
    { name: 'server-anbindung', muster: ['src/api/**/*.ts'], aufgenommen: '2026-09-28', reihenfolge: 2 },
  ],
};

const MUTATE = ['src/lib/**/*.ts', 'src/api/**/*.ts', '!src/**/*.test.ts', '!src/**/*.test.tsx'];

/** Der Stufenplan des Repositories, wie Konfiguration und Treiber ihn lesen. */
function repoPlan() {
  return JSON.parse(readFileSync(join(HIER, '..', 'frontend', 'mutationsstufen.json'), 'utf-8'));
}

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

test('globZuRegex: {a,b} waehlt eine der Alternativen (Stufenplan, #1276)', () => {
  const r = globZuRegex('src/lib/**/*.{ts,tsx}');
  assert.equal(r.test('src/lib/a.ts'), true);
  assert.equal(r.test('src/lib/x/b.tsx'), true);
  assert.equal(r.test('src/lib/a.js'), false);
  assert.equal(r.test('src/lib/a.{ts,tsx}'), false);
  assert.equal(globZuRegex('src/a.{t.s}').test('src/a.t.s'), true);
  assert.equal(globZuRegex('src/a.{t.s}').test('src/a.tXs'), false);
  assert.equal(globZuRegex('src/{a').test('src/{a'), true);
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

test('frontendBereich: Musterschnitt aus den aufgenommenen Ausschnitten, Negationen schneiden heraus', () => {
  const bereich = frontendBereich(PLAN);
  assert.equal(bereich.trifft('frontend/src/lib/statusColors.ts'), true);
  assert.equal(bereich.trifft('frontend/src/api/cards.ts'), true);
  assert.equal(bereich.trifft('frontend/src/lib/statusColors.test.ts'), false);
  assert.equal(bereich.trifft('frontend/src/components/BoardView.tsx'), false);
  assert.equal(bereich.trifft('src/main/java/org/mwolff/manban/card/application/CardService.java'), false);
  assert.deepEqual(bereich.woertlich, MUTATE);
  assert.equal(bereich.quelle, 'frontend/mutationsstufen.json (aufgenommene Ausschnitte)');
});

test('frontendBereich: der Pruefbereich ist mutateFuer ueber die aufgenommenen Ausschnitte des Repo-Plans', async () => {
  const { aufgenommene, mutateFuer } = await import('../frontend/mutationsbereich.mjs');
  const plan = repoPlan();
  assert.deepEqual(frontendBereich(plan).woertlich, mutateFuer(aufgenommene(plan).map((a) => a.name), plan));
});

test('stryker.config.mjs: mutate ist dieselbe Ableitung wie im Treiber und schliesst die Tests aus', async () => {
  const { default: config } = await import('../frontend/stryker.config.mjs');
  assert.deepEqual(config.mutate, frontendBereich(repoPlan()).woertlich);
  assert.ok(config.mutate.includes('!src/**/*.test.ts'));
  assert.ok(config.mutate.includes('!src/**/*.test.tsx'));
  assert.equal(config.testRunner, 'vitest');
  assert.deepEqual(config.vitest, { configFile: 'vitest.mutation.config.ts' });
});

test('stufenplanPruefen: der Repo-Plan ist gueltig', () => {
  assert.equal(stufenplanPruefen(repoPlan()), null);
});

test('stufenplanPruefen: fehlende oder falsch geformte Felder werden benannt', () => {
  assert.match(stufenplanPruefen(null), /kein Objekt/);
  assert.match(stufenplanPruefen({ ausschnitte: [] }), /ausnahmen/);
  assert.match(stufenplanPruefen({ ausnahmen: [] }), /ausschnitte/);
  assert.match(stufenplanPruefen({ ausnahmen: [], ausschnitte: [{ muster: ['a'] }] }), /name/);
  assert.match(stufenplanPruefen({ ausnahmen: [], ausschnitte: [{ name: 'x', muster: 'a' }] }), /x.*muster/);
  assert.match(stufenplanPruefen({ ausnahmen: [], ausschnitte: [{ name: 'x', muster: [] }] }), /x.*muster/);
});

test('ausschnittVon: jede Datei landet in ihrem Ausschnitt, Ausnahmen und Tests in keinem', () => {
  const bereich = frontendBereich(repoPlan());
  assert.equal(ausschnittVon(bereich, 'frontend/src/lib/statusColors.ts')?.name, 'hilfsfunktionen');
  assert.equal(ausschnittVon(bereich, 'frontend/src/api/cards.ts')?.name, 'server-anbindung');
  const leitstand = ausschnittVon(bereich, 'frontend/src/components/leitstand/Kachel.tsx');
  assert.equal(leitstand?.name, 'bausteine-leitstand');
  assert.equal(leitstand?.aufgenommen, false);
  assert.equal(bereich.trifft('frontend/src/components/leitstand/Kachel.tsx'), false);
  assert.equal(ausschnittVon(bereich, 'frontend/src/lib/__fixtures__/probe.ts'), null);
  assert.equal(bereich.trifft('frontend/src/lib/__fixtures__/probe.ts'), false);
  assert.equal(ausschnittVon(bereich, 'frontend/src/lib/statusColors.test.ts'), null);
  assert.equal(ausschnittVon(bereich, 'src/main/java/org/mwolff/manban/card/application/CardService.java'), null);
});

test('frontendBereich: die Ausschnitte tragen ihren Namen und ob sie aufgenommen sind', () => {
  const bereich = frontendBereich(repoPlan());
  assert.equal(bereich.ausschnitte.length, repoPlan().ausschnitte.length);
  assert.deepEqual(
    bereich.ausschnitte.filter((a) => a.aufgenommen).map((a) => a.name),
    ['hilfsfunktionen', 'server-anbindung'],
  );
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

test('backendBereich: genau ein Ausschnitt ueber den ganzen Pruefbereich (E11)', () => {
  const bereich = backendBereich(pitBereichLesen(POM_MIT_PROPERTY));
  assert.equal(bereich.ausschnitte.length, 1);
  const [einziger] = bereich.ausschnitte;
  assert.equal(einziger.name, 'backend');
  assert.equal(einziger.aufgenommen, true);
  const drin = 'src/main/java/org/mwolff/manban/card/application/CardService.java';
  const draussen = 'src/main/java/org/mwolff/manban/card/infrastructure/CardRepository.java';
  assert.equal(ausschnittVon(bereich, drin), einziger);
  assert.equal(ausschnittVon(bereich, draussen), null);
  assert.equal(ausschnittVon(bereich, 'frontend/src/lib/statusColors.ts'), null);
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
    bereich: frontendBereich(PLAN),
    zuordnung: new Map(),
    existiert: () => true,
  });
  assert.deepEqual(ergebnis.dateien, ['frontend/src/lib/a.ts']);
  assert.equal(ergebnis.ganzeSeite, false);
});

test('beruehrung: geaenderter Test zieht seine Quelle ueber die Namenskonvention mit', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/a.test.ts'],
    bereich: frontendBereich(PLAN),
    zuordnung: new Map(),
    existiert: (pfad) => pfad === 'frontend/src/lib/a.ts',
  });
  assert.deepEqual(ergebnis.dateien, ['frontend/src/lib/a.ts']);
  assert.equal(ergebnis.ganzeSeite, false);
});

test('beruehrung: geaenderter Test zieht seine Quelle ueber die Berichtsumkehrung mit', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/andersHeissend.test.ts'],
    bereich: frontendBereich(PLAN),
    zuordnung: new Map([['frontend/src/lib/andersHeissend.test.ts', new Set(['frontend/src/lib/a.ts'])]]),
    existiert: () => false,
  });
  assert.deepEqual(ergebnis.dateien, ['frontend/src/lib/a.ts']);
  assert.equal(ergebnis.ganzeSeite, false);
});

test('beruehrung: Test ohne Zuordnung macht die ganze Seite beruehrt', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/ohnePartner.test.ts'],
    bereich: frontendBereich(PLAN),
    zuordnung: new Map(),
    existiert: () => false,
  });
  assert.equal(ergebnis.ganzeSeite, true);
  assert.deepEqual(ergebnis.ohneZuordnung, ['frontend/src/lib/ohnePartner.test.ts']);
});

test('beruehrung: ein Test der anderen Seite loest die ganze Seite nicht aus', () => {
  const ergebnis = beruehrung({
    geaendert: ['src/test/java/org/mwolff/manban/card/OhnePartnerTest.java'],
    bereich: frontendBereich(PLAN),
    zuordnung: new Map(),
    existiert: () => false,
  });
  assert.equal(ergebnis.ganzeSeite, false);
  assert.deepEqual(ergebnis.dateien, []);
});

test('beruehrung: feste Zuordnung zieht eine Quelle im Bereich mit (Issue #1287)', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/andersHeissend.test.ts'],
    bereich: frontendBereich(PLAN),
    zuordnung: new Map(),
    festeZuordnung: { 'frontend/src/lib/andersHeissend.test.ts': ['frontend/src/lib/a.ts'] },
    existiert: () => false,
  });
  assert.deepEqual(ergebnis, { dateien: ['frontend/src/lib/a.ts'], ganzeSeite: false, ohneZuordnung: [] });
});

test('beruehrung: feste Zuordnung nur ausserhalb des Bereichs macht den Test zugeordnet, ohne Datei', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/strykerUmfang.test.ts'],
    bereich: frontendBereich(PLAN),
    zuordnung: new Map(),
    festeZuordnung: { 'frontend/src/lib/strykerUmfang.test.ts': ['frontend/mutationTestUmfang.ts'] },
    existiert: () => false,
  });
  assert.deepEqual(ergebnis, { dateien: [], ganzeSeite: false, ohneZuordnung: [] });
});

test('mutationszuordnung.json: gueltiges JSON, jeder Test und jede Quelle existiert im Repo', () => {
  const repo = join(HIER, '..');
  const zuordnung = festeZuordnungLesen(repo);
  assert.ok(Object.keys(zuordnung).length > 0, 'die ausgelieferte Zuordnung ist leer');
  for (const [testPfad, quellen] of Object.entries(zuordnung)) {
    assert.ok(existsSync(join(repo, testPfad)), `Test fehlt: ${testPfad}`);
    assert.ok(Array.isArray(quellen) && quellen.length > 0, `keine Quelle fuer ${testPfad}`);
    for (const quelle of quellen) assert.ok(existsSync(join(repo, quelle)), `Quelle fehlt: ${quelle}`);
  }
});

test('mutationszuordnung.json: strykerUmfang.test.ts allein loest weder Datei noch ganze Seite aus', () => {
  const repo = join(HIER, '..');
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/lib/strykerUmfang.test.ts'],
    bereich: frontendBereich(repoPlan()),
    zuordnung: new Map(),
    festeZuordnung: festeZuordnungLesen(repo),
    existiert: (pfad) => existsSync(join(repo, pfad)),
  });
  assert.deepEqual(ergebnis, { dateien: [], ganzeSeite: false, ohneZuordnung: [] });
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

function mitProjekt(fn, {
  plan = PLAN,
  pom = POM_MIT_PROPERTY,
  config = { mainBranch: 'main', buildChecks: [] },
  vollauf = null,
  vollaufRoh = null,
} = {}) {
  const wurzel = mkdtempSync(join(tmpdir(), 'mutpruef-'));
  try {
    mkdirSync(join(wurzel, '.claude'), { recursive: true });
    mkdirSync(join(wurzel, 'frontend'), { recursive: true });
    writeFileSync(join(wurzel, '.claude', 'workflow.config.json'), JSON.stringify(config));
    if (plan !== null) writeFileSync(join(wurzel, 'frontend', 'mutationsstufen.json'), JSON.stringify(plan));
    writeFileSync(join(wurzel, 'pom.xml'), pom);
    if (vollauf) {
      for (const [seite, inhalt] of Object.entries(vollauf)) {
        writeFileSync(join(wurzel, '.claude', `mutationsvollauf-${seite}.json`), JSON.stringify(inhalt));
      }
    }
    for (const [seite, text] of Object.entries(vollaufRoh ?? {})) {
      writeFileSync(join(wurzel, '.claude', `mutationsvollauf-${seite}.json`), text);
    }
    return fn(wurzel);
  } finally {
    rmSync(wurzel, { recursive: true, force: true });
  }
}

function sammelLauf(argv, wurzel, gitAntworten = {}, starte = undefined, extra = {}) {
  const zeilen = [];
  const aufrufe = [];
  const code = laufen(argv, {
    cwd: wurzel,
    git: gitDoppel({
      'merge-base HEAD origin/main': OK('1a2b3c4\n'),
      'diff --name-status -z 1a2b3c4': OK(''),
      'status --porcelain -z --untracked-files=all': OK(''),
      'rev-parse HEAD': OK('9f8e7d6c5b4a\n'),
      ...gitAntworten,
    }),
    starte: (befehl, args, optionen) => {
      aufrufe.push({ befehl, args, optionen });
      return starte ? starte(befehl, args, optionen) : { status: 0, stdout: '', stderr: '' };
    },
    ausgabe: (text) => zeilen.push(text),
    ...extra,
  });
  return { code, text: zeilen.join(''), aufrufe };
}

/** Die Gedaechtnisdatei des Vollaufs, gelesen im tmp-Projekt (vor dem Aufraeumen). */
function gedaechtnis(wurzel, seite) {
  return JSON.parse(readFileSync(join(wurzel, '.claude', `mutationsvollauf-${seite}.json`), 'utf-8'));
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

test('laufen: leere Beruehrungsmenge endet gruen, nennt Bereich und Satz', () => {
  const { code, text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel));
  assert.equal(code, 0);
  assert.ok(text.includes('keine berührte Datei im Prüfbereich'));
  for (const muster of MUTATE) assert.ok(text.includes(muster), `Muster fehlt woertlich: ${muster}`);
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

// --- PIT-Bericht ------------------------------------------------------------

const CARD_SERVICE = 'src/main/java/org/mwolff/manban/card/application/CardService.java';
const CARD = 'src/main/java/org/mwolff/manban/card/domain/Card.java';

test('pitZustand: PIT-Zustaende gehen in dasselbe Vokabular wie die Stryker-Zustaende', () => {
  assert.equal(pitZustand('KILLED'), 'Killed');
  assert.equal(pitZustand('TIMED_OUT'), 'Timeout');
  assert.equal(pitZustand('SURVIVED'), 'Survived');
  assert.equal(pitZustand('NO_COVERAGE'), 'NoCoverage');
  assert.equal(ZUSTAND_GETOETET.has(pitZustand('KILLED')), true);
  assert.equal(ZUSTAND_UEBERLEBT.has(pitZustand('NO_COVERAGE')), true);
});

test('pitZustand: ein unbekannter Zustand faellt in keine der beiden Mengen', () => {
  for (const roh of ['NON_VIABLE', 'RUN_ERROR', 'MEMORY_ERROR']) {
    assert.equal(ZUSTAND_GETOETET.has(pitZustand(roh)), false);
    assert.equal(ZUSTAND_UEBERLEBT.has(pitZustand(roh)), false);
  }
});

test('pitQuellPfad: Paket der Klasse plus sourceFile, auch bei einer inneren Klasse', () => {
  assert.equal(pitQuellPfad('org.mwolff.manban.card.application.CardService', 'CardService.java'), CARD_SERVICE);
  assert.equal(pitQuellPfad('org.mwolff.manban.card.domain.Card$Zustand', 'Card.java'), CARD);
  assert.equal(pitQuellPfad('', 'Card.java'), null);
  assert.equal(pitQuellPfad('org.mwolff.manban.card.domain.Card', ''), null);
});

test('pitMutanten: getoeteter Mutant traegt Datei, Zeile, Mutator, Ersetzung und den toetenden Test', () => {
  const mutanten = pitMutanten(BEISPIEL_XML);
  assert.deepEqual(mutanten[0], {
    datei: CARD_SERVICE,
    zeile: 42,
    mutator: 'NegateConditionalsMutator',
    ersetzung: 'negated conditional',
    zustand: 'Killed',
    deckendeTests: ['src/test/java/org/mwolff/manban/card/application/CardServiceTest.java'],
  });
});

test('pitMutanten: ein Ueberlebender mit leerem killingTest bleibt ohne deckenden Test', () => {
  const ueberlebend = pitMutanten(BEISPIEL_XML).find((m) => m.zustand === 'Survived');
  assert.deepEqual(ueberlebend, {
    datei: CARD_SERVICE,
    zeile: 43,
    mutator: 'ConditionalsBoundaryMutator',
    ersetzung: 'changed conditional boundary',
    zustand: 'Survived',
    deckendeTests: [],
  });
});

test('pitMutanten: ein Mutant ganz ohne killingTest-Element wird gelesen und der inneren Klasse ihre Datei zugeordnet', () => {
  const ohneDeckung = pitMutanten(BEISPIEL_XML).find((m) => m.zustand === 'NoCoverage');
  assert.equal(ohneDeckung.datei, CARD);
  assert.equal(ohneDeckung.zeile, 17);
  assert.deepEqual(ohneDeckung.deckendeTests, []);
});

test('pitMutanten: XML-Entitaeten der Beschreibung werden aufgeloest', () => {
  const nichtTragfaehig = pitMutanten(BEISPIEL_XML).find((m) => m.zustand === 'NON_VIABLE');
  assert.equal(nichtTragfaehig.ersetzung, 'replaced return value with "" for org/mwolff/manban/card/domain/Card::titel');
});

test('pitMutanten: der Zeitablauf zaehlt als getoetet und liest seinen toetenden Test', () => {
  const zeitablauf = pitMutanten(BEISPIEL_XML).find((m) => m.zustand === 'Timeout');
  assert.deepEqual(zeitablauf.deckendeTests, ['src/test/java/org/mwolff/manban/card/domain/CardTest.java']);
});

test('pitMutanten: leerer oder fehlender Bericht ergibt keine Mutanten statt eines Fehlers', () => {
  assert.deepEqual(pitMutanten('<mutations/>'), []);
  assert.deepEqual(pitMutanten(null), []);
});

test('pitMutanten: die Auswertung des Beispielberichts trennt getoetet, ueberlebt und ausserhalb', () => {
  const mutanten = pitMutanten(BEISPIEL_XML);
  const ergebnis = auswerten({
    mutanten,
    quellen: new Map(),
    istBeruehrt: (datei) => datei === CARD_SERVICE,
    zeileGeaendert: () => true,
    dateiGeaendert: () => false,
    vollauf: null,
  });
  assert.deepEqual(ergebnis.zaehlung, { geprueft: 4, getoetet: 2, ueberlebt: 1, ausgenommen: 0, ausserhalb: 1 });
  assert.equal(ergebnis.haltende.length, 1);
  assert.equal(ergebnis.haltende[0].zeile, 43);
});

test('pitArgumente: beruehrte Dateien werden zu voll qualifizierten Klassennamen samt inneren Typen', () => {
  const args = pitArgumente([CARD_SERVICE, CARD, 'src/test/java/org/mwolff/manban/card/domain/CardTest.java']);
  assert.deepEqual(args, [
    '-B',
    '-Ppit',
    '-Dskip.frontend=true',
    '-Dpit.marke=0',
    '-Dpit.targetClasses=org.mwolff.manban.card.application.CardService,org.mwolff.manban.card.application.CardService$*,'
      + 'org.mwolff.manban.card.domain.Card,org.mwolff.manban.card.domain.Card$*',
    'test',
  ]);
});

test('pitArgumente: ohne Dateiliste bleibt der Default-Umfang des Profils stehen, die Marke faellt trotzdem', () => {
  const args = pitArgumente([]);
  assert.deepEqual(args, ['-B', '-Ppit', '-Dskip.frontend=true', '-Dpit.marke=0', 'test']);
  assert.equal(args.some((a) => a.startsWith('-Dpit.targetClasses')), false);
  assert.equal(args.some((a) => a.includes('withHistory') || a.includes('historie')), false);
});

// --- Lauf mit PIT -----------------------------------------------------------

/** Ein `starte`-Doppel, das den PIT-Bericht an seinen vereinbarten Ort schreibt. */
function pitDoppel(wurzel, xml, status = 0) {
  return () => {
    mkdirSync(join(wurzel, 'target', 'pit-reports'), { recursive: true });
    if (xml) writeFileSync(join(wurzel, 'target', 'pit-reports', 'mutations.xml'), xml);
    return { status, stdout: '', stderr: '' };
  };
}

function javaAblegen(wurzel, pfad, inhalt) {
  mkdirSync(join(wurzel, dirname(pfad)), { recursive: true });
  writeFileSync(join(wurzel, pfad), inhalt);
}

const GEAENDERT_SERVICE = { 'diff --name-status -z 1a2b3c4': OK(`M\0${CARD_SERVICE}\0`) };

test('laufen: der Backend-Aufruf verengt targetClasses auf die beruehrten Klassen und schaltet die Marke ab', () => {
  const { aufrufe } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'backend'], wurzel, {
      ...GEAENDERT_SERVICE,
      [`diff -U0 1a2b3c4 -- ${CARD_SERVICE}`]: OK('@@ -43 +43 @@\n'),
    }, pitDoppel(wurzel, BEISPIEL_XML)),
  );
  assert.equal(aufrufe.length, 1);
  assert.equal(aufrufe[0].befehl, 'mvn');
  assert.deepEqual(aufrufe[0].args, [
    '-B',
    '-Ppit',
    '-Dskip.frontend=true',
    '-Dpit.marke=0',
    '-Dpit.targetClasses=org.mwolff.manban.card.application.CardService,org.mwolff.manban.card.application.CardService$*',
    'test',
  ]);
});

test('laufen: ein Ueberlebender in einer beruehrten Backend-Datei endet ungleich 0 und nennt Datei, Zeile und Mutator', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'backend'], wurzel, {
      ...GEAENDERT_SERVICE,
      [`diff -U0 1a2b3c4 -- ${CARD_SERVICE}`]: OK('@@ -43 +43 @@\n'),
    }, pitDoppel(wurzel, BEISPIEL_XML)),
  );
  assert.notEqual(code, 0);
  assert.match(text, /CardService\.java:43 — ConditionalsBoundaryMutator/);
  assert.ok(!text.includes('noch nicht umgesetzt'));
});

test('laufen: ein Ueberlebender in einer nicht beruehrten Backend-Datei endet gruen und ist kein Grund', () => {
  const fremd = 'src/main/java/org/mwolff/manban/board/domain/Column.java';
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'backend'], wurzel, {
      'diff --name-status -z 1a2b3c4': OK(`M\0${fremd}\0`),
      [`diff -U0 1a2b3c4 -- ${fremd}`]: OK('@@ -1 +1 @@\n'),
    }, pitDoppel(wurzel, BEISPIEL_XML)),
  );
  assert.equal(code, 0);
  assert.ok(!text.includes('CardService.java:43'));
  assert.match(text, /2 Überlebende außerhalb/);
});

test('laufen: ein Altlast-Vermerk in einer Backend-Quelle laesst gruen enden und zaehlt trotzdem mit', () => {
  const quelle = [
    'package org.mwolff.manban.card.application;', //                              1
    'class CardService {', //                                                      2
    '  int zaehle(int n) {', //                                                    3
    '    // Mutations-Altlast: äquivalenter Grenzfall (#1213, 2026-09-25)', //     4
    '    return n > 0 ? 1 : 2;', //                                                5
  ].join('\n');
  const vollauf = {
    backend: {
      datum: '2026-09-24T11:36:00.000Z',
      dauerMs: 1000,
      mutanten: [{ datei: CARD_SERVICE, zeile: 5, mutator: 'ConditionalsBoundaryMutator' }],
    },
  };
  const { code, text } = mitProjekt((wurzel) => {
    javaAblegen(wurzel, CARD_SERVICE, quelle);
    return sammelLauf(['aenderung', 'backend'], wurzel, {
      ...GEAENDERT_SERVICE,
      [`diff -U0 1a2b3c4 -- ${CARD_SERVICE}`]: OK('@@ -1 +1 @@\n'),
    }, pitDoppel(wurzel, BEISPIEL_XML.replaceAll('<lineNumber>43</lineNumber>', '<lineNumber>5</lineNumber>')));
  }, { vollauf });
  assert.equal(code, 0);
  assert.match(text, /Altlast-Vermerk/);
  assert.match(text, /1 überlebt/);
});

test('laufen: ein abgebrochener PIT-Lauf ohne Bericht endet ungleich 0 und sagt warum', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'backend'], wurzel, GEAENDERT_SERVICE, () => ({ status: 1, stdout: '', stderr: 'boom' })),
  );
  assert.notEqual(code, 0);
  assert.match(text, /mutations\.xml/);
});

test('laufen: ein alter PIT-Bericht wird vor dem Lauf verworfen und nicht als neuer gelesen', () => {
  const { code, text } = mitProjekt((wurzel) => {
    mkdirSync(join(wurzel, 'target', 'pit-reports'), { recursive: true });
    writeFileSync(join(wurzel, 'target', 'pit-reports', 'mutations.xml'), BEISPIEL_XML);
    return sammelLauf(['aenderung', 'backend'], wurzel, GEAENDERT_SERVICE, () => ({ status: 1, stdout: '', stderr: 'boom' }));
  });
  assert.notEqual(code, 0);
  assert.ok(!text.includes('ConditionalsBoundaryMutator'));
});

test('laufen: die Zaehlung des Backends nennt die Ausnahme je Einheit statt der Stryker-Form je Stelle', () => {
  const { text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'backend'], wurzel, {
      ...GEAENDERT_SERVICE,
      [`diff -U0 1a2b3c4 -- ${CARD_SERVICE}`]: OK('@@ -43 +43 @@\n'),
    }, pitDoppel(wurzel, BEISPIEL_XML)),
  );
  assert.match(text, /ausgenommen \(.*je Einheit/);
  assert.ok(!text.includes('Stryker-Ausnahme'));
});

test('laufen: unaufloesbarer Anker fuehrt im Backend zum Default-Umfang des Profils', () => {
  const { aufrufe, text } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'backend'], wurzel, {
      'merge-base HEAD origin/main': { status: 128, stdout: '', stderr: 'no upstream' },
    }, pitDoppel(wurzel, BEISPIEL_XML)),
  );
  assert.match(text, /Umfang: die ganze Seite/);
  assert.deepEqual(aufrufe[0].args, ['-B', '-Ppit', '-Dskip.frontend=true', '-Dpit.marke=0', 'test']);
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

test('laufen: ein Test ohne Zuordnung haelt sofort an, ohne Werkzeuglauf (Issue #1287)', () => {
  const { code, text, aufrufe } = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      'diff --name-status -z 1a2b3c4': OK('M\0frontend/src/lib/ohnePartner.test.ts\0'),
    }, strykerDoppel(wurzel, BERICHT_A)),
  );
  assert.equal(code, 1);
  assert.equal(aufrufe.length, 0);
  assert.ok(text.includes('frontend/src/lib/ohnePartner.test.ts'));
  assert.ok(text.includes('scripts/mutationszuordnung.json'));
  assert.ok(!text.includes('Umfang: die ganze Seite'));
});

test('laufen: die feste Zuordnung aus scripts/mutationszuordnung.json wird gelesen', () => {
  const { code, text, aufrufe } = mitProjekt((wurzel) => {
    mkdirSync(join(wurzel, 'scripts'), { recursive: true });
    writeFileSync(join(wurzel, 'scripts', 'mutationszuordnung.json'),
      JSON.stringify({ 'frontend/src/lib/ohnePartner.test.ts': ['frontend/mutationTestUmfang.ts'] }));
    return sammelLauf(['aenderung', 'frontend'], wurzel, {
      'diff --name-status -z 1a2b3c4': OK('M\0frontend/src/lib/ohnePartner.test.ts\0'),
    }, strykerDoppel(wurzel, BERICHT_A));
  });
  assert.equal(code, 0);
  assert.equal(aufrufe.length, 0);
  assert.ok(text.includes('keine berührte Datei im Prüfbereich'));
});

// --- Vollauf: Schwelle und Gedaechtnisdatei (Issue #1215) --------------------

test('SCHWELLEN: je Seite genau eine Zahl, Frontend 80, Backend 100', () => {
  assert.deepEqual(SCHWELLEN, { frontend: 80, backend: 100 });
});

test('quoteAus: getoetet je geprueft in Prozent, auf zwei Stellen gerundet', () => {
  assert.equal(quoteAus({ geprueft: 6, getoetet: 5 }), 83.33);
  assert.equal(quoteAus({ geprueft: 4, getoetet: 4 }), 100);
});

test('quoteAus: ohne einen einzigen gepruefsten Mutanten gilt die Quote als erfuellt', () => {
  assert.equal(quoteAus({ geprueft: 0, getoetet: 0 }), 100);
});

test('vollaufAuswerten: im Vollauf gilt jede Datei, Ausnahmen zaehlen nicht mit', () => {
  const { zaehlung, ueberlebende } = vollaufAuswerten([
    { datei: 'frontend/src/lib/a.ts', zeile: 2, mutator: 'ConditionalExpression', zustand: 'Survived', deckendeTests: ['frontend/src/lib/a.test.ts'] },
    { datei: 'frontend/src/lib/b.ts', zeile: 9, mutator: 'EqualityOperator', zustand: 'Killed', deckendeTests: [] },
    { datei: 'frontend/src/lib/b.ts', zeile: 10, mutator: 'BooleanLiteral', zustand: 'Timeout', deckendeTests: [] },
    { datei: 'frontend/src/lib/b.ts', zeile: 11, mutator: 'StringLiteral', zustand: 'Ignored', deckendeTests: [] },
    { datei: 'frontend/src/lib/b.ts', zeile: 12, mutator: 'ArrowFunction', zustand: 'CompileError', deckendeTests: [] },
    { datei: 'frontend/src/lib/c.ts', zeile: 4, mutator: 'ObjectLiteral', zustand: 'NoCoverage', deckendeTests: [] },
  ]);
  assert.deepEqual(zaehlung, { geprueft: 4, getoetet: 2, ueberlebt: 2, ausgenommen: 1, ausserhalb: 0 });
  assert.deepEqual(ueberlebende.map((m) => m.zeile), [2, 4]);
});

test('pitVollaufArgumente: Default-Umfang und Default-Marke des Profils, kein inkrementeller Zustand', () => {
  const args = pitVollaufArgumente();
  assert.deepEqual(args, ['-B', '-Ppit', '-Dskip.frontend=true', 'test']);
  assert.equal(args.some((a) => a.startsWith('-Dpit.targetClasses')), false);
  assert.equal(args.some((a) => a.startsWith('-Dpit.marke')), false);
});

/** 6 Mutanten, 5 getoetet -> 83,33 %: ueber der Frontend-Schwelle. */
const BERICHT_UEBER_SCHWELLE = bericht({
  'src/lib/a.ts': {
    source: QUELLE_A,
    mutants: [
      mutant(2, 'ConditionalExpression', 'Survived'),
      ...['a', 'b', 'c', 'd', 'e'].map((k, n) => mutant(2, 'EqualityOperator', 'Killed', { id: `k${k}`, ersetzung: `x${n}` })),
    ],
  },
});

/** 5 Mutanten, 4 getoetet -> genau 80,00 %: der Grenzfall. */
const BERICHT_AUF_SCHWELLE = bericht({
  'src/lib/a.ts': {
    source: QUELLE_A,
    mutants: [
      mutant(2, 'ConditionalExpression', 'Survived'),
      ...['a', 'b', 'c', 'd'].map((k, n) => mutant(2, 'EqualityOperator', 'Killed', { id: `k${k}`, ersetzung: `x${n}` })),
    ],
  },
});

test('laufen: vollauf frontend ruft Stryker mit dem vollen Pruefbereich als -m im Arbeitsverzeichnis frontend', () => {
  const { aufrufe } = mitProjekt((wurzel) =>
    sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, BERICHT_UEBER_SCHWELLE)),
  );
  assert.equal(aufrufe.length, 1);
  // Ohne Kandidat im Plan ist der Umfang genau der Pruefbereich; `--mutate` ersetzt die Liste der
  // Konfiguration, darum stehen die Negationen mit darin (E6, Issue #1278).
  assert.deepEqual(aufrufe[0].args, ['run', '-m', MUTATE.join(','), '--reporters', 'json,html,clear-text']);
  assert.match(aufrufe[0].optionen.cwd, /frontend$/);
});

test('laufen: vollauf frontend ueber der Schwelle endet gruen und nennt Quote und Schwelle', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, BERICHT_UEBER_SCHWELLE)),
  );
  assert.equal(code, 0);
  assert.match(text, /Quote: 83,33 %/);
  assert.match(text, /Schwelle 80 %/);
});

test('laufen: vollauf frontend genau auf der Schwelle endet gruen', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, BERICHT_AUF_SCHWELLE)),
  );
  assert.equal(code, 0);
  assert.match(text, /Quote: 80,00 %/);
});

test('laufen: vollauf frontend unter der Schwelle endet ungleich 0 und nennt gemessenen Wert und Schwelle', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, BERICHT_A)),
  );
  assert.notEqual(code, 0);
  assert.match(text, /Quote: 50,00 %/);
  assert.match(text, /unter der Schwelle 80 %/);
});

test('laufen: eine kuenstlich auf 99 gesetzte Frontend-Schwelle laesst denselben Lauf anhalten', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, BERICHT_UEBER_SCHWELLE), {
      schwellen: { frontend: 99, backend: 100 },
    }),
  );
  assert.notEqual(code, 0);
  assert.match(text, /Quote: 83,33 %/);
  assert.match(text, /unter der Schwelle 99 %/);
});

test('laufen: vollauf frontend legt die Gedaechtnisdatei mit allen fuenf Feldern und der Mutantenliste an', () => {
  const inhalt = mitProjekt((wurzel) => {
    sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, BERICHT_UEBER_SCHWELLE));
    return gedaechtnis(wurzel, 'frontend');
  });
  assert.equal(inhalt.stand, '9f8e7d6c5b4a');
  assert.match(inhalt.datum, /^\d{4}-\d{2}-\d{2}T/);
  assert.equal(typeof inhalt.dauerMs, 'number');
  assert.deepEqual(inhalt.umfang, MUTATE);
  assert.equal(inhalt.quote, 83.33);
  assert.deepEqual(inhalt.mutanten, [{
    datei: 'frontend/src/lib/a.ts',
    zeile: 2,
    mutator: 'ConditionalExpression',
    tests: ['frontend/src/lib/andersHeissend.test.ts'],
  }]);
});

test('laufen: auch ein an der Schwelle gescheiterter Vollauf hinterlaesst die Gedaechtnisdatei', () => {
  const inhalt = mitProjekt((wurzel) => {
    const { code } = sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, BERICHT_A));
    assert.notEqual(code, 0);
    return gedaechtnis(wurzel, 'frontend');
  });
  assert.equal(inhalt.quote, 50);
  assert.equal(inhalt.mutanten.length, 1);
});

test('laufen: ohne aufloesbaren Stand steht der Stand als unbekannt in der Gedaechtnisdatei', () => {
  const inhalt = mitProjekt((wurzel) => {
    sammelLauf(['vollauf', 'frontend'], wurzel, {
      'rev-parse HEAD': { status: 128, stdout: '', stderr: 'not a repository' },
    }, strykerDoppel(wurzel, BERICHT_UEBER_SCHWELLE));
    return gedaechtnis(wurzel, 'frontend');
  });
  assert.equal(inhalt.stand, null);
});

test('laufen: vollauf frontend ohne Bericht endet ungleich 0, sagt warum und schreibt keine Gedaechtnisdatei', () => {
  const { code, text } = mitProjekt((wurzel) => {
    const ergebnis = sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, null, 1));
    assert.throws(() => gedaechtnis(wurzel, 'frontend'));
    return ergebnis;
  });
  assert.notEqual(code, 0);
  assert.match(text, /keinen Bericht/);
});

test('laufen: vollauf backend faehrt das Profil mit seinem Default-Umfang und seiner Default-Marke', () => {
  const { aufrufe } = mitProjekt((wurzel) =>
    sammelLauf(['vollauf', 'backend'], wurzel, {}, pitDoppel(wurzel, BEISPIEL_XML)),
  );
  assert.equal(aufrufe[0].befehl, 'mvn');
  assert.deepEqual(aufrufe[0].args, ['-B', '-Ppit', '-Dskip.frontend=true', 'test']);
});

test('laufen: vollauf backend unter 100 Prozent endet ungleich 0 und legt die Gedaechtnisdatei an', () => {
  const { code, text, inhalt } = mitProjekt((wurzel) => {
    const ergebnis = sammelLauf(['vollauf', 'backend'], wurzel, {}, pitDoppel(wurzel, BEISPIEL_XML));
    return { ...ergebnis, inhalt: gedaechtnis(wurzel, 'backend') };
  });
  assert.notEqual(code, 0);
  assert.match(text, /unter der Schwelle 100 %/);
  assert.equal(inhalt.quote, 50);
  assert.equal(inhalt.mutanten.length, 2);
  assert.deepEqual(inhalt.umfang, [
    'eingeschlossen: org.mwolff.manban.*.application.*',
    'eingeschlossen: org.mwolff.manban.*.domain.*',
    'ausgenommen:    org.mwolff.manban.*.infrastructure.*',
    'ausgenommen:    org.mwolff.manban.ManbanApplication',
  ]);
});

test('laufen: vollauf backend mit lauter getoeteten Mutanten endet gruen bei 100 Prozent', () => {
  const xml = '<mutations>'
    + '<mutation detected="true" status="KILLED"><sourceFile>CardService.java</sourceFile>'
    + '<mutatedClass>org.mwolff.manban.card.application.CardService</mutatedClass>'
    + '<lineNumber>7</lineNumber><mutator>org.pitest.mutationtest.engine.gregor.mutators.MathMutator</mutator>'
    + '<description>Replaced addition</description>'
    + '<killingTest>org.mwolff.manban.card.application.CardServiceTest.x(org.mwolff.manban.card.application.CardServiceTest)</killingTest>'
    + '</mutation></mutations>';
  const { code, text, inhalt } = mitProjekt((wurzel) => {
    const ergebnis = sammelLauf(['vollauf', 'backend'], wurzel, {}, pitDoppel(wurzel, xml));
    return { ...ergebnis, inhalt: gedaechtnis(wurzel, 'backend') };
  });
  assert.equal(code, 0);
  assert.match(text, /Quote: 100,00 %/);
  assert.deepEqual(inhalt.mutanten, []);
});

test('laufen: vollauf backend ohne Bericht endet ungleich 0 und sagt warum', () => {
  const { code, text } = mitProjekt((wurzel) =>
    sammelLauf(['vollauf', 'backend'], wurzel, {}, pitDoppel(wurzel, null, 1)),
  );
  assert.notEqual(code, 0);
  assert.match(text, /keinen Bericht/);
});

test('laufen: ohne Gedaechtnisdatei steht im Vollauf der Satz, dass es noch keinen Vollauf gibt', () => {
  const { text } = mitProjekt((wurzel) =>
    sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, BERICHT_UEBER_SCHWELLE)),
  );
  assert.match(text, /noch keinen Vollauf/);
});

test('laufen: eine beschaedigte Gedaechtnisdatei gilt wie eine fehlende, ohne Absturz', () => {
  const { code, text } = mitProjekt(
    (wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel),
    { vollaufRoh: { frontend: '{ das ist kein JSON' } },
  );
  assert.equal(code, 0);
  assert.match(text, /noch keinen Vollauf/);
});

test('laufen: die Aenderungspruefung nennt die eigene Dauer neben Dauer und Datum des Vollaufs', () => {
  const vollauf = {
    frontend: { stand: 'abc', datum: '2026-09-24T11:36:00.000Z', dauerMs: 1789000, quote: 84.7, umfang: [], mutanten: [] },
  };
  const { text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel), { vollauf });
  assert.match(text, /Dauer: \d+,\d s\. Letzter Vollauf frontend: 29 min 49 s am 2026-09-24, Quote 84,7 %\./);
});

// --- Stufenplan als Quelle des Frontend-Bereichs (Issue #1276) ---------------

test('laufen: ohne Stufenplan endet das Frontend ungleich 0 und nennt die fehlende Datei', () => {
  const { code, text, aufrufe } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel), { plan: null });
  assert.equal(code, 1);
  assert.match(text, /frontend\/mutationsstufen\.json fehlt oder ist kein gültiges JSON/);
  assert.equal(aufrufe.length, 0);
});

test('laufen: ein falsch geformter Stufenplan endet ungleich 0 und nennt den Fehler', () => {
  const { code, text, aufrufe } = mitProjekt(
    (wurzel) => sammelLauf(['vollauf', 'frontend'], wurzel),
    { plan: { ausnahmen: [], ausschnitte: [{ name: 'kaputt', muster: 'src/lib' }] } },
  );
  assert.equal(code, 1);
  assert.match(text, /frontend\/mutationsstufen\.json ist ungültig: .*kaputt.*muster/);
  assert.equal(aufrufe.length, 0);
});

test('laufen: der Frontend-Bereich nennt den Stufenplan als Quelle', () => {
  const { text } = mitProjekt((wurzel) => sammelLauf(['aenderung', 'frontend'], wurzel));
  assert.ok(text.includes('Geprüfter Bereich (frontend/mutationsstufen.json (aufgenommene Ausschnitte)):'));
});

/**
 * Gegenprobe zur Unveraendertheit des Backends (Issue #1276): Die Meldung des Backend-Vollaufs ist
 * zeichengleich mit der vor dem Umbau auf den Stufenplan erzeugten, Rueckgabewert und
 * Gedaechtnisdatei ebenso. Die Uhr steht still, damit Dauer und Datum feste Werte tragen.
 */
const BACKEND_VOLLAUF_MELDUNG = `Mutationsprüfung — Vollauf backend

Geprüfter Bereich (pom.xml, Profil pit, Property pit.targetClasses + excludedClasses):
  eingeschlossen: org.mwolff.manban.*.application.*
  eingeschlossen: org.mwolff.manban.*.domain.*
  ausgenommen:    org.mwolff.manban.*.infrastructure.*
  ausgenommen:    org.mwolff.manban.ManbanApplication

Umfang: die ganze Seite.

Überlebende Stellen (2):
  src/main/java/org/mwolff/manban/card/application/CardService.java:43 — ConditionalsBoundaryMutator: changed conditional boundary überlebt
  src/main/java/org/mwolff/manban/card/domain/Card.java:17 — BooleanTrueReturnValsMutator: replaced boolean return with true for org/mwolff/manban/card/domain/Card$Zustand::istOffen überlebt

Mutanten: 4 geprüft, 2 getötet, 2 überlebt, 0 ausgenommen (@ExcludeFromJacocoGeneratedReport je Einheit — solche Mutanten entstehen gar nicht erst).

Quote: 50,00 % — unter der Schwelle 100 %. Der Vollauf hält an.

Dauer: 1 min 2 s. Letzter Vollauf backend: 29 min 49 s am 2026-09-24, Quote 97,5 %.
Stufe: noch nicht eingetragen
Ausnahme: im Backend gilt sie je Einheit (Typ, Methode, Konstruktor), nicht je Stelle.
`;

test('laufen: vollauf backend bleibt mit dem Stufenplan zeichengleich (Gegenprobe #1276)', () => {
  const vollauf = {
    backend: { stand: 'abc', datum: '2026-09-24T11:36:00.000Z', dauerMs: 1789000, quote: 97.5, umfang: [], mutanten: [] },
  };
  let uhr = 1000;
  const { code, text, inhalt } = mitProjekt((wurzel) => {
    const ergebnis = sammelLauf(['vollauf', 'backend'], wurzel, {}, pitDoppel(wurzel, BEISPIEL_XML), {
      jetzt: () => {
        const wert = uhr;
        uhr += 61500;
        return wert;
      },
    });
    return { ...ergebnis, inhalt: gedaechtnis(wurzel, 'backend') };
  }, { vollauf });
  assert.equal(code, 1);
  assert.equal(text, BACKEND_VOLLAUF_MELDUNG);
  assert.equal(inhalt.datum, '1970-01-01T00:02:04.000Z');
  assert.equal(inhalt.dauerMs, 61500);
  assert.equal(inhalt.quote, 50);
});

// --- Vollauf je Ausschnitt (Issue #1278) -------------------------------------

/**
 * Ein Stufenplan mit vier aufgenommenen Ausschnitten und einem Kandidaten. Die beiden ersten
 * tragen die gemeinsame Schwelle des Bestands (E7), die beiden folgenden nicht.
 */
const PLAN_AUSSCHNITTE = {
  ausnahmen: ['src/theme.ts'],
  ausschnitte: [
    { name: 'bestand-a', muster: ['src/lib/**/*.{ts,tsx}'], aufgenommen: '2026-09-28', gemeinsameSchwelle: true, reihenfolge: 1 },
    { name: 'bestand-b', muster: ['src/api/**/*.ts'], aufgenommen: '2026-09-28', gemeinsameSchwelle: true, reihenfolge: 2 },
    { name: 'stufe-c', muster: ['src/c/**/*.tsx'], aufgenommen: '2026-09-29', reihenfolge: 3 },
    { name: 'stufe-d', muster: ['src/d/**/*.tsx'], aufgenommen: '2026-09-29', reihenfolge: 4 },
    { name: 'kandidat-k', muster: ['src/k/**/*.tsx'], aufgenommen: false, reihenfolge: 5 },
    { name: 'spaeter-z', muster: ['src/z/**/*.tsx'], aufgenommen: false, reihenfolge: 6 },
  ],
};

let laufendeId = 0;

/**
 * Rohmutanten eines Stryker-Berichts in der verlangten Mischung. `ignoriert` erzeugt Mutanten, die
 * der Darstellungs-Ignorer ausgenommen hat, `kommentar` solche aus einer Stryker-Ausnahme je Stelle.
 */
function mischung({ getoetet = 0, ueberlebt = 0, ohneDeckung = 0, ignoriert = 0, kommentar = 0 }) {
  const reihe = (anzahl, zustand, extra = {}) => Array.from({ length: anzahl }, () => {
    laufendeId += 1;
    return { ...mutant(2, 'ConditionalExpression', zustand, { id: `m${laufendeId}`, ersetzung: `x${laufendeId}` }), ...extra };
  });
  return [
    ...reihe(getoetet, 'Killed'),
    ...reihe(ueberlebt, 'Survived'),
    ...reihe(ohneDeckung, 'NoCoverage', { coveredBy: [] }),
    ...reihe(ignoriert, 'Ignored', { statusReason: 'Darstellung: Stilwert im sx- oder style-Attribut' }),
    ...reihe(kommentar, 'Ignored', { statusReason: 'Disabled by user comment' }),
  ];
}

function berichtJeDatei(dateien) {
  const files = {};
  for (const [pfad, zusammensetzung] of Object.entries(dateien)) {
    files[pfad] = { source: QUELLE_A, mutants: mischung(zusammensetzung) };
  }
  return bericht(files);
}

/** Drei aufgenommene Ausschnitte bei 95 %, einer bei 79,9 %, der Kandidat bei 90 %. */
const BERICHT_EINER_UNTER = berichtJeDatei({
  'src/lib/a.ts': { getoetet: 95, ueberlebt: 5 },
  'src/api/b.ts': { getoetet: 95, ueberlebt: 5 },
  'src/c/C.tsx': { getoetet: 799, ueberlebt: 201 },
  'src/d/D.tsx': { getoetet: 95, ueberlebt: 5 },
  'src/k/K.tsx': { getoetet: 9, ueberlebt: 1 },
});

function vollaufMit(berichtInhalt, optionen = {}, extra = {}) {
  return mitProjekt((wurzel) => {
    const ergebnis = sammelLauf(['vollauf', 'frontend'], wurzel, {}, strykerDoppel(wurzel, berichtInhalt), {
      jetzt: () => Date.parse('2026-09-29T10:00:00.000Z'),
      ...extra,
    });
    return { ...ergebnis, inhalt: gedaechtnis(wurzel, 'frontend') };
  }, { plan: PLAN_AUSSCHNITTE, ...optionen });
}

test('strykerMutanten: der Grund einer Ausnahme wird mitgelesen', () => {
  const [ignoriert] = strykerMutanten(berichtJeDatei({ 'src/c/C.tsx': { ignoriert: 1 } }));
  assert.equal(ignoriert.zustand, 'Ignored');
  assert.match(ignoriert.grund, /^Darstellung:/);
});

test('klammernAufloesen: {a,b} wird zu zwei Mustern, alles andere bleibt', () => {
  assert.deepEqual(klammernAufloesen('src/lib/**/*.{ts,tsx}'), ['src/lib/**/*.ts', 'src/lib/**/*.tsx']);
  assert.deepEqual(klammernAufloesen('!src/**/*.test.ts'), ['!src/**/*.test.ts']);
});

test('frontendBereich: der Vollauf mutiert die aufgenommenen Ausschnitte plus den Kandidaten samt Ausschluessen (E6)', () => {
  const bereich = frontendBereich(PLAN_AUSSCHNITTE);
  assert.deepEqual(bereich.gemessen, ['bestand-a', 'bestand-b', 'stufe-c', 'stufe-d', 'kandidat-k']);
  assert.deepEqual(bereich.vollaufMutate, [
    'src/lib/**/*.{ts,tsx}', 'src/api/**/*.ts', 'src/c/**/*.tsx', 'src/d/**/*.tsx', 'src/k/**/*.tsx',
    '!src/**/*.test.ts', '!src/**/*.test.tsx', '!src/theme.ts',
  ]);
  const kandidat = bereich.ausschnitte.find((a) => a.name === 'kandidat-k');
  assert.equal(kandidat.kandidat, true);
  assert.equal(bereich.ausschnitte.filter((a) => a.kandidat).length, 1);
});

test('laufen: vollauf frontend traegt -m aus mutateFuer mit Negationen, die Klammern aufgeloest (E6)', () => {
  const { aufrufe } = vollaufMit(BERICHT_EINER_UNTER);
  assert.equal(aufrufe.length, 1);
  const [, schalter, liste] = aufrufe[0].args;
  assert.equal(schalter, '-m');
  assert.deepEqual(liste.split(','), [
    'src/lib/**/*.ts', 'src/lib/**/*.tsx', 'src/api/**/*.ts', 'src/c/**/*.tsx', 'src/d/**/*.tsx', 'src/k/**/*.tsx',
    '!src/**/*.test.ts', '!src/**/*.test.tsx', '!src/theme.ts',
  ]);
});

test('vollaufAuswerten: zaehlt je Ausschnitt geprueft, getoetet, ueberlebt, ausgenommen und Darstellung', () => {
  const bereich = frontendBereich(PLAN_AUSSCHNITTE);
  const mutanten = strykerMutanten(berichtJeDatei({
    'src/lib/a.ts': { getoetet: 3, ueberlebt: 1, ignoriert: 2, kommentar: 1 },
    'src/c/C.tsx': { getoetet: 1, ohneDeckung: 1 },
    'src/k/K.tsx': { getoetet: 1, ueberlebt: 1 },
  }));
  const { zaehlung, ausschnitte, ohneAusschnitt, ueberlebende } = vollaufAuswerten(mutanten, bereich.ausschnitte.filter((a) => bereich.gemessen.includes(a.name)));
  const je = Object.fromEntries(ausschnitte.map((a) => [a.name, a]));
  assert.deepEqual(je['bestand-a'].zaehlung, { geprueft: 4, getoetet: 3, ueberlebt: 1, ausgenommen: 3, darstellung: 2, ohneDeckung: 0 });
  assert.equal(je['bestand-a'].quote, 75);
  assert.deepEqual(je['stufe-c'].zaehlung, { geprueft: 2, getoetet: 1, ueberlebt: 1, ausgenommen: 0, darstellung: 0, ohneDeckung: 1 });
  assert.equal(je['bestand-b'].zaehlung.geprueft, 0);
  assert.equal(je['kandidat-k'].quote, 50);
  assert.deepEqual(ohneAusschnitt, []);
  // Gesamtwert und Ueberlebendenliste bleiben beim Pruefbereich: den aufgenommenen Ausschnitten.
  assert.deepEqual(zaehlung, { geprueft: 6, getoetet: 4, ueberlebt: 2, ausgenommen: 3, ausserhalb: 0 });
  assert.equal(ueberlebende.length, 2);
});

test('vollaufAuswerten: ein Mutant ohne Ausschnitt wird gemeldet, nicht verschluckt', () => {
  const bereich = frontendBereich(PLAN_AUSSCHNITTE);
  const mutanten = strykerMutanten(berichtJeDatei({ 'src/fremd/F.tsx': { getoetet: 1 } }));
  const { ohneAusschnitt } = vollaufAuswerten(mutanten, bereich.ausschnitte);
  assert.deepEqual(ohneAusschnitt.map((m) => m.datei), ['frontend/src/fremd/F.tsx']);
});

test('laufen: ein Mutant ohne Ausschnitt endet ungleich 0 und nennt die Datei', () => {
  const { code, text } = vollaufMit(berichtJeDatei({
    'src/lib/a.ts': { getoetet: 5 },
    'src/fremd/F.tsx': { getoetet: 1 },
  }));
  assert.equal(code, 1);
  assert.match(text, /ohne Ausschnitt.*\n.*frontend\/src\/fremd\/F\.tsx/);
});

test('laufen: ein aufgenommener Ausschnitt bei 79,9 % neben drei bei 95 % haelt an und wird genannt (AK 3)', () => {
  const { code, text } = vollaufMit(BERICHT_EINER_UNTER);
  assert.equal(code, 1);
  assert.match(text, /stufe-c: 79,90 % — unter der Schwelle 80 %/);
  assert.match(text, /Der Vollauf hält an: stufe-c unter der Schwelle 80 %\./);
  assert.match(text, /stufe-d: 95,00 % — Schwelle 80 % erfüllt/);
});

test('laufen: derselbe Wert beim Kandidaten haelt nicht an (AK 3)', () => {
  const { code, text } = vollaufMit(berichtJeDatei({
    'src/lib/a.ts': { getoetet: 95, ueberlebt: 5 },
    'src/k/K.tsx': { getoetet: 799, ueberlebt: 201 },
  }));
  assert.equal(code, 0);
  assert.match(text, /Kandidat kandidat-k: 79,90 %/);
  assert.ok(!text.includes('hält an'));
});

test('laufen: der Kandidat bei 79 % haelt nicht an', () => {
  const { code } = vollaufMit(berichtJeDatei({
    'src/lib/a.ts': { getoetet: 9, ueberlebt: 1 },
    'src/k/K.tsx': { getoetet: 79, ueberlebt: 21 },
  }));
  assert.equal(code, 0);
});

test('laufen: Kandidat bei 81,9 % ohne, bei 82,0 % mit Vorschlagszeile', () => {
  const darunter = vollaufMit(berichtJeDatei({ 'src/k/K.tsx': { getoetet: 819, ueberlebt: 181 } }));
  assert.match(darunter.text, /Kandidat kandidat-k: 81,90 % — unter 82 %, nicht zur Aufnahme vorgeschlagen\./);
  assert.ok(!darunter.text.includes('zur Aufnahme vorgeschlagen. '));
  const erreicht = vollaufMit(berichtJeDatei({ 'src/k/K.tsx': { getoetet: 82, ueberlebt: 18 } }));
  assert.match(erreicht.text, /Kandidat kandidat-k: 82,00 % — erreicht 82 %, zur Aufnahme vorgeschlagen\. Die Aufnahme trägt der Mensch in frontend\/mutationsstufen\.json ein\./);
});

test('laufen: ein Ausschnitt ohne Mutanten steht als bestanden mit dem Vermerk keine Mutanten', () => {
  const { code, text } = vollaufMit(berichtJeDatei({ 'src/lib/a.ts': { getoetet: 5 } }));
  assert.equal(code, 0);
  assert.match(text, /stufe-d: keine Mutanten — bestanden\./);
});

test('laufen: ein Kandidat ohne Tests im Umfang steht als 0 % — keine Tests im Umfang', () => {
  const { code, text } = vollaufMit(berichtJeDatei({
    'src/lib/a.ts': { getoetet: 5 },
    'src/k/K.tsx': { ohneDeckung: 7 },
  }));
  assert.equal(code, 0);
  assert.match(text, /Kandidat kandidat-k: 0 % — keine Tests im Umfang\./);
});

test('laufen: je Ausschnitt die Zahl der Darstellungsmutanten, auch fuer die Bestandsausschnitte', () => {
  const { text } = vollaufMit(berichtJeDatei({
    'src/lib/a.ts': { getoetet: 5, ignoriert: 3, kommentar: 1 },
    'src/api/b.ts': { getoetet: 5, ignoriert: 2 },
    'src/c/C.tsx': { getoetet: 5, ignoriert: 11 },
    'src/k/K.tsx': { getoetet: 5, ignoriert: 4 },
  }));
  assert.match(text, /bestand-a: 100,00 %[^\n]*\n\s+5 geprüft, 5 getötet, 0 überlebt, 4 ausgenommen, davon 3 Darstellung\./);
  assert.match(text, /bestand-b: 100,00 %[^\n]*\n\s+5 geprüft, 5 getötet, 0 überlebt, 2 ausgenommen, davon 2 Darstellung\./);
  assert.match(text, /stufe-c: 100,00 %[^\n]*\n\s+5 geprüft, 5 getötet, 0 überlebt, 11 ausgenommen, davon 11 Darstellung\./);
  assert.match(text, /Kandidat kandidat-k: 100,00 %[^\n]*\n\s+5 geprüft, 5 getötet, 0 überlebt, 4 ausgenommen, davon 4 Darstellung\./);
});

/** Bestand-a bei 90 %, bestand-b bei 75 %: gemeinsam 82,5 %. */
const BERICHT_BESTAND_B_UNTER = berichtJeDatei({
  'src/lib/a.ts': { getoetet: 90, ueberlebt: 10 },
  'src/api/b.ts': { getoetet: 75, ueberlebt: 25 },
});

test('Bestandsregel (E7): ohne erstmals80 zaehlt die gemeinsame Quote, der Bericht verlangt eine Karte', () => {
  const { code, text, inhalt } = vollaufMit(BERICHT_BESTAND_B_UNTER);
  assert.equal(code, 0);
  assert.match(text, /Bestand gemeinsam \(bestand-a, bestand-b\): 82,50 % — Schwelle 80 % erfüllt/);
  assert.match(text, /bestand-b unter 80 % — Karte für die fehlenden Tests anlegen\./);
  assert.match(text, /Kein Halt: Schwelle 80 % in jedem aufgenommenen Ausschnitt erfüllt, im Bestand gemeinsam\./);
  const je = Object.fromEntries(inhalt.ausschnitte.map((a) => [a.name, a]));
  assert.equal(je['bestand-a'].erstmals80, '2026-09-29');
  assert.equal(je['bestand-b'].erstmals80, null);
});

test('Bestandsregel (E7): ein erstmals80 gesetzt — weiter die gemeinsame Quote, das Datum bleibt stehen', () => {
  const vollauf = {
    frontend: {
      datum: '2026-09-27T00:00:00.000Z', dauerMs: 1, quote: 84.58, mutanten: [],
      ausschnitte: [{ name: 'bestand-a', erstmals80: '2026-09-20' }, { name: 'bestand-b', erstmals80: null }],
    },
  };
  const { code, text, inhalt } = vollaufMit(BERICHT_BESTAND_B_UNTER, { vollauf });
  assert.equal(code, 0);
  assert.match(text, /Karte für die fehlenden Tests anlegen/);
  const je = Object.fromEntries(inhalt.ausschnitte.map((a) => [a.name, a]));
  assert.equal(je['bestand-a'].erstmals80, '2026-09-20');
  assert.equal(je['bestand-b'].erstmals80, null);
});

test('Bestandsregel (E7): beide erstmals80 gesetzt — die getrennte Schwelle haelt an', () => {
  const vollauf = {
    frontend: {
      datum: '2026-09-27T00:00:00.000Z', dauerMs: 1, quote: 84.58, mutanten: [],
      ausschnitte: [{ name: 'bestand-a', erstmals80: '2026-09-20' }, { name: 'bestand-b', erstmals80: '2026-09-21' }],
    },
  };
  const { code, text } = vollaufMit(BERICHT_BESTAND_B_UNTER, { vollauf });
  assert.equal(code, 1);
  assert.match(text, /Der Vollauf hält an: bestand-b unter der Schwelle 80 %\./);
  assert.ok(!text.includes('Karte für die fehlenden Tests anlegen'));
});

test('Bestandsregel (E7): die gemeinsame Quote unter 80 % haelt an', () => {
  const { code, text } = vollaufMit(berichtJeDatei({
    'src/lib/a.ts': { getoetet: 70, ueberlebt: 30 },
    'src/api/b.ts': { getoetet: 75, ueberlebt: 25 },
  }));
  assert.equal(code, 1);
  assert.match(text, /Der Vollauf hält an: Bestand gemeinsam \(bestand-a, bestand-b\) unter der Schwelle 80 %\./);
});

test('Gedaechtnisdatei: Feld ausschnitte mit Name, Muster, aufgenommen, Quote und Zaehlung; quote bleibt Gesamtwert', () => {
  const { inhalt } = vollaufMit(BERICHT_EINER_UNTER);
  assert.deepEqual(inhalt.ausschnitte.map((a) => a.name), ['bestand-a', 'bestand-b', 'stufe-c', 'stufe-d', 'kandidat-k']);
  const c = inhalt.ausschnitte.find((a) => a.name === 'stufe-c');
  assert.deepEqual(c, {
    name: 'stufe-c',
    muster: ['src/c/**/*.tsx'],
    aufgenommen: '2026-09-29',
    quote: 79.9,
    zaehlung: { geprueft: 1000, getoetet: 799, ueberlebt: 201, ausgenommen: 0, darstellung: 0, ohneDeckung: 0 },
  });
  assert.equal(inhalt.ausschnitte.find((a) => a.name === 'kandidat-k').aufgenommen, false);
  assert.ok('erstmals80' in inhalt.ausschnitte.find((a) => a.name === 'bestand-a'));
  // Gesamtwert ueber die aufgenommenen Ausschnitte: (95+95+799+95)/(100+100+1000+100).
  assert.equal(inhalt.quote, 83.38);
});

test('Gedaechtnisdatei: eine Datei ohne ausschnitte wird weiter gelesen und wie bisher ausgewertet', () => {
  const vollauf = { frontend: { datum: '2026-09-27T00:00:00.000Z', dauerMs: 2100000, quote: 84.58, mutanten: [] } };
  const { code, text } = vollaufMit(BERICHT_BESTAND_B_UNTER, { vollauf });
  assert.equal(code, 0);
  assert.match(text, /Letzter Vollauf frontend: 35 min 0 s am 2026-09-27, Quote 84,58 %\./);
  assert.match(text, /Bestand gemeinsam/);
});

test('Backend im Ein-Ausschnitt-Fall: keine Ausschnittszeile, keine ausschnitte in der Gedaechtnisdatei (E11)', () => {
  const { text, inhalt } = mitProjekt((wurzel) => {
    const ergebnis = sammelLauf(['vollauf', 'backend'], wurzel, {}, pitDoppel(wurzel, BEISPIEL_XML));
    return { ...ergebnis, inhalt: gedaechtnis(wurzel, 'backend') };
  });
  assert.ok(!text.includes('Ausschnitt'));
  assert.ok(!text.includes('Kandidat'));
  assert.equal('ausschnitte' in inhalt, false);
});

test('mutationspruefung.mjs schreibt nie in frontend/mutationsstufen.json (grep-fest)', () => {
  const quelle = readFileSync(join(HIER, 'mutationspruefung.mjs'), 'utf-8');
  const schreibend = /\b(writeFileSync|appendFileSync|rmSync|renameSync|unlinkSync|copyFileSync|writeFile|appendFile|createWriteStream)\s*\(([^)]*)/g;
  const aufrufe = [...quelle.matchAll(schreibend)];
  assert.ok(aufrufe.length > 0);
  for (const [, , ziel] of aufrufe) {
    assert.ok(!/STRYKER_PFAD|mutationsstufen/.test(ziel), `Schreibzugriff auf den Stufenplan: ${ziel}`);
  }
});

// --- Aenderungspruefung folgt den aufgenommenen Ausschnitten (Issue #1279) ---

/**
 * Ein Stufenplan mit einem aufgenommenen Stufen-Ausschnitt (`stufe-c`), einem Kandidaten und einem
 * spaeteren Ausschnitt mit eigenem `testMuster`. Die Ausnahmen liegen einmal ausserhalb jedes
 * Ausschnitts (`src/theme.ts`) und einmal mitten in einem aufgenommenen.
 */
const PLAN_STUFEN = {
  ausnahmen: ['src/theme.ts', 'src/c/stil.tsx'],
  ausschnitte: [
    { name: 'bestand-a', muster: ['src/lib/**/*.{ts,tsx}'], aufgenommen: '2026-09-28', gemeinsameSchwelle: true, reihenfolge: 1 },
    { name: 'stufe-c', muster: ['src/c/**/*.tsx'], aufgenommen: '2026-09-29', reihenfolge: 2 },
    { name: 'kandidat-k', muster: ['src/k/**/*.tsx'], aufgenommen: false, reihenfolge: 3 },
    {
      name: 'spaeter-z',
      muster: ['src/Z.tsx'],
      testMuster: ['src/ZSammel.test.tsx'],
      aufgenommen: false,
      reihenfolge: 4,
    },
  ],
};

const C_DATEI = 'frontend/src/c/C.tsx';
const K_DATEI = 'frontend/src/k/K.tsx';

function geaendert(...pfade) {
  return { 'diff --name-status -z 1a2b3c4': OK(pfade.map((p) => `M\0${p}\0`).join('')) };
}

function aenderungStufen(pfade, berichtInhalt, gitExtra = {}, optionen = {}) {
  return mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'frontend'], wurzel, {
      ...geaendert(...pfade),
      ...Object.fromEntries(pfade.map((p) => [`diff -U0 1a2b3c4 -- ${p}`, OK('@@ -2 +2 @@\n')])),
      ...gitExtra,
    }, strykerDoppel(wurzel, berichtInhalt)),
  { plan: PLAN_STUFEN, ...optionen });
}

const BERICHT_C_UEBERLEBT = berichtJeDatei({ 'src/c/C.tsx': { getoetet: 1, ueberlebt: 1 } });

test('laufen: eine beruehrte Datei in einem aufgenommenen Ausschnitt wird mutiert, ihr Ueberlebender haelt an (#1279)', () => {
  const { code, text, aufrufe } = aenderungStufen([C_DATEI], BERICHT_C_UEBERLEBT);
  assert.equal(aufrufe.length, 1);
  assert.deepEqual(aufrufe[0].args, ['run', '-m', 'src/c/C.tsx', '--reporters', 'json,html,clear-text']);
  assert.equal(code, 1);
  assert.match(text, /frontend\/src\/c\/C\.tsx:2 — ConditionalExpression/);
  assert.match(text, /hält an — kein Altlast-Vermerk/);
});

test('laufen: eine beruehrte Datei in einem nicht aufgenommenen Ausschnitt wird nicht mutiert, ihr Ueberlebender haelt nicht an (#1279)', () => {
  // Der Bericht traegt einen Ueberlebenden in K — mutiert Stryker die Datei doch mit, darf er
  // trotzdem nicht anhalten; verlangt wird aber schon, dass K nicht im -m steht.
  const berichtInhalt = berichtJeDatei({
    'src/c/C.tsx': { getoetet: 1 },
    'src/k/K.tsx': { ueberlebt: 1 },
  });
  const { code, text, aufrufe } = aenderungStufen([C_DATEI, K_DATEI], berichtInhalt);
  assert.equal(aufrufe.length, 1);
  assert.deepEqual(aufrufe[0].args, ['run', '-m', 'src/c/C.tsx', '--reporters', 'json,html,clear-text']);
  assert.equal(code, 0);
  assert.ok(!text.includes('src/k/K.tsx:'));
  assert.ok(!text.includes(`  ${K_DATEI}\n`));
  assert.match(text, /1 Überlebende außerhalb/);
});

test('laufen: eine beruehrte Ausnahme-Datei wird nicht mutiert, auch mitten in einem aufgenommenen Ausschnitt (#1279)', () => {
  const { code, text, aufrufe } = aenderungStufen(['frontend/src/theme.ts', 'frontend/src/c/stil.tsx'], BERICHT_C_UEBERLEBT);
  assert.equal(aufrufe.length, 0);
  assert.equal(code, 0);
  assert.ok(text.includes('keine berührte Datei im Prüfbereich'));
});

test('laufen: nur nicht aufgenommene Dateien beruehrt — kein Werkzeugstart, Rueckgabewert 0 (#1279)', () => {
  // Dabei auch Tests der nicht aufgenommenen Ausschnitte ohne zuordenbare Quelle: ueber das Muster
  // (K.test.tsx) und ueber das testMuster (ZSammel.test.tsx). Sie gehoeren zu einem Ausschnitt
  // ausserhalb des Pruefbereichs und halten deshalb nicht als "Test ohne Zuordnung" an.
  const { code, text, aufrufe } = aenderungStufen(
    [K_DATEI, 'frontend/src/k/K.test.tsx', 'frontend/src/Z.tsx', 'frontend/src/ZSammel.test.tsx'],
    BERICHT_C_UEBERLEBT,
  );
  assert.equal(aufrufe.length, 0);
  assert.equal(code, 0);
  assert.ok(text.includes('keine berührte Datei im Prüfbereich'));
  assert.ok(!text.includes('ohne zuordenbare Quelle'));
});

test('beruehrung: ein Test ohne Zuordnung ausserhalb jedes Ausschnitts haelt weiter an (#1279, #1287)', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/sonstwo/lose.test.ts', 'frontend/src/c/Ohne.test.tsx', 'frontend/src/k/K.test.tsx'],
    bereich: frontendBereich(PLAN_STUFEN),
    zuordnung: new Map(),
    existiert: () => false,
  });
  // lose.test.ts liegt in keinem Ausschnitt, Ohne.test.tsx in einem aufgenommenen: beide bleiben
  // unbekannt und halten an. Nur K.test.tsx faellt als Test eines fremden Ausschnitts heraus.
  assert.deepEqual(ergebnis.ohneZuordnung, ['frontend/src/sonstwo/lose.test.ts', 'frontend/src/c/Ohne.test.tsx']);
});

test('beruehrung: ein Test eines nicht aufgenommenen Ausschnitts mit Zuordnung in den Pruefbereich zieht seine Quelle mit (#1279)', () => {
  const ergebnis = beruehrung({
    geaendert: ['frontend/src/k/K.test.tsx'],
    bereich: frontendBereich(PLAN_STUFEN),
    zuordnung: new Map([['frontend/src/k/K.test.tsx', ['frontend/src/c/C.tsx']]]),
    existiert: () => false,
  });
  assert.deepEqual(ergebnis, { dateien: [C_DATEI], ganzeSeite: false, ohneZuordnung: [] });
});

test('laufen: die Altlast-Markierung greift weiterhin in einem aufgenommenen Stufen-Ausschnitt (#1279)', () => {
  const berichtInhalt = bericht({
    'src/c/C.tsx': { source: VERMERKTE_QUELLE, mutants: [mutant(3, 'ConditionalExpression', 'Survived')] },
  });
  const vollauf = {
    frontend: {
      datum: '2026-09-29T08:00:00.000Z',
      dauerMs: 1000,
      quote: 84.7,
      mutanten: [{ datei: C_DATEI, zeile: 3, mutator: 'ConditionalExpression' }],
    },
  };
  const { code, text } = aenderungStufen([C_DATEI], berichtInhalt, {
    [`diff -U0 1a2b3c4 -- ${C_DATEI}`]: OK('@@ -1 +1 @@\n'),
  }, { vollauf });
  assert.equal(code, 0);
  assert.match(text, /Altlast-Vermerk \(#1213, 2026-09-25\)/);
  assert.match(text, /1 überlebt/);
});

test('laufen: der Rueckgabewert haengt nur an Ueberlebenden in beruehrten Dateien aufgenommener Ausschnitte (#1279)', () => {
  const beide = [C_DATEI, K_DATEI];
  const nurK = berichtJeDatei({ 'src/c/C.tsx': { getoetet: 2 }, 'src/k/K.tsx': { ueberlebt: 3 } });
  const nurC = berichtJeDatei({ 'src/c/C.tsx': { getoetet: 1, ueberlebt: 1 }, 'src/k/K.tsx': { getoetet: 3 } });
  const ungeaendertC = berichtJeDatei({ 'src/c/C.tsx': { ueberlebt: 1 } });
  assert.equal(aenderungStufen(beide, nurK).code, 0);
  assert.equal(aenderungStufen(beide, nurC).code, 1);
  // C traegt einen Ueberlebenden, ist aber nicht beruehrt: kein Halt.
  assert.equal(aenderungStufen([K_DATEI, 'frontend/src/lib/a.ts'], ungeaendertC).code, 0);
});

test('laufen: der Backend-Zweig bleibt unveraendert — beruehrte Klasse mutiert, Test ohne Zuordnung haelt an (#1279)', () => {
  const beruehrt = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'backend'], wurzel, {
      ...GEAENDERT_SERVICE,
      [`diff -U0 1a2b3c4 -- ${CARD_SERVICE}`]: OK('@@ -43 +43 @@\n'),
    }, pitDoppel(wurzel, BEISPIEL_XML)),
  { plan: PLAN_STUFEN });
  assert.equal(beruehrt.aufrufe.length, 1);
  assert.ok(beruehrt.aufrufe[0].args.includes('-Dpit.targetClasses=org.mwolff.manban.card.application.CardService,org.mwolff.manban.card.application.CardService$*'));
  assert.equal(beruehrt.code, 1);
  assert.match(beruehrt.text, /CardService\.java:43 — ConditionalsBoundaryMutator/);

  const ohne = 'src/test/java/org/mwolff/manban/card/application/NirgendsTest.java';
  const test = mitProjekt((wurzel) =>
    sammelLauf(['aenderung', 'backend'], wurzel, geaendert(ohne), pitDoppel(wurzel, BEISPIEL_XML)),
  { plan: PLAN_STUFEN });
  assert.equal(test.code, 1);
  assert.equal(test.aufrufe.length, 0);
  assert.ok(test.text.includes(ohne));
});
