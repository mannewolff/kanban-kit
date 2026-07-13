#!/usr/bin/env node
/**
 * migrate-issues-to-github.mjs — Einmalige Migration der lokalen Issue-Dateien
 * (issues/*.md + issues/archive/*.md) auf GitHub Issues + das GitHub Project
 * "kanban-kit" (#13, Owner mannewolff).
 *
 * Delegiert Erzeugung und Board-Zuordnung an board.mjs (.claude/kit/board.mjs),
 * damit exakt derselbe Code-Pfad läuft, der künftig für issueTracker: "github"
 * verwendet wird — keine Duplizierung der GraphQL-/gh-project-Logik.
 *
 * Zusätzlich zu board.mjs: Issues mit lokalem Status "done" werden nach der
 * Board-Zuordnung per `gh issue close` auch als GitHub-Issue geschlossen
 * (board.mjs selbst rührt den Open/Closed-Zustand nicht an).
 *
 * Idempotent: bereits migrierte lokale IDs werden anhand der Mapping-Datei
 * übersprungen — ein Abbruch (z. B. Rate-Limit) kann per erneutem Lauf
 * fortgesetzt werden.
 *
 * Nutzung: node scripts/migrate-issues-to-github.mjs
 */

import { readFileSync, readdirSync, existsSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BOARD_MJS = join(REPO_ROOT, '.claude', 'kit', 'board.mjs');
const MAP_PATH = join(REPO_ROOT, 'scripts', '.issue-migration-map.json');
const REPO = 'mannewolff/kanban-kit';

const SOURCE_DIRS = [join(REPO_ROOT, 'issues'), join(REPO_ROOT, 'issues', 'archive')];

function parseFrontmatter(content) {
  const match = content.match(/^---\n([\s\S]*?)\n---\n?([\s\S]*)$/);
  if (!match) return { meta: {}, body: content };
  const meta = {};
  for (const line of match[1].split('\n')) {
    const m = line.match(/^(\w+):\s*(.*)$/);
    if (m) meta[m[1]] = m[2].trim().replace(/^["']|["']$/g, '');
  }
  return { meta, body: match[2] };
}

function loadLocalIssues() {
  const issues = [];
  for (const dir of SOURCE_DIRS) {
    if (!existsSync(dir)) continue;
    for (const file of readdirSync(dir)) {
      if (!file.endsWith('.md')) continue;
      const raw = readFileSync(join(dir, file), 'utf-8');
      const { meta, body } = parseFrontmatter(raw);
      issues.push({
        id: meta.id || file.replace('.md', ''),
        type: meta.type || 'task',
        title: meta.title || '(ohne Titel)',
        status: meta.status || 'backlog',
        created: meta.created || '',
        body: body.trim(),
      });
    }
  }
  issues.sort((a, b) => Number(a.id) - Number(b.id));
  return issues;
}

function loadMap() {
  if (!existsSync(MAP_PATH)) return {};
  try {
    return JSON.parse(readFileSync(MAP_PATH, 'utf-8'));
  } catch {
    return {};
  }
}

function saveMap(map) {
  writeFileSync(MAP_PATH, JSON.stringify(map, null, 2) + '\n');
}

function board(args) {
  const out = execFileSync('node', [BOARD_MJS, ...args], { encoding: 'utf-8' });
  return JSON.parse(out);
}

function sleep(ms) {
  return new Promise((res) => setTimeout(res, ms));
}

function buildBody(issue) {
  const footer = `\n\n---\n_Migriert aus dem lokalen Issue-Tracker, ursprüngliche ID \`#${issue.id}\`` +
    (issue.created ? `, erstellt ${issue.created}` : '') + `._`;
  return issue.body + footer;
}

/** Schließt ein Issue mit kurzem Retry — gh/GraphQL liefert gelegentlich einen
 * transienten Fehler, obwohl der Close serverseitig durchläuft. */
function closeWithRetry(githubNumber, attempts = 3) {
  let lastErr;
  for (let i = 1; i <= attempts; i++) {
    try {
      execFileSync('gh', ['issue', 'close', githubNumber, '--repo', REPO], { encoding: 'utf-8' });
      return true;
    } catch (e) {
      lastErr = e;
      if (i < attempts) {
        console.error(`  [retry ${i}/${attempts - 1}] close #${githubNumber} fehlgeschlagen, erneut...`);
      }
    }
  }
  console.error(`  [WARNUNG] close #${githubNumber} endgültig fehlgeschlagen: ${lastErr.message}`);
  return false;
}

async function migrateOne(issue, map) {
  if (map[issue.id]) {
    console.log(`[skip] #${issue.id} bereits migriert -> GitHub #${map[issue.id].githubNumber}`);
    return;
  }

  const created = board(['issue', 'create', '--title', issue.title, '--body', buildBody(issue)]);
  const githubNumber = created.id;

  if (issue.status && issue.status !== 'backlog') {
    board(['issue', 'move', githubNumber, issue.status]);
  }

  // Mapping SOFORT nach create+move sichern — unabhängig davon, ob der folgende
  // close-Schritt klappt. Sonst würde ein erneuter Lauf nach einem Close-Fehler
  // dasselbe lokale Issue ein zweites Mal auf GitHub anlegen (Duplikat).
  map[issue.id] = { githubNumber, status: issue.status, title: issue.title, closed: false };
  saveMap(map);

  if (issue.status === 'done') {
    map[issue.id].closed = closeWithRetry(githubNumber);
    saveMap(map);
    if (!map[issue.id].closed) await sleep(500);
  }

  console.log(`[ok]   #${issue.id} -> GitHub #${githubNumber} (${issue.status})`);
}

async function main() {
  const issues = loadLocalIssues();
  const map = loadMap();
  console.log(`${issues.length} lokale Issues gefunden. Ziel: ${REPO}, Project #13.\n`);

  const failedCloses = [];
  for (const issue of issues) {
    try {
      await migrateOne(issue, map);
      if (map[issue.id]?.status === 'done' && !map[issue.id]?.closed) {
        failedCloses.push(issue.id);
      }
    } catch (e) {
      console.error(`[FEHLER] #${issue.id} '${issue.title}': ${e.message}`);
      console.error('Abbruch. Erneuter Lauf überspringt bereits migrierte Issues.');
      process.exitCode = 1;
      break;
    }
  }

  console.log(`\nFertig. ${Object.keys(map).length}/${issues.length} Issues migriert.`);
  if (failedCloses.length > 0) {
    console.log(`Nicht geschlossen (manuell nachziehen): ${failedCloses.join(', ')}`);
  }
}

main();
