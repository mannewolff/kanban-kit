#!/usr/bin/env node
/**
 * bump-version.mjs — erhoeht die dreiteilige Betriebsversion (X.Y.Z) in VERSION, pom.xml und
 * frontend/package.json (+ package-lock.json). Quelle der Wahrheit ist die Datei VERSION im
 * Repo-Root; die anderen beiden Dateien werden daraus synchronisiert.
 *
 * Seit Issue #1267 ziehen `minor` und `major` zusaetzlich die Tags der EIGENEN Abbilder in den
 * Betriebsdateien nach: die ghcr-Zeile in docker-compose.yml und in docker-compose.backup.yml
 * sowie die beiden ghcr-Eintraege in scripts/bausteine.json. Warum `patch` aussen vor bleibt: Nur
 * der Tag vX.Y.Z eines `merge production` loest .github/workflows/release-images.yml aus, ein
 * `push main` veroeffentlicht kein Abbild. Eine Betriebsdatei, die nach einem Patch-Bump auf
 * 2.14.3 zeigte, zeigte auf etwas, das nie gebaut wurde.
 *
 * Nutzung:
 *   node scripts/bump-version.mjs patch   (Z+1)              -> push main
 *   node scripts/bump-version.mjs minor   (Y+1, Z=0)         -> merge production
 *   node scripts/bump-version.mjs major   (X+1, Y=0, Z=0)    -> nur auf explizite Anordnung
 *   node scripts/bump-version.mjs tag     annotated Tag vX.Y.Z auf HEAD -> merge production,
 *                                          NACH dem Release-Commit (sonst zeigt der Tag auf den
 *                                          Commit davor statt auf den Release-Stand)
 *
 * Siehe RELEASING.md fuer den Kontext, wann welcher Teil/Befehl faellig ist.
 */

import { readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const VERSION_PATH = join(REPO_ROOT, 'VERSION');
const POM_PATH = join(REPO_ROOT, 'pom.xml');
const FRONTEND_DIR = join(REPO_ROOT, 'frontend');

const PARTS = ['major', 'minor', 'patch'];

function fail(message) {
  process.stderr.write(`Fehler: ${message}\n`);
  process.exit(1);
}

function parseVersion(text) {
  const trimmed = text.trim();
  const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(trimmed);
  if (!match) {
    fail(`VERSION enthält keine gültige X.Y.Z-Version: '${trimmed}'`);
  }
  const [, major, minor, patch] = match;
  return { major: Number(major), minor: Number(minor), patch: Number(patch) };
}

function formatVersion({ major, minor, patch }) {
  return `${major}.${minor}.${patch}`;
}

/** Reset-Semantik: eine Erhöhung setzt alle niedrigeren Teile auf 0 zurück. */
function bump({ major, minor, patch }, part) {
  if (part === 'major') return { major: major + 1, minor: 0, patch: 0 };
  if (part === 'minor') return { major, minor: minor + 1, patch: 0 };
  return { major, minor, patch: patch + 1 };
}

function updatePom(newVersion) {
  const pom = readFileSync(POM_PATH, 'utf-8');
  const pattern = /(<artifactId>manban<\/artifactId>\s*\n\s*<version>)[^<]+(<\/version>)/;
  if (!pattern.test(pom)) {
    fail('Projekt-Version in pom.xml (Artefakt "manban") nicht gefunden');
  }
  writeFileSync(POM_PATH, pom.replace(pattern, `$1${newVersion}$2`));
}

/** Die beiden eigenen Abbilder, die dieses Projekt selbst nach ghcr.io veroeffentlicht. */
export const GHCR_ANWENDUNG = 'ghcr.io/mannewolff/kanban-kit';
export const GHCR_SICHERUNG = 'ghcr.io/mannewolff/kanban-kit-backup';

/**
 * Ob ein Bump die eigenen Image-Tags in den Betriebsdateien mitzieht. Nur `minor` und `major`:
 * siehe Kopfkommentar.
 */
export function betriebsdateienFaellig(part) {
  return part === 'minor' || part === 'major';
}

/**
 * Ersetzt in `datei` den Tag hinter `abbild` durch `neueVersion`.
 *
 * Getroffen wird die Zeile ueber den ABBILDNAMEN, nicht ueber die alte Version: Zwischen zwei
 * Releases heben mehrere `patch`-Bumps VERSION an, ohne die Betriebsdateien anzufassen — der
 * naechste `minor` traefe die Zeile also mit einer aelteren Fassung an und liefe mit einem Muster
 * aus der alten Version ins Leere. Das `:` hinter dem Namen grenzt die Anwendung gegen
 * `…-backup` ab.
 */
function tagInDatei(pfad, abbild, neueVersion) {
  const text = readFileSync(pfad, 'utf-8');
  const muster = new RegExp(`(image:\\s*${abbild.replaceAll('.', '\\.')}:)\\S+`);
  if (!muster.test(text)) {
    throw new Error(`Zeile 'image: ${abbild}:<Fassung>' in ${pfad} nicht gefunden`);
  }
  writeFileSync(pfad, text.replace(muster, `$1${neueVersion}`));
}

/**
 * Zieht in `bausteine.json` Bezugsstelle und Fassung des Bausteins zu `abbild` nach. Die Datei
 * wird als JSON gelesen und mit zwei Leerzeichen Einrueckung zurueckgeschrieben — genau die Form,
 * in der sie im Repo liegt.
 */
function bausteinNachziehen(bausteine, abbild, neueVersion, pfad) {
  const baustein = bausteine.find((eintrag) => eintrag.bezugsstelle?.startsWith(`${abbild}:`));
  if (!baustein) {
    throw new Error(`Baustein zu '${abbild}' in ${pfad} nicht gefunden`);
  }
  baustein.bezugsstelle = `${abbild}:${neueVersion}`;
  baustein.fassung = neueVersion;
}

/**
 * Zieht die Tags der eigenen Abbilder in den Betriebsdateien unterhalb von `wurzel` nach. Fehlt
 * eine der vier Stellen, bricht der Lauf ab statt still weiterzulaufen — dieselbe Haltung wie bei
 * updatePom: Eine Betriebsdatei, die auf ein nie gebautes Abbild zeigt, ist schlimmer als keine.
 */
export function betriebsdateienNachziehen(wurzel, neueVersion) {
  tagInDatei(join(wurzel, 'docker-compose.yml'), GHCR_ANWENDUNG, neueVersion);
  tagInDatei(join(wurzel, 'docker-compose.backup.yml'), GHCR_SICHERUNG, neueVersion);

  const bausteinePfad = join(wurzel, 'scripts', 'bausteine.json');
  const daten = JSON.parse(readFileSync(bausteinePfad, 'utf-8'));
  const bausteine = daten.bausteine ?? [];
  bausteinNachziehen(bausteine, GHCR_ANWENDUNG, neueVersion, bausteinePfad);
  bausteinNachziehen(bausteine, GHCR_SICHERUNG, neueVersion, bausteinePfad);
  writeFileSync(bausteinePfad, `${JSON.stringify(daten, null, 2)}\n`);
}

function updateFrontend(newVersion) {
  execFileSync(
    'npm',
    ['--prefix', FRONTEND_DIR, 'version', newVersion, '--no-git-tag-version', '--allow-same-version'],
    { stdio: 'inherit' },
  );
}

/**
 * Setzt einen annotated Tag `vX.Y.Z` (git tag -a), der den Release-Stand markiert — annotated
 * statt lightweight, damit `git push --follow-tags` (siehe RELEASING.md) ihn mitnimmt; ein
 * lightweight Tag bliebe sonst beim Push liegen und müsste separat gepusht werden. Der Tag ist
 * der Anker für die Range-Abgrenzung von gen-changelog.mjs. Bewusst ein eigener Befehl statt Teil
 * von `minor`: er muss NACH dem Release-Commit laufen (siehe RELEASING.md), sonst zeigt der Tag
 * auf den Commit davor statt auf den eigentlichen Release-Stand. Idempotent: existiert der Tag
 * bereits, wird er nicht neu gesetzt.
 */
function tagRelease(newVersion) {
  const tag = `v${newVersion}`;
  const existing = execFileSync('git', ['tag', '--list', tag], { cwd: REPO_ROOT, encoding: 'utf-8' }).trim();
  if (existing) {
    process.stdout.write(`Tag ${tag} existiert bereits — kein neuer Tag.\n`);
    return;
  }
  execFileSync('git', ['tag', '-a', tag, '-m', `Release ${tag}`], { cwd: REPO_ROOT, stdio: 'inherit' });
  process.stdout.write(`Tag ${tag} (annotated) auf HEAD gesetzt.\n`);
}

function main(argv) {
  const cmd = argv[0];

  if (cmd === 'tag') {
    const current = parseVersion(readFileSync(VERSION_PATH, 'utf-8'));
    tagRelease(formatVersion(current));
    return;
  }

  if (!PARTS.includes(cmd)) {
    fail(`Erwartet einen von: ${PARTS.join(', ')}, tag`);
  }

  const current = parseVersion(readFileSync(VERSION_PATH, 'utf-8'));
  const next = bump(current, cmd);
  const currentText = formatVersion(current);
  const nextText = formatVersion(next);

  writeFileSync(VERSION_PATH, `${nextText}\n`);
  updatePom(nextText);
  updateFrontend(nextText);
  if (betriebsdateienFaellig(cmd)) {
    try {
      betriebsdateienNachziehen(REPO_ROOT, nextText);
    } catch (fehler) {
      fail(fehler.message);
    }
    process.stdout.write(`Eigene Image-Tags in den Betriebsdateien auf ${nextText} gesetzt.\n`);
  }

  process.stdout.write(`Version: ${currentText} -> ${nextText}\n`);
}

// Nur beim direkten Aufruf laufen lassen: Der Test importiert dieses Modul, und ein main() beim
// Import zerlegte die Repo-Dateien mit den Argumenten des Test-Runners.
const direktAufgerufen =
  process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url));
if (direktAufgerufen) {
  main(process.argv.slice(2));
}
