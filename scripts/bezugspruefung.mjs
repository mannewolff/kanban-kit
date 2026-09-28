#!/usr/bin/env node
/**
 * bezugspruefung.mjs — Nachweis, dass jede fremde Bezugsquelle des Projekts anonym beziehbar ist
 * (Issue #1229, Plan #1222, fachliche Quelle #1221).
 *
 * Nutzung:
 *   node scripts/bezugspruefung.mjs [pfad/zu/bausteine.json]
 *   node scripts/bezugspruefung.mjs --eigen [pfad/zu/bausteine.json]
 *
 * Warum die eigenen Abbilder nur auf Verlangen abgerufen werden (Pruefart `eigen`): Der Job `bezug`
 * in .github/workflows/ci.yml laeuft bei JEDEM Push auf `main` und JEDEM Pull Request, das eigene
 * Abbild entsteht aber erst Minuten nach dem Release-Commit — ohne diese Pruefart waere jeder
 * Release-Lauf und der PR `main -> production` rot, ohne dass etwas fehlt.
 *
 * Warum HTTP und nicht Docker (E10): `docker pull` beantwortet die Frage falsch, weil ein
 * vorhandener lokaler Vorrat sie beantwortet, statt die Bezugsstelle zu fragen. Genau daran fiel
 * die Sperre von `quay.io/minio/minio` monatelang nicht auf. Dieses Skript fragt je Abbild ein
 * ANONYMES Pull-Token an und ruft damit das Manifest ab — der Vorrat des Rechners ist strukturell
 * nicht beteiligt. Fuer Archive genuegt ein `HEAD`.
 *
 * Warum jede Frist ein Befund ist und kein Abbruch: Ein Lauf, der ohne Ausgabe in einer
 * Zeitueberschreitung endet, sagt nicht, WELCHE Quelle haengt. Darum traegt jede Quelle eine
 * eigene Frist, der ganze Lauf eine Gesamtfrist, und jede gerissene Frist erscheint als Zeile mit
 * Baustein und Bezugsstelle. Am Ende stehen ALLE Befunde, nicht nur der erste.
 *
 * Das Skript ist bewusst kein Pflichtcheck (E12): Es haengt am Netz und machte jede lokale
 * Pruefung offline unbestehbar. Es laeuft als eigener CI-Job `bezug` und woechentlich ueber
 * .github/workflows/bezug.yml — ein weggefallener Bezug ist dann ein eigener, benannter Fehler und
 * nicht ein roter Code-Job, den irgendwann niemand mehr liest.
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ARTEN = ['abbild', 'archiv'];
export const PRUEFARTEN = ['bezug', 'keine', 'eigen'];

/**
 * Die feste Begruendung der Pruefart `eigen`. Sie traegt KEINE `ablaufversion`: Anders als bei
 * `keine` ist das keine Ausnahme auf Zeit, sondern die dauerhaft richtige Behandlung eines Abbilds,
 * das dieses Projekt selbst erst herstellt.
 */
export const GRUND_EIGEN =
  'Abbild aus diesem Projekt — anonym erst nach seiner Veroeffentlichung abrufbar, Abruf nur mit --eigen';

export const ZUSTAND_OK = 'ok';
export const ZUSTAND_FEHLSCHLAG = 'fehlschlag';
export const ZUSTAND_UEBERSPRUNGEN = 'uebersprungen';

/** Frist je Quelle (E10). */
export const FRIST_QUELLE_MS = 20_000;
/**
 * Gesamtfrist des Laufs. Grosszuegiger als "Anzahl Quellen x Einzelfrist" muss sie nicht sein: Sie
 * fasst den Fall, dass viele Quellen gleichzeitig langsam antworten, ohne einzeln die Frist zu
 * reissen.
 */
export const FRIST_GESAMT_MS = 300_000;

const DOCKER_HUB_REGISTRY = 'registry-1.docker.io';

/**
 * Die Medientypen, die ein Manifest haben kann. Ohne diesen Accept-Header antwortet eine Registry
 * fuer ein Multi-Arch-Abbild mit 404 statt mit dem Index — die Pruefung meldete dann einen
 * Wegfall, den es nicht gibt.
 */
const MANIFEST_TYPEN = [
  'application/vnd.oci.image.index.v1+json',
  'application/vnd.oci.image.manifest.v1+json',
  'application/vnd.docker.distribution.manifest.list.v2+json',
  'application/vnd.docker.distribution.manifest.v2+json',
].join(', ');

// --- Liste ----------------------------------------------------------------

/**
 * Liest die Bausteinliste und prueft ihr Schema. Die Pruefung gehoert hierher und nicht in den
 * Test: Ein unvollstaendiger Eintrag soll den LAUF anhalten, nicht stillschweigend als "ok"
 * durchgehen.
 */
export function bausteineLesen(pfad) {
  const rohdaten = JSON.parse(readFileSync(pfad, 'utf-8'));
  const bausteine = rohdaten.bausteine ?? [];
  for (const baustein of bausteine) {
    const wer = baustein.name ?? JSON.stringify(baustein);
    if (!baustein.name) throw new Error(`Baustein ohne Name: ${wer}`);
    if (!baustein.zweck) throw new Error(`Baustein ${wer} ohne Zweck`);
    if (!ARTEN.includes(baustein.art)) throw new Error(`Baustein ${wer} mit unbekannter Art`);
    if (!baustein.bezugsstelle) throw new Error(`Baustein ${wer} ohne Bezugsstelle`);
    if (!baustein.fassung) throw new Error(`Baustein ${wer} ohne Fassung`);
    if (!PRUEFARTEN.includes(baustein.pruefung)) {
      throw new Error(`Baustein ${wer} mit unbekannter Pruefart`);
    }
    if (baustein.pruefung === 'keine') {
      if (!baustein.grund) throw new Error(`Baustein ${wer} ohne Grund zur uebersprungenen Pruefung`);
      if (!baustein.ablaufversion) throw new Error(`Baustein ${wer} ohne Ablaufversion`);
    }
  }
  return bausteine;
}

// --- Bezugsstelle zerlegen ------------------------------------------------

/**
 * Zerlegt eine Abbild-Referenz in Registry, Repository und Fassung — nach derselben Regel, die
 * Docker selbst anwendet: Ein erstes Segment mit Punkt oder Doppelpunkt ist eine Registry, sonst
 * ist es Docker Hub; ein einsegmentiger Name dort liegt unter `library/`.
 */
export function abbildTeile(bezugsstelle) {
  const segmente = bezugsstelle.split('/');
  const fremd = segmente.length > 1 && /[.:]/.test(segmente[0]);
  const registry = fremd ? segmente.shift() : DOCKER_HUB_REGISTRY;
  const rest = segmente.join('/');
  const trenner = rest.lastIndexOf(':');
  const name = trenner === -1 ? rest : rest.slice(0, trenner);
  const tag = trenner === -1 ? 'latest' : rest.slice(trenner + 1);
  const repository = registry === DOCKER_HUB_REGISTRY && !name.includes('/') ? `library/${name}` : name;
  return { registry, repository, tag };
}

export function manifestUrl({ registry, repository, tag }) {
  return `https://${registry}/v2/${repository}/manifests/${tag}`;
}

/** Zerlegt den WWW-Authenticate-Kopf des 401, mit dem eine Registry ihren Token-Dienst nennt. */
export function wwwAuthenticateLesen(wert) {
  if (!wert || !/^Bearer /i.test(wert)) return null;
  const feld = (name) => wert.match(new RegExp(`${name}="([^"]*)"`))?.[1] ?? null;
  const realm = feld('realm');
  if (!realm) return null;
  return { realm, service: feld('service'), scope: feld('scope') };
}

/**
 * Baut die Token-URL. Der Scope kommt aus dem Repository und nicht aus der Angabe der Registry:
 * quay.io nennt im 401 gar keinen, und ohne Scope gaebe es ein Token ohne jedes Recht — der
 * folgende 401 wuerde dann als "Abbild weg" gelesen, obwohl nur der Scope fehlte.
 */
export function tokenUrl(angabe, { repository }) {
  const url = new URL(angabe.realm);
  if (angabe.service) url.searchParams.set('service', angabe.service);
  url.searchParams.set('scope', `repository:${repository}:pull`);
  return url.toString();
}

/**
 * Der Befund, mit dem die Sperre am 2026-09-26 belegt wurde: Das Token kommt mit 200, traegt aber
 * eine leere Aktionsliste — anonym gibt es kein Pull-Recht. Docker Hub liefert gar kein
 * `access`-Feld; das ist kein Befund, sondern der Normalfall.
 */
export function tokenAktionenLeer(koerper) {
  const zugang = koerper?.access;
  if (!Array.isArray(zugang) || zugang.length === 0) return false;
  return zugang.every((eintrag) => (eintrag?.actions ?? []).length === 0);
}

// --- Pruefung je Quelle ---------------------------------------------------

function befund(baustein, zustand, grund, fristMs) {
  return {
    name: baustein.name,
    bezugsstelle: baustein.bezugsstelle,
    zustand,
    grund,
    ablaufversion: baustein.ablaufversion ?? null,
    fristMs,
  };
}

/** Legt eine Frist um ein Versprechen. Der Timer wird immer abgeraeumt, sonst haengt der Prozess. */
async function mitFrist(versprechen, fristMs) {
  let uhr;
  try {
    return await Promise.race([
      versprechen,
      new Promise((_, ablehnen) => {
        uhr = setTimeout(() => ablehnen(new Error(`Zeitueberschreitung nach ${sekunden(fristMs)}`)), fristMs);
      }),
    ]);
  } finally {
    clearTimeout(uhr);
  }
}

async function abbildAbrufen(baustein, holen) {
  const teile = abbildTeile(baustein.bezugsstelle);
  const url = manifestUrl(teile);
  const kopfzeilen = { Accept: MANIFEST_TYPEN };
  const erste = await holen(url, { method: 'GET', headers: kopfzeilen });
  if (erste.ok) return null;
  if (erste.status !== 401) return `Manifest-Abruf antwortete ${erste.status}`;

  const angabe = wwwAuthenticateLesen(erste.headers.get('www-authenticate'));
  if (!angabe) return '401 ohne verwertbaren WWW-Authenticate-Kopf';

  const tokenAntwort = await holen(tokenUrl(angabe, teile), { method: 'GET' });
  if (!tokenAntwort.ok) return `Token-Abruf antwortete ${tokenAntwort.status}`;
  const koerper = await tokenAntwort.json();
  if (tokenAktionenLeer(koerper)) {
    return 'anonymes Pull-Token ohne Pull-Recht (actions: [])';
  }

  const zweite = await holen(url, {
    method: 'GET',
    headers: { ...kopfzeilen, Authorization: `Bearer ${koerper.token ?? koerper.access_token}` },
  });
  if (zweite.ok) return null;
  return `Manifest-Abruf mit anonymem Token antwortete ${zweite.status}`;
}

/**
 * `fetch` meldet jeden Netzfehler als "fetch failed" und haengt den wahren Grund an `cause`. Ohne
 * die Ursache saehe ein Lauf gegen einen DNS-Fehler genauso aus wie einer gegen ein abgelaufenes
 * Zertifikat — und der CI-Job `bezug` waere wieder ein Rot, das nichts sagt.
 */
function fehlerText(fehler) {
  const ursache = fehler.cause?.message;
  return ursache ? `${fehler.message}: ${ursache}` : fehler.message;
}

async function archivAbrufen(baustein, holen) {
  const antwort = await holen(baustein.bezugsstelle, { method: 'HEAD' });
  return antwort.ok ? null : `HEAD antwortete ${antwort.status}`;
}

/**
 * Prueft genau eine Quelle. `pruefenErzwingen` ist der Weg der Gegenprobe: Damit laesst sich ein
 * Eintrag mit `pruefung: "keine"` oder `"eigen"` einzeln doch abrufen, ohne die Liste zu aendern.
 * `eigenePruefen` ist das Verlangen des Aufrufs `--eigen` und gilt nur fuer die Pruefart `eigen`.
 */
export async function quellePruefen(
  baustein,
  { holen, fristMs = FRIST_QUELLE_MS, pruefenErzwingen = false, eigenePruefen = false } = {},
) {
  if (baustein.pruefung === 'keine' && !pruefenErzwingen) {
    return befund(baustein, ZUSTAND_UEBERSPRUNGEN, baustein.grund, fristMs);
  }
  if (baustein.pruefung === 'eigen' && !eigenePruefen && !pruefenErzwingen) {
    return befund(baustein, ZUSTAND_UEBERSPRUNGEN, GRUND_EIGEN, fristMs);
  }
  try {
    const abrufen = baustein.art === 'archiv' ? archivAbrufen : abbildAbrufen;
    const grund = await mitFrist(abrufen(baustein, holen), fristMs);
    return grund
      ? befund(baustein, ZUSTAND_FEHLSCHLAG, grund, fristMs)
      : befund(baustein, ZUSTAND_OK, null, fristMs);
  } catch (fehler) {
    return befund(baustein, ZUSTAND_FEHLSCHLAG, fehlerText(fehler), fristMs);
  }
}

// --- Ausgabe --------------------------------------------------------------

function sekunden(ms) {
  return `${Math.round(ms / 1000)} s`;
}

export function befundZeile(befundDaten) {
  const { name, bezugsstelle, zustand, grund, ablaufversion, fristMs } = befundDaten;
  const kopf = `${zustand.padEnd(13)} ${name} — ${bezugsstelle}`;
  if (zustand === ZUSTAND_OK) return `${kopf} (Frist ${sekunden(fristMs)})`;
  if (zustand === ZUSTAND_UEBERSPRUNGEN) {
    // Die Ablaufversion gehoert in die Zeile: Sonst steht die Ausnahme still, bis sie jemand in
    // der Liste nachliest — und genau das tut niemand. Die Pruefart `eigen` traegt keine, weil sie
    // nicht ablaeuft; dort bliebe sonst ein "entfaellt mit null" stehen.
    return ablaufversion ? `${kopf}: ${grund} (entfaellt mit ${ablaufversion})` : `${kopf}: ${grund}`;
  }
  return `${kopf} (Frist ${sekunden(fristMs)}): ${grund}`;
}

// --- Lauf -----------------------------------------------------------------

/**
 * Faehrt die Liste sequenziell ab. Sequenziell und nicht parallel, damit die Ausgabe in der
 * Reihenfolge der Liste steht und eine haengende Quelle nicht die Fristen der anderen mitzieht.
 */
export async function laufen(bausteine, {
  holen,
  schreiben = console.log,
  fristMs = FRIST_QUELLE_MS,
  gesamtFristMs = FRIST_GESAMT_MS,
  jetzt = () => Date.now(),
  eigenePruefen = false,
} = {}) {
  const beginn = jetzt();
  const befunde = [];
  if (bausteine.length === 0) {
    schreiben('kein Baustein in der Liste — nichts zu pruefen');
    return { befunde, exitcode: 0 };
  }
  for (const baustein of bausteine) {
    const verbraucht = jetzt() - beginn;
    const naechster =
      verbraucht > gesamtFristMs
        ? befund(
            baustein,
            ZUSTAND_FEHLSCHLAG,
            `Gesamtfrist von ${sekunden(gesamtFristMs)} ueberschritten — nicht mehr geprueft`,
            fristMs,
          )
        : await quellePruefen(baustein, { holen, fristMs, eigenePruefen });
    befunde.push(naechster);
    schreiben(befundZeile(naechster));
  }
  const fehlschlaege = befunde.filter((b) => b.zustand === ZUSTAND_FEHLSCHLAG);
  const uebersprungen = befunde.filter((b) => b.zustand === ZUSTAND_UEBERSPRUNGEN);
  schreiben('');
  schreiben(
    `${befunde.length} Bausteine: ${befunde.length - fehlschlaege.length - uebersprungen.length} ok, ` +
      `${fehlschlaege.length} Fehlschlaege, ${uebersprungen.length} uebersprungen`,
  );
  for (const fehlschlag of fehlschlaege) {
    schreiben(`FEHLSCHLAG ${fehlschlag.name} — ${fehlschlag.bezugsstelle}: ${fehlschlag.grund}`);
  }
  return { befunde, exitcode: fehlschlaege.length === 0 ? 0 : 1 };
}

// --- Abgleich gegen den Bestand (E9) -------------------------------------

export function abbilderAusCompose(text) {
  const abbilder = new Set();
  for (const treffer of text.matchAll(/^\s*image:\s*(\S+)/gm)) abbilder.add(treffer[1]);
  return abbilder;
}

/**
 * FROM-Zeilen ohne die eigenen Baustufen: `FROM werkzeuge AS zwei` bezieht nichts von aussen und
 * gehoerte sonst als Phantom-Baustein in die Liste.
 */
export function abbilderAusDockerfile(text) {
  const stufen = new Set(
    [...text.matchAll(/^FROM\s+\S+\s+AS\s+(\S+)/gim)].map((t) => t[1].toLowerCase()),
  );
  const abbilder = new Set();
  for (const treffer of text.matchAll(/^FROM\s+(\S+)/gim)) {
    const wert = treffer[1];
    if (!stufen.has(wert.toLowerCase())) abbilder.add(wert);
  }
  return abbilder;
}

/** Die drei Testcontainers-Muster aus E9. */
export function abbilderAusJava(text) {
  const abbilder = new Set();
  const muster =
    /(?:DockerImageName\.parse|new PostgreSQLContainer<>|new GenericContainer<>)\(\s*"([^"]+)"/g;
  for (const treffer of text.matchAll(muster)) abbilder.add(treffer[1]);
  return abbilder;
}

function javaDateien(verzeichnis) {
  if (!existsSync(verzeichnis)) return [];
  const gefunden = [];
  for (const eintrag of readdirSync(verzeichnis)) {
    const pfad = join(verzeichnis, eintrag);
    if (statSync(pfad).isDirectory()) gefunden.push(...javaDateien(pfad));
    else if (eintrag.endsWith('.java')) gefunden.push(pfad);
  }
  return gefunden;
}

/** Sammelt jede Abbild-Referenz des Bestands — die eine Seite des Abgleichs. */
export function bestandAbbilder(wurzel) {
  const bestand = new Set();
  const aufnehmen = (menge) => {
    for (const abbild of menge) bestand.add(abbild);
  };
  for (const datei of readdirSync(wurzel)) {
    if (/^docker-compose.*\.ya?ml$/.test(datei)) {
      aufnehmen(abbilderAusCompose(readFileSync(join(wurzel, datei), 'utf-8')));
    }
  }
  for (const datei of ['Dockerfile', join('backup', 'Dockerfile')]) {
    const pfad = join(wurzel, datei);
    if (existsSync(pfad)) aufnehmen(abbilderAusDockerfile(readFileSync(pfad, 'utf-8')));
  }
  for (const datei of javaDateien(join(wurzel, 'src', 'test'))) {
    aufnehmen(abbilderAusJava(readFileSync(datei, 'utf-8')));
  }
  return bestand;
}

/**
 * Haelt Liste und Bestand gegeneinander — in BEIDE Richtungen: `fehlen` sind Abbilder des Bestands
 * ohne Eintrag (die Liste altert sonst still), `ueberzaehlig` sind Eintraege ohne Fundstelle (die
 * Pruefung faehre sonst bald etwas, das niemand mehr benutzt). Archive bleiben aussen vor: Sie
 * stehen als URL in einem RUN-Schritt und nicht als Abbild-Referenz.
 */
export function abgleich(bausteine, bestand) {
  const eingetragen = new Set(
    bausteine.filter((b) => b.art === 'abbild').map((b) => b.bezugsstelle),
  );
  const fehlen = [...bestand].filter((abbild) => !eingetragen.has(abbild)).sort();
  const ueberzaehlig = [...eingetragen].filter((abbild) => !bestand.has(abbild)).sort();
  return { fehlen, ueberzaehlig };
}

// --- Einsprung ------------------------------------------------------------

const HIER = dirname(fileURLToPath(import.meta.url));

/**
 * Trennt Flags vom optionalen Pfad zur Bausteinliste. Eigene Funktion und exportiert, weil der
 * Einsprung selbst im `c8 ignore`-Block liegt und die Zerlegung sonst ungeprueft bliebe: Ohne sie
 * landete ein `--eigen` als Pfad in `readFileSync`.
 */
export function argumenteZerlegen(argumente) {
  return {
    pfad: argumente.find((wert) => !wert.startsWith('-')) ?? null,
    eigenePruefen: argumente.includes('--eigen'),
  };
}

/* c8 ignore start — der Einsprung laeuft nur als Kommando, nie im Test. */
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const { pfad, eigenePruefen } = argumenteZerlegen(process.argv.slice(2));
  const listenPfad = pfad ?? join(HIER, 'bausteine.json');
  const { exitcode } = await laufen(bausteineLesen(listenPfad), { holen: fetch, eigenePruefen });
  process.exitCode = exitcode;
}
/* c8 ignore stop */
