import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  ARTEN,
  PRUEFARTEN,
  FRIST_QUELLE_MS,
  FRIST_GESAMT_MS,
  ZUSTAND_OK,
  ZUSTAND_FEHLSCHLAG,
  ZUSTAND_UEBERSPRUNGEN,
  GRUND_EIGEN,
  bausteineLesen,
  argumenteZerlegen,
  abbildTeile,
  manifestUrl,
  wwwAuthenticateLesen,
  tokenUrl,
  tokenAktionenLeer,
  befundZeile,
  quellePruefen,
  laufen,
  abbilderAusCompose,
  abbilderAusDockerfile,
  abbilderAusJava,
  bestandAbbilder,
  abgleich,
} from './bezugspruefung.mjs';

const HIER = dirname(fileURLToPath(import.meta.url));
const WURZEL = join(HIER, '..');

// --- Antwort-Attrappen -----------------------------------------------------

function antwort(status, { koerper = {}, kopfzeilen = {} } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(kopfzeilen),
    async json() {
      return koerper;
    },
  };
}

/** Ein `holen`, das Antworten nach Reihenfolge ausgibt und die Aufrufe mitschreibt. */
function holenMit(...antworten) {
  const aufrufe = [];
  const fn = async (url, optionen = {}) => {
    aufrufe.push({ url, optionen });
    const naechste = antworten.shift();
    if (naechste === undefined) throw new Error(`unerwarteter Abruf: ${url}`);
    if (naechste instanceof Error) throw naechste;
    return naechste;
  };
  fn.aufrufe = aufrufe;
  return fn;
}

const BEARER_DOCKERHUB =
  'Bearer realm="https://auth.docker.io/token",service="registry.docker.io"';

const ABBILD = {
  name: 'postgres:16',
  zweck: 'Datenbank',
  art: 'abbild',
  bezugsstelle: 'postgres:16',
  fassung: '16',
  pruefung: 'bezug',
};

const ARCHIV = {
  name: 'age',
  zweck: 'Verschluesselung der Sicherung',
  art: 'archiv',
  bezugsstelle: 'https://example.invalid/age.tar.gz',
  fassung: '1.2.1',
  pruefung: 'bezug',
};

const UEBERSPRUNGEN = {
  name: 'quay.io/minio/minio',
  zweck: 'Rueckweg-Overlay',
  art: 'abbild',
  bezugsstelle: 'quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z',
  fassung: 'RELEASE.2025-09-07T16-13-09Z',
  pruefung: 'keine',
  grund: 'anonym nicht mehr beziehbar',
  ablaufversion: '2.13.0',
};

const EIGEN = {
  name: 'manban',
  zweck: 'das Abbild dieses Projekts',
  art: 'abbild',
  bezugsstelle: 'ghcr.io/manfredwolff/manban:2.14.0',
  fassung: '2.14.0',
  pruefung: 'eigen',
};

// --- Zerlegung der Bezugsstelle -------------------------------------------

test('abbildTeile ergaenzt Docker Hub und library fuer den amtlichen Namen', () => {
  assert.deepEqual(abbildTeile('postgres:16'), {
    registry: 'registry-1.docker.io',
    repository: 'library/postgres',
    tag: '16',
  });
});

test('abbildTeile laesst ein Docker-Hub-Abbild mit Organisation ungeschachtelt', () => {
  assert.deepEqual(abbildTeile('axllent/mailpit:v1.27'), {
    registry: 'registry-1.docker.io',
    repository: 'axllent/mailpit',
    tag: 'v1.27',
  });
});

test('abbildTeile erkennt eine fremde Bezugsstelle am Punkt im ersten Segment', () => {
  assert.deepEqual(abbildTeile('quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z'), {
    registry: 'quay.io',
    repository: 'minio/minio',
    tag: 'RELEASE.2025-09-07T16-13-09Z',
  });
});

test('abbildTeile setzt latest, wenn keine Fassung angegeben ist', () => {
  assert.equal(abbildTeile('caddy').tag, 'latest');
});

test('manifestUrl zeigt auf den Manifest-Endpunkt der Registry', () => {
  assert.equal(
    manifestUrl(abbildTeile('caddy:2')),
    'https://registry-1.docker.io/v2/library/caddy/manifests/2',
  );
});

// --- Token-Aushandlung -----------------------------------------------------

test('wwwAuthenticateLesen zerlegt realm und service', () => {
  assert.deepEqual(wwwAuthenticateLesen(BEARER_DOCKERHUB), {
    realm: 'https://auth.docker.io/token',
    service: 'registry.docker.io',
    scope: null,
  });
});

test('wwwAuthenticateLesen gibt null ohne Bearer-Angabe', () => {
  assert.equal(wwwAuthenticateLesen(null), null);
  assert.equal(wwwAuthenticateLesen('Basic realm="x"'), null);
});

test('tokenUrl setzt den Pull-Scope des Repositorys, auch ohne Angabe der Registry', () => {
  const url = tokenUrl(wwwAuthenticateLesen(BEARER_DOCKERHUB), abbildTeile('postgres:16'));
  assert.equal(
    url,
    'https://auth.docker.io/token?service=registry.docker.io&scope=repository%3Alibrary%2Fpostgres%3Apull',
  );
});

test('tokenAktionenLeer erkennt das Token ohne Pull-Recht', () => {
  assert.equal(tokenAktionenLeer({ token: 'x', access: [{ actions: [] }] }), true);
  assert.equal(tokenAktionenLeer({ token: 'x', access: [{ actions: ['pull'] }] }), false);
  // Docker Hub liefert gar kein access-Feld — das ist kein Befund.
  assert.equal(tokenAktionenLeer({ token: 'x' }), false);
});

// --- Fall 1: 200 ----------------------------------------------------------

test('quellePruefen meldet ok, wenn das Manifest ohne Token ausgeliefert wird', async () => {
  const holen = holenMit(antwort(200));
  const befund = await quellePruefen(ABBILD, { holen });
  assert.equal(befund.zustand, ZUSTAND_OK);
  assert.equal(befund.bezugsstelle, 'postgres:16');
  assert.equal(holen.aufrufe.length, 1);
});

test('quellePruefen meldet ok, wenn erst das anonyme Token das Manifest oeffnet', async () => {
  const holen = holenMit(
    antwort(401, { kopfzeilen: { 'www-authenticate': BEARER_DOCKERHUB } }),
    antwort(200, { koerper: { token: 'geheim', access: [{ actions: ['pull'] }] } }),
    antwort(200),
  );
  const befund = await quellePruefen(ABBILD, { holen });
  assert.equal(befund.zustand, ZUSTAND_OK);
  assert.equal(holen.aufrufe.length, 3);
  assert.equal(holen.aufrufe[2].optionen.headers.Authorization, 'Bearer geheim');
});

// --- Fall 2: 401 beim Manifest trotz Token --------------------------------

test('quellePruefen meldet Fehlschlag, wenn der Manifest-Abruf mit Token 401 bleibt', async () => {
  const holen = holenMit(
    antwort(401, { kopfzeilen: { 'www-authenticate': BEARER_DOCKERHUB } }),
    antwort(200, { koerper: { token: 'geheim' } }),
    antwort(401),
  );
  const befund = await quellePruefen(ABBILD, { holen });
  assert.equal(befund.zustand, ZUSTAND_FEHLSCHLAG);
  assert.match(befund.grund, /401/);
});

test('quellePruefen meldet Fehlschlag, wenn der 401 keine Bearer-Angabe traegt', async () => {
  const holen = holenMit(antwort(401));
  const befund = await quellePruefen(ABBILD, { holen });
  assert.equal(befund.zustand, ZUSTAND_FEHLSCHLAG);
  assert.match(befund.grund, /WWW-Authenticate/);
});

test('quellePruefen meldet Fehlschlag, wenn der Token-Abruf selbst scheitert', async () => {
  const holen = holenMit(
    antwort(401, { kopfzeilen: { 'www-authenticate': BEARER_DOCKERHUB } }),
    antwort(403),
  );
  const befund = await quellePruefen(ABBILD, { holen });
  assert.equal(befund.zustand, ZUSTAND_FEHLSCHLAG);
  assert.match(befund.grund, /Token-Abruf/);
});

// --- Fall 3: actions:[] ---------------------------------------------------

test('quellePruefen benennt das anonyme Token mit leerer Aktionsliste als eigenen Befund', async () => {
  const holen = holenMit(
    antwort(401, { kopfzeilen: { 'www-authenticate': BEARER_DOCKERHUB } }),
    antwort(200, { koerper: { token: 'x', access: [{ actions: [] }] } }),
  );
  const befund = await quellePruefen(UEBERSPRUNGEN, { holen, pruefenErzwingen: true });
  assert.equal(befund.zustand, ZUSTAND_FEHLSCHLAG);
  assert.match(befund.grund, /actions/);
  // Ohne Pull-Recht wird das Manifest nicht mehr abgerufen.
  assert.equal(holen.aufrufe.length, 2);
});

// --- Fall 4: 404 ----------------------------------------------------------

test('quellePruefen meldet Fehlschlag bei 404 auf das Manifest', async () => {
  const holen = holenMit(antwort(404));
  const befund = await quellePruefen(ABBILD, { holen });
  assert.equal(befund.zustand, ZUSTAND_FEHLSCHLAG);
  assert.match(befund.grund, /404/);
});

// --- Fall 5: Zeitueberschreitung -----------------------------------------

test('quellePruefen meldet die Zeitueberschreitung als Fehlschlag mit Baustein und Bezugsstelle', async () => {
  const holen = () => new Promise(() => {});
  const befund = await quellePruefen(ABBILD, { holen, fristMs: 20 });
  assert.equal(befund.zustand, ZUSTAND_FEHLSCHLAG);
  assert.equal(befund.name, 'postgres:16');
  assert.equal(befund.bezugsstelle, 'postgres:16');
  assert.match(befund.grund, /Zeitueberschreitung/);
});

test('quellePruefen meldet einen Netzfehler als Fehlschlag statt zu werfen', async () => {
  const holen = holenMit(new Error('getaddrinfo ENOTFOUND example.invalid'));
  const befund = await quellePruefen(ARCHIV, { holen });
  assert.equal(befund.zustand, ZUSTAND_FEHLSCHLAG);
  assert.match(befund.grund, /ENOTFOUND/);
});

// --- Archive -------------------------------------------------------------

test('quellePruefen fragt ein Archiv mit HEAD ab und meldet ok', async () => {
  const holen = holenMit(antwort(200));
  const befund = await quellePruefen(ARCHIV, { holen });
  assert.equal(befund.zustand, ZUSTAND_OK);
  assert.equal(holen.aufrufe[0].optionen.method, 'HEAD');
  assert.equal(holen.aufrufe[0].url, ARCHIV.bezugsstelle);
});

test('quellePruefen meldet Fehlschlag, wenn das Archiv mit 404 antwortet', async () => {
  const befund = await quellePruefen(ARCHIV, { holen: holenMit(antwort(404)) });
  assert.equal(befund.zustand, ZUSTAND_FEHLSCHLAG);
  assert.match(befund.grund, /404/);
});

// --- Fall 6: mehrere Fehlschlaege zugleich -------------------------------

test('laufen listet alle Befunde, nicht nur den ersten, und endet mit Exitcode 1', async () => {
  const holen = holenMit(antwort(404), antwort(200), antwort(500));
  const zeilen = [];
  const ergebnis = await laufen(
    [
      { ...ABBILD, name: 'eins' },
      { ...ABBILD, name: 'zwei' },
      { ...ARCHIV, name: 'drei' },
    ],
    { holen, schreiben: (zeile) => zeilen.push(zeile) },
  );
  assert.equal(ergebnis.exitcode, 1);
  assert.equal(ergebnis.befunde.length, 3);
  assert.deepEqual(
    ergebnis.befunde.filter((b) => b.zustand === ZUSTAND_FEHLSCHLAG).map((b) => b.name),
    ['eins', 'drei'],
  );
  assert.ok(zeilen.some((z) => z.includes('eins')));
  assert.ok(zeilen.some((z) => z.includes('drei')));
});

test('laufen endet mit Exitcode 0, wenn jede Quelle antwortet', async () => {
  const ergebnis = await laufen([ABBILD, ARCHIV], {
    holen: holenMit(antwort(200), antwort(200)),
    schreiben: () => {},
  });
  assert.equal(ergebnis.exitcode, 0);
});

// --- Fall 7: leere Liste -------------------------------------------------

test('laufen meldet die leere Liste ausdruecklich und endet mit 0', async () => {
  const zeilen = [];
  const ergebnis = await laufen([], { holen: holenMit(), schreiben: (z) => zeilen.push(z) });
  assert.equal(ergebnis.exitcode, 0);
  assert.deepEqual(ergebnis.befunde, []);
  assert.ok(zeilen.some((z) => /kein Baustein/i.test(z)));
});

// --- Fall 8: uebersprungener Eintrag ------------------------------------

test('laufen ueberspringt pruefung "keine" mit eigener Zeile und ohne Netzabruf', async () => {
  const zeilen = [];
  const holen = holenMit(antwort(200));
  const ergebnis = await laufen([UEBERSPRUNGEN, ABBILD], {
    holen,
    schreiben: (z) => zeilen.push(z),
  });
  assert.equal(ergebnis.exitcode, 0);
  assert.equal(ergebnis.befunde[0].zustand, ZUSTAND_UEBERSPRUNGEN);
  assert.equal(holen.aufrufe.length, 1, 'nur das geprueffte Abbild wird abgerufen');
  const zeile = zeilen.find((z) => z.includes('minio'));
  assert.match(zeile, /uebersprungen/);
  assert.match(zeile, /anonym nicht mehr beziehbar/);
  assert.match(zeile, /2\.13\.0/);
});

// --- Gesamtfrist ---------------------------------------------------------

test('laufen meldet die restlichen Bausteine als Fehlschlag, wenn die Gesamtfrist reisst', async () => {
  let uhr = 0;
  const ergebnis = await laufen([ABBILD, { ...ABBILD, name: 'zwei' }], {
    holen: holenMit(antwort(200)),
    schreiben: () => {},
    gesamtFristMs: 10,
    jetzt: () => (uhr += 100),
  });
  assert.equal(ergebnis.exitcode, 1);
  const letzter = ergebnis.befunde.at(-1);
  assert.equal(letzter.zustand, ZUSTAND_FEHLSCHLAG);
  assert.match(letzter.grund, /Gesamtfrist/);
  assert.equal(letzter.name, 'zwei');
});

// --- Ausgabeform --------------------------------------------------------

test('befundZeile nennt Zustand, Baustein, Bezugsstelle und Frist', () => {
  const zeile = befundZeile({
    name: 'postgres:16',
    bezugsstelle: 'postgres:16',
    zustand: ZUSTAND_FEHLSCHLAG,
    grund: 'Manifest-Abruf antwortete 404',
    fristMs: 20000,
  });
  assert.match(zeile, /fehlschlag/);
  assert.match(zeile, /postgres:16/);
  assert.match(zeile, /20 s/);
  assert.match(zeile, /404/);
});

test('die Fristen stehen als Vorgabe im Skript', () => {
  assert.equal(FRIST_QUELLE_MS, 20_000);
  assert.ok(FRIST_GESAMT_MS > FRIST_QUELLE_MS);
  assert.deepEqual(ARTEN, ['abbild', 'archiv']);
  assert.deepEqual(PRUEFARTEN, ['bezug', 'keine', 'eigen']);
});

// --- Liste: Schema -------------------------------------------------------

test('bausteineLesen nimmt die Liste des Projekts an', () => {
  const bausteine = bausteineLesen(join(HIER, 'bausteine.json'));
  assert.ok(bausteine.length >= 12);
  for (const baustein of bausteine) {
    assert.ok(baustein.zweck, `${baustein.name} ohne Zweck`);
    assert.ok(ARTEN.includes(baustein.art), `${baustein.name} mit unbekannter Art`);
    assert.ok(baustein.bezugsstelle, `${baustein.name} ohne Bezugsstelle`);
    assert.ok(baustein.fassung, `${baustein.name} ohne Fassung`);
    assert.ok(PRUEFARTEN.includes(baustein.pruefung), `${baustein.name} mit unbekannter Pruefart`);
    if (baustein.pruefung === 'keine') {
      assert.ok(baustein.grund, `${baustein.name} ohne Grund`);
      assert.ok(baustein.ablaufversion, `${baustein.name} ohne Ablaufversion`);
    }
  }
});

test('bausteineLesen weist einen Eintrag ohne Grund zur uebersprungenen Pruefung ab', () => {
  const verzeichnis = mkdtempSync(join(tmpdir(), 'bausteine-'));
  const pfad = join(verzeichnis, 'bausteine.json');
  writeFileSync(
    pfad,
    JSON.stringify({ bausteine: [{ ...UEBERSPRUNGEN, grund: undefined, ablaufversion: undefined }] }),
  );
  assert.throws(() => bausteineLesen(pfad), /Grund/);
  rmSync(verzeichnis, { recursive: true, force: true });
});

// --- Abgleich gegen den Bestand -----------------------------------------

test('abbilderAusCompose liest jede image-Zeile', () => {
  const abbilder = abbilderAusCompose(
    ['services:', '  a:', '    image: postgres:16', '  b:', '    image: caddy:2'].join('\n'),
  );
  assert.deepEqual([...abbilder].sort(), ['caddy:2', 'postgres:16']);
});

test('abbilderAusDockerfile liest FROM und laesst eigene Baustufen aus', () => {
  const abbilder = abbilderAusDockerfile(
    ['FROM debian:bookworm-slim AS werkzeuge', 'FROM werkzeuge AS zwei', 'FROM postgres:16-bookworm'].join(
      '\n',
    ),
  );
  assert.deepEqual([...abbilder].sort(), ['debian:bookworm-slim', 'postgres:16-bookworm']);
});

test('abbilderAusJava liest alle drei Testcontainers-Muster', () => {
  const abbilder = abbilderAusJava(
    [
      'new PostgreSQLContainer<>("postgres:16").withCommand("postgres", "-c", "max_connections=200");',
      'new GenericContainer<>("chrislusf/seaweedfs:4.47")',
      'new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))',
    ].join('\n'),
  );
  assert.deepEqual(
    [...abbilder].sort(),
    ['axllent/mailpit:v1.27', 'chrislusf/seaweedfs:4.47', 'postgres:16'],
  );
});

test('der Bestand des Projekts und die Liste deckten sich in beide Richtungen', () => {
  const bausteine = bausteineLesen(join(HIER, 'bausteine.json'));
  const { fehlen, ueberzaehlig } = abgleich(bausteine, bestandAbbilder(WURZEL));
  assert.deepEqual(fehlen, [], 'Abbild im Bestand ohne Eintrag in scripts/bausteine.json');
  assert.deepEqual(ueberzaehlig, [], 'Eintrag in scripts/bausteine.json ohne Fundstelle im Bestand');
});

test('bestandAbbilder findet das Abbild des Speichers und das der IT-Suite', () => {
  const bestand = bestandAbbilder(WURZEL);
  assert.ok(bestand.has('axllent/mailpit:v1.27'));
  assert.ok(bestand.has('quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z'));
});

test('abgleich schlaegt bei einer Abbild-Referenz ohne Eintrag an — auch aus einer Java-Datei', () => {
  const verzeichnis = mkdtempSync(join(tmpdir(), 'bestand-'));
  mkdirSync(join(verzeichnis, 'src', 'test', 'java', 'tief'), { recursive: true });
  writeFileSync(join(verzeichnis, 'docker-compose.yml'), 'services:\n  a:\n    image: postgres:16\n');
  writeFileSync(
    join(verzeichnis, 'src', 'test', 'java', 'tief', 'NeuIT.java'),
    'new GenericContainer<>(DockerImageName.parse("redis:7"));\n',
  );
  const bestand = bestandAbbilder(verzeichnis);
  assert.deepEqual([...bestand].sort(), ['postgres:16', 'redis:7']);
  const { fehlen, ueberzaehlig } = abgleich([ABBILD], bestand);
  assert.deepEqual(fehlen, ['redis:7']);
  assert.deepEqual(ueberzaehlig, []);
});

test('abgleich schlaegt bei einem Eintrag ohne Fundstelle an und laesst Archive aussen vor', () => {
  const bestand = new Set(['postgres:16']);
  const { fehlen, ueberzaehlig } = abgleich(
    [ABBILD, ARCHIV, { ...ABBILD, name: 'weg', bezugsstelle: 'weg:1' }],
    bestand,
  );
  assert.deepEqual(fehlen, []);
  assert.deepEqual(ueberzaehlig, ['weg:1']);
});

test('quellePruefen haengt die Ursache eines fetch-Fehlers an die Meldung', async () => {
  const fehler = new Error('fetch failed');
  fehler.cause = new Error('getaddrinfo ENOTFOUND registry-1.docker.io');
  const befund = await quellePruefen(ABBILD, { holen: holenMit(fehler) });
  assert.equal(befund.zustand, ZUSTAND_FEHLSCHLAG);
  assert.equal(befund.grund, 'fetch failed: getaddrinfo ENOTFOUND registry-1.docker.io');
});

// --- Pruefart eigen ------------------------------------------------------

test('bausteineLesen nimmt einen eigen-Eintrag ohne Grund und ohne Ablaufversion an', () => {
  const verzeichnis = mkdtempSync(join(tmpdir(), 'bausteine-eigen-'));
  const pfad = join(verzeichnis, 'bausteine.json');
  writeFileSync(pfad, JSON.stringify({ bausteine: [EIGEN] }));
  const bausteine = bausteineLesen(pfad);
  assert.equal(bausteine.length, 1);
  assert.equal(bausteine[0].pruefung, 'eigen');
  rmSync(verzeichnis, { recursive: true, force: true });
});

test('quellePruefen ueberspringt einen eigen-Eintrag ohne Verlangen und ruft nichts ab', async () => {
  const holen = holenMit();
  const befund = await quellePruefen(EIGEN, { holen });
  assert.equal(befund.zustand, ZUSTAND_UEBERSPRUNGEN);
  assert.equal(befund.grund, GRUND_EIGEN);
  assert.equal(befund.ablaufversion, null);
  assert.equal(holen.aufrufe.length, 0);
});

test('GRUND_EIGEN sagt, dass das Abbild aus diesem Projekt stammt und erst veroeffentlicht werden muss', () => {
  assert.match(GRUND_EIGEN, /diesem Projekt/);
  assert.match(GRUND_EIGEN, /Veroeffentlichung/);
});

test('befundZeile nennt beim eigen-Eintrag die Begruendung ohne Ablaufversion', () => {
  const zeile = befundZeile({
    name: EIGEN.name,
    bezugsstelle: EIGEN.bezugsstelle,
    zustand: ZUSTAND_UEBERSPRUNGEN,
    grund: GRUND_EIGEN,
    ablaufversion: null,
    fristMs: FRIST_QUELLE_MS,
  });
  assert.match(zeile, /uebersprungen/);
  assert.match(zeile, /diesem Projekt/);
  assert.ok(!/entfaellt mit/.test(zeile), 'ohne Ablaufversion steht kein Ablauf in der Zeile');
});

test('quellePruefen ruft einen eigen-Eintrag mit Verlangen normal ab', async () => {
  const holen = holenMit(antwort(200));
  const befund = await quellePruefen(EIGEN, { holen, eigenePruefen: true });
  assert.equal(befund.zustand, ZUSTAND_OK);
  assert.equal(holen.aufrufe.length, 1);
  assert.equal(holen.aufrufe[0].url, 'https://ghcr.io/v2/manfredwolff/manban/manifests/2.14.0');
});

test('quellePruefen laesst einen eigen-Eintrag auch per pruefenErzwingen abrufen', async () => {
  const holen = holenMit(antwort(200));
  const befund = await quellePruefen(EIGEN, { holen, pruefenErzwingen: true });
  assert.equal(befund.zustand, ZUSTAND_OK);
  assert.equal(holen.aufrufe.length, 1);
});

test('laufen ueberspringt eigen-Eintraege ohne Verlangen und endet mit 0', async () => {
  const zeilen = [];
  const holen = holenMit(antwort(200));
  const ergebnis = await laufen([EIGEN, ABBILD], { holen, schreiben: (z) => zeilen.push(z) });
  assert.equal(ergebnis.exitcode, 0);
  assert.equal(ergebnis.befunde[0].zustand, ZUSTAND_UEBERSPRUNGEN);
  assert.equal(holen.aufrufe.length, 1, 'nur das fremde Abbild wird abgerufen');
  assert.ok(zeilen.some((z) => z.includes('manban') && /uebersprungen/.test(z)));
});

test('laufen reicht das Verlangen durch und ruft den eigen-Eintrag ab', async () => {
  const holen = holenMit(antwort(200), antwort(200));
  const ergebnis = await laufen([EIGEN, ABBILD], {
    holen,
    schreiben: () => {},
    eigenePruefen: true,
  });
  assert.equal(ergebnis.exitcode, 0);
  assert.equal(ergebnis.befunde[0].zustand, ZUSTAND_OK);
  assert.equal(holen.aufrufe.length, 2);
});

test('laufen setzt den Exitcode, wenn der erzwungene Abruf des eigen-Eintrags scheitert', async () => {
  const zeilen = [];
  const ergebnis = await laufen([EIGEN], {
    holen: holenMit(antwort(404)),
    schreiben: (z) => zeilen.push(z),
    eigenePruefen: true,
  });
  assert.equal(ergebnis.exitcode, 1);
  assert.equal(ergebnis.befunde[0].zustand, ZUSTAND_FEHLSCHLAG);
  assert.ok(zeilen.some((z) => /FEHLSCHLAG/.test(z) && z.includes('manban')));
});

// --- Argument-Zerlegung des Einsprungs ----------------------------------

test('argumenteZerlegen erkennt --eigen ohne Pfad', () => {
  assert.deepEqual(argumenteZerlegen(['--eigen']), { pfad: null, eigenePruefen: true });
});

test('argumenteZerlegen erkennt einen Pfad ohne Flag', () => {
  assert.deepEqual(argumenteZerlegen(['scripts/bausteine.json']), {
    pfad: 'scripts/bausteine.json',
    eigenePruefen: false,
  });
});

test('argumenteZerlegen erkennt beides in beiden Reihenfolgen', () => {
  const erwartet = { pfad: 'scripts/bausteine.json', eigenePruefen: true };
  assert.deepEqual(argumenteZerlegen(['--eigen', 'scripts/bausteine.json']), erwartet);
  assert.deepEqual(argumenteZerlegen(['scripts/bausteine.json', '--eigen']), erwartet);
});

test('argumenteZerlegen erkennt keines von beidem', () => {
  assert.deepEqual(argumenteZerlegen([]), { pfad: null, eigenePruefen: false });
});

test('argumenteZerlegen nimmt nur das erste Argument ohne Bindestrich als Pfad', () => {
  assert.deepEqual(argumenteZerlegen(['eins.json', 'zwei.json']), {
    pfad: 'eins.json',
    eigenePruefen: false,
  });
});
