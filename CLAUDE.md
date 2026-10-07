# CLAUDE.md — Projekt-Standards

Diese Datei ist der Einstiegspunkt für alle Engineering-Regeln in diesem Projekt. Sie definiert den Mindeststandard — Abweichungen sind Fehler und müssen vor dem Abschluss einer Aufgabe korrigiert werden.

---

## 🎯 Schnelleinstieg

- **Neu im Projekt?** Lies diese Datei + [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md).
- **Java/Spring-Backend arbeiten?** → [CLAUDE-java.md](CLAUDE-java.md)
- **React-Frontend arbeiten?** → [CLAUDE-react.md](CLAUDE-react.md)
- **Farben, Radien, Tiefe im Frontend?** → [CLAUDE-design.md](CLAUDE-design.md)
- **Security?** → [CLAUDE-security.md](CLAUDE-security.md)
- **Plan-Mode / Git / Issue-Workflow?** → [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md)

---

## 📚 Guide-Familie

| Guide | Fokus | Wiederverwendbar |
|---|---|---|
| **CLAUDE.md** (diese Datei) | Projekt-Übersicht + Pflichtchecks | ❌ Projekt |
| [CLAUDE-java.md](CLAUDE-java.md) | Java 25, Spring Boot 3, TDD, Coverage, Mutationstests | ✅ Allgemein |
| [CLAUDE-react.md](CLAUDE-react.md) | React 18, Vite, TypeScript, MUI, Lazy Loading, ESLint/A11y | ✅ Allgemein |
| [CLAUDE-design.md](CLAUDE-design.md) | Palette, Font, Radien, Tiefe, Kontrast des Leitstands | ❌ Projekt |
| [CLAUDE-security.md](CLAUDE-security.md) | Spring Security, JPA, Frontend-XSS, Secrets, Session-/Token-Handling | ✅ Allgemein |
| [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md) | 9-Schritte-Workflow, Issues, Git, Pflichtchecks | ✅ Allgemein |

---

## 🌐 Projektkontext

**Ziel:** **kanban-kit** (Repo `manban`) — ein self-hostbares, mandantenfähiges Kanban-Board als schlanke Trello-Alternative zum Selbstbetreiben. Projekte, Boards mit konfigurierbaren Spalten, Karten mit Markdown, Vorhaben, Datei-Anhänge (Bild-/PDF-Vorschau) und eine rollenbasierte Rechteverwaltung (Projekt- und Plattform-Rollen). UI im Stil eines Dashboards: linke Navigation, rechter Inhaltsbereich.

**Stack:**

| Schicht | Technologie |
|---|---|
| Backend-Sprache | Java 25 (LTS) |
| Backend-Framework | Spring Boot 3.5, Spring Data JPA, Spring Web |
| Build (Backend) | Maven (inkl. `frontend-maven-plugin` für den Vite-Build) |
| Datenbank | PostgreSQL 16 |
| Objektspeicher | SeaweedFS (S3-kompatibel) für Datei-Anhänge |
| Schema-Migrationen | Flyway (`db/migration/V<n>__…sql`) |
| Test (Backend) | JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit, PIT |
| Frontend-Sprache | TypeScript (`strict: true`) |
| Frontend-Framework | React 18, React Router 6 |
| Frontend-Build | Vite 5 |
| UI-Library | Material UI 6 (MUI) + Emotion |
| Test (Frontend) | Vitest + React Testing Library |
| Containerisierung | Docker (Multi-Stage: Node + Maven + JRE), Docker Compose |
| Reverse-Proxy | Caddy 2 (automatisches TLS, `https://localhost` bzw. `MANBAN_DOMAIN`) |
| Identity / Auth | Eigenes E-Mail/Passwort-Auth mit Session-Cookies (kein Keycloak/OIDC) |

**Verbindung Frontend↔Backend:** Im Dev leitet der Vite-Dev-Server (`:5173`) `/api/*` an Spring Boot auf `:8080` weiter. In Produktion serviert Spring Boot den React-Build aus `classpath:/static/` (SPA-Forwarding über [`SpaWebConfig`](src/main/java/org/mwolff/manban/config/SpaWebConfig.java)); davor liegt Caddy als Reverse-Proxy mit TLS. Eine Origin, kein CORS.

**Identity / Auth:** Authentifizierung ist projekteigen — kein externer Identity-Provider. Registrierung mit E-Mail-Verifikation, Passwort-Reset per Token/Mail, ein per Bootstrap-Token angelegter erster Plattform-Admin, sowie signierte Session-Tokens (HttpOnly-Cookie). Für den Kanban-kompatiblen Ingest ohne Login gibt es projektgebundene Access-Tokens (`accesstoken` + `kanbancompat`). Autorisierung ist rollenbasiert: **Projekt-Rollen** (RBAC pro Projekt) plus **Plattform-Admin**. Rollen- und Rechte-Matrix: [docs/rollen-und-rechte.md](docs/rollen-und-rechte.md).

---

## 📂 Projektstruktur

```
/
├── CLAUDE*.md                          # Guide-Familie (Workflow-Guide unter .claude/)
├── pom.xml                             # Maven-Konfiguration (inkl. frontend-maven-plugin)
├── Dockerfile, docker-compose.yml      # Multi-Stage-Image + lokale Composition (Postgres, Objektspeicher, Caddy)
├── Caddyfile                           # Reverse-Proxy + automatisches TLS
├── .env.example                        # DB-, Objektspeicher- und App-Konfig-Vorlage
├── .claude/workflow.config.json        # issueTracker: toolbox — Issues auf dem Board https://kanban.mwolff.org (node .claude/kit/board.mjs)
├── src/main/java/org/mwolff/manban/    # Backend (je Modul: domain/application/web/infrastructure)
│   ├── ManbanApplication.java
│   ├── auth/                           # Registrierung, Login, Session, Passwort-Reset, Bootstrap-Admin
│   ├── project/                        # Projekte, Mitgliedschaften, RBAC (Projekt-Rollen)
│   ├── board/                          # Boards + konfigurierbare Spalten
│   ├── card/                           # Karten, Vorhaben, Abhängigkeiten, Done-Retention-Job
│   ├── comment/                        # Kommentare an Karten
│   ├── attachment/                     # Datei-Anhänge (Objektspeicher, Bild-/PDF-Vorschau)
│   ├── accesstoken/                    # Projektgebundene API-/Ingest-Tokens
│   ├── kanbancompat/                   # Kanban-kompatibler Ingest (Token→Board-Binding)
│   ├── config/                         # SpaWebConfig (SPA-Forwarding)
│   └── common/                         # SecureTokens, gemeinsame Token-Utilities
├── src/main/resources/                 # application.yml + Flyway-Migrationen
│   └── db/migration/                   # V1__baseline.sql … (Flyway-Konvention, Postgres)
├── src/test/java/org/mwolff/manban/    # Tests (*Test = Unit/Slice, *IT = Testcontainers-Integration)
└── frontend/                           # React-App
    ├── package.json, vite.config.ts, tsconfig*.json
    ├── index.html
    └── src/
        ├── main.tsx, App.tsx, theme.ts
        ├── auth/                       # AuthContext (Session-basiert)
        ├── layout/                     # navItems
        ├── components/                 # geteilte UI-Bausteine (AppShell, BoardView, Modals, …)
        ├── pages/                      # Routen-Komponenten (Projects, Boards, Vorhaben, Admin, Auth-Seiten)
        ├── routes/                     # ProtectedRoute
        ├── lib/                        # Frontend-Hilfsfunktionen (statusColors, boardOps, …)
        ├── api/                        # client.ts (fetch-Wrapper) + <domain>.ts
        └── test/                       # Vitest-Setup
```

---

## ✅ Pflichtchecks vor Abschluss einer Aufgabe

```bash
# Backend
mvn -Dskip.frontend=true -Djacoco.skip=true -Dit.test=OpenApiIT verify   # je Paket: Kompilieren, Unit-Tests, statische Analyse, Vertragsvergleich
mvn verify                              # beim Push: dazu Integrationstests, 100-%-Abdeckung, Doku-Seite

# Frontend
cd frontend && npm run build            # tsc + vite build
cd frontend && npm run lint             # ESLint + jsx-a11y
cd frontend && npm test                 # Vitest

# CLI
node --test cli/tbx.test.mjs            # tbx-Kommandozeilenwerkzeug

# Skripte
node --test scripts/*.test.mjs          # Projektskripte, darunter scripts/mutationspruefung.test.mjs

# Doku
npm --prefix docs-site ci --no-audit --no-fund && npm --prefix docs-site run build   # Doku-Seite (VitePress), Bereich doku

# Mutationsprüfung (Bereiche frontend bzw. backend)
node scripts/mutationspruefung.mjs zuordnung frontend  # je Paket: geänderte Tests ohne Zuordnung, ohne Werkzeuglauf
node scripts/mutationspruefung.mjs zuordnung backend   # je Paket: dasselbe fürs Backend
node scripts/mutationspruefung.mjs aenderung frontend   # Stufe push: Stryker über die geänderten Dateien des Batches
node scripts/mutationspruefung.mjs aenderung backend    # Stufe push: PIT über die geänderten Klassen des Batches
node scripts/mutationspruefung.mjs vollauf backend      # Stufe push: PIT über den ganzen Bereich, Sperrschwelle 80 %

# Mutationsprüfung von Hand (etwa wöchentlich — keine Pflichtprüfung, Issue #1344)
node scripts/mutationspruefung.mjs vollauf frontend     # Handlauf: Stryker über den ganzen Bereich, Schwelle 80 %
```

**Je Paket nach Bereichen eingegrenzt.** Die Checks stehen in `.claude/workflow.config.json` (`buildChecks`)
mit Bereichen aus `checkAreas`: `backend` (`src/**`, `pom.xml`, `config/**`) →
`mvn -Dskip.frontend=true -Djacoco.skip=true -Dit.test=OpenApiIT verify` (Kompilieren, Unit-Tests, Spotless,
Checkstyle, PMD, SpotBugs und der Vertragsvergleich `OpenApiIT` — ohne die übrigen Integrationstests,
Abdeckung und Frontend-/Doku-Build) sowie `node scripts/mutationspruefung.mjs zuordnung backend`. Der
Paketlauf des Backends setzt einen laufenden Docker-Daemon voraus (zwei Container: Postgres, SeaweedFS),
auch nachts. `frontend` (`frontend/**`, `CLAUDE-design.md`) → die drei npm-Checks und
`node scripts/mutationspruefung.mjs zuordnung frontend`; `cli` (`cli/**`) →
`node --test cli/tbx.test.mjs`; `scripts` (`scripts/**`) → `node --test scripts/*.test.mjs`; `doku`
(`docs/**`, `docs-site/**`, `README.md`) → der Bau der Doku-Seite (Issue #1361), damit ein Doku-Fehler schon
beim Abschluss eines Pakets auffällt und nicht erst beim Push. Das volle `mvn verify` (Bereiche `backend` und `doku`, inklusive
Integrationstests, 100-%-Abdeckung und Doku-Seite) trägt `stufe: "push"` und läuft erst bei `push main`
und `merge production` — die Abdeckungsgrenze ist ohne Integrationstests nicht zu halten, deshalb
wandert sie mit. Beim Abschluss eines Pakets läuft nur, was die geänderten Dateien berühren; eine Datei
ohne Bereich (etwa `Dockerfile`, `.github/`) fährt alle der Paketstufe. **Vor `push main` laufen alle
Prüfungen der Paket- und Push-Stufe (dreizehn); `merge production` wiederholt keine davon.** Jeder Eintrag
läuft zu Ende, auch nach einem Rot: Alle Einträge tragen `gleichzeitig`, und `KIT_CHECKS_GLEICHZEITIG=1`
hält sie nacheinander. Die Config ändert nur Manne.

**Mutationsprüfung (Issue #1104).** Die Änderungsprüfung (`aenderung`) läuft an der Push-Stufe und mutiert,
was die Karten des Batches berühren (Anker `git merge-base HEAD origin/main`). Der Prüfbereich des
Frontends kommt aus dem Stufenplan [`frontend/mutationsstufen.json`](frontend/mutationsstufen.json):
Er wächst Ausschnitt für Ausschnitt, die Schwelle gilt je Ausschnitt (Einzelheiten in CLAUDE-react.md).

**Ausnahme von W3: Sperrschwelle statt Ziel (Issue #1516).** Die Prozessbeschreibung des Kits sagt in
Regel W3: „Ein Vorschlag, der … eine Schwelle senkt, hebt die Mechanik auf.“ Für die Mutationsprüfung
beim Veröffentlichen gilt davon abweichend: **100 % ist das Ziel, gesperrt wird erst unter der
Sperrschwelle von 80 %.** Grund: Bei jedem Überlebenden anzuhalten sperrte die Veröffentlichung auch für
alte Lücken, die mit der fertigen Arbeit nichts zu tun hatten — vier von neun Abbrüchen seit dem
2026-09-27, einmal bei 99,3 %. Die Mängel gehen trotzdem nicht verloren, sie werden zu Karten. Diese
Ausnahme steht hier und nicht in `.claude/CLAUDE-workflow.md`, weil jene Datei eine unversionierte
Kit-Kopie ist; nach „Verhältnis der Guides“ hat sie Vorrang vor W3. Im Einzelnen:

- **Bezugsmenge der Änderungsprüfung:** Gezählt werden die Mutanten in Zeilen, die seit dem Anker
  geändert wurden, und in Dateien, deren prüfender Test sich geändert hat (testberührte Dateien) — dort
  ohne die Stellen, die schon im letzten Vollauf überlebten. Alte Lücken außerhalb erscheinen nur als
  Zahl und zählen nicht.
- **Quote je Seite:** Änderungsprüfung und Backend-Vollauf messen je eine Quote, Backend und Frontend
  getrennt. Ab 80 % läuft die Veröffentlichung durch, darunter sperrt sie. Der Backend-Vollauf urteilt
  mit derselben Sperrschwelle; der Frontend-Vollauf bleibt ein Handlauf mit 80 % je Ausschnitt.
- **Karten:** Lässt ein Lauf Überlebende durch, entsteht je Datei eine Karte
  `Mutations-Überlebende in <Pfad>` im Backlog, die alle Stellen aufzählt; eine offene Karte derselben
  Datei wird ergänzt, ihr Anlagedatum bleibt. Der Frontend-Vollauf legt keine Karten an. Das gilt für
  jeden grünen Lauf des Treibers, auch für einen Handlauf.
- **Liegezeit 7 Tage:** Liegt eine solche Karte länger als 7 Tage offen, sperrt sie die nächste
  Veröffentlichung; die Meldung nennt Karte und Regel.
- **Altlast-Vermerk:** Ein Überlebender mit tragendem Vermerk zählt wie ein getöteter und bekommt keine
  Karte (Bedingungen in CLAUDE-java.md §5.5 und CLAUDE-react.md).
- **Abschlussbericht von `push main`:** Er nennt die Einträge aus `.claude/mutationsdurchlass.json` zum
  gepushten Commit — durchgelassene Überlebende, Quote und die neu angelegten oder ergänzten Karten.
- **Handläufe mit Board-Zugriff** (`aenderung`, `vollauf backend`) laufen in Mannes Terminal oder über
  `checks.mjs`: Aus einer Claude-Code-Sitzung heraus fehlt `node scripts/mutationspruefung.mjs` das
  Netz, und ein Lauf mit Überlebenden endet dann rot.

**Stufenschaltung (Issue #1280).** Der Treiber kann per `--stufe` und Dauerprotokoll
(`.claude/mutationsdauer-<seite>.json`) zwischen `paket` und `push` schalten; die Config nutzt das nicht,
weil die Änderungsprüfung fest an `push` steht.
Das frühere Feld `mutationCommand` ist entfallen; PIT und Stryker laufen nur noch über den Treiber. Wie eine
bewusst hingenommene Altlast markiert wird, steht in CLAUDE-java.md §5.5 und CLAUDE-react.md. Der
Vollauf (`vollauf`) prüft den ganzen Bereich gegen 80 % (Frontend je Ausschnitt, Backend als Sperrschwelle) und schreibt
die Gedächtnisdatei `.claude/mutationsvollauf-<seite>.json`, aus der die Änderungsprüfung Dauer und
Altlast-Stellen liest. Der **Backend-Vollauf** hängt wie die Änderungsprüfung an der Stufe `push`. Der
**Frontend-Vollauf** ist keine Pflichtprüfung (Issue #1344): Er dauerte zuletzt rund 48 Minuten, und neue
Lücken fängt die Änderungsprüfung ab. Manne startet ihn etwa wöchentlich von Hand; liegt ein aufgenommener
Ausschnitt unter 80 %, entstehen Karten, die die Quote wieder anheben (Einzelheiten in CLAUDE-react.md).
`push main` dauert mit Backend-Vollauf und Integrationstests trotzdem mehrere Minuten — der Prüflauf läuft
im Hintergrund, und die Sitzung wartet sein Ende ab, statt mit „ich melde mich“ zu enden. Die
Werkzeug-Aufrufe `mvn -Ppit -Dskip.frontend=true test` und `npm --prefix frontend run test:mutation` fährt
der Treiber selbst.

Verfahren, Reporting-Format und detaillierte Schritte → [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md).

---

## ⚠️ Prioritäten bei Zielkonflikten

1. **Sicherheit**
2. **Korrektheit**
3. **Datenintegrität**
4. **Accessibility**
5. **Wartbarkeit**
6. **Testbarkeit**
7. **Performance**
8. **Visuelle Präferenz**
9. **Bequemlichkeit der Implementierung**

Keine kurzfristige Bequemlichkeit rechtfertigt unsicheren, untypisierten oder schwer wartbaren Code. Wenn Sicherheit gegen Performance abgewogen wird, gewinnt Sicherheit. Wenn Korrektheit gegen Geschwindigkeit der Lieferung abgewogen wird, gewinnt Korrektheit.

---

## 📐 Verhältnis der Guides untereinander

- **CLAUDE.md** ist die Übersicht. Konflikte zwischen den Sub-Guides werden hier geklärt.
- **CLAUDE-java.md** und **CLAUDE-react.md** beschreiben die schichtspezifischen Engineering-Regeln. Bei Widerspruch zur Sicherheit gewinnt [CLAUDE-security.md](CLAUDE-security.md).
- **CLAUDE-design.md** und **CLAUDE-react.md** teilen sich die Oberfläche: [CLAUDE-design.md](CLAUDE-design.md) regelt das *Was* (Farben, Radien, Tiefen, Kontrast), [CLAUDE-react.md](CLAUDE-react.md) das *Wie* (Theme-zentral, `sx`-Prop, keine hartcodierten Werte).
- **CLAUDE-security.md** hat in allen Sicherheitsfragen Vorrang.
- **CLAUDE-workflow.md** beschreibt das Prozess-Drumherum (Plan-Mode, Issues, Commits, GO-Freigabe, Tests). Wer Code schreibt ohne den Workflow zu befolgen, hat die Aufgabe nicht abgeschlossen.

---

**TL;DR:** Java 25 + Spring Boot 3 (TDD-pflichtig, 100 % Coverage) auf PostgreSQL 16 + SeaweedFS. React 18 + TypeScript strict + MUI. Eigenes Session-Auth, rollenbasierte Rechte. Sicherheit > Korrektheit > Komfort. Vor jedem Push: `mvn verify` und `npm run build`/`lint`/`test` grün. Plan-Mode und Board-Issues sind verbindlich (siehe Workflow).

## Gedächtnis (Obsidian-Vault)

Über den MCP-Server obsidian-memory hast du Zugriff auf meinen
Gedächtnis-Vault unter /Users/manfredwolff/Nextcloud/ClaudeMemory.

- Lies zu Sessionbeginn Projekte/kanban-kit/kanban-kit.md (Projektstand,
  Entscheidungen, offene Punkte).
- Lies Index.md und Profil.md nur bei Bedarf.
- Wenn ich "Tagesabschluss" sage: Halte neue Entscheidungen und
  den erreichten Stand in Projekte/kanban-kit/kanban-kit.md fest und ergänze
  in Index.md unter "Zuletzt aktualisiert" eine Zeile.
