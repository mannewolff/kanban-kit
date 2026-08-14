# kanban-kit als eigenes Board anbinden

kanban-kit kann sein **eigenes** Board als Issue-Tracker treiben — sowohl über die
`board.mjs`-CLI (claude-workflow-kit) als auch über die eigenständige
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

## 1. Board-gebundenes Token erzeugen

Am einfachsten in der **Web-UI** — kein DevTools-/Cookie-Gefummel nötig:

1. Anmelden und in der Seitenleiste unten **„Administration"** öffnen.
2. Im Abschnitt **„API-Tokens"** auf **„Token erzeugen"** klicken.
3. Einen **Namen** vergeben und **Projekt → Board** wählen (zur Auswahl stehen nur Boards, auf
   denen du Karten anlegen darfst).
4. **„Erzeugen"** — der Klartext (`tk_…`) erscheint **genau einmal**; über den
   **Kopieren**-Button sichern.

> Das Token ist an genau dieses Board gebunden. Es zu erzeugen setzt das Recht voraus, auf dem
> Board **Karten anzulegen** (`TICKET_CREATE`) — ein VIEWER kann kein board-gebundenes Token
> bauen. In der Liste lässt sich jedes Token wieder **widerrufen**.

**Alternative (Automatisierung, ohne UI):** direkt per API. Projekt-/Board-ID stehen in der URL
(`…/boards/7` → Board `7`, `…/projects/3/boards` → Projekt `3`); der Session-Cookie ist hier nötig,
weil die Token-Verwaltung aus Sicherheitsgründen nur per Login erreichbar ist, nicht per Token
selbst:

```
curl -sk https://localhost/api/access-tokens \
  -b "manban_session=DEIN_SESSION_COOKIE" \
  -H "Content-Type: application/json" \
  -d '{"name":"board-cli","projectId":3,"boardId":7}'
```

Antwort (einmalig sichtbar): `{ "id": 1, "name": "board-cli", "plaintext": "tk_…" }`.

> Bindet man ein Board, in dem man kein Karten-Anlegerecht hat → `403`. Passt das Board nicht zum
> Projekt → `400`. Ohne `projectId`/`boardId` entsteht ein **ungebundenes** Token, das die
> Kanban-Compat-API mit `409` ablehnt.

## 2. CLI anmelden

Mit der im Repo mitgelieferten `cli/tbx.mjs` (oder direkt über die von `board.mjs`
gelesenen Token-Dateien unter `~/.config/toolbox-cli/`):

```
node cli/tbx.mjs auth login --host https://localhost --token tk_…
```

## 3. board.mjs auf das eigene Board umstellen

In der `.claude/workflow.config.json` des Repos, das das Board treiben soll:

```json
{
  "issueTracker": "toolbox",
  "toolbox": { "host": "https://localhost" }
}
```

`codeHost` (z. B. `github`) bleibt davon unberührt — nur der Issue-Tracker wechselt aufs
eigene Board (Zwei-Achsen-Modell: Code bleibt, wo er ist).

## 4. Smoke-Test

```
node .claude/kit/board.mjs issue list
node .claude/kit/board.mjs issue create --title "Testkarte" --body "aus board.mjs"
node .claude/kit/board.mjs issue move <nummer> in_progress
```

Die Karten erscheinen live im Web-Board. Status ↔ Spalte werden abgebildet:
`backlog↔Backlog`, `ready↔Ready`, `in_progress↔In Progress`, `in_review↔In Review`,
`done↔Done`.

## 5. Karte lesen — inklusive Kommentaren

`tbx issue get` liefert die Karte samt ihrer Kommentare (Autor, Text, Zeitstempel):

```
node cli/tbx.mjs issue get 42
```

```json
{
  "id": 42,
  "title": "Testkarte",
  "body": "aus board.mjs",
  "status": "in_progress",
  "comments": [
    { "author": "Manne", "body": "Abschlussbericht …", "createdAt": "2026-07-28T10:00:00Z" }
  ]
}
```

Kommentare schreibt man mit `node cli/tbx.mjs issue comment 42 --text "…"`.

> Spricht die CLI gegen eine **ältere** Instanz, die den Kommentar-Endpoint noch nicht
> kennt, bleibt `comments` leer — der Aufruf bricht nicht ab.

## 6. Labels setzen und entfernen

`GET /api/kanban/items` liefert je Item die zugeordneten Label-Namen (`"labels": ["kit:nightrun"]`).
Geschrieben wird **je Label einzeln** — alle übrigen Labels der Karte bleiben dabei unangetastet.
Genau daran hängt das Routing-Label `kit:nightrun`, über das der Nacht-Runner seine Ready-Issues
filtert.

Beide Endpunkte adressieren die Karte über ihre **interne Karten-ID** (das Feld `id` aus
`GET /api/kanban/items`) — nicht über die projektweite Anzeigenummer `#N`. Das ist dieselbe
Adressierung wie bei `/items/{id}/move` und `/items/{id}/comments`; `board.mjs` löst eine
übergebene Board-Nummer intern in diese ID auf.

```
# Label hinzufügen
curl -sk -X POST https://localhost/api/kanban/items/42/labels \
  -H "X-Kanban-Token: tk_…" \
  -H "Content-Type: application/json" \
  -d '{"name":"kit:nightrun"}'

# Label entfernen (Name im Query-Parameter, URL-kodiert)
curl -sk -X DELETE "https://localhost/api/kanban/items/42/labels?name=kit%3Anightrun" \
  -H "X-Kanban-Token: tk_…"
```

Beide antworten mit **`204 No Content`** — auch dann, wenn sich nichts geändert hat: Ein bereits
gesetztes Label erneut zu setzen und ein nicht gesetztes zu entfernen gelten als Erfolg, damit ein
abgebrochener Nachtlauf ohne Sonderbehandlung wiederholbar ist.

> **Der Labelname steht beim `DELETE` im Query-Parameter, nicht im Pfad**, und muss **URL-kodiert**
> werden. Grund: Ein Labelname darf jedes Zeichen tragen, auch `/` — ein Pfadsegment trüge das
> nicht, weil Spring/Tomcat kodierte Slashes per Default ablehnen. Der Parameter ist Pflicht.

> **Ein unbekannter Labelname wird mit `404` abgelehnt und *nicht* angelegt.** Labels sind
> boardweit definierte Objekte mit Farbe; ein Tippfehler in einem unbeaufsichtigten Lauf erzeugte
> sonst dauerhaft Label-Müll, den niemand bemerkt. Labels legt man in der Web-UI am Board an.
> Labelnamen sind nur **boardweit** eindeutig und werden nach dem Trimmen **case-sensitiv**
> verglichen — aufgelöst wird immer am Board der Karte.

Fehlercodes (jeder Fehler lässt Labels und Zuordnungen unverändert):

| Code | Bedeutung |
|---|---|
| `400` | Name fehlt, ist leer oder länger als 60 Zeichen — oder die ID gehört zu einem **Epic** (Epics haben keine Labels) |
| `401` | Token fehlt oder ist ungültig |
| `403` | Der Token-Nutzer hat kein `TICKET_UPDATE` im Projekt (ein VIEWER darf nicht labeln, ein MEMBER schon) |
| `404` | Karte unbekannt, Karte liegt auf einem **anderen** Board als dem gebundenen — oder das Board kennt kein Label dieses Namens |
| `409` | Gültiges, aber an kein Board gebundenes Token |
