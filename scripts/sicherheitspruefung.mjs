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

import { bausteineLesen } from './bezugspruefung.mjs';

/** Schwer heisst: die beiden hoechsten Schweregrade (PO-Entscheidung in #676). */
export const SCHWERE = ['CRITICAL', 'HIGH'];

/**
 * Frist je Ziel. Grosszuegig, weil der erste Lauf eines Abbilds die Schwachstellen-Datenbank und
 * das Abbild selbst laedt.
 */
export const FRIST_ZIEL_MS = 600_000;
export const FRIST_GESAMT_MS = 1_800_000;

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
  ausgeliefert true und pruefung "bezug".

Listen:
  --bausteine <pfad>    Bausteinliste (Standard: scripts/bausteine.json)
  --ausnahmen <pfad>    Ausnahmeliste (Standard: scripts/sicherheitsausnahmen.json)

  --hilfe, -h           diese Hilfe

Ausgabe nach $GITHUB_STEP_SUMMARY, wenn gesetzt, sonst auf die Standardausgabe.
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

// --- Ziele ----------------------------------------------------------------

/**
 * Bildet die Zielliste (E13). Fremde Abbilder werden mit ihrem Digest geprueft, wenn die Liste
 * einen nennt — geprueft wird so genau, was ausgeliefert wird. Der Name bleibt tag-foermig: Er ist
 * der Schluessel der Ausnahmeliste und soll nicht mit jedem Digest-Hub neu sperren.
 */
export function zieleBilden(bausteine, { abbilder, sbom, sperrdateien, arbeitsbaum }) {
  const ziele = bausteine
    .filter((b) => b.art === 'abbild' && b.ausgeliefert === true && b.pruefung === 'bezug')
    .map((b) => ({
      art: 'abbild',
      name: b.bezugsstelle,
      referenz: b.digest ? `${b.bezugsstelle}@${b.digest}` : b.bezugsstelle,
    }));
  for (const name of abbilder) ziele.push({ art: 'abbild', name, referenz: name });
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
      });
    }
    for (const g of ergebnis.Secrets ?? []) {
      geheimnisse.push({ kennung: g.RuleID, datei: g.Target ?? ergebnis.Target });
    }
  }
  return { schwachstellen, geheimnisse };
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

function schwachstellenText(s) {
  return `${s.kennung} (${s.schweregrad}) in ${s.paket}`;
}

/**
 * Urteilt ueber die Ergebnisse aller Ziele. `heute` (JJJJ-MM-TT) ist der Pruefzeitpunkt: Ob eine
 * Ausnahme abgelaufen ist, entscheidet der Lauf und nicht der Tag ihres Eintrags.
 */
export function urteilen(ergebnisse, { eintraege, formfehler }, { heute }) {
  const sperrend = [];
  const ohneKorrektur = [];
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

  for (const { ziel, befunde, fehler } of ergebnisse) {
    if (fehler) {
      sperrend.push({ ziel: ziel.name, text: `Ziel nicht geprueft: ${fehler}` });
      continue;
    }
    for (const s of befunde.schwachstellen) {
      if (!SCHWERE.includes(s.schweregrad)) continue;
      if (!s.korrektur) {
        ohneKorrektur.push({ ziel: ziel.name, text: `${schwachstellenText(s)} — keine Korrektur verfuegbar` });
        continue;
      }
      const text = `${schwachstellenText(s)} — Korrektur in ${s.korrektur}`;
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

  const hinweise = eintraege
    .filter((e) => !beruehrt.has(e))
    .map((e) => ({
      ziel: e.ziel,
      text: `Ausnahme ${e.kennung} fuer ${e.ziel} trifft keinen sperrenden Befund — Eintrag pruefen oder entfernen`,
    }));

  return { sperrend, ohneKorrektur, genutzt, hinweise, ziele: ergebnisse.map((e) => e.ziel) };
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
    ...abschnitt('Genutzte Ausnahmen', urteil.genutzt),
    ...abschnitt('Hinweise', urteil.hinweise),
    '### Gepruefte Ziele',
    '',
    ...urteil.ziele.map((z) => `- ${z.art}: ${md(z.name)}`),
    '',
  ].join('\n');
}

export function zusammenfassungAusgeben(text, { umgebung = process.env, schreiben = (t) => process.stdout.write(t) } = {}) {
  if (umgebung.GITHUB_STEP_SUMMARY) appendFileSync(umgebung.GITHUB_STEP_SUMMARY, text);
  else schreiben(text);
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
    if (jetzt() - beginn > gesamtFristMs) {
      ergebnisse.push({ ziel, fehler: `Gesamtfrist von ${sekunden(gesamtFristMs)} ueberschritten` });
    } else {
      ergebnisse.push(await zielPruefen(ziel, ausfuehren, fristMs));
    }
  }
  const heute = new Date(beginn).toISOString().slice(0, 10);
  const urteil = urteilen(ergebnisse, ausnahmen, { heute });
  if (ziele.length === 0) urteil.sperrend.push({ ziel: '-', text: 'kein Ziel uebergeben — nichts geprueft' });
  zusammenfassungAusgeben(zusammenfassung(urteil), { umgebung, schreiben });
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
    const { exitcode } = await laufen(zieleBilden(bausteine, argumente), { ausnahmen: ausnahmenLesen(ausnahmenText) });
    process.exitCode = exitcode;
  }
}
/* c8 ignore stop */
