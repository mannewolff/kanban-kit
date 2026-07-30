#!/usr/bin/env node
/**
 * sync-sonar-issues-to-board.mjs — Holt offene SonarCloud-Findings über die Web-API und legt
 * sie als Karten im Backlog des Sonar-Boards an (kanbancompat-Ingest, Issue #536).
 *
 * Ablauf je Finding: POST /api/kanban/items mit `externalKey: "sonar:<issue-key>"` und
 * `direct: true` — der Server routet die Karte in die erste Spalte des token-gebundenen
 * Boards (#535) und dedupliziert idempotent über den Schlüssel (#534). Eine clientseitige
 * Duplikat-Prüfung gibt es deshalb nicht mehr: `created: false` in der Antwort heißt
 * "schon vorhanden" — auch wenn die Karte inzwischen verschoben, archiviert oder in den
 * Papierkorb verworfen wurde. Erst endgültiges Löschen (purge) gibt den Schlüssel frei;
 * ein danach noch offenes Finding wird beim nächsten Lauf erneut angelegt.
 *
 * Voraussetzungen (beide als Umgebungsvariablen, NICHT im Klartext hier oder im Chat):
 *   SONAR_TOKEN         SonarCloud-User-Token ("My Account" -> "Security" -> "Generate Token")
 *   KANBAN_SONAR_TOKEN  kanban-kit-Access-Token, gebunden an Projekt + Sonar-Board
 *                       (Administration -> API-Tokens; in GitHub Actions als Secret)
 * Optional: KANBAN_HOST überschreibt die Ziel-Instanz (Default: https://kanban.mwolff.org).
 * Projekt-/Organisationsschlüssel werden aus sonar-project.properties gelesen.
 *
 * Läuft ohne weiteren Repo-Kontext: nur node >= 18 (fetch) plus die zwei Variablen;
 * lokaler Probelauf mit `--dry-run` (listet Findings, legt nichts an).
 */

import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const PROPERTIES_PATH = join(REPO_ROOT, 'sonar-project.properties');
// Der sonar-scanner schreibt die Analyse-Task-Referenz hierhin (projectBaseDir=. im Workflow).
const REPORT_TASK_PATH = join(REPO_ROOT, '.scannerwork', 'report-task.txt');
// SonarCloud verarbeitet den Report asynchron (Compute-Engine-Task) — vor dem Auslesen der
// Findings darauf warten. Timeout großzügig, Backoff moderat.
const CE_TASK_TIMEOUT_MS = 5 * 60 * 1000;
const CE_TASK_POLL_START_MS = 2000;
const CE_TASK_POLL_MAX_MS = 15000;
const SONAR_HOST = 'https://sonarcloud.io';
const KANBAN_HOST = process.env.KANBAN_HOST || 'https://kanban.mwolff.org';

const DRY_RUN = process.argv.includes('--dry-run');

function readProperties() {
  const raw = readFileSync(PROPERTIES_PATH, 'utf-8');
  const props = {};
  for (const line of raw.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const idx = trimmed.indexOf('=');
    if (idx === -1) continue;
    props[trimmed.slice(0, idx).trim()] = trimmed.slice(idx + 1).trim();
  }
  return props;
}

/** Ant-Style-Glob (`**`, `*`) zu RegExp — genügt für die einfachen Pfadmuster in sonar.exclusions. */
function globToRegExp(pattern) {
  const escaped = pattern
    .split('**').join('\u0000')
    .replace(/[.+^${}()|[\]\\]/g, '\\$&')
    .split('\u0000').join('.*')
    .replace(/\*/g, '[^/]*');
  return new RegExp(`^${escaped}$`);
}

/** Filtert Issues heraus, deren Datei-Pfad auf sonar.exclusions passt (z. B. db/migration/**). */
function excludeByExclusions(issues, exclusionsRaw) {
  if (!exclusionsRaw) return issues;
  const patterns = exclusionsRaw.split(',').map((p) => globToRegExp(p.trim()));
  return issues.filter((issue) => {
    const file = issue.component?.split(':').slice(1).join(':') || '';
    return !patterns.some((re) => re.test(file));
  });
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Liest die `ceTaskId` aus `.scannerwork/report-task.txt` (vom sonar-scanner geschrieben).
 * Fehlt die Datei (z. B. lokaler Lauf ohne vorherigen Scan), wird `null` zurückgegeben.
 */
function readCeTaskId() {
  if (!existsSync(REPORT_TASK_PATH)) return null;
  const raw = readFileSync(REPORT_TASK_PATH, 'utf-8');
  for (const line of raw.split('\n')) {
    const idx = line.indexOf('=');
    if (idx === -1) continue;
    if (line.slice(0, idx).trim() === 'ceTaskId') return line.slice(idx + 1).trim();
  }
  return null;
}

/**
 * Pollt `GET /api/ce/task?id=…`, bis der Analyse-Task `SUCCESS` meldet. Ohne dieses Warten
 * liest der Sync die Findings, bevor SonarCloud den Report verarbeitet hat, und synchronisiert
 * fälschlich 0. Bricht bei `FAILED`/`CANCELED` oder Timeout mit klarer Meldung ab.
 */
async function waitForCeTask(taskId, token) {
  const auth = Buffer.from(`${token}:`).toString('base64');
  const url = `${SONAR_HOST}/api/ce/task?id=${encodeURIComponent(taskId)}`;
  const deadline = Date.now() + CE_TASK_TIMEOUT_MS;
  let delay = CE_TASK_POLL_START_MS;
  for (;;) {
    const res = await fetch(url, { headers: { Authorization: `Basic ${auth}` } });
    if (!res.ok) {
      throw new Error(`SonarCloud-CE-Task-API-Fehler (HTTP ${res.status}): ${await res.text()}`);
    }
    const status = (await res.json()).task?.status;
    if (status === 'SUCCESS') return;
    if (status === 'FAILED' || status === 'CANCELED') {
      throw new Error(`SonarCloud-Analyse-Task ${taskId} endete mit Status ${status}.`);
    }
    // PENDING / IN_PROGRESS — weiter warten, bis Timeout.
    if (Date.now() >= deadline) {
      throw new Error(
        `Timeout: SonarCloud-CE-Task ${taskId} nicht innerhalb von ${CE_TASK_TIMEOUT_MS / 1000}s ` +
          `abgeschlossen (zuletzt Status ${status ?? 'unbekannt'}).`,
      );
    }
    await sleep(delay);
    delay = Math.min(Math.round(delay * 1.5), CE_TASK_POLL_MAX_MS);
  }
}

async function fetchAllIssues(projectKey, organization, token) {
  const auth = Buffer.from(`${token}:`).toString('base64');
  const issues = [];
  let page = 1;
  const pageSize = 500;

  for (;;) {
    // Schweregrad-Filter direkt an der API: nur MAJOR/CRITICAL/BLOCKER holen, Kleinst-Smells
    // (MINOR/INFO) gar nicht erst laden. Grund: ein einzelnes Test-Idiom (prefer-find-by, MINOR)
    // hatte den Sync zu 60 Rausch-Issues aufgebläht. Bewusst der Legacy-Parameter `severities`
    // statt `impactSeverities`: severityLabel() liest `issue.severity` zuerst, dieses Feld ist auf
    // den Findings verlässlich gesetzt, und `severities` deckt die Top-Grade vollständig ab (kein
    // Risiko, BLOCKER aus einer unvollständigen impactSeverities-Aufzählung zu verlieren).
    const url =
      `${SONAR_HOST}/api/issues/search?componentKeys=${encodeURIComponent(projectKey)}` +
      `&organization=${encodeURIComponent(organization)}&resolved=false` +
      `&severities=MAJOR,CRITICAL,BLOCKER&ps=${pageSize}&p=${page}`;
    const res = await fetch(url, { headers: { Authorization: `Basic ${auth}` } });
    if (!res.ok) {
      throw new Error(`SonarCloud-API-Fehler (HTTP ${res.status}): ${await res.text()}`);
    }
    const data = await res.json();
    issues.push(...data.issues);
    const total = data.paging?.total ?? issues.length;
    if (issues.length >= total || data.issues.length === 0) break;
    page++;
  }
  return issues;
}

function severityLabel(issue) {
  // SEVERITY ist bei neueren Findings ggf. leer; impacts[0].severity ist der Nachfolger.
  return issue.severity || issue.impacts?.[0]?.severity || 'UNKNOWN';
}

function isSecurityIssue(issue) {
  return issue.type === 'VULNERABILITY' || issue.type === 'SECURITY_HOTSPOT';
}

function buildTitle(issue) {
  const prefix = isSecurityIssue(issue) ? '[Sonar][Security]' : '[Sonar]';
  const message = issue.message.length > 100 ? issue.message.slice(0, 97) + '...' : issue.message;
  return `${prefix} ${message}`;
}

/**
 * Karten-Body im Vier-Abschnitt-Format des Workflows — eingeplante Findings sind damit direkt
 * implement-ready-/nachtlauf-tauglich. Der `sonar-issue-key`-Marker bleibt zusätzlich zum
 * serverseitigen externalKey im Body (menschenlesbar, Debug).
 */
function buildBody(issue, projectKey) {
  const file = issue.component?.split(':').slice(1).join(':') || issue.component;
  const line = issue.line ? `:${issue.line}` : '';
  const link =
    `${SONAR_HOST}/project/issues?id=${encodeURIComponent(projectKey)}` +
    `&issues=${encodeURIComponent(issue.key)}&open=${encodeURIComponent(issue.key)}`;

  return [
    '## Kontext',
    `SonarCloud-Finding (${issue.type}, Schweregrad ${severityLabel(issue)}), automatisch`,
    `synchronisiert vom Sonar-Sync. [Auf SonarCloud ansehen](${link})`,
    '',
    '## Aufgabe',
    `**Datei:** \`${file}${line}\``,
    `**Regel:** \`${issue.rule}\``,
    '',
    issue.message,
    '',
    'Finding beheben oder — falls es ein begründeter Sonderfall ist — mit dokumentierter',
    'Begründung auf SonarCloud als akzeptiert/False Positive markieren.',
    '',
    '## Akzeptanzkriterium',
    'Das Finding erscheint im nächsten SonarCloud-Scan nicht mehr als offen; die Pflicht-Gates',
    '(mvn verify, PIT, Frontend-Checks) bleiben grün.',
    '',
    '## Abhängigkeiten',
    'Keine.',
    '',
    `<!-- sonar-issue-key: ${issue.key} -->`,
  ].join('\n');
}

/**
 * Legt ein Finding als Karte im Backlog des token-gebundenen Sonar-Boards an. Der Server
 * dedupliziert über den externalKey (#534) und routet direkt aufs Board (#535). HTTP 409
 * (seltener Wettlauf zweier Läufe am Unique-Index) wird wie "schon vorhanden" behandelt.
 */
async function postToBoard(issue, projectKey, kanbanToken) {
  const res = await fetch(`${KANBAN_HOST}/api/kanban/items`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Kanban-Token': kanbanToken },
    body: JSON.stringify({
      title: buildTitle(issue),
      body: buildBody(issue, projectKey),
      externalKey: `sonar:${issue.key}`,
      direct: true,
    }),
  });
  if (res.status === 409) {
    return { created: false, conflict: true };
  }
  if (!res.ok) {
    throw new Error(
      `kanban-Ingest-Fehler für ${issue.key} (HTTP ${res.status}): ${await res.text()}`,
    );
  }
  return await res.json();
}

async function main() {
  const token = process.env.SONAR_TOKEN;
  if (!token) {
    console.error('Fehler: SONAR_TOKEN ist nicht gesetzt.');
    console.error('Erzeugen unter sonarcloud.io -> My Account -> Security -> Generate Token.');
    process.exit(1);
  }
  const kanbanToken = process.env.KANBAN_SONAR_TOKEN;
  if (!kanbanToken && !DRY_RUN) {
    console.error('Fehler: KANBAN_SONAR_TOKEN ist nicht gesetzt.');
    console.error(
      'Access-Token (gebunden an Projekt + Sonar-Board) unter Administration -> API-Tokens ' +
        'erzeugen und als Umgebungsvariable bzw. GitHub-Secret hinterlegen.',
    );
    process.exit(1);
  }

  const props = readProperties();
  const projectKey = props['sonar.projectKey'];
  const organization = props['sonar.organization'];
  if (!projectKey || !organization) {
    console.error('Fehler: sonar.projectKey/sonar.organization fehlen in sonar-project.properties.');
    process.exit(1);
  }

  // Vor dem Auslesen auf die serverseitige Verarbeitung des frischen Scans warten (sonst
  // liefert die Issues-API u. U. den alten Stand oder 0). Fehlt report-task.txt (lokaler Lauf
  // ohne vorherigen Scan), wird die Wartephase übersprungen.
  const ceTaskId = readCeTaskId();
  if (ceTaskId) {
    console.log(`Warte auf Abschluss des SonarCloud-Analyse-Tasks ${ceTaskId}...`);
    await waitForCeTask(ceTaskId, token);
    console.log('Analyse-Task abgeschlossen (SUCCESS).\n');
  } else {
    console.warn(
      '[WARNUNG] .scannerwork/report-task.txt nicht gefunden — Wartephase übersprungen. ' +
        'Ohne vorherigen Scan (lokaler Lauf) ist das erwartet; in der CI weist es auf ein ' +
        'Problem der Schrittreihenfolge hin.',
    );
  }

  console.log(`Hole offene Findings für ${projectKey} (Org: ${organization})...`);
  const fetched = await fetchAllIssues(projectKey, organization, token);
  const issues = excludeByExclusions(fetched, props['sonar.exclusions']);
  const excludedCount = fetched.length - issues.length;
  console.log(
    `${fetched.length} offene Findings gefunden, ${excludedCount} über sonar.exclusions ` +
      `herausgefiltert (z. B. db/migration) — ${issues.length} werden verarbeitet.\n`,
  );

  const securityCount = issues.filter(isSecurityIssue).length;
  console.log(`Davon sicherheitskritisch (VULNERABILITY/SECURITY_HOTSPOT): ${securityCount}\n`);

  if (DRY_RUN) {
    console.log('--dry-run: keine Karten werden angelegt. Vorschau:\n');
    for (const issue of issues) {
      console.log(`  [${severityLabel(issue)}] ${buildTitle(issue)} (${issue.key})`);
    }
    return;
  }

  let created = 0;
  let existing = 0;

  for (const issue of issues) {
    const result = await postToBoard(issue, projectKey, kanbanToken);
    if (result.created) {
      created++;
      console.log(`[ok]  ${issue.key} -> Karte #${result.number}`);
    } else {
      existing++;
      const where = result.conflict ? 'Wettlauf (409)' : `Karte #${result.number ?? '?'}`;
      console.log(`[alt] ${issue.key} schon vorhanden (${where}).`);
    }
  }

  console.log(`\nFertig. ${created} neu angelegt, ${existing} bereits vorhanden.`);
}

main().catch((e) => {
  console.error(`Fehler: ${e.message}`);
  process.exit(1);
});
