# Befund: Verbrauchsangaben, Hook-Ereignisse und Worktrees

Dieser Befund hält drei Tatsachenfragen am Bestand fest, bevor die Erfassung interaktiver Sitzungen
gebaut wird (Issue #1008, Plan #1007). Er baut nichts. Alle Angaben sind an der Installation vom
**2026-09-17** erhoben; die CLI trug dabei die Fassung **2.1.236**
(`/opt/homebrew/Caskroom/claude-code/2.1.236/claude`, verlinkt von `/opt/homebrew/bin/claude`).

## Verbrauchsangaben im Sitzungsprotokoll

Untersucht wurde das Protokoll einer echten interaktiven Sitzung dieses Projekts:

```
/Users/manfredwolff/.claude/projects/-Users-manfredwolff-ki-projects-kanban-kit/f94eaad6-8e16-45ee-9981-bdcc057b4427.jsonl
```

Dass es eine interaktive Sitzung ist und kein Druckmodus-Lauf, belegen drei Dinge im Protokoll
selbst: `cwd` zeigt auf `/Users/manfredwolff/ki-projects/kanban-kit` (nicht auf einen Nachtlauf-
Worktree unter `$TMPDIR`), alle 142 Nutzersätze tragen `userType` mit dem Wert `external`, und die
Satztypen umfassen `last-prompt` und `queue-operation` — beides gibt es nur in einer Sitzung mit
Eingabezeile.

**Feldnamen ermittelt mit:**

```bash
jq -r 'paths(scalars) | map(tostring) | join(".")' \
  ~/.claude/projects/-Users-manfredwolff-ki-projects-kanban-kit/f94eaad6-8e16-45ee-9981-bdcc057b4427.jsonl \
  | sed -E 's/\.[0-9]+/[]/g' | sort -u | grep -iE 'usage|model|cost|token'
```

**Ausgabe:**

```
message.diagnostics.cache_miss_reason.cache_missed_input_tokens
message.model
message.usage.cache_creation_input_tokens
message.usage.cache_creation.ephemeral_1h_input_tokens
message.usage.cache_creation.ephemeral_5m_input_tokens
message.usage.cache_read_input_tokens
message.usage.inference_geo
message.usage.input_tokens
message.usage.iterations[].cache_creation_input_tokens
message.usage.iterations[].cache_creation.ephemeral_1h_input_tokens
message.usage.iterations[].cache_creation.ephemeral_5m_input_tokens
message.usage.iterations[].cache_read_input_tokens
message.usage.iterations[].input_tokens
message.usage.iterations[].output_tokens
message.usage.iterations[].type
message.usage.output_tokens
message.usage.output_tokens_details.thinking_tokens
message.usage.server_tool_use.web_fetch_requests
message.usage.server_tool_use.web_search_requests
message.usage.service_tier
message.usage.speed
```

Das Protokoll führt die Mengen also **je Zug** (je `assistant`-Satz), nicht je Sitzung: Eingabe in
`input_tokens`, Ausgabe in `output_tokens`, zwischengespeichert getrennt in
`cache_creation_input_tokens` (neu angelegt) und `cache_read_input_tokens` (gelesen), die
Anlegekosten zusätzlich nach Haltedauer aufgeschlüsselt in `ephemeral_5m_input_tokens` und
`ephemeral_1h_input_tokens`. Der Modellname steht je Zug in `message.model`. Eine Summe über die
Sitzung gibt es nicht — wer sie will, addiert über alle `assistant`-Sätze.

**Kein Dollarbetrag.** Die Suche nach einem Kosten- oder Währungsfeld bleibt ohne Treffer:

```bash
grep -oiE '"[a-zA-Z_]*(cost|usd)[a-zA-Z_]*"' \
  ~/.claude/projects/-Users-manfredwolff-ki-projects-kanban-kit/f94eaad6-8e16-45ee-9981-bdcc057b4427.jsonl \
  | sort -u
```

Die Ausgabe ist leer. Das bestätigt die Annahme des Plans (E20): `total_cost_usd` stammt aus dem
`result`-Ereignis des Druckmodus, das der Nacht-Runner in `.claude/kit/night.mjs` (Funktion
`leseKennzahlen`, Zeile 1388) ausliest. Ein Satz mit `type` = `result` kommt im Protokoll der
interaktiven Sitzung **nicht** vor — die Satztypen sind dort ausschließlich `assistant`, `user`,
`attachment`, `last-prompt`, `queue-operation` und `atis-latch`. Auch
`/Users/manfredwolff/.claude/telemetry/` ist leer, führt den Betrag also ebenfalls nicht.

**Modellnamen, die tatsächlich vorkommen.** Erhoben über alle Protokolle dieses Projekts
(`grep -roh '"model":"[^"]*"' ~/.claude/projects/-Users-manfredwolff-ki-projects-kanban-kit/ --include='*.jsonl' | sort | uniq -c`):
`claude-opus-5` (24117 Züge), `claude-sonnet-5` (6038), `claude-fable-5-1` (3026),
`claude-fable-5` (1415), `claude-haiku-4-5-20251001` (241). Daneben stehen die Kurznamen `fable`
(175), `sonnet` (39) und `opus` (19), der Platzhalter `<synthetic>` (5) für maschinell erzeugte
Sätze ohne Abruf und ein Fremdmodell `gpt-5.6-sol` (1) aus einem Codex-Aufruf. Eine Preistabelle
muss also nicht nur fünf echte Modellkennungen führen, sondern auch die Kurznamen auflösen, die
Nichtbepreisbaren (`<synthetic>`, Fremdmodelle) als solche kennzeichnen und bei jedem neuen Modell
nachgezogen werden — das ist die Pflegelast, und sie fällt bei jedem Modellwechsel an, nicht
einmalig.

Ergebnis: Kein Dollarbetrag im Protokoll — Preistabelle noetig.

## Hook-Ereignisse SessionEnd und Stop

Quelle ist das ausgelieferte Programm selbst: `/opt/homebrew/Caskroom/claude-code/2.1.236/claude`,
gelesen mit `strings`. Die Liste der bekannten Ereignisse steht dort als ein Feld wörtlich im
Bündel (31 Einträge, Fundstelle: das Feld, das mit `"PreToolUse"` beginnt und mit
`"MessageDisplay"` endet):

```
PreToolUse, PostToolUse, PostToolUseFailure, PostToolBatch, Notification, UserPromptSubmit,
UserPromptExpansion, SessionStart, SessionEnd, Stop, StopFailure, SubagentStart, SubagentStop,
PreCompact, PostCompact, PermissionRequest, PermissionDenied, Setup, TeammateIdle, TaskCreated,
TaskCompleted, Elicitation, ElicitationResult, ConfigChange, WorktreeCreate, WorktreeRemove,
InstructionsLoaded, CwdChanged, FileChanged, DirectoryAdded, MessageDisplay
```

`SessionEnd` und `Stop` sind darin enthalten. Dieselbe Fassung führt zu jedem Ereignis eine
Kurzbeschreibung; für die beiden hier interessanten lautet sie:

- `SessionEnd` — „When a session is ending"
- `Stop` — „Right before Claude concludes its response"

**Felder des Hook-Eingangs.** Jeder Eingang trägt einen gemeinsamen Rumpf; im Bündel steht er als
Schema mit diesen Feldern:

| Feld | Bedeutung |
|---|---|
| `session_id` | Sitzungskennung — dieselbe, die den Protokolldateinamen bildet |
| `transcript_path` | Pfad des Sitzungsprotokolls (die `.jsonl`-Datei aus dem ersten Abschnitt) |
| `cwd` | Arbeitsverzeichnis der Sitzung |
| `prompt_id` | Kennung der Nutzereingabe, gültig bis zur nächsten Eingabe |
| `permission_mode` | eingestellter Berechtigungsmodus |
| `agent_id`, `agent_type`, `effort` | gesetzt, wenn der Aufruf zu einem Unteragenten gehört |

Dazu kommt je Ereignis `hook_event_name` und die ereigniseigenen Felder:

- `SessionEnd` ergänzt genau ein Feld: `reason`, mit einem der Werte `clear`, `resume`, `logout`,
  `prompt_input_exit`, `other`. Das Ereignis lässt sich über diesen Wert auch filtern
  (`fieldToMatch` = `reason`).
- `Stop` ergänzt `stop_hook_active`, `last_assistant_message`, `background_tasks` und
  `session_crons`. Die Variante für Unteragenten heißt `SubagentStop` und trägt zusätzlich
  `agent_id`, `agent_transcript_path` und `agent_type`.

**Ende der Sitzung gegen Ende eines Zugs.** Ein Hook unterscheidet beides am Feld
`hook_event_name`: `Stop` feuert am Ende **jedes** Zuges der Hauptschleife, `SessionEnd` einmal beim
Beenden der Sitzung. Nur `SessionEnd` trägt `reason`, nur `Stop` trägt `stop_hook_active` — jeder
der beiden Eingänge ist also auch ohne `hook_event_name` erkennbar. Für eine Meldung je Sitzung ist
`SessionEnd` das richtige Ereignis; ein Hook an `Stop` würde pro Sitzung vielfach feuern.
`stop_hook_active` ist dabei kein Zähler, sondern ein Wiedereintrittsschutz: Das Bündel führt dazu
den Hinweis „For Stop/SubagentStop hooks, check stop_hook_active in the input and return success
while it's true."

**Was hier nicht belegt ist.** Dass beide Ereignisse in dieser Installation tatsächlich feuern, ist
nicht beobachtet, sondern nur am Programmtext belegt: Es gibt den Auslöser `executeSessionEndHooks`
und die zugehörige Zeitgrenze `getSessionEndHookTimeoutMs`. Konfiguriert ist derzeit nur
`SessionStart`, und zwar in `/Users/manfredwolff/.claude/settings.json`
(`jq -r '.hooks | keys[]'` liefert genau diesen einen Namen); `.claude/settings.json` dieses
Projekts führt keinen `hooks`-Block. Das Feuern von Hand nachzusehen ist der manuelle Prüfpunkt des
Issues und bleibt beim Menschen.

## Worktrees

Ausprobiert mit einem Wegwerf-Worktree unter `$TMPDIR` (`/tmp/claude-501`):

```bash
git worktree add --detach /tmp/claude-501/wt-probe-1008 HEAD
ls -la /tmp/claude-501/wt-probe-1008/.claude/
git worktree remove --force /tmp/claude-501/wt-probe-1008
```

Unter `.claude/` lag genau eine Datei: `workflow.config.json`. Kein `settings.json`, kein
`settings.local.json`, kein `kit/`, kein `skills/`. Das ist auch die Erwartung, denn versioniert ist
nur diese eine Datei — `git ls-files .claude` liefert `.claude/workflow.config.json`, und
`.gitignore` schließt in den Zeilen 3 bis 4 alles andere aus (`.claude/*` mit der Ausnahme
`!.claude/workflow.config.json`). Ein Worktree bekommt aus Git nur, was Git kennt.

**Auch der Weg über das Werkzeug hilft nicht von selbst.** Die CLI legt ihre eigenen Worktrees
unter `.claude/worktrees/<name>` an und kennt dafür die Ereignisse `WorktreeCreate` (Eingang mit
`name`) und `WorktreeRemove` (Eingang mit `worktree_path`). Unversionierte Dateien kopiert sie dabei
nur, wenn die Wurzel des Repositorys eine Datei `.worktreeinclude` führt: Die Funktion, die diese
Liste liest, gibt bei fehlender Datei eine leere Liste zurück (`catch { return [] }`), und ebenso
bei einer Datei ohne Einträge. Dieses Repository hat kein `.worktreeinclude`.

Ein Gegenbeispiel aus der Vergangenheit liegt noch im Arbeitsbaum:
`.claude/worktrees/nifty-noyce-980f0a` (angelegt am 17. Juli, Commit `21918e9`) trägt sehr wohl
`.claude/kit/`, `.claude/skills/` und `.claude/settings.local.json`, obwohl
`git ls-tree -r 21918e9 -- .claude` auch dort nichts ausgibt. Eine ältere Fassung des Werkzeugs hat
diese Dateien also mitkopiert. Für die heute ausgelieferte Fassung 2.1.236 gilt das nicht mehr,
solange `.worktreeinclude` fehlt.

Eine Sitzung in einem frischen Worktree kann damit weder `.claude/kit/board.mjs` aufrufen noch einen
Hook aus `.claude/settings.json` auslösen — sie hat beides nicht. Ihr Protokoll entsteht trotzdem,
nur unter einem eigenen Projektverzeichnis (das `cwd` des Worktrees, verkürzt zum Verzeichnisnamen
unter `~/.claude/projects/`), erreichbar über `transcript_path` im Hook-Eingang. Wer
Worktree-Sitzungen erfassen will, braucht also einen Weg, der nicht im Worktree liegt — oder ein
`.worktreeinclude`, das `.claude/` mitnimmt.

Ergebnis: Worktree traegt kein Kit — Worktree-Sitzungen bleiben unerfasst.
