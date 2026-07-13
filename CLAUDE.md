# CLAUDE.md — Projekt-Standards

Diese Datei ist der Einstiegspunkt für alle Engineering-Regeln in diesem Projekt. Sie definiert den Mindeststandard — Abweichungen sind Fehler und müssen vor dem Abschluss einer Aufgabe korrigiert werden.

---

## 🎯 Schnelleinstieg

- **Neu im Projekt?** Lies diese Datei + [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md).
- **Java/Spring-Backend arbeiten?** → [CLAUDE-java.md](CLAUDE-java.md)
- **React-Frontend arbeiten?** → [CLAUDE-react.md](CLAUDE-react.md)
- **Security?** → [CLAUDE-security.md](CLAUDE-security.md)
- **Plan-Mode / Git / Issue-Workflow?** → [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md)

---

## 📚 Guide-Familie

| Guide | Fokus | Wiederverwendbar |
|---|---|---|
| **CLAUDE.md** (diese Datei) | Projekt-Übersicht + Pflichtchecks | ❌ Projekt |
| [CLAUDE-java.md](CLAUDE-java.md) | Java 21, Spring Boot 3, TDD, Coverage, Mutationstests | ✅ Allgemein |
| [CLAUDE-react.md](CLAUDE-react.md) | React 18, Vite, TypeScript, MUI, Lazy Loading, ESLint/A11y | ✅ Allgemein |
| [CLAUDE-security.md](CLAUDE-security.md) | Spring Security, JPA, Frontend-XSS, Secrets, Session-/Token-Handling | ✅ Allgemein |
| [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md) | 9-Schritte-Workflow, Issues, Git, Pflichtchecks | ✅ Allgemein |

---

## 🌐 Projektkontext

**Ziel:** **kanban-kit** (Repo `manban`) — ein self-hostbares, mandantenfähiges Kanban-Board als schlanke Trello-Alternative zum Selbstbetreiben. Projekte, Boards mit konfigurierbaren Spalten, Karten mit Markdown, Epics, Datei-Anhänge (Bild-/PDF-Vorschau) und eine rollenbasierte Rechteverwaltung (Projekt- und Plattform-Rollen). UI im Stil eines Dashboards: linke Navigation, rechter Inhaltsbereich.

**Stack:**

| Schicht | Technologie |
|---|---|
| Backend-Sprache | Java 21 (LTS) |
| Backend-Framework | Spring Boot 3.5, Spring Data JPA, Spring Web |
| Build (Backend) | Maven (inkl. `frontend-maven-plugin` für den Vite-Build) |
| Datenbank | PostgreSQL 16 |
| Objektspeicher | MinIO (S3-kompatibel) für Datei-Anhänge |
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

**Identity / Auth:** Authentifizierung ist projekteigen — kein externer Identity-Provider. Registrierung mit E-Mail-Verifikation, Passwort-Reset per Token/Mail, ein per Bootstrap-Token angelegter erster Plattform-Admin, sowie signierte Session-Tokens (HttpOnly-Cookie). Für den Kanban-kompatiblen Ingest ohne Login gibt es projektgebundene Access-Tokens (`accesstoken` + `kanbancompat`). Autorisierung ist rollenbasiert: **Projekt-Rollen** (RBAC pro Projekt) plus **Plattform-Admin**. Rollen- und Rechte-Matrix: [rollen_rechte.md](rollen_rechte.md).

---

## 📂 Projektstruktur

```
/
├── CLAUDE*.md                          # Guide-Familie (Workflow-Guide unter .claude/)
├── pom.xml                             # Maven-Konfiguration (inkl. frontend-maven-plugin)
├── Dockerfile, docker-compose.yml      # Multi-Stage-Image + lokale Composition (Postgres, MinIO, Caddy)
├── Caddyfile                           # Reverse-Proxy + automatisches TLS
├── .env.example                        # DB-, MinIO- und App-Konfig-Vorlage
├── issues/                             # Lokaler Issue-Tracker (0001.md …) für den 9-Schritte-Workflow
├── src/main/java/org/mwolff/manban/    # Backend (je Modul: domain/application/web/infrastructure)
│   ├── ManbanApplication.java
│   ├── auth/                           # Registrierung, Login, Session, Passwort-Reset, Bootstrap-Admin
│   ├── project/                        # Projekte, Mitgliedschaften, RBAC (Projekt-Rollen)
│   ├── board/                          # Boards + konfigurierbare Spalten
│   ├── card/                           # Karten, Epics, Abhängigkeiten, Done-Retention-Job
│   ├── comment/                        # Kommentare an Karten
│   ├── attachment/                     # Datei-Anhänge (MinIO-Speicher, Bild-/PDF-Vorschau)
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
        ├── layout/                     # AppShell, navItems
        ├── components/                 # geteilte UI-Bausteine (BoardView, Modals, …)
        ├── pages/                      # Routen-Komponenten (Projects, Boards, Epics, Admin, Auth-Seiten)
        ├── routes/                     # ProtectedRoute
        ├── lib/                        # Frontend-Hilfsfunktionen (statusColors, boardOps, …)
        ├── api/                        # client.ts (fetch-Wrapper) + <domain>.ts
        └── test/                       # Vitest-Setup
```

---

## ✅ Pflichtchecks vor Abschluss einer Aufgabe

```bash
# Backend
mvn verify                              # Tests + Coverage + Mutation (siehe CLAUDE-java.md §5)

# Frontend
cd frontend && npm run build            # tsc + vite build
cd frontend && npm run lint             # ESLint + jsx-a11y
cd frontend && npm test                 # Vitest
```

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
- **CLAUDE-security.md** hat in allen Sicherheitsfragen Vorrang.
- **CLAUDE-workflow.md** beschreibt das Prozess-Drumherum (Plan-Mode, Issues, Commits, GO-Freigabe, Tests). Wer Code schreibt ohne den Workflow zu befolgen, hat die Aufgabe nicht abgeschlossen.

---

**TL;DR:** Java 21 + Spring Boot 3 (TDD-pflichtig, 100 % Coverage) auf PostgreSQL 16 + MinIO. React 18 + TypeScript strict + MUI. Eigenes Session-Auth, rollenbasierte Rechte. Sicherheit > Korrektheit > Komfort. Vor jedem Push: `mvn verify` und `npm run build`/`lint`/`test` grün. Plan-Mode und lokale Issues sind verbindlich (siehe Workflow).

## Gedächtnis (Obsidian-Vault)

Über den MCP-Server obsidian-memory hast du Zugriff auf meinen
Gedächtnis-Vault unter /Users/manfredwolff/Nextcloud/ClaudeMemory.

- Lies zu Sessionbeginn Projekte/kanban-kit/kanban-kit.md (Projektstand,
  Entscheidungen, offene Punkte).
- Lies Index.md und Profil.md nur bei Bedarf.
- Wenn ich "Tagesabschluss" sage: Halte neue Entscheidungen und
  den erreichten Stand in Projekte/kanban-kit/kanban-kit.md fest und ergänze
  in Index.md unter "Zuletzt aktualisiert" eine Zeile.
