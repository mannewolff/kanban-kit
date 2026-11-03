#!/usr/bin/env node
// SPDX-License-Identifier: MIT
// Das Commit-Gate (Issue #470, Plan #467).
//
// Ein Commit entsteht nur, wenn jede Datei, die in ihn geht, beim gruenen
// Prueflauf denselben Inhalt trug. Der Nachweis steht in der Zusammenfassung, die
// `checks.mjs run` hinterlaesst; verglichen wird gegen den INDEX, nicht gegen den
// Working Tree — der Commit schreibt den Index, und die implement-Skills stagen
// ausdruecklich selektiv.
//
// Warum ein Git-Hook und kein Kit-Kommando: Kein Kit-Code committet. Jeder Commit
// im Workflow entsteht dadurch, dass eine Session Skill-Text befolgt. Nur git
// sieht jeden Commit, gleich welches Werkzeug ihn ausloest — auch ein
// selbstgebautes Hilfsskript.
//
// Die Zusicherung ist bewusst die schwaechere: Jede committete Datei war Teil
// eines gruenen Laufs. NICHT: Der committete Stand als Ganzes wurde geprueft. Wer
// A committet und das dazugehoerige B ungestagt liegen laesst, erzeugt einen
// Stand, den so nie jemand geprueft hat (Plan #467, A3).

import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, realpathSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

const HILFE = "node .claude/kit/checks.mjs run";

/**
 * Die Texte des Gates in beiden Sprachen (Issue #1458, Plan #1420 E13). Das Gate laeuft in
 * jedem Projekt allein und importiert nur Node-Bordmittel; es traegt seine Tabelle deshalb
 * selbst, statt den Texthelfer aus kit/board/namen.mjs zu laden. Vertragswerte wie das
 * Ergebnis einer Pruefung (`rot`) und die Option `--area` gehen unuebersetzt ein (E14).
 */
export const TEXTE = Object.freeze({
  de: Object.freeze({
    abgewiesen: "Commit abgewiesen — {grund}.\n  {hilfe}\n",
    abgewiesenPfad: "Commit abgewiesen — {pfad}: {grund}.\n  {hilfe}\n",
    checksFehlt: "checks.mjs liegt weder unter .claude/kit/ noch unter kit/ — bitte den Installer laufen lassen",
    zusammenfassungFehlt: "die Pruef-Zusammenfassung fehlt",
    zusammenfassungUnlesbar: "die Pruef-Zusammenfassung ist unlesbar",
    altesFormat: "die Pruef-Zusammenfassung hat ein altes Format ohne hashes — bitte den Installer laufen lassen",
    gitFehler: "{kommando} schlug fehl: {fehler}",
    pruefungEndete: "die Pruefung endete {ergebnis} ({cmd})",
    bereichslauf: "die Pruefung lief eingegrenzt auf den Bereich '{bereich}' und deckt den Commit nicht — bitte '{hilfe}' ohne --area fahren",
    loeschungNichtGeprueft: "Loeschung nicht geprueft",
    nichtGeprueft: "nicht geprueft",
    geaendert: "nach der Pruefung geaendert",
    einzigesKommando: "gate.mjs: Kommandos sind 'pre-commit', 'post-commit' und 'post-merge'.\n",
    nichtGestartet: "{hook}: Kartenpruefung nicht gestartet — {grund}\n",
    exitCode: "Exit {status}",
  }),
  en: Object.freeze({
    abgewiesen: "Commit rejected — {grund}.\n  {hilfe}\n",
    abgewiesenPfad: "Commit rejected — {pfad}: {grund}.\n  {hilfe}\n",
    checksFehlt: "checks.mjs is in neither .claude/kit/ nor kit/ — please run the installer",
    zusammenfassungFehlt: "the check summary is missing",
    zusammenfassungUnlesbar: "the check summary is unreadable",
    altesFormat: "the check summary has an old format without hashes — please run the installer",
    gitFehler: "{kommando} failed: {fehler}",
    pruefungEndete: "the check ended {ergebnis} ({cmd})",
    bereichslauf: "the check ran limited to the area '{bereich}' and does not cover the commit — please run '{hilfe}' without --area",
    loeschungNichtGeprueft: "deletion not checked",
    nichtGeprueft: "not checked",
    geaendert: "changed after the check",
    einzigesKommando: "gate.mjs: the commands are 'pre-commit', 'post-commit' and 'post-merge'.\n",
    nichtGestartet: "{hook}: card check not started — {grund}\n",
    exitCode: "exit {status}",
  }),
});

let sprache = null;
let config = null;

/** Die Team-Config aus `.claude/workflow.config.json` im cwd, einmal gelesen; kaputt oder fehlend: `{}`. */
function projektConfig() {
  if (config === null) {
    try {
      config = JSON.parse(readFileSync(join(process.cwd(), ".claude", "workflow.config.json"), "utf-8")) ?? {};
    } catch {
      // keine oder kaputte Config: Vorgabe
      config = {};
    }
  }
  return config;
}

/**
 * Die Sprache des Projekts, einmal aus `.claude/workflow.config.json` im cwd gelesen:
 * `language`, sonst `sprache`, sonst `de`; ein unbekannter Wert gilt als `de`.
 * SYNC: dieselbe Regel wie `projektSprache` in kit/board/namen.mjs.
 */
function projektSprache() {
  if (sprache === null) {
    const wert = projektConfig().language ?? projektConfig().sprache;
    sprache = wert === "en" || wert === "de" ? wert : "de";
  }
  return sprache;
}

/** Der Text zu `schluessel` in der Projektsprache, die Platzhalter `{name}` ersetzt. */
function text(schluessel, werte = {}) {
  return TEXTE[projektSprache()][schluessel].replaceAll(/\{([A-Za-z_]\w*)\}/g, (_, name) => String(werte[name]));
}

function ab(grund, pfad) {
  const werte = { grund, pfad, hilfe: HILFE };
  process.stderr.write(text(pfad ? "abgewiesenPfad" : "abgewiesen", werte));
  process.exit(1);
}

function git(...args) {
  return spawnSync("git", args, { cwd: process.cwd(), encoding: "utf-8" });
}

/**
 * Wo liegt `checks.mjs`? Zwei Orte, in dieser Reihenfolge.
 *
 * Das Nachbar-Muster aus `night.mjs` traegt hier nicht: Neben dieser Datei liegt
 * keine `checks.mjs`. Im Zielprojekt schreibt der Installer sie nach
 * `.claude/kit/` (dort per .gitignore ausgeschlossen), im Kit-Repo und seinem
 * CI-Checkout liegt sie unter `kit/`. Ein fester Pfad ginge in einer der beiden
 * Lagen schief — und weil ein nicht lauffaehiges Gate nicht durchlaesst, waere
 * das dort jeder Commit.
 */
async function ladeZusammenfassungPfad() {
  const orte = [
    join(__dirname, "..", ".claude", "kit", "checks.mjs"),
    join(__dirname, "..", "kit", "checks.mjs"),
  ];
  for (const ort of orte) {
    if (existsSync(ort)) return (await import(pathToFileURL(ort).href)).zusammenfassungPfad;
  }
  ab(text("checksFehlt"));
  return null;
}

/** Die Zusammenfassung, gepruft auf Form. */
function leseZusammenfassung(pfad) {
  if (!existsSync(pfad)) ab(text("zusammenfassungFehlt"));
  let daten;
  try {
    daten = JSON.parse(readFileSync(pfad, "utf-8"));
  } catch {
    ab(text("zusammenfassungUnlesbar"));
  }
  // Ein gueltiges JSON ohne `hashes` stammt aus einer checks.mjs vor Issue #469.
  // Ohne diesen Zweig wuerde jede Datei einzeln als ungeprueft gemeldet, und das
  // genannte Heilkommando heilte nichts, solange die alte Datei liegt.
  if (!Array.isArray(daten.laufen) || daten.hashes === null || typeof daten.hashes !== "object") {
    ab(text("altesFormat"));
  }
  return daten;
}

/**
 * Was in den Commit geht, aus dem Index.
 *
 * `-z`, weil git Pfade mit Nicht-ASCII sonst quotet (`core.quotePath`) und die
 * Schluessel in `hashes` unquotiert sind. `--no-renames`, weil eine R-Zeile zwei
 * Pfade traegt: Ohne die Option braeuchte das Gate Sonderlogik, mit ihr erscheint
 * eine Umbenennung als Loeschung plus Neuanlage und faellt unter die zwei Regeln,
 * die es ohnehin hat.
 */
function indexEintraege() {
  const res = git("diff", "--cached", "--name-status", "-z", "--no-renames");
  if (res.status !== 0) ab(text("gitFehler", { kommando: "git diff --cached", fehler: (res.stderr || "").trim() }));
  const felder = res.stdout.split("\0");
  const eintraege = [];
  for (let i = 0; i + 1 < felder.length; i += 2) {
    if (felder[i]) eintraege.push({ status: felder[i], pfad: felder[i + 1] });
  }
  return eintraege;
}

/** Blob-Hash je gestagtem Pfad, so wie git ihn committen wird. */
function indexHashes() {
  const res = git("ls-files", "--stage", "-z");
  if (res.status !== 0) ab(text("gitFehler", { kommando: "git ls-files --stage", fehler: (res.stderr || "").trim() }));
  const hashes = new Map();
  for (const zeile of res.stdout.split("\0")) {
    if (!zeile) continue;
    // "<mode> <sha> <stage>\t<pfad>"
    const tab = zeile.indexOf("\t");
    if (tab < 0) continue;
    const teile = zeile.slice(0, tab).split(" ");
    hashes.set(zeile.slice(tab + 1), teile[1]);
  }
  return hashes;
}

function preCommit(zusammenfassungPfad) {
  const daten = leseZusammenfassung(zusammenfassungPfad(process.cwd()));

  // Das erste `rot`, erst ohne ein solches das erste ungruene (Issue #1072): Nach einem
  // roten Teillauf stehen vor der roten Gruppe nicht gestartete, und die Meldung soll die
  // Pruefung nennen, die tatsaechlich rot war.
  // SYNC: dieselbe Regel wie `rotesKommando` in kit/checks.mjs und `lesePruefung` in kit/night.mjs.
  const rot = daten.laufen.find((e) => e.ergebnis === "rot") ?? daten.laufen.find((e) => e.ergebnis !== "gruen");
  if (rot) ab(text("pruefungEndete", { ergebnis: rot.ergebnis, cmd: rot.cmd }));

  // Ein Bereichslauf ist ein TEILNACHWEIS (Issue #922, Code-Review): `--area` grenzt
  // die gefahrenen Pruefungen auf einen Bereich ein, bestimmt `geaendert` und `hashes`
  // aber weiterhin aus dem Anker. Ohne diesen Zweig traegt jede geaenderte Datei einen
  // Hash und das Gate liesse den Commit durch — auch fuer Bereiche, deren Pruefungen nie
  // liefen. Der eingegrenzte Lauf bleibt richtig; er ist nur kein Abschlussnachweis.
  if (typeof daten.bereichWahl === "string") {
    ab(text("bereichslauf", { bereich: daten.bereichWahl, hilfe: HILFE }));
  }

  const geprueft = daten.hashes;
  const imIndex = indexHashes();
  for (const { status, pfad } of indexEintraege()) {
    const geloescht = status.startsWith("D");
    if (!(pfad in geprueft)) {
      ab(text(geloescht ? "loeschungNichtGeprueft" : "nichtGeprueft"), pfad);
    }
    if (geloescht) continue;
    if (geprueft[pfad] !== imIndex.get(pfad)) ab(text("geaendert"), pfad);
  }
  return 0;
}

// --- Die Pruefung je Karte nach Commit und Merge (Issue #2156, Plan #2145 E1) ---------------
//
// `post-commit` und `post-merge` starten `night.mjs --card-check --detach`. Beide halten nie
// etwas auf: Der Commit steht schon, jeder Ausgang endet mit 0, ein Fehler beim Start ist eine
// Zeile. Gestartet wird nur, wenn alles zutrifft; sonst still nichts.

/** Ein Pfad aus `git rev-parse <option>`, absolut zum cwd; `null`, wenn git ihn nicht nennt. */
function gitPfad(...args) {
  const res = git("rev-parse", ...args);
  return res.status === 0 ? resolve(process.cwd(), res.stdout.trim()) : null;
}

/**
 * Das Kit-Verzeichnis mit dem Runner: im Projekt `.claude/kit/`, sonst — im CI-Checkout des
 * Kit-Repos — `kit/`; dieselbe Reihenfolge wie `ladeZusammenfassungPfad`. Gesucht wird unter
 * dem cwd, das git fuer einen Hook auf die Wurzel des Arbeitsbaums setzt, nicht neben dieser
 * Datei: Ein Fixture, das auf ein fremdes `.githooks` zeigt, startet so nie dessen Runner.
 */
function kitVerzeichnis() {
  return [join(process.cwd(), ".claude", "kit"), join(process.cwd(), "kit")]
    .find((ort) => existsSync(join(ort, "night.mjs"))) ?? null;
}

/**
 * Pro mit dem Teil `card-check`? Die Ausgabe steht in `board/fassung.mjs` als `KIT_AUSGABE`;
 * fehlt sie, gilt Pro. Der Teil muss zudem liegen — ohne ihn bliebe der Start wirkungslos.
 * SYNC: dieselbe Regel wie `ausgabe` in kit/board/ausgabe.mjs, die Datei des Teils wie
 * `PRO_TEILE["card-check"]` dort.
 */
function kartenpruefungInPro(kitDir) {
  let fassung = "";
  try {
    fassung = readFileSync(join(kitDir, "board", "fassung.mjs"), "utf-8");
  } catch {
    // keine Fassung: Vorgabe Pro
  }
  if (/^export const KIT_AUSGABE = "community";$/m.test(fassung)) return false;
  return existsSync(join(kitDir, "night", "kartenpruefung.mjs"));
}

/**
 * Steht der Arbeitsbaum auf `mainBranch` der Hauptkopie, ohne laufenden Rebase, mit
 * `origin/<mainBranch>`? Ohne den Ursprungszweig fehlt der Pruefung je Karte die Basis
 * (Plan #2145, E4) — wie im Abschluss eines Laufs (`abschlussDesLaufs` in kit/night/kitstand.mjs).
 * Ein Worktree hat ein eigenes Git-Verzeichnis neben dem gemeinsamen; nur die Hauptkopie nicht.
 */
function aufHauptzweigDerHauptkopie() {
  const mainBranch = typeof projektConfig().mainBranch === "string" ? projektConfig().mainBranch : "main";
  const zweig = git("symbolic-ref", "-q", "--short", "HEAD");
  if (zweig.status !== 0 || zweig.stdout.trim() !== mainBranch) return false;
  const eigenes = gitPfad("--git-dir");
  if (!eigenes || eigenes !== gitPfad("--git-common-dir")) return false;
  if (["rebase-merge", "rebase-apply"].some((name) => existsSync(gitPfad("--git-path", name)))) return false;
  return git("rev-parse", "--verify", "-q", `refs/remotes/origin/${mainBranch}^{commit}`).status === 0;
}

/**
 * Die Variablen, mit denen git im Hook jeden Git-Aufruf an dieses Repo bindet, aus
 * `git rev-parse --local-env-vars` ohne die drei zur Konfiguration (`GIT_CONFIG`,
 * `GIT_CONFIG_PARAMETERS`, `GIT_CONFIG_COUNT`), die der Aufrufer mitbringt. Erbt der Runner etwa
 * `GIT_INDEX_FILE=.git/index`, scheitert sein `git worktree add`, weil `.git` im neuen Worktree
 * eine Datei ist (Issue #2165).
 */
const REPO_BINDUNG = Object.freeze([
  "GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_OBJECT_DIRECTORY", "GIT_DIR", "GIT_WORK_TREE", "GIT_IMPLICIT_WORK_TREE",
  "GIT_GRAFT_FILE", "GIT_INDEX_FILE", "GIT_NO_REPLACE_OBJECTS", "GIT_REPLACE_REF_BASE", "GIT_PREFIX", "GIT_SHALLOW_FILE",
  "GIT_COMMON_DIR",
]);

const ohneRepoBindung = (env) => Object.fromEntries(Object.entries(env).filter(([name]) => !REPO_BINDUNG.includes(name)));

function kartenpruefungAnstossen(hook) {
  const kitDir = kitVerzeichnis();
  if (!kitDir || !kartenpruefungInPro(kitDir) || !aufHauptzweigDerHauptkopie()) return;
  const res = spawnSync(process.execPath, [join(kitDir, "night.mjs"), "--card-check", "--detach"], {
    cwd: process.cwd(), env: ohneRepoBindung(process.env), stdio: ["ignore", "inherit", "pipe"], encoding: "utf-8", timeout: 60_000,
  });
  if (!res.error && res.status === 0) {
    process.stderr.write(res.stderr || "");
  } else {
    const grund = res.error?.message ?? [(res.stderr || "").trim(), text("exitCode", { status: res.status })].filter(Boolean).join(" — ");
    process.stderr.write(text("nichtGestartet", { hook, grund }));
  }
}

/**
 * Laedt ein anderes Programm diese Datei als Modul (der Test der Texttabelle), laeuft das
 * Gate nicht. Im Zweifel laeuft es: Ein Gate, das still nichts tut, liesse jeden Commit durch.
 * realpathSync, weil Node fuer import.meta.url Symlinks aufloest (macOS: /var -> /private/var).
 */
function alsModulGeladen() {
  try {
    return Boolean(process.argv[1]) && realpathSync(process.argv[1]) !== realpathSync(fileURLToPath(import.meta.url));
  } catch {
    return false;
  }
}

if (!alsModulGeladen()) {
  const [befehl] = process.argv.slice(2);
  if (befehl === "post-commit" || befehl === "post-merge") {
    kartenpruefungAnstossen(befehl);
    process.exit(0);
  }
  if (befehl !== "pre-commit") {
    process.stderr.write(text("einzigesKommando"));
    process.exit(2);
  }
  process.exit(preCommit(await ladeZusammenfassungPfad()));
}
