# CLAUDE-react.md — React / Vite / TypeScript / MUI

Verbindliche Regeln für das Frontend (`frontend/src/`). Ergänzend zu [CLAUDE.md](CLAUDE.md) und [CLAUDE-security.md](CLAUDE-security.md). Java-/Backend-Pendant: [CLAUDE-java.md](CLAUDE-java.md).

---

## 🏗️ Frontend-Stack

- **React 18** (funktionale Komponenten, Hooks, keine Class-Components)
- **Vite 5** (kein CRA, keine zusätzliche Webpack-Konfiguration)
- **TypeScript 5+** mit `strict: true`
- **React Router 6** (BrowserRouter, flache Routen)
- **Material UI 6 (MUI)** + Emotion für Styling
- **Vitest + React Testing Library** für Tests
- **ESLint 9** (`eslint.config.js` flat config) mit TypeScript, React, React-Hooks, jsx-a11y, sonarjs

Der Dev-Server (Vite, `:5173`) leitet `/api/*` per Proxy an Spring Boot (`:8080`). In Produktion serviert Spring Boot den Vite-Build aus `classpath:/static/`. Eine Domain, kein CORS.

---

## 📘 TypeScript

- `strict: true`, `noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch` sind aktiviert.
- `any` ist verboten, außer es gibt einen zwingenden Grund. Begründung dann im Code dokumentieren.
- Verwende `unknown` für extern stammende Daten und engere sie über Type-Guards ein.
- Discriminated Unions für fachliche Varianten.
- Keine `as`-Casts zur Umgehung von Modellfehlern. Datenmodell korrigieren statt unterdrücken.
- Keine Non-Null-Assertions (`!`) aus Bequemlichkeit. Ausnahme: `document.getElementById('root')!` in `main.tsx` ist akzeptabel.
- Props explizit typisieren. API-Responses an der Systemgrenze typisieren und auf interne Modelle mappen — die TypeScript-Typen unter `frontend/src/api/` müssen mit den Java-DTOs übereinstimmen.

---

## 🔧 Vite

- Umgebungsvariablen über `import.meta.env`. Nur `VITE_`-Prefix ist im Client sichtbar.
- **Keine** Secrets in `VITE_*` — alles dort ist potenziell öffentlich (siehe [CLAUDE-security.md](CLAUDE-security.md)).
- `vite.config.ts` nur ändern, wenn unvermeidbar. Insbesondere die Proxy-Konfiguration für `/api` bleibt stabil, damit Dev- und Prod-Verhalten symmetrisch sind.
- Neue Vite-Plugins nur mit erkennbarem Nutzen.

### Performance-Budget

Messung 2026-07-11 nach Einführung von Route-Level Lazy Loading (Issue #63):

| Chunk | Größe (minified) | Gzip | Limit | Status |
|---|---|---|---|---|
| `index.js` (Vendor) | ~408 kB | ~131 kB | 600 kB | ✅ Floor: React + MUI + Router |
| `EpicBadge.js` (react-markdown/remark) | ~170 kB | ~52 kB | 600 kB | ✅ lazy nachgeladen |
| Alle Route-Chunks | < 10 kB | < 4 kB | 600 kB | ✅ |
| `ApiUebersichtPage.js` (swagger-ui-react, Messung 2026-10-05, Issue #1410) | ~1.335 kB | ~383 kB | 600 kB | ⚠️ dokumentierter Ausnahmefall: Swagger UI lässt sich nicht sinnvoll aufteilen; nur über die Administration erreichbar und lazy geladen, belastet keine andere Seite (dazu `ApiUebersichtPage.css` ~184 kB) |

**Regeln:**
- **Route-Level Lazy Loading ist Pflicht** für alle Top-Level-Routen in `App.tsx` (via `React.lazy` + `Suspense`). Kein direktes Import einer Page-Komponente in `App.tsx` ohne `lazy()`.
- **Neuer Chunk > 600 kB** → Pflicht-Review: lässt sich die Komponente aufteilen? Wenn nein, dokumentierter Ausnahmefall in dieser Tabelle.
- **Vendor-Bundle** (`index.js`) wird durch MUI-Basis bestimmt. Keine zusätzlichen Abhängigkeiten ins Vendor-Bundle einschleppen, ohne Größe zu prüfen.
- `build.chunkSizeWarningLimit: 600` in `vite.config.ts` ist die Grenze für Build-Warnungen — entspricht dem dokumentierten Budget.

---

## ⚛️ React-Komponenten

### Größe & Struktur

- Eine Verantwortung pro Komponente.
- Große JSX-Blöcke und tiefe Bedingungen in benannte Hilfskomponenten extrahieren.
- Geschäftslogik gehört nicht ins JSX, sondern in dedizierte Module/Hooks.

### Props

- Minimal, eindeutig, stabil.
- Keine ungeplante Kombination mehrerer Booleans — stattdessen Union-Type-Varianten.
- `children` nutzen, wenn Komposition natürlicher ist als ein Prop-Sumpf.

### Code-Organisation

Aktuelle Struktur unter `frontend/src/`:

- `components/` — geteilte UI-Bausteine inkl. `AppShell` (AppBar oben, permanenter Drawer links, Main-Bereich mit `<Outlet />`), `BoardView`, `CardDetailModal`, `NewCardModal`, `EpicBadge`, `AuthCard`, `AttachmentPreview`.
- `layout/` — `navItems` (Navigationsstruktur der Sidebar).
- `pages/` — Routen-Komponenten (`ProjectsPage`, `ProjectBoardsPage`, `BoardPage`, `BoardListPage`, `EpicsPage`, `ProjectMembersPage`, `AdminPage`, `RolesPage` + Auth-Seiten Login/Signup/Verify/Forgot/Reset/Bootstrap/AcceptInvitation).
- `routes/` — `ProtectedRoute` (Session-Guard).
- `auth/` — `AuthContext` (Session-basierter Auth-State).
- `api/` — Typisierte API-Aufrufe (`client.ts` als fetch-Wrapper, je Domäne eine eigene Datei: `auth`, `projects`, `boards`, `cards`, `epics`, `comments`, `attachments`, `members`, `roles`, `admin`, `config`).
- `lib/` — Frontend-Hilfslogik (`statusColors`, `boardOps`, `roles`, `epicMeta`, …) — reine, gut testbare Module.
- `theme.ts` — MUI-Theme zentral (Tokens laut CLAUDE-design.md).
- `main.tsx` — React-Root, `BrowserRouter`, `ThemeProvider`, `CssBaseline`.
- `App.tsx` — `<Routes>` mit `React.lazy`-Pages in `<Suspense>`.
- `test/` — Vitest-Setup.

---

## 🎨 MUI / Styling

- **MUI ist die primäre UI-Library.** Vor dem Anlegen einer eigenen Komponente prüfen, ob MUI das schon liefert.
- **Theme zentral** in `theme.ts`. Farben, Spacing, Border-Radius über das Theme, nicht hartcodiert. `useTheme()` oder `sx={{ ... }}` mit Theme-Funktionen für Token-Zugriff.
- **`sx`-Prop bevorzugen** für komponentennahe Styles. Inline-Style-Objekte (`style={{ ... }}`) nur für dynamische Einzelwerte ohne Theme-Bezug.
- **Keine zweite Styling-Library** (kein Tailwind, kein styled-components on top, keine eigenen CSS-Module ohne klaren Grund).
- **CSS-Globals** sind tabu. Globaler Reset kommt aus `<CssBaseline />`.
- **Responsiveness** über `theme.breakpoints` (`xs`, `sm`, `md`, …) bzw. `useMediaQuery`. Layouts werden mobile-first geschrieben.

---

## 🪝 Hooks

- Nur auf Top-Level aufrufen, nie in Schleifen oder Bedingungen.
- Eigene Hooks beginnen mit `use…` und haben **eine** klare Verantwortung.
- `useEffect` ist kein Standardwerkzeug für Datenableitung — nur für echte Seiteneffekte (Subscriptions, Timer, Netzwerk, DOM, Synchronisation).
- Dependency-Arrays vollständig und ehrlich. Lint-Regeln dürfen nicht stillschweigend umgangen werden.
- Cleanups (Subscriptions, Timer, Listener) müssen aufgeräumt werden.
- Asynchrone Effekte müssen Race-Conditions verhindern (Cancellation-Flag oder `AbortController`).

---

## 🎛️ State Management

- Lokaler UI-State bleibt lokal.
- `useReducer` ab dem Punkt, an dem mehrere Übergänge zusammengehören.
- Globaler State nur, wenn mehrere entfernte Bereiche dieselbe Source of Truth brauchen. Vor der Einführung eines neuen Contexts oder einer State-Library (Zustand, Redux Toolkit, …): rechtfertigen, warum lokal nicht reicht.
- Server-State ist kein UI-State — keine Caches doppelt halten. Wenn der Server die Wahrheit ist, ist der Server zu fragen.
- URL-State bevorzugen, wenn ein Zustand teilbar oder navigationsrelevant ist (`useParams`, `useSearchParams`).

---

## 🌐 Datenzugriff & APIs

- Externer Input ist unsicher, bis er validiert und gemappt wurde. Sicherheitsrelevante Endpoints übergeben dem Wrapper einen `parse`-Type-Guard (z. B. `authApi.me`/`login` → `parseMe`), der die Antwort zur Laufzeit verengt.
- API-Aufrufe gehören in `frontend/src/api/` (aktuell `client.ts` + Domänen-Module wie `boards.ts`, `cards.ts`, `projects.ts`), nicht direkt in Komponenten.
- **Fehlerbehandlung an der Quelle:** Das Backend antwortet mit RFC-9457 Problem Details (`application/problem+json`, `GlobalExceptionHandler`); der `client.ts`-Wrapper wirft `ApiError` mit Statuscode, `detail`/`title` als Message und optionalem, typisiertem `fieldErrors`. UI mappt das auf nutzerverständliche Fehler.
- **Keine leeren `catch`-Blöcke.**
- **Keine technischen Fehlertexte (Stacktraces, Endpoints, Tokens) im UI.**
- **Mutationen müssen doppelte Submits verhindern** (Submit-Button mit `disabled` während Pending-State).
- Backend-DTOs und Frontend-Typen müssen synchron gehalten werden — bei Änderung der Java-DTOs immer auch die TS-Typen aktualisieren.

---

## ♿ Accessibility

- Semantisches HTML vor ARIA — Buttons lösen Aktionen aus, Links navigieren.
- MUI-Komponenten sind weitgehend a11y-tauglich; Custom-Wrapper dürfen das nicht kaputt machen.
- Sichtbare Fokus-Styles erhalten (Theme nicht so überschreiben, dass `:focus-visible` verschwindet).
- Tastaturbedienbarkeit für alle interaktiven Elemente.
- Formulare brauchen `<label>` (bei MUI: `<TextField label="…">`), verständliche Fehlermeldungen, passende `autocomplete`-Attribute.
- Bilder mit aussagekräftigem `alt` oder als dekorativ markieren.
- Farben dürfen nicht die einzige Informationsträger sein. Kontraste prüfen.

**Kachel mit eigenem Öffnen-Element (Issue #796).** Eine klickbare Kachel wird nicht selbst zum Knopf
(`role="button"` mit `tabIndex`), denn sie enthält meist weitere Knöpfe (⋮-Menü, Bearbeiten-Modus) —
verschachtelte Interaktion. Stattdessen trägt der Titel bzw. Name die Handlung als echtes Element:
`ButtonBase` mit `disableRipple`, wenn die Kachel einen Dialog öffnet (Karte, Vorhaben), und MUI
`Link component={RouterLink}` mit `underline="none"` und `color="inherit"`, wenn sie auf eine andere
Seite führt (Projekt, Board). Das Element stoppt die Weitergabe seines Klicks; die Fläche behält ihren
`onClick` für die Maus. Kein `aria-label` — der Name ist der sichtbare Titel, die Rolle sagt, was
geschieht. Der Fokusring kommt aus dem Theme, keine eigene Stilregel. In der Tab-Reihenfolge steht das
Öffnen vor den sekundären Knöpfen der Kachel.

---

## ⚡ Performance

- Saubere Komponentenstruktur und lokaler State sparen die meisten Re-Renders.
- `useMemo`/`useCallback`/`React.memo` nur mit erkennbarem Nutzen. Keine pauschale Memoization.
- Selten genutzte oder große Routen können lazy geladen werden (`React.lazy` + `Suspense`) — Chunk-Grenzen fachlich sinnvoll schneiden.
- Listen mit stabilen IDs als Key — keine Array-Indizes bei veränderlichen Listen.
- Bundle-Auswirkung neuer Dependencies vor Hinzunahme prüfen (`vite build` zeigt Chunk-Größen).

---

## 📋 Formulare

- Clientseitige Validierung ist Nutzerführung, kein Ersatz für Server-Validierung (Spring `@Valid` ist die Quelle der Wahrheit).
- Fehler feldnah anzeigen, Eingaben bei Validierungsfehlern erhalten.
- Submit-Buttons während laufender Mutation deaktivieren.
- Passende `type=…`-Attribute und `autocomplete` setzen.
- MUI: `<TextField error={…} helperText={…}>` für Feld-Validierungsfehler. Aus `ApiError.body.fieldErrors` mappen.

---

## 🧭 Routing

- Routen sind flach (`/dashboard`, `/settings`, `/tools/...`). Keine verschachtelten Routen ohne Not.
- Routenkomponenten bleiben schlank — Datenladen und Komposition in Sub-Komponenten oder Hooks.
- Lade- und Fehlerzustände auf Routenebene behandeln, wenn dort geladen wird.
- URL-Parameter validieren oder defensiv interpretieren (`Number.parseInt(id, 10)` + Range-Check, nicht naked `+id`).
- React Router läuft mit Browser-History — der serverseitige SPA-Fallback (Spring `SpaWebConfig`) sorgt dafür, dass Direktaufrufe von Sub-URLs funktionieren.

---

## 🧪 Tests

- Neue oder geänderte Logik braucht Tests (Vitest + React Testing Library).
- **Coverage-Gate:** `npm run test:coverage` (v8-Provider) bricht bei Unterschreitung der Schwellen in `vite.config.ts` (Stand 2026-07-16: 93 % Lines/Statements, 90 % Branches, 79 % Functions — ehrlicher Ist-Floor gegen Rückschritt). Ausschlüsse einzeln begründet in der Config; läuft auch in CI.
- Verhalten testen, nicht Implementierungsdetails — Tests sollen aus Nutzerperspektive lesbar sein.
- **Asynchrones Erscheinen:** `await screen.findByX(...)` statt `await waitFor(() => expect(screen.getByX(...)).toBeInTheDocument())` — die ESLint-Regel `testing-library/prefer-find-by` erzwingt das (autofixbar). `waitFor` bleibt legitim für mehrere Assertions oder Nicht-Query-Bedingungen (z. B. `expect(mock).toHaveBeenCalled()`).
- Kritische UI-Zustände abdecken: Loading, Error, Empty, Success, Disabled.
- Mocks realistisch und klein halten. Snapshot-Tests nur, wenn sie wirklich Stabilität messen.
- API-Mocks: bevorzugt `fetch` über `vi.spyOn(global, 'fetch')` oder `msw` — keine ungetypten Mock-Objekte.

**Coverage-Philosophie** (analog zu `CLAUDE-java.md` §5.4 — die Backend-Antwort auf „100 % unmöglich" ist dort schon verbindlich; das Folgende überträgt sie aufs Frontend):

- **Signal-Charakter.** Die Coverage-Zahl ist ein externes Vertrauenssignal, kein Selbstzweck — sie ist das Werkzeug, mit dem der Mensch KI-erzeugten Code beurteilt, ohne jede Zeile selbst lesen zu müssen. Zeigt sie 98 %, müssen die restlichen 2 % *echt ungetestete Logik* sein: eine bewusste Entscheidungsstelle („reinschauen oder Tests nachziehen"), kein Blindfleck durch großzügige Ausschlüsse.
- **Ziel 100 % der sinnvollen Logik.** Die vite-Schwellen (`thresholds` in `vite.config.ts`) sind ein **Ratchet**: nur anheben, nie senken. Der aktuelle Stand ist ein Zwischenstand, kein Endziel.
- **Ausschließen nur logikfrei.** `coverage.exclude` nimmt ausschließlich Dateien ohne eigenes Verhalten — Bootstrap/Wiring/Design-Tokens (Vorbild: `main.tsx`, `App.tsx`, `theme.ts`), jeweils mit Inline-Begründung in `vite.config.ts`, dateiweise wie die JaCoCo-Ausschlüsse im Backend (keine Verzeichnis-Wildcards). **Untestete Logik gehört nie in `coverage.exclude`** — das würde das Signal fälschen. Gegenbeispiel aus der Praxis: die `api/*.ts`-Endpoint-Wrapper enthalten echte Logik (Pfad, HTTP-Methode, Body, Response-Mapping) und wurden deshalb direkt getestet statt ausgeschlossen (kanban-kit#216).
- **Wenn 100 % schwierig erscheint,** lautet die Antwort wie im Backend **nicht** „Schwellwert senken", sondern: testen, den Code testbar umbauen, oder — nur wenn die Datei wirklich logikfrei ist — begründet ausschließen.
- **Sonar-Sync.** `sonar.coverage.exclusions` (`sonar-project.properties`) muss synchron zu `vite.config.ts` `coverage.exclude` (und zu den JaCoCo-Excludes in `pom.xml`) bleiben. Ohne diesen Abgleich sieht SonarCloud einen anderen Mess-Scope als die lokalen Gates und wertet dort ausgeschlossene, logikfreie Dateien als 0 % ab — die Overall-Zahl fällt dann weit unter den echten lokalen Stand, obwohl lokal alles grün ist (siehe kanban-kit#215). Bei jeder Änderung an einer der drei Exclude-Listen die anderen beiden gegenprüfen.

---

## ❌ Verbotene Muster

- `any` ohne zwingende Begründung
- Type-Assertions zur Unterdrückung echter Modellfehler
- Non-Null-Assertions aus Bequemlichkeit
- Business-Logik tief im JSX
- Unnötiger globaler State
- Leere `catch`-Blöcke
- Index-Keys für dynamische Listen
- Unzugängliche Custom-Controls
- Neue Dependencies aus Bequemlichkeit
- Secrets im Client-Bundle
- `useEffect` für reine Datenableitung
- `dangerouslySetInnerHTML` ohne dokumentierten, geprüften Grund
- Große Refactorings ohne Aufgabenbezug

---

## 🔍 ESLint / A11y-Gate

**ESLint ist verbindlich** und muss vor jedem Push grün sein.

```bash
cd frontend && npm run lint   # ESLint auf src/
```

**Konfiguration:** [`eslint.config.js`](frontend/eslint.config.js) (flat config, ESLint 9+)
- `typescript-eslint` (recommended): TypeScript-Korrektheit, kein `any`
- `eslint-plugin-react` (recommended + jsx-runtime): React-Regeln
- `eslint-plugin-react-hooks` (recommended): Hooks-Regeln, `exhaustive-deps`
- `eslint-plugin-jsx-a11y` (recommended): Accessibility-Regeln
- `eslint-plugin-testing-library` (recommended, **nur an Test-Dateien** `**/*.test.{ts,tsx}`): fängt Test-Anti-Muster wie `waitFor(() => expect(getByX()))` → `findByX` (`prefer-find-by`) direkt im Gate ab, autofixbar.
- `@typescript-eslint/no-deprecated` (**typed rule**, deshalb `parserOptions.projectService: true`): Nutzung `@deprecated`-markierter APIs (z. B. abgekündigte MUI-Props wie `inputProps`) ist ein harter Lint-Fehler.
- `eslint-plugin-sonarjs` (**nicht** das recommended-Set, sondern genau fünf Regeln): bildet die Befunde nach, die sonst erst nach dem Push bei SonarCloud auffielen (Plan #1042).
  - `sonarjs/cognitive-complexity` mit Schwelle **15** (Sonar S3776)
  - `sonarjs/no-nested-template-literals` (S4624)
  - `no-nested-ternary` (S3358 — Kernregel, kein Plugin nötig)
  - `react/jsx-no-useless-fragment` (S6749)
  - `@typescript-eslint/prefer-nullish-coalescing` (S6606) mit `ignorePrimitives: { string: true }` und `ignoreMixedLogicalExpressions: true` — ohne die beiden Optionen meldet die Regel String-Defaults und gemischte Logik-Ausdrücke, die Sonar nicht beanstandet und deren Umbau das Verhalten änderte.

**Leitplanke im Gate statt Doku, die bittet.** Wenn ein Modell wiederholt dasselbe veraltete/nicht-idiomatische Muster reproduziert (es kennt das *häufigste*, nicht das *aktuellste* aus dem Trainingskorpus — so entstanden die `prefer-find-by`- und `inputProps`-Wellen, die erst spät bei Sonar auffielen), ist die wirksame Antwort eine **harte ESLint-Regel im Pflicht-Gate**, nicht ein Satz in dieser Datei. Doku wird übersehen; das Gate nicht.

**Regel-Deaktivierungen** stehen einzeln begründet in der Config — pauschales Abschalten von Kategorien ist verboten. **Neue `eslint-disable`-Kommentare** im Produktivcode brauchen einen Begründungskommentar direkt darüber (etabliertes Beispiel: die zwei dokumentierten `exhaustive-deps`-Ausnahmen für das Auto-Routing in ProjectsPage/ProjectBoardsPage).

**Pflichtchecks vor Push (Frontend):**

```bash
cd frontend && npm run build    # TypeScript + Vite
cd frontend && npm run lint     # ESLint + jsx-a11y
cd frontend && npm test         # Vitest
```

**Mutationsprüfung (Issue #1104):**

```bash
node scripts/mutationspruefung.mjs aenderung frontend   # beim Push über den Batch, Stufe push
node scripts/mutationspruefung.mjs vollauf frontend     # Handlauf, etwa wöchentlich, Schwelle 80 % je Ausschnitt
```

Die Änderungsprüfung steht als `buildChecks` in `.claude/workflow.config.json` an der Stufe `push` und ist
Pflicht. Der Vollauf ist keine Pflichtprüfung mehr (Issue #1344).

**Wöchentlicher Vollauf (von Hand).** Manne startet ihn etwa einmal in der Woche mit
`node scripts/mutationspruefung.mjs vollauf frontend`. Er misst jeden aufgenommenen Ausschnitt gegen 80 %,
schlägt den nächsten Kandidaten ab 82 % zur Aufnahme vor und schreibt die Gedächtnisdatei
`.claude/mutationsvollauf-frontend.json`. Liegt ein aufgenommener Ausschnitt unter 80 %, entsteht je
betroffenem Ausschnitt eine Karte, die seine Quote wieder anhebt. Die Altlast-Bedingung 4 unten („im
letzten Vollauf überlebt“) bezieht sich auf diesen Handlauf; je älter er ist, desto seltener greift ein
Altlast-Vermerk.

**Der Stufenplan ist die einzige Quelle des Prüfbereichs** (Plan #1270, Issues #1275, #1276):
[`frontend/mutationsstufen.json`](frontend/mutationsstufen.json) führt die `ausnahmen` (reine Stil- und
Theme-Dateien, der Testbaum) und die `ausschnitte` — benannte Teile des Frontends mit `muster`, optional
`testMuster`, `aufgenommen` (`false` oder das Aufnahmedatum), `phase`, `reihenfolge` und `begruendung`.
Mensch und Werkzeug lesen dieselbe Datei: [`frontend/mutationsbereich.mjs`](frontend/mutationsbereich.mjs)
leitet daraus die `mutate`-Liste für [`frontend/stryker.config.mjs`](frontend/stryker.config.mjs) und den
Prüfbereich des Treibers ab, [`frontend/mutationTestUmfang.ts`](frontend/mutationTestUmfang.ts) den
Testumfang. Niemand pflegt eine dieser Listen von Hand. Jede Quelldatei unter `frontend/src` gehört genau
einem Ausschnitt oder den Ausnahmen; eine neue Datei ohne Platz im Plan lässt
[`mutationsstufen.test.ts`](frontend/src/lib/mutationsstufen.test.ts) im Pflicht-Gate fallen.

**Aufnahme Ausschnitt für Ausschnitt.** Mutiert werden die aufgenommenen Ausschnitte. Der Vollauf misst
zusätzlich genau den nächsten **Kandidaten** — den nicht aufgenommenen mit der kleinsten `reihenfolge` —
und schlägt ihn **ab 82 %** zur Aufnahme vor; das ist eine Zeile im Bericht, zwei Punkte über der
Schwelle, damit ein frisch aufgenommener Ausschnitt nicht beim ersten schwankenden Lauf wieder darunter
fällt. **Die Aufnahme trägt der Mensch per Karte ein** (Datum in `aufgenommen`); kein Codepfad nimmt
selbst auf. Eine **Rücknahme** setzt `aufgenommen` wieder auf `false` und braucht eine Begründung in
`begruendung` des Ausschnitts. Nach der Aufnahme gilt in diesem Ausschnitt von selbst die Pfadfinderregel
aus #1104: Wer eine Datei dort berührt, hinterlässt sie ohne neuen Überlebenden.

**Die Schwelle von 80 % gilt je Ausschnitt**, nicht über die Gesamtmenge: Ein aufgenommener Ausschnitt
darunter hält den Vollauf an und wird genannt, auch wenn alle anderen darüber liegen; der Kandidat hält
nie an. Die beiden Bestandsausschnitte `hilfsfunktionen` und `server-anbindung` tragen
`gemeinsameSchwelle` und zählen zunächst gemeinsam, bis jeder von ihnen die 80 % einmal erreicht hat.

**Die Änderungsprüfung** mutiert nur die berührten Dateien der aufgenommenen Ausschnitte; ein
Überlebender in einer berührten Datei hält an, Überlebende anderswo erscheinen nur als Zahl. Der Vollauf
prüft den ganzen Bereich ohne `--incremental` und schreibt die Gedächtnisdatei
`.claude/mutationsvollauf-frontend.json`. Einen geänderten Test ordnet die Änderungsprüfung über den Namen
(`a.test.ts` → `a.ts`), den letzten Vollauf oder die feste Zuordnung in
[`scripts/mutationszuordnung.json`](scripts/mutationszuordnung.json) seiner Quelle zu. Ein Test, dessen
Name keine Quelle trifft, braucht dort einen Eintrag — sonst hält die Änderungsprüfung sofort an, statt
die ganze Seite zu mutieren (Issue #1287). Die Stryker-Berichte (JSON und HTML) liegen unter
**`.claude/stryker/`** — dort sind sie ignoriert und verschmutzen den Arbeitsbaum nicht; der Treiber liest
den JSON-Bericht von dort.

Einen einzelnen Ausschnitt misst man eingegrenzt mit **einer** kommagetrennten Liste:
`npx stryker run -m 'src/components/leitstand/**/*.tsx,!src/**/*.test.tsx'` aus `frontend/`. Zwei
`-m`-Schalter überschreiben einander — übrig bliebe nur der Ausschluss, und Stryker mutierte nichts.

**Darstellungsmutanten laufen gar nicht erst** (Issue #1277): Der Ignorer
[`frontend/stryker/darstellungIgnorer.js`](frontend/stryker/darstellungIgnorer.js) nimmt beim
Instrumentieren Mutanten in `sx`- und `style`-Attributen, in den Argumenten eines `styled(...)` und in
Objektliteralen benannter Stilkonstanten (`…Sx`, `…_SX`) heraus, deren Schlüssel sämtlich Stilschlüssel
sind. Sie erscheinen als `Ignored` und zählen nicht in der Quote. Stilwerte an anderer Stelle — ein
Objekt, das an eine Hilfsfunktion geht, ein `keyframes`-Text, eine `color-mix`-Konstante — erfasst er
nicht; dort töten Tests über die erzeugte CSS-Regel (`cssRegel` aus `src/test/cssRegel.ts`) oder eine
Ausnahme je Stelle.

**Ausnahmen, zwei Formen:**

- `// Stryker disable next-line <mutator>: <Grund>` **je Stelle** — Strykers eigene Ausnahme; der Mutant
  zählt als ausgenommen, nicht als überlebt. Gedacht für gleichwertige Mutanten, deren Wirkung von außen
  nicht zu beobachten ist. In JSX steht sie als Zeilenkommentar **im Tag vor dem Attribut**; ein
  `{/* … */}` vor dem Element greift nicht.
- Der **Altlast-Vermerk** an der Zeile des Mutanten:
  `// Mutations-Altlast: <Grund> (#<Issue>, <JJJJ-MM-TT>)`. Die Begründung ist Pflicht. Er gibt die
  Änderungsprüfung frei, **zählt aber weiter mit**, und er trägt nur, wenn alle vier Bedingungen
  zugleich erfüllt sind:
  1. Der Vermerk steht an der Stelle des Überlebenden.
  2. Die Zeile des Mutanten ist gegenüber dem Anker unverändert.
  3. Keine Testdatei, die die Stelle deckt, hat sich geändert.
  4. Der Mutant hat auch im letzten Vollauf überlebt (`.claude/mutationsvollauf-frontend.json`) —
     sonst ist er keine Altlast, sondern neu.

**Der zugesagte Umfang muss eingelöst sein.** Was mutiert wird, muss der Testlauf der Mutationsprüfung
auch erreichen. Der Testumfang leitet sich je Ausschnitt ab — für die aufgenommenen und den Kandidaten:
`testMuster`, falls vorhanden; sonst die Musterwurzel bei einem Muster mit Wildcard
(`src/components/leitstand/**/*.tsx` → alle Tests unter `src/components/leitstand/`); sonst der
Konventionstest daneben (`X.tsx` → `X.test.tsx`). Liefen Mutations- und Testumfang auseinander, sähe der
Bericht trotzdem vollständig aus, enthielte aber keine Tötung aus dem fehlenden Teil — ein stiller
Blindfleck (Issue #1073, Befund 1). Die Deckung hält deshalb der Test
[`strykerUmfang.test.ts`](frontend/src/lib/strykerUmfang.test.ts) im Pflicht-Gate, nicht dieser
Absatz. Folge für Tests: Ein Baustein, den nur Seiten-Tests außerhalb seines Ausschnitts rendern, ist in
der Abdeckung grün, in der Mutationsprüfung aber ungedeckt — er braucht Tests im eigenen Ausschnitt.
`thresholds.break` steht auf `null`: Ob und wann der Lauf abbricht, regelt die Mutationsprüfung auf dem
geänderten Code (Issue #1104), nicht eine Gesamtschwelle. Den Halt liefert der Rückgabewert von
`scripts/mutationspruefung.mjs` — bei der Änderungsprüfung ein Überlebender in einer berührten Datei, beim
Vollauf ein aufgenommener Ausschnitt unter 80 %. Dieser Halt betrifft den wöchentlichen Handlauf, nicht
`push main`.

---

## 📌 Versionsstrategie

- **Ist:** React 18.3, Vite 5.4, MUI 6.1, React Router 6.28, TypeScript 5.6, Vitest 2.1.
- **Zielkorridor (jeweils eigener Plan, kein Nebenbei-Upgrade):** React 19 + **React Compiler** (macht manuelles `useMemo`/`useCallback`/`React.memo` weitgehend obsolet — bis dahin gilt die Memoization-Zurückhaltung aus §Performance), Vite 7, MUI 7, React Router 7, Vitest 3+.
- **Bewusst offene Architektur-Optionen** (bei Bedarf eigener `/plan`, nicht nebenbei einführen): TanStack Query für Server-State (würde die handgeschriebenen Cancellation-Flags ersetzen), Playwright-E2E für Login-/Board-Golden-Paths.

---

## 🔗 Weiterführende Docs

- [CLAUDE.md](CLAUDE.md) — Projekt-Übersicht
- [CLAUDE-java.md](CLAUDE-java.md) — Backend-Pendant
- [CLAUDE-security.md](CLAUDE-security.md) — XSS, Storage, Secrets, Session-Cookie
- [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md) — 9-Schritte-Workflow + Pflichtchecks
