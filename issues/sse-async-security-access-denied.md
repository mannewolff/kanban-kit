# SSE-Endpoints werfen 403 auf Async-Dispatch (Reconnect-Sturm destabilisiert Prod)

> Board war beim Anlegen nicht erreichbar — dieses File ist zum Nachziehen auf den Tracker gedacht.

## Kontext
Auf Prod (`kanban.mwolff.org`) flutet das `manban-api`-Log dauerhaft mit
`AuthorizationDeniedException: Access Denied` auf dem **Async-Dispatch** plus
`Unable to handle the Spring Security Exception because the response is already committed`.
Ursache: Die Live-Update-Endpoints sind async (`SseEmitter`) —
`GET /api/boards/{boardId}/events` (#341–#343) und `GET /api/projects/{projectId}/ideas/events` (#401).
Spring Security 6 lässt den `AuthorizationFilter` **jeden** Dispatcher-Typ laufen, auch `ASYNC`.
Beim Re-Dispatch der offenen SSE-Verbindung ist kein `SecurityContext` gesetzt → 403; da die
SSE-Antwort bereits committet ist, kann der Fehler nicht ausgeliefert werden → Verbindung reißt ab.

Folge: Der Browser-`EventSource` reconnectet automatisch (~3 s) → **Reconnect-Sturm** + Log-Flut →
intermittierende Verbindungs-/Thread-Contention → sporadische `fetch failed` für andere Requests
(u. a. `/api/kanban/items`, den der board.mjs-Adapter nutzt). Die Live-Updates funktionieren in Prod
faktisch nicht (jeder Stream endet in 403). Diagnose bestätigt: `manban-api` läuft stabil (Up 22h,
keine Restarts, kein OOM) — es ist **kein** Infrastruktur-/RAM-Problem, sondern ein Async-Security-Bug.

## Aufgabe
- `auth/infrastructure/security/SecurityConfig.java`: in der `authorizeHttpRequests`-Kette **zu Beginn**
  `dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()` ergänzen, damit
  interne Async-/Error-Re-Dispatches nicht erneut autorisiert werden. Die eigentliche Anfrage (REQUEST-
  Dispatch) bleibt voll autorisiert; die SSE-Abo-Autorisierung erledigt ohnehin einmalig
  `BoardEventService`/`ProjectIdeaEventService.requireSubscribable`. Import `jakarta.servlet.DispatcherType`.
- Sicherheit: `DispatcherType` wird vom Servlet-Container gesetzt, nicht vom Client — ASYNC/ERROR sind
  nicht von außen fälschbar; kein Auth-Loch.

## Akzeptanzkriterium
- Kein `AuthorizationDeniedException` mehr auf dem Async-Dispatch der SSE-Streams (Prod-Log sauber).
- SSE-Streams (Board + Ideen-Pool) bleiben offen und liefern Events, statt in 403 zu enden; kein
  Reconnect-Sturm mehr.
- `mvn verify` grün (JaCoCo, ArchUnit, ITs) + PIT 100 %. (SecurityConfig ist coverage-exkludiert; eine
  faithful Reproduktion des Container-Async-Re-Dispatch im Test ist nicht zuverlässig darstellbar —
  Verifikation daher über die bestehenden SSE-Endpoint-ITs (Subscribe-Pfad) + Prod-Log.)

## Abhängigkeiten
Keine.
