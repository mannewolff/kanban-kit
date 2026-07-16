#!/usr/bin/env node
/**
 * gen-changelog.mjs — erzeugt/aktualisiert CHANGELOG.md im Keep-a-Changelog-Format.
 *
 * Sammelt die Commit-Titel seit dem letzten Release (roher Dump, keine manuelle Kuration)
 * und fügt sie als neuen Versionsblock oben in CHANGELOG.md ein. Die Version stammt aus der
 * Datei VERSION (Quelle der Wahrheit, siehe bump-version.mjs), das Datum aus dem Release-Commit
 * bzw. dem Parameter --date (reproduzierbar, bewusst kein new Date()).
 *
 * Nutzung:
 *   node scripts/gen-changelog.mjs                 (Range: <Tag der Vorversion>..HEAD)
 *   node scripts/gen-changelog.mjs --date 2026-07-15
 *   node scripts/gen-changelog.mjs --end <ref>     (Range-Endpunkt statt HEAD)
 *
 * Range-Abgrenzung läuft über Git-Tags (vX.Y.Z), die `bump-version.mjs tag` nach dem Release-
 * Commit setzt (siehe RELEASING.md). Für die Range-Untergrenze ist das Timing des NEUEN Tags
 * unerheblich: previousVersionTag() sucht ausschließlich Tags mit Version < der aktuellen und
 * findet damit den Tag der vorherigen Minor-Version, unabhängig davon, ob/wann der Tag der
 * aktuellen Version schon existiert. Existiert noch kein passender Tag (erster Lauf), greift der
 * definierte Startpunkt INITIAL_SINCE für den retroaktiven 0.6.0-Block. Reines Node-Skript, nur
 * git + Dateizugriff, keine externen Abhängigkeiten. Idempotent: ein bereits vorhandener
 * Versionsblock wird nicht dupliziert.
 */

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const VERSION_PATH = join(REPO_ROOT, 'VERSION');
const CHANGELOG_PATH = join(REPO_ROOT, 'CHANGELOG.md');

/**
 * Definierter Startpunkt für den allerersten Lauf (retroaktiver 0.6.0-Block): der Parent des
 * ersten 0.6.0-Feature-Commits (Issue #170), also das Ende des 0.5.0-Release. Sobald Minor-
 * Releases über `bump-version.mjs tag` getaggt werden, wird dieser Fallback nicht mehr gebraucht —
 * die Range-Abgrenzung läuft dann über den Tag der Vorversion.
 */
const INITIAL_SINCE = '61812816ecf16c125a7511ed762886e73de858e9';

/** Titel-Muster, die als Release-/Format-/Prozessrauschen aus dem Changelog fallen. */
const NOISE = [/^Release: Version/, /^Format:.*Nachtrag/];

const KEEP_A_CHANGELOG_HEADER = `# Changelog

Alle nennenswerten Änderungen an kanban-kit werden hier festgehalten.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/); die
Versionierung folgt der dreiteiligen Betriebsversion (siehe [RELEASING.md](RELEASING.md)). Die
Einträge je Version sind ein automatischer Auszug der Commit-Titel seit dem letzten Release,
erzeugt von \`scripts/gen-changelog.mjs\`.
`;

function fail(message) {
  process.stderr.write(`Fehler: ${message}\n`);
  process.exit(1);
}

function git(args) {
  return execFileSync('git', args, { cwd: REPO_ROOT, encoding: 'utf-8' });
}

function readVersion() {
  const version = readFileSync(VERSION_PATH, 'utf-8').trim();
  if (!/^\d+\.\d+\.\d+$/.test(version)) {
    fail(`VERSION enthält keine gültige X.Y.Z-Version: '${version}'`);
  }
  return version;
}

function repoUrl() {
  const raw = git(['remote', 'get-url', 'origin']).trim();
  const match = /github\.com[:/]([^/]+\/[^/]+?)(?:\.git)?$/.exec(raw);
  return match ? `https://github.com/${match[1]}` : raw.replace(/\.git$/, '');
}

function parseSemver(text) {
  const match = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(text.trim());
  return match ? [Number(match[1]), Number(match[2]), Number(match[3])] : null;
}

function compareSemver(a, b) {
  for (let i = 0; i < 3; i += 1) {
    if (a[i] !== b[i]) return a[i] - b[i];
  }
  return 0;
}

/** Der höchste vorhandene Tag vX.Y.Z, dessen Version strikt kleiner als die aktuelle ist. */
function previousVersionTag(currentVersion) {
  const current = parseSemver(currentVersion);
  return git(['tag', '--list', 'v*'])
    .split('\n')
    .map((tag) => ({ tag: tag.trim(), semver: parseSemver(tag) }))
    .filter((entry) => entry.semver && compareSemver(entry.semver, current) < 0)
    .sort((a, b) => compareSemver(b.semver, a.semver))
    .map((entry) => entry.tag)[0];
}

function collectEntries(sinceRef, endRef, url) {
  const raw = git(['log', '--no-merges', '--format=%s', `${sinceRef}..${endRef}`]);
  return raw
    .split('\n')
    .map((title) => title.trim())
    .filter(Boolean)
    .filter((title) => !NOISE.some((pattern) => pattern.test(title)))
    .map((title) => title.replace(/\(Issue #(\d+)\)/g, (_match, n) => `([#${n}](${url}/issues/${n}))`));
}

function releaseDate(endRef, argDate) {
  if (argDate) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(argDate)) {
      fail(`--date erwartet das Format JJJJ-MM-TT: '${argDate}'`);
    }
    return argDate;
  }
  return git(['log', '-1', '--format=%cs', endRef]).trim();
}

function parseArgs(argv) {
  const options = { date: null, end: 'HEAD' };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--date') options.date = argv[(i += 1)];
    else if (argv[i] === '--end') options.end = argv[(i += 1)];
    else fail(`Unbekanntes Argument: '${argv[i]}'`);
  }
  return options;
}

function main(argv) {
  const options = parseArgs(argv);
  const version = readVersion();
  const url = repoUrl();
  const sinceRef = previousVersionTag(version) ?? INITIAL_SINCE;
  const date = releaseDate(options.end, options.date);
  const entries = collectEntries(sinceRef, options.end, url);

  const body = entries.length ? entries.map((entry) => `- ${entry}`).join('\n') : '- (keine Änderungen)';
  const block = `## [${version}] – ${date}\n\n${body}\n`;

  const existing = existsSync(CHANGELOG_PATH)
    ? readFileSync(CHANGELOG_PATH, 'utf-8')
    : KEEP_A_CHANGELOG_HEADER;

  const versionBlockPattern = new RegExp(`^## \\[${version.replace(/\./g, '\\.')}\\]`, 'm');
  if (versionBlockPattern.test(existing)) {
    process.stdout.write(`CHANGELOG.md enthält [${version}] bereits — nichts zu tun.\n`);
    return;
  }

  const firstBlock = existing.search(/^## \[/m);
  const updated =
    firstBlock === -1
      ? `${existing.trimEnd()}\n\n${block}`
      : `${existing.slice(0, firstBlock).trimEnd()}\n\n${block}\n${existing.slice(firstBlock).trimEnd()}\n`;

  writeFileSync(CHANGELOG_PATH, updated);
  process.stdout.write(
    `CHANGELOG.md: Block [${version}] eingefügt (${entries.length} Einträge, Range ${sinceRef}..${options.end}).\n`,
  );
}

main(process.argv.slice(2));
