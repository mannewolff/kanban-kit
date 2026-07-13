#!/usr/bin/env node
/**
 * fix-issue-cross-references.mjs — Einmaliger Folgeschritt zur Migration
 * (migrate-issues-to-github.mjs): korrigiert `#DDDD`-Querverweise INNERHALB der
 * migrierten Issue-Bodies, die noch auf die alten lokalen Issue-Nummern zeigen
 * (z. B. "siehe #0097") statt auf die tatsächliche GitHub-Nummer (z. B. "#101").
 * Ohne diese Korrektur linkt/schließt GitHub beim Anzeigen das FALSCHE Issue,
 * weil #DDDD als literale GitHub-Issue-Nummer interpretiert wird.
 *
 * Nutzt die von migrate-issues-to-github.mjs geschriebene Mapping-Datei
 * (scripts/.issue-migration-map.json) als Quelle der Wahrheit.
 *
 * Nutzung: node scripts/fix-issue-cross-references.mjs
 */

import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
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
      issues.push({ id: meta.id || file.replace('.md', ''), created: meta.created || '', body: body.trim() });
    }
  }
  return issues;
}

function rewriteCrossReferences(body, map) {
  return body.replace(/#(\d{4})/g, (match, digits) => {
    const target = map[digits];
    return target ? `#${target.githubNumber}` : match;
  });
}

function buildBody(issue, map) {
  const rewritten = rewriteCrossReferences(issue.body, map);
  const footer = `\n\n---\n_Migriert aus dem lokalen Issue-Tracker, ursprüngliche ID \`#${issue.id}\`` +
    (issue.created ? `, erstellt ${issue.created}` : '') + `._`;
  return rewritten + footer;
}

function main() {
  const map = JSON.parse(readFileSync(MAP_PATH, 'utf-8'));
  const issues = loadLocalIssues();

  let fixed = 0;
  for (const issue of issues) {
    const entry = map[issue.id];
    if (!entry) continue;
    if (!/#\d{4}/.test(issue.body)) continue;

    const body = buildBody(issue, map);
    execFileSync(
      'gh',
      ['issue', 'edit', entry.githubNumber, '--repo', REPO, '--body', body],
      { encoding: 'utf-8' },
    );
    console.log(`[fixed] #${issue.id} -> GitHub #${entry.githubNumber}`);
    fixed++;
  }
  console.log(`\nFertig. ${fixed} Issue-Bodies korrigiert.`);
}

main();
