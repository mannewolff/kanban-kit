#!/usr/bin/env node
/**
 * bump-version.mjs — erhoeht die dreiteilige Betriebsversion (X.Y.Z) in VERSION, pom.xml und
 * frontend/package.json (+ package-lock.json). Quelle der Wahrheit ist die Datei VERSION im
 * Repo-Root; die anderen beiden Dateien werden daraus synchronisiert.
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

import { readFileSync, writeFileSync } from 'node:fs';
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

  process.stdout.write(`Version: ${currentText} -> ${nextText}\n`);
}

main(process.argv.slice(2));
