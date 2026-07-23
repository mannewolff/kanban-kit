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
 * Reines Node-Skript, nur git/gh + Dateizugriff, keine externen Abhängigkeiten.
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const VERSION_PATH = join(REPO_ROOT, 'VERSION');
const CHANGELOG_PATH = join(REPO_ROOT, 'CHANGELOG.md');

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
    process.stdout.write(`[dry-run] gh release create ${tag} --title ${tag} ` + (notes ? '--notes <Changelog-Block>' : '--generate-notes') + '\n\n');
    process.stdout.write(notes ? `--- Release-Notes (${tag}) ---\n${notes}\n` : `(kein Changelog-Block für ${version} gefunden — würde --generate-notes nutzen)\n`);
    return;
  }

  // Idempotenz: existiert das Release schon, nichts tun.
  const existing = tryRun('gh', ['release', 'view', tag, '--json', 'tagName']);
  if (existing.ok) {
    process.stdout.write(`Release '${tag}' existiert bereits — nichts zu tun.\n`);
    return;
  }

  execFileSync('gh', ['release', 'create', tag, '--title', tag, ...notesArgs, '--verify-tag'], {
    cwd: REPO_ROOT,
    stdio: 'inherit',
  });
  process.stdout.write(`\nRelease '${tag}' angelegt.\n`);
}

main(process.argv.slice(2));
