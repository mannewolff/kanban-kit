# kanban-kit als eigenes Board anbinden

kanban-kit kann sein **eigenes** Board als Issue-Tracker treiben — sowohl über die
Stellwerk-CLI `board.mjs` (claude-workflow-kit) als auch über die eigenständige
`tbx.mjs`-CLI, die mit dem Produkt ausgeliefert wird ([`cli/tbx.mjs`](../cli/tbx.mjs)).
Beide sprechen dasselbe Kanban-Protokoll (`/api/kanban/*`) und authentifizieren sich
mit einem **board-gebundenen Token** (`X-Kanban-Token`).

Der Clou: Der Client sendet **nur den Token**, keine Board-ID. Welches Board bedient
wird, steckt in der Token-Bindung — ähnlich einem GitHub-Fine-grained-PAT, der auf ein
Repository beschränkt ist. Damit ist zugleich sichergestellt, dass ein Token **nur** auf
genau dieses Projekt/Board zugreift.

## Voraussetzungen

- Eine laufende kanban-kit-Instanz (siehe [Betrieb](betrieb.md)).
- Ein Projekt mit einem Board. Das Board sollte die Standard-Spalten
  **Backlog · Ready · In Progress · In Review · Done** haben (die Compat-API bildet die
  fünf festen Kanban-Status auf diese Spalten ab).

## 1. Projekt- und Board-ID ermitteln

Öffne das gewünschte Board in der Web-UI. Die IDs stehen in der URL, z. B.
`https://localhost/boards/7` → Board-ID `7`. Die Projekt-ID zeigt die Projekt-/Boardliste
(bzw. die URL der Boardauswahl `.../projects/3/boards`).

## 2. Board-gebundenes Token erzeugen

Das Token wird per API angelegt (Cookie-Login vorausgesetzt — Token-Verwaltung ist aus
Sicherheitsgründen nur per Session erreichbar, nicht per Token selbst). `projectId` und
`boardId` binden das Token an genau dieses Board:

```
curl -sk https://localhost/api/access-tokens \
  -b "manban_session=DEIN_SESSION_COOKIE" \
  -H "Content-Type: application/json" \
  -d '{"name":"stellwerk","projectId":3,"boardId":7}'
```

Antwort (einmalig sichtbar):

```json
{ "id": 1, "name": "stellwerk", "plaintext": "tk_…" }
```

Den `plaintext` (`tk_…`) sicher notieren — er wird **nur genau einmal** angezeigt.

> Bindet man ein Board, in dem man nicht Mitglied ist → `403`. Passt das Board nicht zum
> Projekt → `400`. Ohne `projectId`/`boardId` entsteht ein **ungebundenes** Token, das die
> Kanban-Compat-API mit `409` ablehnt.

## 3. CLI anmelden

Mit der im Repo mitgelieferten `cli/tbx.mjs` (oder direkt über die von `board.mjs`
gelesenen Token-Dateien unter `~/.config/toolbox-cli/`):

```
node cli/tbx.mjs auth login --host https://localhost --token tk_…
```

## 4. board.mjs auf das eigene Board umstellen

In der `.claude/workflow.config.json` des Repos, das das Board treiben soll:

```json
{
  "issueTracker": "toolbox",
  "toolbox": { "host": "https://localhost" }
}
```

`codeHost` (z. B. `github`) bleibt davon unberührt — nur der Issue-Tracker wechselt aufs
eigene Board (Zwei-Achsen-Modell: Code bleibt, wo er ist).

## 5. Smoke-Test

```
node .claude/kit/board.mjs issue list
node .claude/kit/board.mjs issue create --title "Testkarte" --body "aus board.mjs"
node .claude/kit/board.mjs issue move <nummer> in_progress
```

Die Karten erscheinen live im Web-Board. Status ↔ Spalte werden abgebildet:
`backlog↔Backlog`, `ready↔Ready`, `in_progress↔In Progress`, `in_review↔In Review`,
`done↔Done`.
