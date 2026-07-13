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
 *
 * Siehe RELEASING.md fuer den Kontext, wann welcher Teil erhoeht wird.
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

function main(argv) {
  const part = argv[0];
  if (!PARTS.includes(part)) {
    fail(`Erwartet einen von: ${PARTS.join(', ')}`);
  }

  const current = parseVersion(readFileSync(VERSION_PATH, 'utf-8'));
  const next = bump(current, part);
  const currentText = formatVersion(current);
  const nextText = formatVersion(next);

  writeFileSync(VERSION_PATH, `${nextText}\n`);
  updatePom(nextText);
  updateFrontend(nextText);

  process.stdout.write(`Version: ${currentText} -> ${nextText}\n`);
}

main(process.argv.slice(2));
