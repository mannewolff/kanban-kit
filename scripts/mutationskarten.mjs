#!/usr/bin/env node
/**
 * Kartenmodul der Mutationspruefung (Plan #1528, Issue #1531): Laesst eine Mutationspruefung
 * Ueberlebende durch, steht je Datei eine Karte `Mutations-Überlebende in <Pfad>` im Backlog;
 * liegt eine solche Karte laenger als 7 Tage offen, sperrt sie die naechste Veroeffentlichung
 * (#1516, AK 4 und 5). Dasselbe gilt fuer die Testabdeckung des Backends mit Karten
 * `Abdeckungslücken in <Pfad>` (Plan #2062, E3); das Feld `pruefung` waehlt die Art.
 *
 * Zwei Auftraege, als JSON ueber stdin, Antwort als JSON auf stdout:
 *   {"auftrag":"liegezeit"[, "pruefung":"mutation"|"abdeckung"][, "tage":7]}
 *     → {"ueberfaellig":[{"nummer","titel","angelegt"}]}
 *   {"auftrag":"schreiben"[, "pruefung":"mutation"|"abdeckung"],"herkunft":"…","stellen":[{"datei","zeile","mutator","ersetzung"}]}
 *     → {"karten":[{"pfad","art":"neu"|"ergaenzt","nummer","neueStellen"}]}
 * Ohne `pruefung` gilt `mutation`. Eine Stelle der Abdeckung traegt als `mutator` `Zeile` oder
 * `Zweig` und als `ersetzung` `nicht gedeckt` bzw. `nicht vollständig gedeckt`, ohne Zahlen, weil
 * der Dublettenschluessel den Text enthaelt.
 * Ein Fehler endet mit {"fehler":"…"} und Rueckgabewert 1.
 *
 * Bewusst nicht wie `sync-sonar-issues-to-board.mjs`: Dort traegt `externalKey` die
 * Dublettenabwehr — er liefert aber auch fuer eine Karte in Done `created: false`, eine erledigte
 * Karte entstuende nie neu. Hier traegt die Titelsuche ueber die offenen Karten die Abwehr (E6, E7),
 * und der Zugang ist der lokale tbx-Login statt eines CI-Tokens (E10).
 */
import { spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { readFileSync, realpathSync } from 'node:fs';
import { dirname, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import { configPath, readJsonFile, resolveHost, tokenFetch, tokensPath } from '../cli/tbx.mjs';

export const LIEGEZEIT_TAGE = 7;
const PRUEFUNGEN = {
  mutation: {
    name: 'Mutationsprüfung',
    titelPraefix: 'Mutations-Überlebende in ',
    kontext: (pfad, herkunft) => `Die Mutationsprüfung (${herkunft}) ließ in \`${pfad}\` überlebende Mutanten durch: Die Quote lag unter `
      + 'dem Ziel von 100 %, aber nicht unter der Sperrschwelle von 80 %. Liegt diese Karte länger als '
      + `${LIEGEZEIT_TAGE} Tage offen, sperrt sie die nächste Veröffentlichung (#1516).`,
    aufgabe: 'Je Stelle einen Test ergänzen, der den Mutanten tötet, oder die Stelle als bewusst hingenommene '
      + 'Altlast vermerken (CLAUDE-java.md §5.5 bzw. CLAUDE-react.md):',
    akzeptanz: (pfad) => `- Ein Lauf der Mutationsprüfung über \`${pfad}\` meldet keine der oben genannten Stellen mehr als `
      + 'überlebend ohne Altlast-Vermerk.',
  },
  abdeckung: {
    name: 'Abdeckungsprüfung',
    titelPraefix: 'Abdeckungslücken in ',
    kontext: (pfad, herkunft) => `Die Abdeckungsprüfung (${herkunft}) fand in \`${pfad}\` ungedeckte Stellen: Das Ziel von 100 % `
      + `ist nicht erreicht. Liegt diese Karte länger als ${LIEGEZEIT_TAGE} Tage offen, sperrt sie die nächste `
      + 'Veröffentlichung (#2062).',
    aufgabe: 'Je Stelle einen Test ergänzen oder die Stelle nach CLAUDE-java.md §5.2 und §5.4 begründet ausschließen:',
    akzeptanz: (pfad) => `- Ein Lauf der Abdeckungsprüfung über \`${pfad}\` meldet keine der oben genannten Stellen mehr als ungedeckt.`,
  },
};
const TAG_MS = 24 * 60 * 60 * 1000;
const STELLE_MUSTER = /^- `([^`]+)` Zeile (\d+), ([^:]+): (.*)$/;

// --- reine Funktionen -------------------------------------------------------------------

/** Die Art zu `pruefung`; ein unbekannter Wert ist ein Fehler, bevor etwas ans Board geht. */
function pruefungsArt(pruefung = 'mutation') {
  if (!Object.hasOwn(PRUEFUNGEN, pruefung)) {
    throw new Error(`Unbekannte pruefung: ${JSON.stringify(pruefung)} (erwartet mutation oder abdeckung)`);
  }
  return PRUEFUNGEN[pruefung];
}

/** Der feste Titel, an dem die Karte einer Datei erkannt wird (E7). */
export function kartenTitel(pfad, pruefung) {
  return `${pruefungsArt(pruefung).titelPraefix}${pfad}`;
}

/** Eine Ersetzung auf eine Zeile gefaltet — die Stellenliste ist zeilenweise. */
function einzeilig(text) {
  return String(text ?? '').replace(/\s+/g, ' ').trim();
}

function schluessel(s) {
  return `${s.datei}|${s.zeile}|${s.mutator}|${einzeilig(s.ersetzung)}`;
}

function stellenZeile(s) {
  return `- \`${s.datei}\` Zeile ${s.zeile}, ${s.mutator}: ${einzeilig(s.ersetzung)}`;
}

function sortiert(stellen) {
  return [...stellen].sort((a, b) => a.zeile - b.zeile || schluessel(a).localeCompare(schluessel(b)));
}

/** Body im Vier-Abschnitt-Format, damit die Karte ohne Umschreiben nach Ready kann (E7). */
export function kartenBody({ pfad, stellen, herkunft, pruefung }) {
  const art = pruefungsArt(pruefung);
  return [
    '## Kontext',
    art.kontext(pfad, herkunft),
    '',
    '## Aufgabe',
    art.aufgabe,
    '',
    ...sortiert(stellen).map(stellenZeile),
    '',
    '## Akzeptanzkriterium',
    art.akzeptanz(pfad),
    '',
    '## Abhängigkeiten',
    'Keine.',
    '',
  ].join('\n');
}

/** Liest die Stellenliste zurueck, die `kartenBody` und `ergaenzen` schreiben. */
export function stellenAusBody(body) {
  const stellen = [];
  for (const zeile of String(body ?? '').split('\n')) {
    const treffer = STELLE_MUSTER.exec(zeile);
    if (treffer) {
      stellen.push({ datei: treffer[1], zeile: Number(treffer[2]), mutator: treffer[3], ersetzung: treffer[4] });
    }
  }
  return stellen;
}

/**
 * Haengt an die Stellenliste unter `## Aufgabe` nur die Stellen an, deren Schluessel aus Datei,
 * Zeile, Mutator und Ersetzung noch fehlt; alles andere am Body bleibt, wie es ist. Fehlt der
 * Abschnitt (von Hand umgeschrieben), kommen die Stellen ans Ende.
 */
export function ergaenzen(body, neueStellen) {
  const text = String(body ?? '');
  const bekannt = new Set(stellenAusBody(text).map(schluessel));
  const neue = [];
  for (const s of neueStellen) {
    const k = schluessel(s);
    if (bekannt.has(k)) continue;
    bekannt.add(k);
    neue.push(s);
  }
  if (neue.length === 0) return { body: text, neue };

  const zeilen = text.split('\n');
  const neueZeilen = sortiert(neue).map(stellenZeile);
  const aufgabe = zeilen.findIndex((z) => z.trim() === '## Aufgabe');
  if (aufgabe < 0) {
    return { body: `${text.replace(/\n*$/, '')}\n\n${neueZeilen.join('\n')}\n`, neue };
  }
  let ende = zeilen.findIndex((z, i) => i > aufgabe && z.startsWith('## '));
  if (ende < 0) ende = zeilen.length;
  let einfuegen = -1;
  for (let i = aufgabe + 1; i < ende; i++) if (STELLE_MUSTER.test(zeilen[i])) einfuegen = i + 1;
  if (einfuegen < 0) {
    einfuegen = ende;
    while (einfuegen > aufgabe + 1 && zeilen[einfuegen - 1].trim() === '') einfuegen--;
  }
  zeilen.splice(einfuegen, 0, ...neueZeilen);
  return { body: zeilen.join('\n'), neue };
}

/** Strikt laenger als `tage` × 24 h — genau sieben Tage sind noch nicht ueberfaellig. */
export function ueberfaellig({ angelegt, jetzt, tage }) {
  return new Date(jetzt).getTime() - new Date(angelegt).getTime() > tage * TAG_MS;
}

// --- Board-Teil -----------------------------------------------------------------------

async function aufruf({ host, token, fetchImpl }, pfad, options = {}) {
  const res = await tokenFetch(host, token, pfad, options, fetchImpl);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    const method = (options.method || 'GET').toUpperCase();
    throw new Error(`${method} ${pfad}: HTTP ${res.status}${body?.message ? ` — ${body.message}` : ''}`);
  }
  return res.json().catch(() => ({}));
}

/**
 * Die offenen Karten dieser Art: `GET /api/kanban/items` liefert eine Map je Spalte; alle Spalten
 * ausser DONE, nur Arbeitspakete (`type` card), Titel mit dem festen Praefix. Archivierte Karten
 * fehlen in der Antwort und gelten damit als nicht offen (E7).
 */
export async function offeneKarten(zugang, pruefung) {
  const { titelPraefix } = pruefungsArt(pruefung);
  const spalten = await aufruf(zugang, '/api/kanban/items');
  return Object.entries(spalten ?? {})
    .filter(([spalte]) => spalte !== 'DONE')
    .flatMap(([, karten]) => karten ?? [])
    .filter((k) => k?.type === 'card' && typeof k.title === 'string' && k.title.startsWith(titelPraefix));
}

/** Anlagedatum aus dem Verlauf der Karte, Eintrag CREATED (E8). */
export async function anlagedatum(zugang, id) {
  const verlauf = await aufruf(zugang, `/api/kanban/items/${id}/activity`);
  const eintrag = (Array.isArray(verlauf) ? verlauf : []).find((e) => e?.type === 'CREATED');
  if (!eintrag?.createdAt) throw new Error(`Karte ${id}: kein CREATED-Eintrag im Verlauf`);
  return eintrag.createdAt;
}

/** Die offenen Karten dieser Art, die laenger als `tage` liegen. */
export async function liegezeit({ jetzt = new Date(), tage = LIEGEZEIT_TAGE, pruefung, ...zugang }) {
  const ergebnis = [];
  for (const karte of await offeneKarten(zugang, pruefung)) {
    const angelegt = await anlagedatum(zugang, karte.id);
    if (ueberfaellig({ angelegt, jetzt, tage })) {
      ergebnis.push({ nummer: karte.number, titel: karte.title, angelegt });
    }
  }
  return { ueberfaellig: ergebnis };
}

function jeDatei(stellen) {
  const gruppen = new Map();
  for (const s of stellen ?? []) {
    if (!gruppen.has(s.datei)) gruppen.set(s.datei, []);
    gruppen.get(s.datei).push(s);
  }
  return gruppen;
}

/**
 * Je Datei: gibt es eine offene Karte mit dem Titel, wird sie per PUT um die neuen Stellen ergaenzt
 * (Titel unveraendert, ohne neue Stellen kein Aufruf); sonst entsteht eine neue in BACKLOG. Der
 * `Idempotency-Key` ist je Anlage zufaellig — die Dublettenabwehr traegt allein die Titelsuche (E6).
 */
export async function schreiben({ herkunft, stellen, pruefung, ...zugang }) {
  const offen = new Map((await offeneKarten(zugang, pruefung)).map((k) => [k.title, k]));
  const karten = [];
  for (const [pfad, dateiStellen] of jeDatei(stellen)) {
    const titel = kartenTitel(pfad, pruefung);
    const vorhanden = offen.get(titel);
    if (vorhanden) {
      const { body, neue } = ergaenzen(vorhanden.body, dateiStellen);
      if (neue.length > 0) {
        await aufruf(zugang, `/api/kanban/items/${vorhanden.id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ title: vorhanden.title, body }),
        });
      }
      karten.push({ pfad, art: 'ergaenzt', nummer: vorhanden.number, neueStellen: neue.length });
      continue;
    }
    const { neue } = ergaenzen('', dateiStellen);
    const angelegt = await aufruf(zugang, '/api/kanban/items', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: titel, body: kartenBody({ pfad, stellen: neue, herkunft, pruefung }), column: 'BACKLOG' }),
      idempotencyKey: randomUUID(),
    });
    karten.push({ pfad, art: 'neu', nummer: angelegt.number, neueStellen: neue.length });
  }
  return { karten };
}

// --- Zugang und Auftraege -------------------------------------------------------------

/** Absoluter Pfad aus `git rev-parse --path-format=absolute <option>`; ohne Git oder bei Fehler `null`. */
function gitPfad(projektDir, option) {
  const lauf = spawnSync('git', ['rev-parse', '--path-format=absolute', option], { cwd: projektDir, encoding: 'utf-8' });
  return lauf.status === 0 ? lauf.stdout.trim() || null : null;
}

/** Hauptkopie, wenn `projektDir` ein verknuepfter Git-Worktree ist (Issue #2053), sonst `null`. */
function hauptkopie(projektDir) {
  const gitDir = gitPfad(projektDir, '--git-dir');
  const commonDir = gitPfad(projektDir, '--git-common-dir');
  return gitDir && commonDir && gitDir !== commonDir ? dirname(commonDir) : null;
}

/** Ein `toolbox.tokenFile`, das aus seinem Verzeichnis herausfuehrt (Issue #2058); kein Rueckfall. */
class TokenDateiAusbruch extends Error {}

/** Inhalt von `tokenFile` unter `wurzel`; massgeblich ist der tatsaechliche Pfad samt Symlinks. */
function tokenDateiLesen(wurzel, tokenFile) {
  const echterPfad = realpathSync(join(wurzel, tokenFile));
  if (!echterPfad.startsWith(realpathSync(wurzel) + sep)) {
    throw new TokenDateiAusbruch(`toolbox.tokenFile ${tokenFile} liegt ausserhalb von ${wurzel}.`);
  }
  return readFileSync(echterPfad, 'utf-8').trim();
}

/**
 * Token aus der Datei `toolbox.tokenFile` (relativ zum Projekt); leer oder unlesbar ist ein Fehler,
 * ein Pfad aus dem Projekt heraus ebenso (Issue #2058).
 * Fehlt sie in einem verknuepften Git-Worktree (`.claude/*` ist gitignored), gilt die Datei, die
 * die Config der Hauptkopie nennt (Issue #2053, #2058); fehlt sie auch dort, bleibt es beim Fehler
 * des Projektpfads. Liefert das Token und, wenn es aus der Hauptkopie kam, deren Pfad als `haupt`
 * (sonst `null`).
 */
function projektToken(projektDir, tokenFile) {
  let inhalt;
  let herkunft = null;
  try {
    inhalt = tokenDateiLesen(projektDir, tokenFile);
  } catch (e) {
    if (e instanceof TokenDateiAusbruch) throw e;
    const haupt = hauptkopie(projektDir);
    try {
      if (!haupt) throw e;
      inhalt = tokenDateiLesen(haupt, toolboxLesen(haupt).tokenFile);
      herkunft = haupt;
    } catch {
      throw new Error(`toolbox.tokenFile ${tokenFile} ist nicht lesbar: ${e.message}`);
    }
  }
  if (!inhalt) throw new Error(`toolbox.tokenFile ${tokenFile} ist leer.`);
  return { token: inhalt, haupt: herkunft };
}

/** `toolbox` aus `.claude/workflow.config.json` unter `dir`; fehlt sie, ein leeres Objekt. */
function toolboxLesen(dir) {
  return readJsonFile(join(dir, '.claude', 'workflow.config.json'))?.toolbox ?? {};
}

/**
 * Reihenfolge wie im Kit-Adapter (Issue #1535): `TBX_TOKEN`, dann `toolbox.tokenFile` aus
 * `.claude/workflow.config.json`, erst dann der globale tbx-Login. Im verknuepften Git-Worktree
 * ohne eigene Token-Datei gilt die der Hauptkopie (Issue #2053). Host aus `toolbox.host`,
 * sonst aus der `config.json` des tbx-Logins.
 *
 * Bindung von Token und Host (Issue #2054): Kommt das Token aus der Hauptkopie, gilt auch deren
 * Host. Der Worktree ist der gepruefte, womoeglich fremde Stand; weicht sein Host von dem der
 * Hauptkopie ab, bricht der Zugang mit beiden Hosts im Text ab, und es geht nichts ans Netz.
 * Ebenso gilt das Token des globalen tbx-Logins nur fuer den Host dieses Logins (Issue #2059),
 * denn `toolbox.host` kann aus einem fremden Stand kommen.
 * `TBX_TOKEN` und eine eigene Token-Datei des Worktrees stellt der Aufrufer bewusst bereit, sie
 * bleiben ungebunden.
 */
export function zugangLesen({ env = process.env, baseDir, projektDir = process.cwd() } = {}) {
  const toolbox = toolboxLesen(projektDir);
  const config = readJsonFile(configPath(baseDir));
  const envToken = (env.TBX_TOKEN || '').trim();
  const ausDatei = !envToken && toolbox.tokenFile ? projektToken(projektDir, toolbox.tokenFile) : null;
  const token = envToken
    || ausDatei?.token
    || (readJsonFile(tokensPath(baseDir))?.token || '').trim();
  if (!token) {
    throw new Error('Kein Board-Token: TBX_TOKEN setzen oder per `tbx auth login` anmelden.');
  }
  const loginHost = resolveHost({}, config);
  const host = toolbox.host || loginHost;
  if (!envToken && !ausDatei && host !== loginHost) {
    throw new Error(`toolbox.host (${host}) weicht vom Host des tbx-Logins (${loginHost}) ab; `
      + 'das Login-Token geht nur an dessen Host.');
  }
  if (ausDatei?.haupt) {
    const hauptHost = toolboxLesen(ausDatei.haupt).host || loginHost;
    if (host !== hauptHost) {
      throw new Error(`toolbox.host des Worktrees (${host}) weicht vom Host der Hauptkopie (${hauptHost}) ab; `
        + 'das Token der Hauptkopie geht nur an deren Host.');
    }
    return { host: hauptHost, token };
  }
  return { host, token };
}

/** Fuehrt einen Auftrag aus; wirft nie, ein Fehler wird zu `{ fehler }` mit Code 1. */
export async function ausfuehren(eingabe, { env = process.env, baseDir, projektDir, fetchImpl = fetch, jetzt = new Date() } = {}) {
  try {
    const auftrag = JSON.parse(eingabe);
    const zugang = { ...zugangLesen({ env, baseDir, projektDir }), fetchImpl };
    if (auftrag?.auftrag === 'liegezeit') {
      return { code: 0, ausgabe: await liegezeit({ ...zugang, jetzt, tage: auftrag.tage ?? LIEGEZEIT_TAGE, pruefung: auftrag.pruefung }) };
    }
    if (auftrag?.auftrag === 'schreiben') {
      return { code: 0, ausgabe: await schreiben({ ...zugang, herkunft: auftrag.herkunft ?? pruefungsArt(auftrag.pruefung).name, stellen: auftrag.stellen, pruefung: auftrag.pruefung }) };
    }
    throw new Error(`Unbekannter Auftrag: ${JSON.stringify(auftrag?.auftrag)} (erwartet liegezeit oder schreiben)`);
  } catch (e) {
    return { code: 1, ausgabe: { fehler: e.message } };
  }
}

async function stdinLesen() {
  const teile = [];
  for await (const teil of process.stdin) teile.push(teil);
  return Buffer.concat(teile).toString('utf-8');
}

const direktAufgerufen = process.argv[1]
  && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url));
if (direktAufgerufen) {
  const { code, ausgabe } = await ausfuehren(await stdinLesen());
  process.stdout.write(`${JSON.stringify(ausgabe)}\n`);
  process.exitCode = code;
}
