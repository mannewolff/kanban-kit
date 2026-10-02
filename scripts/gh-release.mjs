#!/usr/bin/env node
/**
 * gh-release.mjs — legt das GitHub-Release zu einem Versions-Tag an.
 *
 * Der `merge production`-Skill setzt den annotierten Tag `vX.Y.Z` und pusht ihn, erstellt das
 * GitHub-Release aber bewusst NICHT (das ist Mannes Schritt nach dem Merge; die KI darf
 * `gh release create` nicht ausführen). Dieses Skript nimmt genau diesen manuellen Schritt ab:
 * es zieht den Changelog-Block der Version als Release-Beschreibung und ruft `gh release create`.
 *
 * Nutzung:
 *   node scripts/gh-release.mjs                 # Version aus VERSION -> Tag vX.Y.Z
 *   node scripts/gh-release.mjs v1.5.0          # expliziter Tag/Version
 *   node scripts/gh-release.mjs 1.5.0
 *   node scripts/gh-release.mjs --dry-run       # nur zeigen, was passieren würde (kein gh-Aufruf)
 *
 * Voraussetzungen: `gh` ist installiert und authentifiziert, der Tag existiert bereits auf origin
 * (also nach `merge production` / `git push origin vX.Y.Z`). Ohne Changelog-Block fällt das Skript
 * auf `gh --generate-notes` zurück. Idempotent: existiert das Release schon, bricht es sauber ab.
 *
 * Stücklisten (Issue #1337, Plan #1295, E19): `release-images.yml` legt je Abbild die CycloneDX-
 * Stückliste als Workflow-Artefakt `stueckliste-<abbild>` ab. Vor `gh release create` holt das
 * Skript beide aus dem Lauf zum Tag und hängt sie als Assets an. Ist der Lauf nicht abgeschlossen
 * und grün, oder fehlt er, bricht es ab — ein Release ohne Stückliste gibt es über dieses Skript
 * nicht; wer es dennoch will, ruft `gh release create` direkt.
 * Reines Node-Skript, nur git/gh + Dateizugriff, keine externen Abhängigkeiten.
 */

import { existsSync, mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const VERSION_PATH = join(REPO_ROOT, 'VERSION');
const CHANGELOG_PATH = join(REPO_ROOT, 'CHANGELOG.md');
const RELEASE_WORKFLOW = 'release-images.yml';

/** Die Abbilder, deren Stückliste ans Release gehört — Kurznamen wie in `release-images.yml`. */
export const STUECKLISTEN_ABBILDER = ['kanban-kit', 'kanban-kit-backup'];

/** Name des Workflow-Artefakts, das die Stückliste eines Abbilds trägt. */
export function stuecklistenArtefakt(abbild) {
  return `stueckliste-${abbild}`;
}

/** Die Asset-Pfade für `gh release create`, nach dem Herunterladen beider Artefakte in `verzeichnis`. */
export function stuecklistenAssets(verzeichnis) {
  return STUECKLISTEN_ABBILDER.map((abbild) => join(verzeichnis, `${stuecklistenArtefakt(abbild)}.cdx.json`));
}

/**
 * Wählt aus der Antwort von `gh run list --json databaseId,status,conclusion` (neuester Lauf
 * zuerst) den Lauf, dessen Stücklisten ans Release gehen. Maßgeblich ist der neueste Lauf zum Tag:
 * Ein älterer grüner Lauf neben einem neueren roten beschriebe nicht, was zuletzt veröffentlicht
 * wurde. Liefert `{ laufId }` oder `{ fehler }` mit der Abbruchmeldung.
 */
export function laufWaehlen(laeufe, tag) {
  const lauf = laeufe[0];
  if (!lauf) {
    return { fehler: `Kein Lauf von ${RELEASE_WORKFLOW} zum Tag '${tag}' gefunden — ohne ihn gibt es keine Stücklisten. Wurde der Tag gepusht?` };
  }
  if (lauf.status !== 'completed') {
    return { fehler: `Der Lauf ${lauf.databaseId} von ${RELEASE_WORKFLOW} zum Tag '${tag}' läuft noch (Status '${lauf.status}') — nach seinem Ende erneut aufrufen.` };
  }
  if (lauf.conclusion !== 'success') {
    return { fehler: `Der Lauf ${lauf.databaseId} von ${RELEASE_WORKFLOW} zum Tag '${tag}' endete mit '${lauf.conclusion}' — ohne grünen Lauf keine Stücklisten, kein Release.` };
  }
  return { laufId: lauf.databaseId };
}

/** Was `--dry-run` zeigt: die geplanten Aufrufe samt Stücklisten-Assets und die Release-Notes. */
export function trockenlaufText(tag, notes) {
  const assets = stuecklistenAssets('<tmp>');
  const zeilen = [
    `[dry-run] gh run list --workflow ${RELEASE_WORKFLOW} --branch ${tag} --json databaseId,status,conclusion`,
    ...STUECKLISTEN_ABBILDER.map((abbild) => `[dry-run] gh run download <lauf> --name ${stuecklistenArtefakt(abbild)} --dir <tmp>`),
    `[dry-run] gh release create ${tag} --title ${tag} ` + (notes ? '--notes <Changelog-Block>' : '--generate-notes') + ` --verify-tag ${assets.join(' ')}`,
    '',
    notes ? `--- Release-Notes (${tag}) ---\n${notes}` : `(kein Changelog-Block für ${tag.slice(1)} gefunden — würde --generate-notes nutzen)`,
  ];
  return zeilen.join('\n') + '\n';
}

function fail(message) {
  process.stderr.write(`Fehler: ${message}\n`);
  process.exit(1);
}

/** Führt ein Kommando aus, ohne bei nicht-null Exit zu werfen: liefert { ok, stdout }. */
function tryRun(cmd, args) {
  try {
    return { ok: true, stdout: execFileSync(cmd, args, { cwd: REPO_ROOT, encoding: 'utf-8' }) };
  } catch (error) {
    return { ok: false, stdout: error.stdout?.toString() ?? '' };
  }
}

function readVersion() {
  if (!existsSync(VERSION_PATH)) fail('VERSION nicht gefunden — im Repo-Root ausführen.');
  const version = readFileSync(VERSION_PATH, 'utf-8').trim();
  if (!/^\d+\.\d+\.\d+$/.test(version)) fail(`VERSION enthält keine gültige X.Y.Z-Version: '${version}'`);
  return version;
}

function parseArgs(argv) {
  const options = { dryRun: false, tag: null };
  for (const arg of argv) {
    if (arg === '--dry-run') options.dryRun = true;
    else if (arg.startsWith('--')) fail(`Unbekanntes Argument: '${arg}'`);
    else if (options.tag === null) options.tag = arg;
    else fail(`Unerwartetes Argument: '${arg}'`);
  }
  return options;
}

/** Normalisiert Version/Tag zu 'vX.Y.Z'; validiert das Format. */
function normalizeTag(raw) {
  const version = raw.replace(/^v/, '');
  if (!/^\d+\.\d+\.\d+$/.test(version)) fail(`Kein gültiger Tag/Version (erwartet vX.Y.Z): '${raw}'`);
  return `v${version}`;
}

/** Der Changelog-Block der Version als Release-Notes (ohne die '## [..]'-Überschrift). */
function changelogNotes(version) {
  if (!existsSync(CHANGELOG_PATH)) return null;
  const text = readFileSync(CHANGELOG_PATH, 'utf-8');
  const blocks = text.split(/^(?=## \[)/m);
  const block = blocks.find((b) => b.startsWith(`## [${version}]`));
  if (!block) return null;
  const body = block.split('\n').slice(1).join('\n').trim();
  return body || null;
}

/** Lädt beide Stücklisten aus dem Lauf zum Tag in ein Temp-Verzeichnis; bricht bei jeder Lücke ab. */
function stuecklistenHolen(tag) {
  const liste = tryRun('gh', ['run', 'list', '--workflow', RELEASE_WORKFLOW, '--branch', tag, '--json', 'databaseId,status,conclusion']);
  if (!liste.ok) fail(`'gh run list' für ${RELEASE_WORKFLOW} schlug fehl.`);
  const { laufId, fehler } = laufWaehlen(JSON.parse(liste.stdout), tag);
  if (fehler) fail(fehler);

  const verzeichnis = mkdtempSync(join(tmpdir(), 'gh-release-'));
  for (const abbild of STUECKLISTEN_ABBILDER) {
    const artefakt = stuecklistenArtefakt(abbild);
    const download = tryRun('gh', ['run', 'download', String(laufId), '--name', artefakt, '--dir', verzeichnis]);
    if (!download.ok) fail(`Artefakt '${artefakt}' fehlt im Lauf ${laufId} von ${RELEASE_WORKFLOW}.`);
  }
  const assets = stuecklistenAssets(verzeichnis);
  const fehlend = assets.filter((pfad) => !existsSync(pfad));
  if (fehlend.length > 0) fail(`Stückliste nicht im Artefakt gefunden: ${fehlend.join(', ')}`);
  return assets;
}

function main(argv) {
  const options = parseArgs(argv);
  const version = options.tag ? normalizeTag(options.tag).slice(1) : readVersion();
  const tag = `v${version}`;

  // Tag muss existieren (lokal) — sonst wurde 'merge production' / der Tag-Push noch nicht gemacht.
  const tagList = tryRun('git', ['tag', '--list', tag]);
  if (!tagList.ok || tagList.stdout.trim() !== tag) {
    fail(`Tag '${tag}' existiert lokal nicht. Erst 'merge production' ausführen bzw. 'git push origin ${tag}' — dann hier erneut.`);
  }

  const notes = changelogNotes(version);
  const notesArgs = notes ? ['--notes', notes] : ['--generate-notes'];

  if (options.dryRun) {
    process.stdout.write(trockenlaufText(tag, notes));
    return;
  }

  // Idempotenz: existiert das Release schon, nichts tun.
  const existing = tryRun('gh', ['release', 'view', tag, '--json', 'tagName']);
  if (existing.ok) {
    process.stdout.write(`Release '${tag}' existiert bereits — nichts zu tun.\n`);
    return;
  }

  const assets = stuecklistenHolen(tag);

  execFileSync('gh', ['release', 'create', tag, '--title', tag, ...notesArgs, '--verify-tag', ...assets], {
    cwd: REPO_ROOT,
    stdio: 'inherit',
  });
  process.stdout.write(`\nRelease '${tag}' angelegt.\n`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main(process.argv.slice(2));
}
