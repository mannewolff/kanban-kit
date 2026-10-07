#!/usr/bin/env node
/**
 * Kartenmodul der Mutationspruefung (Plan #1528, Issue #1531): Laesst eine Mutationspruefung
 * Ueberlebende durch, steht je Datei eine Karte `Mutations-Überlebende in <Pfad>` im Backlog;
 * liegt eine solche Karte laenger als 7 Tage offen, sperrt sie die naechste Veroeffentlichung
 * (#1516, AK 4 und 5).
 *
 * Zwei Auftraege, als JSON ueber stdin, Antwort als JSON auf stdout:
 *   {"auftrag":"liegezeit"[, "tage":7]}
 *     → {"ueberfaellig":[{"nummer","titel","angelegt"}]}
 *   {"auftrag":"schreiben","herkunft":"…","stellen":[{"datei","zeile","mutator","ersetzung"}]}
 *     → {"karten":[{"pfad","art":"neu"|"ergaenzt","nummer","neueStellen"}]}
 * Ein Fehler endet mit {"fehler":"…"} und Rueckgabewert 1.
 *
 * Bewusst nicht wie `sync-sonar-issues-to-board.mjs`: Dort traegt `externalKey` die
 * Dublettenabwehr — er liefert aber auch fuer eine Karte in Done `created: false`, eine erledigte
 * Karte entstuende nie neu. Hier traegt die Titelsuche ueber die offenen Karten die Abwehr (E6, E7),
 * und der Zugang ist der lokale tbx-Login statt eines CI-Tokens (E10).
 */
import { randomUUID } from 'node:crypto';
import { realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { configPath, readJsonFile, resolveHost, tokenFetch, tokensPath } from '../cli/tbx.mjs';

export const LIEGEZEIT_TAGE = 7;
const TITEL_PRAEFIX = 'Mutations-Überlebende in ';
const TAG_MS = 24 * 60 * 60 * 1000;
const STELLE_MUSTER = /^- `([^`]+)` Zeile (\d+), ([^:]+): (.*)$/;

// --- reine Funktionen -------------------------------------------------------------------

/** Der feste Titel, an dem die Karte einer Datei erkannt wird (E7). */
export function kartenTitel(pfad) {
  return `${TITEL_PRAEFIX}${pfad}`;
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
export function kartenBody({ pfad, stellen, herkunft }) {
  return [
    '## Kontext',
    `Die Mutationsprüfung (${herkunft}) ließ in \`${pfad}\` überlebende Mutanten durch: Die Quote lag unter `
      + 'dem Ziel von 100 %, aber nicht unter der Sperrschwelle von 80 %. Liegt diese Karte länger als '
      + `${LIEGEZEIT_TAGE} Tage offen, sperrt sie die nächste Veröffentlichung (#1516).`,
    '',
    '## Aufgabe',
    'Je Stelle einen Test ergänzen, der den Mutanten tötet, oder die Stelle als bewusst hingenommene '
      + 'Altlast vermerken (CLAUDE-java.md §5.5 bzw. CLAUDE-react.md):',
    '',
    ...sortiert(stellen).map(stellenZeile),
    '',
    '## Akzeptanzkriterium',
    `- Ein Lauf der Mutationsprüfung über \`${pfad}\` meldet keine der oben genannten Stellen mehr als `
      + 'überlebend ohne Altlast-Vermerk.',
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
export async function offeneKarten(zugang) {
  const spalten = await aufruf(zugang, '/api/kanban/items');
  return Object.entries(spalten ?? {})
    .filter(([spalte]) => spalte !== 'DONE')
    .flatMap(([, karten]) => karten ?? [])
    .filter((k) => k?.type === 'card' && typeof k.title === 'string' && k.title.startsWith(TITEL_PRAEFIX));
}

/** Anlagedatum aus dem Verlauf der Karte, Eintrag CREATED (E8). */
export async function anlagedatum(zugang, id) {
  const verlauf = await aufruf(zugang, `/api/kanban/items/${id}/activity`);
  const eintrag = (Array.isArray(verlauf) ? verlauf : []).find((e) => e?.type === 'CREATED');
  if (!eintrag?.createdAt) throw new Error(`Karte ${id}: kein CREATED-Eintrag im Verlauf`);
  return eintrag.createdAt;
}

/** Die offenen Karten dieser Art, die laenger als `tage` liegen. */
export async function liegezeit({ jetzt = new Date(), tage = LIEGEZEIT_TAGE, ...zugang }) {
  const ergebnis = [];
  for (const karte of await offeneKarten(zugang)) {
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
export async function schreiben({ herkunft, stellen, ...zugang }) {
  const offen = new Map((await offeneKarten(zugang)).map((k) => [k.title, k]));
  const karten = [];
  for (const [pfad, dateiStellen] of jeDatei(stellen)) {
    const titel = kartenTitel(pfad);
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
      body: JSON.stringify({ title: titel, body: kartenBody({ pfad, stellen: neue, herkunft }), column: 'BACKLOG' }),
      idempotencyKey: randomUUID(),
    });
    karten.push({ pfad, art: 'neu', nummer: angelegt.number, neueStellen: neue.length });
  }
  return { karten };
}

// --- Zugang und Auftraege -------------------------------------------------------------

/** Token aus `TBX_TOKEN`, sonst aus dem tbx-Login; Host aus dessen `config.json` (E10). */
export function zugangLesen({ env = process.env, baseDir } = {}) {
  const config = readJsonFile(configPath(baseDir));
  const token = (env.TBX_TOKEN || '').trim() || (readJsonFile(tokensPath(baseDir))?.token || '').trim();
  if (!token) {
    throw new Error('Kein Board-Token: TBX_TOKEN setzen oder per `tbx auth login` anmelden.');
  }
  return { host: resolveHost({}, config), token };
}

/** Fuehrt einen Auftrag aus; wirft nie, ein Fehler wird zu `{ fehler }` mit Code 1. */
export async function ausfuehren(eingabe, { env = process.env, baseDir, fetchImpl = fetch, jetzt = new Date() } = {}) {
  try {
    const auftrag = JSON.parse(eingabe);
    const zugang = { ...zugangLesen({ env, baseDir }), fetchImpl };
    if (auftrag?.auftrag === 'liegezeit') {
      return { code: 0, ausgabe: await liegezeit({ ...zugang, jetzt, tage: auftrag.tage ?? LIEGEZEIT_TAGE }) };
    }
    if (auftrag?.auftrag === 'schreiben') {
      return { code: 0, ausgabe: await schreiben({ ...zugang, herkunft: auftrag.herkunft ?? 'Mutationsprüfung', stellen: auftrag.stellen }) };
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
