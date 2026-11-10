#!/usr/bin/env node
/**
 * abdeckungspruefung.mjs — Treiber der Abdeckungspruefung des Backends (Issue #2064, Plan #2062).
 *
 * Nutzung: node scripts/abdeckungspruefung.mjs
 *
 * Liest den JaCoCo-Bericht eines vorangegangenen `mvn verify` und urteilt, ohne selbst zu messen:
 *
 * 1. Rot ohne Karten: Der Bericht fehlt, ist nicht lesbar oder aelter als der gepruefte Stand; der
 *    Gesamtstand (Zeilen oder Zweige) liegt unter `SPERRSCHWELLE_GESAMT`; die geaenderten Zeilen
 *    liegen unter `SPERRSCHWELLE_GEAENDERT`. Verglichen wird ungerundet (E5).
 * 2. Rot ueber das Kartenmodul: Eine Karte liegt laenger als `LIEGEZEIT_TAGE` offen, die Liegezeit
 *    ist nicht pruefbar oder das Schreiben der Karten scheitert (E6).
 * 3. Sonst gruen: Jede Datei mit Luecke bekommt ueber `scripts/mutationskarten.mjs` eine Karte, und
 *    die Ausgabe endet mit dem Block fuer den Abschlussbericht von `push main`; derselbe Inhalt
 *    steht in `.claude/abdeckungsdurchlass.json` (E10).
 *
 * Geaendert heisst wie in der Mutationspruefung: seit `git merge-base HEAD origin/<main>`. Laesst
 * sich der Anker nicht aufloesen, gilt jede Zeile als geaendert — "mehr pruefen, nie weniger".
 */
import { spawnSync } from 'node:child_process';
import { readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { kartenTitel } from './mutationskarten.mjs';
import {
  ankerBestimmen,
  geaenderteDateien,
  kartenAuftrag,
  kartenProzess,
  prozentText,
  standLesen,
  zeileGeaendertLeser,
} from './mutationspruefung.mjs';

export const SPERRSCHWELLE_GESAMT = 99;
export const SPERRSCHWELLE_GEAENDERT = 90;
export const ZIEL = 100;
export const LIEGEZEIT_TAGE = 7;
export const BERICHT_PFAD = 'target/site/jacoco/jacoco.xml';

const PRUEFUNG = 'abdeckung';
const HAUPTZWEIG_VORGABE = 'main';
const CONFIG_PFAD = '.claude/workflow.config.json';
const QUELLWURZEL = 'src/main/java/';

// --- Bericht ----------------------------------------------------------------

function attribute(tag) {
  return Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map(([, name, wert]) => [name, wert]));
}

/** Der Zaehler eines Typs auf Berichtsebene; JaCoCo laesst einen Zaehler ohne Elemente weg. */
function gesamtZaehler(ende, typ) {
  for (const [tag] of ende.matchAll(/<counter\b[^>]*\/>/g)) {
    const { type, missed, covered } = attribute(tag);
    if (type === typ) return { gedeckt: Number(covered), gesamt: Number(missed) + Number(covered) };
  }
  return null;
}

function zeilenAus(quelltext) {
  return [...quelltext.matchAll(/<line\b[^>]*\/>/g)].map(([tag]) => {
    const { nr, mi, ci, mb, cb } = attribute(tag);
    return { nr: Number(nr), mi: Number(mi), ci: Number(ci), mb: Number(mb), cb: Number(cb) };
  });
}

/**
 * Der Bericht als Dateien samt Zeilen, Gesamtzaehlern und juengster Startzeit; `null`, wenn er
 * abgeschnitten ist oder eine dieser Angaben fehlt. Ein leerer Bericht ist keine Messung.
 */
export function berichtLesen(xml) {
  const text = String(xml ?? '');
  if (!text.includes('</report>')) return null;
  const starts = [...text.matchAll(/<sessioninfo\b[^>]*\/>/g)].map(([tag]) => Number(attribute(tag).start));
  const berichtsEnde = text.slice(text.lastIndexOf('</package>'));
  const zeilen = gesamtZaehler(berichtsEnde, 'LINE');
  if (starts.length === 0 || zeilen === null) return null;

  const dateien = [];
  for (const [, paketKopf, paket] of text.matchAll(/<package\b([^>]*)>([\s\S]*?)<\/package>/g)) {
    for (const [, dateiKopf, quelltext] of paket.matchAll(/<sourcefile\b([^>]*)>([\s\S]*?)<\/sourcefile>/g)) {
      dateien.push({
        pfad: `${QUELLWURZEL}${attribute(paketKopf).name}/${attribute(dateiKopf).name}`,
        zeilen: zeilenAus(quelltext),
      });
    }
  }
  return {
    dateien,
    zeilen,
    zweige: gesamtZaehler(berichtsEnde, 'BRANCH') ?? { gedeckt: 0, gesamt: 0 },
    sitzungStart: Math.max(...starts),
  };
}

const istAusfuehrbar = (zeile) => zeile.mi + zeile.ci > 0;

/** Die Stellen fuer das Kartenmodul, ohne Zahlen: Der Dublettenschluessel enthaelt den Text (E3). */
export function lueckenStellen(bericht) {
  const stellen = [];
  for (const { pfad, zeilen } of bericht.dateien) {
    for (const zeile of zeilen.filter(istAusfuehrbar)) {
      if (zeile.ci === 0) {
        stellen.push({ datei: pfad, zeile: zeile.nr, mutator: 'Zeile', ersetzung: 'nicht gedeckt' });
      } else if (zeile.mb > 0) {
        stellen.push({ datei: pfad, zeile: zeile.nr, mutator: 'Zweig', ersetzung: 'nicht vollständig gedeckt' });
      }
    }
  }
  return stellen;
}

function geaenderteZeilen(bericht, zeileGeaendert) {
  const zaehlung = { gedeckt: 0, gesamt: 0 };
  for (const { pfad, zeilen } of bericht.dateien) {
    for (const zeile of zeilen.filter(istAusfuehrbar)) {
      if (zeileGeaendert(pfad, zeile.nr)) {
        zaehlung.gesamt += 1;
        if (zeile.ci > 0) zaehlung.gedeckt += 1;
      }
    }
  }
  return zaehlung;
}

// --- Urteil und Text --------------------------------------------------------

/** Ungerundet (E5); ohne Elemente ist die Bedingung erfuellt. */
function erreicht({ gedeckt, gesamt }, schwelle) {
  return gesamt === 0 || gedeckt / gesamt * 100 >= schwelle;
}

function quote({ gedeckt, gesamt }) {
  return gesamt === 0 ? null : gedeckt / gesamt * 100;
}

/** Abgeschnitten statt gerundet: 98,996 % unter der Schwelle 99 darf nicht als 99,00 % erscheinen. */
function quoteText({ gedeckt, gesamt }) {
  return `${prozentText((Math.floor(gedeckt * 10000 / gesamt) / 100).toFixed(2))} % (${gedeckt} von ${gesamt})`;
}

function gesamtZeile(name, zaehlung) {
  if (zaehlung.gesamt === 0) return `${name}: keine`;
  const urteil = erreicht(zaehlung, SPERRSCHWELLE_GESAMT) ? 'erreicht' : 'UNTERSCHRITTEN';
  return `${name}: ${quoteText(zaehlung)} — Sperrschwelle ${SPERRSCHWELLE_GESAMT} % ${urteil}, Ziel ${ZIEL} %`;
}

function geaendertZeile(zaehlung) {
  if (zaehlung.gesamt === 0) return 'Geänderte Zeilen: keine';
  const urteil = erreicht(zaehlung, SPERRSCHWELLE_GEAENDERT) ? 'erreicht' : 'UNTERSCHRITTEN';
  return `Geänderte Zeilen: ${quoteText(zaehlung)} — Sperrschwelle ${SPERRSCHWELLE_GEAENDERT} % ${urteil}`;
}

function stellenText(s) {
  return `${s.datei}:${s.zeile} — ${s.mutator}: ${s.ersetzung}`;
}

function kartenText(k) {
  const titel = kartenTitel(k.pfad, PRUEFUNG);
  return k.art === 'neu'
    ? `Karte neu: #${k.nummer} ${titel} (Stellen: ${k.neueStellen})`
    : `Karte ergänzt: #${k.nummer} ${titel} (neue Stellen: ${k.neueStellen})`;
}

/** Eine ueberfaellige Karte sperrt, eine nicht pruefbare Liegezeit gilt nicht als erfuellt (E6). */
function liegezeitPruefen(karten) {
  const antwort = kartenAuftrag(karten, { auftrag: 'liegezeit', pruefung: PRUEFUNG, tage: LIEGEZEIT_TAGE });
  if (antwort.fehler) {
    return { rot: true, zeilen: [`Liegezeit nicht prüfbar — Board-Fehler: ${antwort.fehler}. Eine nicht prüfbare Liegezeit gilt nicht als erfüllt.`] };
  }
  if (!Array.isArray(antwort.ueberfaellig)) {
    return { rot: true, zeilen: ['Liegezeit nicht prüfbar — das Kartenmodul lieferte keine Liste.'] };
  }
  if (antwort.ueberfaellig.length === 0) {
    return { rot: false, zeilen: [`Liegezeit: keine Karte zu Abdeckungslücken länger als ${LIEGEZEIT_TAGE} Tage offen.`] };
  }
  return {
    rot: true,
    zeilen: [
      `Überfällige Karten (${antwort.ueberfaellig.length}) — Regel (Plan #2062): Eine Karte zu Abdeckungslücken, die länger als `
        + `${LIEGEZEIT_TAGE} Tage offen liegt, sperrt die nächste Veröffentlichung.`,
      ...antwort.ueberfaellig.map((k) => `  #${k.nummer} ${k.titel} — angelegt am ${String(k.angelegt).slice(0, 10)}`),
    ],
  };
}

/** Eine Stelle, deren Datei in der Antwort keine Karte hat, gilt als nicht erfasst. */
function kartenSchreiben(karten, stellen) {
  const antwort = kartenAuftrag(karten, { auftrag: 'schreiben', pruefung: PRUEFUNG, herkunft: 'JaCoCo-Bericht des Backends', stellen });
  const ergebnis = !antwort.fehler && Array.isArray(antwort.karten) ? antwort.karten : [];
  const erfasst = new Set(ergebnis.map((k) => k.pfad));
  const ohneKarte = stellen.filter((s) => !erfasst.has(s.datei));
  const fehler = antwort.fehler ?? (ohneKarte.length > 0 ? 'das Kartenmodul meldete für diese Dateien keine Karte' : null);
  const zeilen = ergebnis.map(kartenText);
  if (fehler) {
    zeilen.push(`Board-Fehler: ${fehler} — der Lauf ist rot, damit die Lücken nicht verloren gehen.`);
    zeilen.push(`Diese Stellen kamen auf keine Karte (${ohneKarte.length}):`);
    for (const s of ohneKarte) zeilen.push(`  ${stellenText(s)}`);
  }
  return { karten: ergebnis, fehler, zeilen };
}

/** Wie in der Mutationspruefung aendert eine nicht schreibbare Datei das Urteil nicht. */
function durchlassMelden({ wurzel, git, jetzt, bericht, geaendert, stellen, karten }) {
  const pfad = join(wurzel, '.claude', 'abdeckungsdurchlass.json');
  const block = [
    'Für den Abschlussbericht von push main:',
    `- Gesamtstand: Zeilen ${quoteText(bericht.zeilen)}, Zweige ${bericht.zweige.gesamt === 0 ? 'keine' : quoteText(bericht.zweige)} `
      + `(Ziel ${ZIEL} %, Sperrschwelle ${SPERRSCHWELLE_GESAMT} %)`,
    `- Geänderte Zeilen: ${geaendert.gesamt === 0 ? 'keine' : quoteText(geaendert)} (Sperrschwelle ${SPERRSCHWELLE_GEAENDERT} %)`,
    `- Durchgelassene Stellen: ${stellen.length}`,
    ...karten.map((k) => `- ${kartenText(k)}`),
  ];
  const inhalt = {
    stand: standLesen(git),
    datum: new Date(jetzt()).toISOString(),
    gesamt: { zeilen: quote(bericht.zeilen), zweige: quote(bericht.zweige) },
    geaendert: quote(geaendert),
    karten,
  };
  try {
    writeFileSync(pfad, `${JSON.stringify(inhalt, null, 2)}\n`);
  } catch (err) {
    block.push(`- Die Datei ${pfad} ließ sich nicht schreiben: ${err.message}`);
  }
  return block;
}

// --- Lauf -------------------------------------------------------------------

function hauptzweigLesen(liesDatei) {
  try {
    return JSON.parse(liesDatei(CONFIG_PFAD)).mainBranch ?? HAUPTZWEIG_VORGABE;
  } catch {
    return HAUPTZWEIG_VORGABE;
  }
}

/** Grund, warum der Bericht nicht zum geprueften Stand passt (E7), sonst `null`. */
function berichtVeraltet(git, sitzungStart) {
  const res = git('log', '-1', '--format=%ct', 'HEAD');
  const sekunden = res.status === 0 ? Number.parseInt(res.stdout.trim(), 10) : Number.NaN;
  if (!Number.isFinite(sekunden)) return 'Die Commit-Zeit von HEAD ist nicht lesbar — der Bericht lässt sich keinem Stand zuordnen.';
  if (sitzungStart < sekunden * 1000) return `Der Bericht ${BERICHT_PFAD} ist älter als der geprüfte Stand — zuerst mvn verify fahren.`;
  return null;
}

/**
 * Das Urteil; Rueckgabe ist der Exitcode. `umgebung` speist wie in der Mutationspruefung Git, das
 * Lesen von Dateien, das Kartenmodul, die Uhr und die Ausgabe ein.
 */
export function laufen(umgebung = {}) {
  const wurzel = umgebung.cwd ?? process.cwd();
  const ausgabe = umgebung.ausgabe ?? ((text) => process.stdout.write(text));
  const jetzt = umgebung.jetzt ?? (() => Date.now());
  const liesDatei = umgebung.liesDatei ?? ((pfad) => {
    try {
      return readFileSync(join(wurzel, pfad), 'utf-8');
    } catch {
      return null;
    }
  });
  const git = umgebung.git ?? ((...args) => spawnSync('git', args, { cwd: wurzel, encoding: 'utf-8' }));
  const karten = umgebung.karten ?? kartenProzess(wurzel);
  const zeilen = ['Abdeckungsprüfung Backend'];
  const ende = (code, ...schluss) => {
    ausgabe(`${[...zeilen, ...schluss, `Ergebnis: ${code === 0 ? 'grün' : 'rot'}`].join('\n')}\n`);
    return code;
  };

  const roh = liesDatei(BERICHT_PFAD);
  const bericht = roh === null ? null : berichtLesen(roh);
  if (bericht === null) {
    return ende(1, `Der Bericht ${BERICHT_PFAD} fehlt oder ist nicht lesbar — eine fehlende Messung ist kein Bestehen. Zuerst mvn verify fahren.`);
  }
  const veraltet = berichtVeraltet(git, bericht.sitzungStart);
  if (veraltet) return ende(1, veraltet);

  const { anker, vollerUmfang, satz } = ankerBestimmen(git, hauptzweigLesen(liesDatei));
  const { ungetrackt } = vollerUmfang ? { ungetrackt: new Set() } : geaenderteDateien(git, anker);
  const geaendert = geaenderteZeilen(bericht, zeileGeaendertLeser(git, anker, ungetrackt));
  const stellen = lueckenStellen(bericht);
  zeilen.push(
    satz,
    gesamtZeile('Zeilen', bericht.zeilen),
    gesamtZeile('Zweige', bericht.zweige),
    geaendertZeile(geaendert),
    `Lücken: ${stellen.length}`,
  );

  const schwellenRot = !erreicht(bericht.zeilen, SPERRSCHWELLE_GESAMT)
    || !erreicht(bericht.zweige, SPERRSCHWELLE_GESAMT)
    || !erreicht(geaendert, SPERRSCHWELLE_GEAENDERT);
  const liegezeit = liegezeitPruefen(karten);
  zeilen.push(...liegezeit.zeilen);
  if (schwellenRot || liegezeit.rot) return ende(1);
  if (stellen.length === 0) return ende(0);

  const geschrieben = kartenSchreiben(karten, stellen);
  zeilen.push(...geschrieben.zeilen);
  if (geschrieben.fehler) return ende(1);
  return ende(0, ...durchlassMelden({ wurzel, git, jetzt, bericht, geaendert, stellen, karten: geschrieben.karten }));
}

const direktAufgerufen = process.argv[1]
  && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url));
if (direktAufgerufen) {
  process.exitCode = laufen();
}
