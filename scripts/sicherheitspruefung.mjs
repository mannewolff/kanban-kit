#!/usr/bin/env node
/**
 * sicherheitspruefung.mjs — Urteil ueber bekannte Schwachstellen und eingecheckte Geheimnisse
 * (Issue #1333, Plan #1295, fachliche Quelle #676).
 *
 * Nutzung: siehe HILFE unten oder `node scripts/sicherheitspruefung.mjs --hilfe`.
 *
 * Warum ein eigener Treiber und nicht die Rueckgabewerte von Trivy (E2): "schwer mit Korrektur
 * sperrt, schwer ohne Korrektur ist nur sichtbar" braeuchte sonst zwei Laeufe je Ziel, und keine
 * Regel waere ohne Netz testbar. Trivy findet und liefert JSON mit Rueckgabewert 0; dieses Skript
 * urteilt, schreibt die Zusammenfassung und setzt als einziges den Rueckgabewert.
 *
 * Warum die Ausnahmeliste eine eigene ist und nicht `.trivyignore.yaml` (E3): Geheimnisse duerfen
 * nur als nachweislicher Fehlalarm ausgenommen werden, ohne Ablauf; Schwachstellen nur mit
 * Begruendung UND Ablauf. Beides prueft nur eine eigene Liste.
 *
 * Warum jeder Fehlerpfad sperrt: Ein Ziel, das nicht geprueft wurde — Frist gerissen, Werkzeug
 * fehlt, Ausgabe unlesbar —, ist ein benannter Befund "Ziel nicht geprueft". Eine unvollstaendige
 * Pruefung, die gruen meldet, behauptete Sicherheit.
 */

import { appendFileSync, readFileSync } from 'node:fs';
import { spawn } from 'node:child_process';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { abbildTeile, bausteineLesen, tokenUrl, wwwAuthenticateLesen } from './bezugspruefung.mjs';

/** Schwer heisst: die beiden hoechsten Schweregrade (PO-Entscheidung in #676). */
export const SCHWERE = ['CRITICAL', 'HIGH'];

/**
 * Frist je Ziel. Grosszuegig, weil der erste Lauf eines Abbilds die Schwachstellen-Datenbank und
 * das Abbild selbst laedt. Die Gesamtfrist traegt die Referenzziele mit (Plan #1351, E11).
 */
export const FRIST_ZIEL_MS = 600_000;
export const FRIST_GESAMT_MS = 2_700_000;

/** Frist je Abruf der Tag-Liste; eine Seite ist klein, ein Haenger soll den Lauf nicht aufhalten. */
export const FRIST_TAGS_MS = 30_000;

/**
 * Frist fuer Befunde in uebernommenen Bausteinen ab dem Erscheinen der korrigierten Anbieter-Fassung
 * (Plan #1351, E3): eine Frist fuer alle Bausteine, wie vom PO festgelegt — kein Config-Feld.
 */
export const FRIST_UEBERNOMMEN_TAGE = 14;

export const STANDARD_SPERRDATEIEN = ['frontend/package-lock.json', 'docs-site/package-lock.json'];

const ART_FEHLALARM = 'fehlalarm';
const DATUM_FORM = /^\d{4}-\d{2}-\d{2}$/;

export const HILFE = `Sicherheitspruefung: Trivy findet, dieses Skript urteilt (Issue #1333).

Nutzung: node scripts/sicherheitspruefung.mjs [Schalter]

Ziele:
  --abbild <name>       eigenes Abbild, das erst im CI-Job entsteht (mehrfach moeglich)
  --sbom <pfad>         Stueckliste, etwa target/bom.json
  --sperrdatei <pfad>   Sperrdatei (mehrfach moeglich; Standard: ${STANDARD_SPERRDATEIEN.join(', ')})
  --arbeitsbaum <pfad>  Verzeichnis fuer die Geheimnis-Suche (Standard: .)
  Dazu kommen aus der Bausteinliste alle fremden Abbilder mit art "abbild",
  ausgeliefert true und pruefung "bezug" — und je solchem Abbild mit digest und
  ohne verwendung "bau" ein Referenzziel: der neueste Tag des Anbieters in
  derselben Hauptlinie und Variante, anonym aus der Tag-Liste der Registry.

Listen:
  --bausteine <pfad>    Bausteinliste (Standard: scripts/bausteine.json)
  --ausnahmen <pfad>    Ausnahmeliste (Standard: scripts/sicherheitsausnahmen.json)

  --hilfe, -h           diese Hilfe

Ausgabe nach $GITHUB_STEP_SUMMARY, wenn gesetzt, sonst auf die Standardausgabe; mit gesetzter
Variable steht jeder sperrende Befund zusaetzlich als eine Zeile auf der Standardausgabe.
Rueckgabewert 1 bei mindestens einem sperrenden Befund, sonst 0; 2 bei falschem Aufruf.
`;

// --- Argumente ------------------------------------------------------------

const MIT_WERT = {
  '--abbild': 'abbilder',
  '--sbom': 'sbom',
  '--sperrdatei': 'sperrdateien',
  '--arbeitsbaum': 'arbeitsbaum',
  '--bausteine': 'bausteine',
  '--ausnahmen': 'ausnahmen',
};
const MEHRFACH = new Set(['abbilder', 'sperrdateien']);

export function argumenteZerlegen(argumente) {
  const ergebnis = {
    hilfe: false,
    abbilder: [],
    sbom: null,
    sperrdateien: [],
    arbeitsbaum: '.',
    bausteine: null,
    ausnahmen: null,
    fehler: [],
  };
  for (let i = 0; i < argumente.length; i += 1) {
    const schalter = argumente[i];
    if (schalter === '--hilfe' || schalter === '-h') {
      ergebnis.hilfe = true;
      continue;
    }
    const feld = MIT_WERT[schalter];
    if (!feld) {
      ergebnis.fehler.push(`unbekanntes Argument ${schalter}`);
      continue;
    }
    const wert = argumente[i + 1];
    if (wert === undefined || wert.startsWith('-')) {
      ergebnis.fehler.push(`${schalter} ohne Wert`);
      continue;
    }
    i += 1;
    if (MEHRFACH.has(feld)) ergebnis[feld].push(wert);
    else ergebnis[feld] = wert;
  }
  if (ergebnis.sperrdateien.length === 0) ergebnis.sperrdateien = [...STANDARD_SPERRDATEIEN];
  return ergebnis;
}

// --- Referenz: neuester Tag der Linie ------------------------------------

/** Bauwerkzeuge stecken nicht im ausgelieferten Abbild: gezeigt, nie sperrend (Plan #1351, E5). */
const VERWENDUNG_BAU = 'bau';

const ROLLE_REFERENZ = 'referenz';

/**
 * Zerlegt einen Tag in Fassung, Build-Nummer und Variante (Entscheidung in #1356, #1568): Die
 * Fassung sind die punktgetrennten Ziffern am Anfang. Eine Zahl hinter einem Unterstrich ist die
 * Build-Nummer (eclipse-temurin: `25.0.4.1_1-jre`, GA-Tag `25_36-jre` = 25.0.0 Build 36) — sie
 * gehoert weder zur Fassung noch zur Variante. Der Rest ist die Variante (`-bookworm`, `-alpine`).
 * Ohne Ziffernanfang: null.
 */
function tagZerlegen(tag) {
  const treffer = /^(\d+(?:\.\d+)*)(?:_(\d+))?(.*)$/.exec(tag);
  if (!treffer) return null;
  const build = treffer[2] === undefined ? null : Number(treffer[2]);
  return { fassung: treffer[1].split('.').map(Number), build, variante: treffer[3] };
}

/**
 * Vergleicht zuerst die Fassung, bei gleicher Fassung die Build-Nummer. Traegt einer der Tags eine
 * Build-Nummer, zaehlen fehlende Stellen der Fassung als 0 (`25` = `25.0.0`); ohne Build-Nummer
 * bleibt es beim bisherigen Vergleich, in dem die laengere Fassung bei gleichem Anfang hoeher ist.
 */
function fassungVergleichen(a, b) {
  const mitBuild = a.build !== null || b.build !== null;
  const fehlend = mitBuild ? 0 : -1;
  for (let i = 0; i < Math.max(a.fassung.length, b.fassung.length); i += 1) {
    const unterschied = (a.fassung[i] ?? fehlend) - (b.fassung[i] ?? fehlend);
    if (unterschied !== 0) return unterschied;
  }
  return (a.build ?? -1) - (b.build ?? -1);
}

/** Trennt `name:tag` am letzten Doppelpunkt hinter dem letzten Schraegstrich (Registry mit Port). */
function nameUndTag(bezugsstelle) {
  const schraeg = bezugsstelle.lastIndexOf('/');
  const trenner = bezugsstelle.indexOf(':', schraeg + 1);
  return trenner === -1 ? null : { name: bezugsstelle.slice(0, trenner), tag: bezugsstelle.slice(trenner + 1) };
}

/**
 * Die Referenz zu einer gebundenen Bezugsstelle (Plan #1351, E2): der numerisch hoechste Tag mit
 * gleicher Hauptversion und gleicher Variante. Der eigene Tag zaehlt immer mit — ist er der hoechste,
 * ist die Referenz sein aktueller Stand, und ein Neubau desselben Tags ist darin enthalten.
 * Ohne Tag oder ohne Ziffernanfang: null.
 */
export function referenzBezugsstelle(bezugsstelle, tags) {
  const teile = nameUndTag(bezugsstelle);
  const eigen = teile && tagZerlegen(teile.tag);
  if (!eigen) return null;
  let bester = { tag: teile.tag, ...eigen };
  for (const tag of tags) {
    const kandidat = tagZerlegen(tag);
    if (!kandidat || kandidat.variante !== eigen.variante || kandidat.fassung[0] !== eigen.fassung[0]) continue;
    if (fassungVergleichen(kandidat, bester) > 0) bester = { tag, ...kandidat };
  }
  return `${teile.name}:${bester.tag}`;
}

const TAG_SEITEN_HOECHSTENS = 50;

function tagListeUrl({ registry, repository }) {
  return `https://${registry}/v2/${repository}/tags/list?n=1000`;
}

/** Der Link-Kopf der Registry nennt die naechste Seite relativ zur Registry. */
function naechsteSeite(antwort, url) {
  const link = antwort.headers.get('link');
  const treffer = link && /<([^>]+)>\s*;\s*rel="?next"?/i.exec(link);
  return treffer ? new URL(treffer[1], url).toString() : null;
}

/**
 * Holt die Tag-Liste eines Abbilds anonym — Docker Hub und andere v2-Registries, nach dem Muster
 * von `bezugspruefung.mjs`: erster Abruf ohne Anmeldung, beim 401 ein anonymes Token aus dem
 * WWW-Authenticate-Kopf. Wirft mit Grund; der Aufrufer macht daraus "Ziel nicht geprueft".
 */
export async function registryTagsHolen(bezugsstelle, { holen = fetch, fristMs = FRIST_TAGS_MS } = {}) {
  const teile = abbildTeile(bezugsstelle);
  const abrufen = (url, kopfzeilen) => holen(url, { method: 'GET', headers: kopfzeilen, signal: AbortSignal.timeout(fristMs) });
  let url = tagListeUrl(teile);
  let kopfzeilen = {};
  let antwort = await abrufen(url, kopfzeilen);
  if (antwort.status === 401) {
    const angabe = wwwAuthenticateLesen(antwort.headers.get('www-authenticate'));
    if (!angabe) throw new Error('401 ohne verwertbaren WWW-Authenticate-Kopf');
    const tokenAntwort = await abrufen(tokenUrl(angabe, teile), {});
    if (!tokenAntwort.ok) throw new Error(`Token-Abruf antwortete ${tokenAntwort.status}`);
    const koerper = await tokenAntwort.json();
    kopfzeilen = { Authorization: `Bearer ${koerper.token ?? koerper.access_token}` };
    antwort = await abrufen(url, kopfzeilen);
  }
  const tags = [];
  for (let seite = 1; ; seite += 1) {
    if (!antwort.ok) throw new Error(`Tag-Liste antwortete ${antwort.status}`);
    const koerper = await antwort.json();
    if (!Array.isArray(koerper?.tags)) throw new Error('Antwort ohne Tag-Liste');
    tags.push(...koerper.tags);
    const weiter = naechsteSeite(antwort, url);
    if (!weiter) return tags;
    if (seite >= TAG_SEITEN_HOECHSTENS) throw new Error(`Tag-Liste mit mehr als ${TAG_SEITEN_HOECHSTENS} Seiten`);
    url = weiter;
    antwort = await abrufen(url, kopfzeilen);
  }
}

// --- Ziele ----------------------------------------------------------------

/**
 * Das Referenzziel traegt einen eigenen Namen (Plan #1351, E10): Der Name ist der Schluessel der
 * Ausnahmeliste, und unter dem Namen des gebundenen Ziels traefe ihn jede Uebergangsausnahme.
 * Geprueft wird ohne Digest — gemeint ist der aktuelle Stand beim Anbieter.
 */
async function referenzZiel(baustein, tagsHolen) {
  const gebunden = baustein.bezugsstelle;
  try {
    const referenz = referenzBezugsstelle(gebunden, await tagsHolen(gebunden));
    if (!referenz) return { ohneReferenz: `kein Referenzziel: Tag von ${gebunden} beginnt nicht mit einer Ziffer` };
    const tag = nameUndTag(referenz).tag;
    return { ziel: { art: 'abbild', rolle: ROLLE_REFERENZ, name: `${gebunden} (aktuell: ${tag})`, referenz, gebunden } };
  } catch (fehler) {
    return {
      ziel: {
        art: 'abbild',
        rolle: ROLLE_REFERENZ,
        name: `${gebunden} (aktuell: unbekannt)`,
        referenz: null,
        gebunden,
        // fetch meldet Netzfehler nur als "fetch failed"; der wahre Grund steht an `cause`.
        fehler: `Tag-Liste nicht abrufbar: ${fehler.message}${fehler.cause?.message ? `: ${fehler.cause.message}` : ''}`,
      },
    };
  }
}

/** Das eigene Abbild aus dem CI-Job heisst wie das letzte Pfadsegment seiner Bezugsstelle. */
function repositoryName(bezugsstelle) {
  const teile = nameUndTag(bezugsstelle);
  const name = teile ? teile.name : bezugsstelle;
  return name.slice(name.lastIndexOf('/') + 1);
}

/** Der Name des Basis-Ziels eines eigenen Abbilds (#1353): die Bezugsstelle seines `basis`-Bausteins. */
function basisZielName(bausteine, abbild) {
  const eigen = bausteine.find((b) => b.pruefung === 'eigen' && b.basis && repositoryName(b.bezugsstelle) === repositoryName(abbild));
  return bausteine.find((b) => eigen && b.name === eigen.basis)?.bezugsstelle ?? null;
}

/**
 * Bildet die Zielliste (E13). Fremde Abbilder werden mit ihrem Digest geprueft, wenn die Liste
 * einen nennt — geprueft wird so genau, was ausgeliefert wird. Der Name bleibt tag-foermig: Er ist
 * der Schluessel der Ausnahmeliste und soll nicht mit jedem Digest-Hub neu sperren. Hinter jedem
 * gebundenen Betriebs-Abbild steht sein Referenzziel (Plan #1351, E2); `tagsHolen` ist wie
 * `ausfuehren` austauschbar, damit die Tests ohne Netz laufen.
 */
export async function zieleBilden(bausteine, { abbilder, sbom, sperrdateien, arbeitsbaum }, { tagsHolen = registryTagsHolen } = {}) {
  const ziele = [];
  for (const b of bausteine) {
    if (b.art !== 'abbild' || b.ausgeliefert !== true || b.pruefung !== 'bezug') continue;
    const ziel = {
      art: 'abbild',
      name: b.bezugsstelle,
      referenz: b.digest ? `${b.bezugsstelle}@${b.digest}` : b.bezugsstelle,
      ...(b.verwendung ? { verwendung: b.verwendung } : {}),
    };
    ziele.push(ziel);
    if (!b.digest || b.verwendung === VERWENDUNG_BAU) continue;
    const { ziel: referenz, ohneReferenz } = await referenzZiel(b, tagsHolen);
    if (referenz) ziele.push(referenz);
    else ziel.ohneReferenz = ohneReferenz;
  }
  for (const name of abbilder) {
    const basis = basisZielName(bausteine, name);
    ziele.push({ art: 'abbild', name, referenz: name, ...(basis ? { basis } : {}) });
  }
  if (sbom) ziele.push({ art: 'sbom', name: sbom, referenz: sbom });
  for (const name of sperrdateien) ziele.push({ art: 'sperrdatei', name, referenz: name });
  if (arbeitsbaum) ziele.push({ art: 'arbeitsbaum', name: arbeitsbaum, referenz: arbeitsbaum });
  return ziele;
}

/**
 * Abbilder und Sperrdateien nur auf Schwachstellen: Geheimnisse gelten fuer den Stand des Commits,
 * und den deckt die Suche im Arbeitsbaum. Sonst meldete jedes Abbild die Beispielschluessel seiner
 * Pakete als eingecheckte Geheimnisse dieses Projekts.
 */
const AUFRUF_JE_ART = {
  abbild: ['image', '--scanners', 'vuln'],
  sbom: ['sbom'],
  sperrdatei: ['fs', '--scanners', 'vuln'],
  arbeitsbaum: ['fs', '--scanners', 'secret'],
};

export function trivyArgumente(ziel) {
  return [...AUFRUF_JE_ART[ziel.art], '--format', 'json', '--exit-code', '0', ziel.referenz];
}

// --- Werkzeugausgabe ------------------------------------------------------

/** Liest Trivys JSON. Wirft, wenn die Ausgabe kein Bericht ist — der Aufrufer macht daraus "nicht geprueft". */
export function trivyAusgabeLesen(text) {
  const bericht = JSON.parse(text);
  if (bericht === null || typeof bericht !== 'object' || Array.isArray(bericht)) {
    throw new Error('Ausgabe ist kein Trivy-Bericht');
  }
  const schwachstellen = [];
  const geheimnisse = [];
  for (const ergebnis of bericht.Results ?? []) {
    for (const v of ergebnis.Vulnerabilities ?? []) {
      schwachstellen.push({
        kennung: v.VulnerabilityID,
        paket: v.PkgName,
        schweregrad: v.Severity,
        korrektur: v.FixedVersion || null,
        fassung: v.InstalledVersion || null,
        bestandteil: ergebnis.Target,
        klasse: ergebnis.Class ?? null,
        pfad: v.PkgPath || null,
      });
    }
    for (const g of ergebnis.Secrets ?? []) {
      geheimnisse.push({ kennung: g.RuleID, datei: g.Target ?? ergebnis.Target });
    }
  }
  return { schwachstellen, geheimnisse, erstellt: bericht.Metadata?.ImageConfig?.created ?? null };
}

// --- Ausnahmeliste --------------------------------------------------------

function datumGueltig(wert) {
  if (typeof wert !== 'string' || !DATUM_FORM.test(wert)) return false;
  const datum = new Date(`${wert}T00:00:00Z`);
  return !Number.isNaN(datum.getTime()) && datum.toISOString().slice(0, 10) === wert;
}

function formMaengel(eintrag) {
  if (eintrag === null || typeof eintrag !== 'object' || Array.isArray(eintrag)) return ['kein Objekt'];
  const maengel = [];
  if (!eintrag.kennung) maengel.push('ohne kennung');
  if (!eintrag.ziel) maengel.push('ohne ziel');
  if (!eintrag.begruendung) maengel.push('ohne begruendung');
  if (eintrag.art === ART_FEHLALARM) {
    if (eintrag.ablauf !== undefined) maengel.push('Geheimnis-Fehlalarm mit ablauf — Geheimnisse laufen nicht ab');
  } else if (eintrag.art !== undefined) {
    maengel.push(`unbekannte art ${JSON.stringify(eintrag.art)}`);
  } else if (eintrag.ablauf === undefined) {
    maengel.push('ohne ablauf');
  } else if (!datumGueltig(eintrag.ablauf)) {
    maengel.push('ablauf nicht im Format JJJJ-MM-TT');
  }
  return maengel;
}

/**
 * Liest und prueft die Ausnahmeliste. Ein Eintrag mit Formfehler gilt nicht — sein Befund sperrt
 * weiter —, und der Formfehler sperrt selbst: Sonst liesse sich eine Ausnahme ohne Begruendung oder
 * ohne Ablauf einfach durch Weglassen einrichten.
 */
export function ausnahmenLesen(text) {
  let roh;
  try {
    roh = JSON.parse(text);
  } catch (fehler) {
    return { eintraege: [], formfehler: [`Ausnahmeliste nicht lesbar: ${fehler.message}`] };
  }
  if (!Array.isArray(roh?.ausnahmen)) {
    return { eintraege: [], formfehler: ['Ausnahmeliste ohne Feld ausnahmen als Liste'] };
  }
  const eintraege = [];
  const formfehler = [];
  roh.ausnahmen.forEach((eintrag, index) => {
    const maengel = formMaengel(eintrag);
    if (maengel.length === 0) {
      eintraege.push(eintrag);
    } else {
      const wer = eintrag?.kennung ? ` (${eintrag.kennung})` : '';
      formfehler.push(`Eintrag ${index + 1}${wer}: ${maengel.join(', ')}`);
    }
  });
  return { eintraege, formfehler };
}

/** Eine Ausnahme gilt einschliesslich ihres Ablauftags. */
export function ausnahmeAbgelaufen(eintrag, heute) {
  return heute > eintrag.ablauf;
}

// --- Urteil ---------------------------------------------------------------

/** Der Pfad ergaenzt den Bestandteil nur, wo er ihn genauer macht (etwa ein JAR unter "Java"). */
function bestandteilText(s) {
  return s.pfad && s.pfad !== s.bestandteil ? `${s.bestandteil}: ${s.pfad}` : s.bestandteil;
}

function schwachstellenText(s) {
  return `${s.kennung} (${s.schweregrad}) in ${s.paket} [${bestandteilText(s)}]`;
}

/** Jeder uebernommene Befund nennt die Regel, nach der er sperrt oder nicht (Plan #1351, Paket F). */
const REGEL_UEBERNOMMEN = '(Regel: docs/betrieb.md, Abschnitt Sicherheitsprüfung)';

/** Frueher erstellte Abbilder tragen ein Platzhalterdatum (etwa 1970-01-01 bei reproduzierbaren Bauten). */
const FRUEHESTES_ERSTELLT = Date.parse('2000-01-01T00:00:00Z');
const TAG_MS = 86_400_000;

function erscheinungstag(erstellt) {
  const zeit = typeof erstellt === 'string' ? Date.parse(erstellt) : Number.NaN;
  return Number.isNaN(zeit) || zeit < FRUEHESTES_ERSTELLT ? null : new Date(zeit).toISOString().slice(0, 10);
}

function tagPlus(tag, tage) {
  return new Date(Date.parse(`${tag}T00:00:00Z`) + tage * TAG_MS).toISOString().slice(0, 10);
}

/**
 * Derselbe Befund im gebundenen und im Referenzziel: Betriebssystem-Pakete tragen im Target den
 * Abbildnamen und die OS-Fassung, die sich zwischen beiden unterscheiden — dort zaehlen nur Kennung
 * und Paket. In Programmen zaehlt der Bestandteil mit: Hat der Anbieter eines davon neu gebaut, ist
 * der Befund darin korrigiert, auch wenn ein anderes Programm ihn noch traegt.
 */
function befundSchluessel(s) {
  const ort = s.klasse === 'os-pkgs' ? '' : `${s.bestandteil}|${s.pfad ?? ''}`;
  return `${s.kennung}|${s.paket}|${ort}`;
}

/**
 * Das Urteil ueber einen uebernommenen Befund mit Korrektur (Plan #1351, E3): Er sperrt erst, wenn
 * der aktuelle Stand des Anbieters ihn nicht mehr enthaelt und dieser Stand aelter als die Frist ist.
 * Baut der Anbieter denselben Tag spaeter erneut, rueckt der Erscheinungstag nach (E4, hingenommen).
 */
function uebernommenUrteilen(befund, referenz, heute) {
  const schluessel = befundSchluessel(befund);
  if (referenz.befunde.schwachstellen.some((r) => befundSchluessel(r) === schluessel)) {
    return { sperrt: false, grund: 'Anbieter hat noch keine korrigierte Fassung' };
  }
  const seit = erscheinungstag(referenz.befunde.erstellt);
  if (!seit) return { sperrt: true, grund: 'Erscheinungstag der Anbieter-Fassung unbekannt' };
  const bis = tagPlus(seit, FRIST_UEBERNOMMEN_TAGE);
  if (heute > bis) {
    return {
      sperrt: true,
      grund: `korrigierte Fassung des Anbieters (${referenz.ziel.referenz}) seit ${seit}, ${FRIST_UEBERNOMMEN_TAGE}-Tage-Frist abgelaufen am ${bis}`,
    };
  }
  return { sperrt: false, grund: `korrigierte Fassung seit ${seit}, Frist bis ${bis}` };
}

/**
 * Woher ein Befund stammt (Plan #1351, E1): Im gebundenen fremden Abbild ist er uebernommen, wenn
 * dessen Referenzziel geprueft wurde. Im eigenen Abbild ist er uebernommen, wenn sein Basis-Ziel ihn
 * mit gleicher Kennung, gleichem Paket und gleicher installierter Fassung traegt — dann gilt die Regel
 * des Basis-Ziels. Sonst null: eigen, oder ohne verwertbares Referenzziel — beides urteilt wie bisher.
 */
function herkunftFinden(ziel, befund, { zielErgebnisse, referenzen }) {
  if (ziel.basis) {
    const basis = zielErgebnisse.get(ziel.basis);
    const gegenstueck = befund.fassung && basis?.befunde?.schwachstellen.find(
      (b) => b.kennung === befund.kennung && b.paket === befund.paket && b.fassung === befund.fassung,
    );
    return gegenstueck ? herkunftFinden(basis.ziel, gegenstueck, { zielErgebnisse, referenzen }) : null;
  }
  const referenz = referenzen.get(ziel.name);
  return referenz?.befunde ? { befund, referenz } : null;
}

/**
 * Urteilt ueber die Ergebnisse aller Ziele. `heute` (JJJJ-MM-TT) ist der Pruefzeitpunkt: Ob eine
 * Ausnahme abgelaufen ist, entscheidet der Lauf und nicht der Tag ihres Eintrags.
 */
export function urteilen(ergebnisse, { eintraege, formfehler }, { heute }) {
  const sperrend = [];
  const ohneKorrektur = [];
  const uebernommen = [];
  const bauwerkzeuge = [];
  const genutzt = [];
  const beruehrt = new Set();

  for (const fehler of formfehler) sperrend.push({ ziel: 'Ausnahmeliste', text: `Ausnahmeliste: ${fehler}` });

  const schwachstellenAusnahmen = eintraege.filter((e) => e.art !== ART_FEHLALARM);
  const fehlalarme = eintraege.filter((e) => e.art === ART_FEHLALARM);
  const nutzen = (eintrag, text) => {
    if (beruehrt.has(eintrag)) return;
    beruehrt.add(eintrag);
    genutzt.push({ ziel: eintrag.ziel, text });
  };

  const herkunft = {
    zielErgebnisse: new Map(ergebnisse.filter((e) => e.ziel.rolle !== ROLLE_REFERENZ).map((e) => [e.ziel.name, e])),
    referenzen: new Map(ergebnisse.filter((e) => e.ziel.rolle === ROLLE_REFERENZ).map((e) => [e.ziel.gebunden, e])),
  };

  for (const { ziel, befunde, fehler } of ergebnisse) {
    if (fehler) {
      sperrend.push({ ziel: ziel.name, text: `Ziel nicht geprueft: ${fehler}` });
      continue;
    }
    // Referenzziele dienen nur dem Vergleich mit dem gebundenen Stand (E10): kein Urteil, keine Ausnahme.
    if (ziel.rolle === ROLLE_REFERENZ) continue;
    for (const s of befunde.schwachstellen) {
      if (!SCHWERE.includes(s.schweregrad)) continue;
      if (ziel.verwendung === VERWENDUNG_BAU) {
        const korrektur = s.korrektur ? `Korrektur in ${s.korrektur}` : 'keine Korrektur verfuegbar';
        bauwerkzeuge.push({ ziel: ziel.name, text: `${schwachstellenText(s)} — ${korrektur}` });
        continue;
      }
      if (!s.korrektur) {
        ohneKorrektur.push({ ziel: ziel.name, text: `${schwachstellenText(s)} — keine Korrektur verfuegbar` });
        continue;
      }
      let text = `${schwachstellenText(s)} — Korrektur in ${s.korrektur}`;
      const quelle = herkunftFinden(ziel, s, herkunft);
      if (quelle) {
        const { sperrt, grund } = uebernommenUrteilen(quelle.befund, quelle.referenz, heute);
        text = `${text} — ${grund} ${REGEL_UEBERNOMMEN}`;
        if (!sperrt) {
          uebernommen.push({ ziel: ziel.name, text });
          continue;
        }
      }
      const ausnahme = schwachstellenAusnahmen.find((e) => e.kennung === s.kennung && e.ziel === ziel.name);
      if (!ausnahme) {
        sperrend.push({ ziel: ziel.name, text });
      } else if (ausnahmeAbgelaufen(ausnahme, heute)) {
        beruehrt.add(ausnahme);
        sperrend.push({ ziel: ziel.name, text: `${text} — Ausnahme abgelaufen am ${ausnahme.ablauf}` });
      } else {
        nutzen(ausnahme, `${ausnahme.kennung}: ${ausnahme.begruendung} (gilt bis ${ausnahme.ablauf})`);
      }
    }
    for (const g of befunde.geheimnisse) {
      const fehlalarm = fehlalarme.find((e) => e.kennung === g.kennung && e.ziel === g.datei);
      if (fehlalarm) {
        nutzen(fehlalarm, `${fehlalarm.kennung} in ${fehlalarm.ziel}: Fehlalarm — ${fehlalarm.begruendung}`);
      } else {
        sperrend.push({ ziel: ziel.name, text: `Geheimnis ${g.kennung} in ${g.datei}` });
      }
    }
  }

  const hinweise = [
    ...eintraege
      .filter((e) => !beruehrt.has(e))
      .map((e) => ({
        ziel: e.ziel,
        text: `Ausnahme ${e.kennung} fuer ${e.ziel} trifft keinen sperrenden Befund — Eintrag pruefen oder entfernen`,
      })),
    ...ergebnisse.filter((e) => e.ziel.ohneReferenz).map((e) => ({ ziel: e.ziel.name, text: e.ziel.ohneReferenz })),
  ];

  return { sperrend, ohneKorrektur, uebernommen, bauwerkzeuge, genutzt, hinweise, ziele: ergebnisse.map((e) => e.ziel) };
}

// --- Zusammenfassung ------------------------------------------------------

/** Werkzeugausgaben sind fremder Text: Markdown-Steuerzeichen darin werden entschaerft. */
function md(text) {
  return String(text).replace(/[\\`*_|<>[\]]/g, (zeichen) => `\\${zeichen}`);
}

function abschnitt(titel, eintraege) {
  const zeilen = eintraege.length === 0
    ? ['- keine']
    : eintraege.map((e) => `- ${md(e.ziel)}: ${md(e.text)}`);
  return [`### ${titel}`, '', ...zeilen, ''];
}

export function zusammenfassung(urteil) {
  const anzahl = urteil.sperrend.length;
  const ergebnis = anzahl === 0
    ? '**Ergebnis: keine sperrenden Befunde**'
    : `**Ergebnis: gesperrt — ${anzahl === 1 ? '1 sperrender Befund' : `${anzahl} sperrende Befunde`}**`;
  return [
    '## Sicherheitspruefung',
    '',
    ergebnis,
    '',
    ...abschnitt('Sperrend', urteil.sperrend),
    ...abschnitt('Schwer ohne Korrektur (sichtbar, sperrt nicht)', urteil.ohneKorrektur),
    ...abschnitt('Übernommen, wartet auf Anbieter oder Frist (sichtbar, sperrt nicht)', urteil.uebernommen),
    ...abschnitt('Bauwerkzeuge (informiert, sperrt nicht)', urteil.bauwerkzeuge),
    ...abschnitt('Genutzte Ausnahmen', urteil.genutzt),
    ...abschnitt('Hinweise', urteil.hinweise),
    '### Gepruefte Ziele',
    '',
    ...urteil.ziele.map((z) => `- ${z.art}${z.rolle === ROLLE_REFERENZ ? ' (Referenz)' : ''}: ${md(z.name)}`),
    '',
  ].join('\n');
}

export function zusammenfassungAusgeben(text, { umgebung = process.env, schreiben = (t) => process.stdout.write(t) } = {}) {
  if (umgebung.GITHUB_STEP_SUMMARY) appendFileSync(umgebung.GITHUB_STEP_SUMMARY, text);
  else schreiben(text);
}

/**
 * Die sperrenden Befunde als Klartext, je Befund eine Zeile (Issue #1564): Mit gesetztem
 * $GITHUB_STEP_SUMMARY steht die Zusammenfassung nur auf der Lauf-Seite, und `gh run view --log-failed`
 * zeigte ein rotes Gate ohne Grund. Ohne die Variable traegt die Zusammenfassung sie schon selbst.
 */
export function sperrendeZeilen(urteil) {
  const anzahl = urteil.sperrend.length;
  if (anzahl === 0) return '';
  const einzeilig = (text) => String(text).replace(/\s*\n\s*/g, ' ');
  return [
    `Sicherheitspruefung: ${anzahl === 1 ? '1 sperrender Befund' : `${anzahl} sperrende Befunde`}`,
    ...urteil.sperrend.map((e) => `sperrend: ${einzeilig(e.ziel)}: ${einzeilig(e.text)}`),
    '',
  ].join('\n');
}

// --- Werkzeugaufruf -------------------------------------------------------

/** Der Standard-Werkzeugaufruf. Loest mit der Standardausgabe auf, lehnt bei Rueckgabewert != 0 ab. */
export function trivyAusfuehren(argumente, { signal, befehl = 'trivy' } = {}) {
  return new Promise((aufloesen, ablehnen) => {
    const prozess = spawn(befehl, argumente, { signal, stdio: ['ignore', 'pipe', 'pipe'] });
    const ausgabe = [];
    const fehlerausgabe = [];
    prozess.stdout.on('data', (stueck) => ausgabe.push(stueck));
    prozess.stderr.on('data', (stueck) => fehlerausgabe.push(stueck));
    prozess.on('error', (fehler) => ablehnen(new Error(`${befehl} nicht ausfuehrbar: ${fehler.message}`)));
    prozess.on('close', (code) => {
      if (code === 0) {
        aufloesen(Buffer.concat(ausgabe).toString('utf-8'));
      } else {
        const rest = Buffer.concat(fehlerausgabe).toString('utf-8').trim().slice(-500);
        ablehnen(new Error(`${befehl} endete mit ${code}: ${rest}`));
      }
    });
  });
}

// --- Lauf -----------------------------------------------------------------

function sekunden(ms) {
  return `${Math.round(ms / 1000)} s`;
}

/** Legt eine Frist um den Werkzeugaufruf und bricht ihn beim Reissen ueber das Signal ab. */
async function zielPruefen(ziel, ausfuehren, fristMs) {
  const abbruch = new AbortController();
  let uhr;
  try {
    const text = await Promise.race([
      ausfuehren(trivyArgumente(ziel), { signal: abbruch.signal }),
      new Promise((_, ablehnen) => {
        uhr = setTimeout(() => {
          abbruch.abort();
          ablehnen(new Error(`Zeitueberschreitung nach ${sekunden(fristMs)}`));
        }, fristMs);
      }),
    ]);
    return { ziel, befunde: trivyAusgabeLesen(text) };
  } catch (fehler) {
    return { ziel, fehler: fehler.message };
  } finally {
    clearTimeout(uhr);
  }
}

/**
 * Prueft die Ziele sequenziell — eine haengende Pruefung zieht so nicht die Fristen der anderen
 * mit —, urteilt und gibt die Zusammenfassung aus, auch bei gruenem Lauf.
 */
export async function laufen(ziele, {
  ausfuehren = trivyAusfuehren,
  ausnahmen,
  fristMs = FRIST_ZIEL_MS,
  gesamtFristMs = FRIST_GESAMT_MS,
  jetzt = () => Date.now(),
  umgebung = process.env,
  schreiben = (t) => process.stdout.write(t),
} = {}) {
  const beginn = jetzt();
  const ergebnisse = [];
  for (const ziel of ziele) {
    if (ziel.fehler) {
      ergebnisse.push({ ziel, fehler: ziel.fehler });
    } else if (jetzt() - beginn > gesamtFristMs) {
      ergebnisse.push({ ziel, fehler: `Gesamtfrist von ${sekunden(gesamtFristMs)} ueberschritten` });
    } else {
      ergebnisse.push(await zielPruefen(ziel, ausfuehren, fristMs));
    }
  }
  const heute = new Date(beginn).toISOString().slice(0, 10);
  const urteil = urteilen(ergebnisse, ausnahmen, { heute });
  if (ziele.length === 0) urteil.sperrend.push({ ziel: '-', text: 'kein Ziel uebergeben — nichts geprueft' });
  zusammenfassungAusgeben(zusammenfassung(urteil), { umgebung, schreiben });
  if (umgebung.GITHUB_STEP_SUMMARY) {
    const zeilen = sperrendeZeilen(urteil);
    if (zeilen) schreiben(zeilen);
  }
  return { urteil, exitcode: urteil.sperrend.length === 0 ? 0 : 1 };
}

// --- Einsprung ------------------------------------------------------------

const HIER = dirname(fileURLToPath(import.meta.url));

/* c8 ignore start — der Einsprung laeuft nur als Kommando; --hilfe und Aufruffehler prueft der Test als Prozess. */
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const argumente = argumenteZerlegen(process.argv.slice(2));
  if (argumente.hilfe) {
    process.stdout.write(HILFE);
  } else if (argumente.fehler.length > 0) {
    process.stderr.write(`${argumente.fehler.join('\n')}\n\n${HILFE}`);
    process.exitCode = 2;
  } else {
    const bausteine = bausteineLesen(argumente.bausteine ?? join(HIER, 'bausteine.json'));
    const ausnahmenPfad = argumente.ausnahmen ?? join(HIER, 'sicherheitsausnahmen.json');
    let ausnahmenText;
    try {
      ausnahmenText = readFileSync(ausnahmenPfad, 'utf-8');
    } catch (fehler) {
      ausnahmenText = `nicht lesbar: ${fehler.message}`;
    }
    const ziele = await zieleBilden(bausteine, argumente);
    const { exitcode } = await laufen(ziele, { ausnahmen: ausnahmenLesen(ausnahmenText) });
    process.exitCode = exitcode;
  }
}
/* c8 ignore stop */
