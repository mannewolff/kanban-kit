# Frontend-Review aa1610afd33f9f0ae4ad51cf9528030bd33d2240

Stand: 09.09.2026. Unabhängiges, risikoorientiertes Review; keine Produktdateien geändert, keine Commits/Pushes/Issue-Kommentare.

## Belastbare neue Befunde

### FE-01 — P1: Ein Checkbox-Klick überschreibt unmittelbar zuvor gespeicherte Kartenfelder

- Ort: `frontend/src/components/CardDetailModal.tsx:1232–1241` (engster Anker 1234), außerdem `1154–1162`, `1191–1203`; Parent `frontend/src/pages/BoardPage.tsx:91–98,315–319`.
- Trigger: Karte mit Taskliste öffnen, Titel und/oder Fälligkeit/Vorhaben ändern, speichern, ohne Schließen eine Task-Checkbox anklicken. Auch erneutes Bearbeiten/Speichern reicht zum Zurücksetzen.
- Ursache/Auswirkung: `save()` übernimmt nur `deps` und Beschreibung in den gespeicherten lokalen Zustand, ignoriert die zurückgegebene Karte. `toggleTask()` sendet weiterhin `card.title`, `card.shortcode`, `card.parentId`, `card.dueDate`; `startEditing()` kopiert dieselben alten Props. BoardPage lädt die Kartenliste neu, aktualisiert aber `selectedCard` nicht. Damit überschreibt der nächste Voll-PATCH eigene bereits bestätigte Änderungen. Der Titel im Modal bleibt ebenfalls veraltet.
- Beleg: Isolierter RTL-Test gegen die Originalkomponente bestätigt zwei Requests: zuerst Titel `New saved title`, anschließend durch Checkbox wieder `Aufgabe`. Backend `CardService.update:710–720` ersetzt genau diese Werte; kein serverseitiger Merge verhindert den Verlust.
- Fehlender Test: Bestandsregressionen prüfen zweite Beschreibung und Dependencies, jedoch nicht Titel/Fälligkeit/Parent/Kürzel über zwei Schreibaktionen.
- Fix: Nach erfolgreicher Mutation vollständigen bestätigten Kartenstand konsistent halten; alle Folgeaktionen und Anzeige daraus speisen. Draft und bestätigte Karte trennen. Checkbox möglichst über schmalen Beschreibungsschreibpfad statt Voll-PATCH führen.

### FE-02 — P2: Abgebrochene Beschreibungsänderungen werden beim Task-Toggle doch gespeichert

- Ort: `frontend/src/components/CardDetailModal.tsx:1575` (Abbrechen), `1347` (Dialog onClose), `1224–1235`, `1465` (Lesemodus nutzt `body`). Exakter Suchanker: `<Button onClick={() => setEditing(false)}>Abbrechen</Button>`.
- Trigger: Beschreibung `- [ ] Original task` zu `- [ ] Discard this draft` ändern, Abbrechen oder Escape, dann die sichtbare Task-Checkbox anklicken.
- Ursache/Auswirkung: Abbrechen setzt ausschließlich `editing=false`. `body` bleibt der verworfene Entwurf, wird im Lesemodus angezeigt und bei Checkbox-Klick vollständig persistiert. Abbrechen verwirft die Änderung tatsächlich nicht.
- Beleg: RTL-Reproduktion sendet nach Abbrechen `- [x] Discard this draft`; keine vorherige Save-Aktion. Bestands-Abbruchtest 1465ff. ändert nur den Titel und kann diesen Fehler nicht erkennen.
- Fix: Gemeinsamen cancel-Handler für Button/Escape/Backdrop, der alle Draft-Felder auf den bestätigten Stand zurücksetzt; Lesemodus und Task-Toggle ausschließlich aus bestätigter Beschreibung bedienen.

### FE-03 — P2: Checkbox-Index zählt Markdown-Codebeispiele und verändert falsche Textstellen

- Ort: `frontend/src/lib/markdownTasks.ts:82–90`; dazu `CardDetailModal.tsx:131–143` (Index der tatsächlich gerenderten Inputs).
- Trigger: Beschreibung `    - [ ] Code sample\n\n- [ ] Real task` (vier führende Leerzeichen beim Beispiel); einzige gerenderte Checkbox anklicken.
- Ursache/Auswirkung: GFM rendert den eingerückten ersten Absatz als Code, `toggleTaskAt` zählt ihn wegen `\s*` trotzdem als Task. Der Klick auf „Real task“ ändert `Code sample` zu `[x]`, während der angeklickte Task unverändert bleibt. Entsprechende Abweichungen sind auch bei Blockquotes bzw. gemischten/längeren Code-Fences möglich; der konkret geprüfte Fall genügt bereits.
- Beleg: Dritter RTL-Test bestätigt genau eine gerenderte Checkbox und anschließend den PATCH-Text `    - [x] Code sample\n\n- [ ] Real task`.
- Fix: Task-Positionen aus derselben Markdown-Struktur/Quellposition wie der Renderer gewinnen. Mindestens indented code, Blockquote-Tasks und korrekte Fence-Grenzen gemeinsam für Darstellung/Mutation behandeln. Nicht ausschließlich Regex-Ähnlichkeit behaupten.

### FE-04 — P2: Harter Nachtlaufabbruch auf Laufebene verschwindet aus Anzeige und Speicherung

- Ort: `frontend/src/pages/NightRunPage.tsx:160–178` (`ausParser`), `228–246` (`zurEinlieferung`), Anzeige `LaufPanel` ab 456.
- Trigger: Gültiger Ergebnisstand mit `abschluss: "harterStopp"`, etwa unsauberer Working Tree vor der ersten Einheit, `einheiten: []`, `fehlerText` gesetzt. Ebenso Abbruch zwischen bereits grünen Einheiten.
- Ursache/Auswirkung: Parser setzt ausdrücklich `runState=RED`, `runErrorClass=HARD_ABORT`, `runExcerpt`; beide Adapter übernehmen keines dieser Felder. UI zeigt nur „0 bearbeitet, 0 übergangen“/grüne Einheiten, ohne Abbruchgrund. Nach Upload geht der Grund endgültig verloren; erneutes Einlesen desselben abgeschlossenen Laufs wird serverseitig dedupliziert.
- Beleg: `frontend/src/lib/nightRunErgebnisstand.ts:256–278`; Parser-Tests `nightRunErgebnisstand.test.ts:375–404` prüfen diesen echten Runner-Fall. `NightRunPage.test.tsx` enthält keinen Laufebenen-Abbruchtest. Backend DTO/Service/View und `api/nightRuns.ts` haben ebenfalls kein entsprechendes Feld — reine JSX-Korrektur reicht für persistierte Läufe nicht.
- Fix: Laufzustand/Fehlerklasse/Auszug durch Anzeige, Einlieferungsvertrag und Persistenz führen, sichtbar auf Laufebene darstellen; leere hart abgebrochene Läufe testen. Bestehende gespeicherte Läufe haben diese Information nicht.

### FE-05 — P2: Nachtlauf-Kartencache verwechselt gleiche Nummern verschiedener Projekte

- Ort: `frontend/src/pages/NightRunPage.tsx:588–589,618–635` (engster Anker 619–620).
- Trigger: Unter `/projects/1/nachtlauf` Lauf mit Karte #700 aufklappen, anschließend bei gemounteter Route auf `/projects/2/nachtlauf` wechseln und dort Lauf mit eigener #700 aufklappen. Nummern sind laut API projektlokal.
- Ursache/Auswirkung: `katalogRef`, `katalog`, `geladeneLaeufe` und offenes Detail bleiben beim Parameterwechsel bestehen. Neue Liste wird geladen, die Nummer #700 gilt jedoch schon als bekannt und wird nie für Projekt 2 angefragt. Herkunft und aufrufbare Karte stammen aus Projekt 1; im geöffneten Modal ist zugleich `projectId=2`, wodurch weitere Nummernsprünge nochmals den Kontext wechseln.
- Beleg: Initial-/Ladeeffekt 591–615 aktualisiert nur Laufdaten/Zähler und setzt keine Caches zurück. Route `App.tsx` versieht die Page nicht mit Projekt-Key; `AppShell.tsx:585` rendert ebenfalls nur `<Outlet />`. Isolierter RTL-Test mit echtem Router-Parameterwechsel bestätigt: Im Lauf von Projekt 2 wird Karte ID 1 aus Projekt 1 geöffnet, und `byNumber(2,700)` erfolgt nie. Keine Projektwechseltests in NightRunPage.test.tsx.
- Fix: Alle projektspezifischen Zustände und Caches an Projekt-ID binden oder Page bei ID-Wechsel remounten; laufende Ketten-/Upload-Antworten des alten Projekts ebenfalls verwerfen. Gleichnamige Nummern und laufende Requests im Regressionstest.

### FE-06 — P2: Verspäteter Board-Reload ersetzt nach Boardwechsel die aktuelle Ansicht

- Ort: `frontend/src/pages/BoardPage.tsx:112–123`; Effekt 128–132.
- Trigger: Auf Board A läuft SSE-/Fokus-Reload; Nutzer wechselt per BoardSwitcher zu B; B antwortet zuerst, anschließend trifft A ein. Analog zwei überlappende Reloads desselben Boards mit älterem Snapshot als letzter Antwort.
- Ursache/Auswirkung: `load()` hat weder Cancellation noch Request-Generation. Die alte Antwort ersetzt `board`, `cards`, `epics`, Projektkontext und Loading-State, obwohl URL und SSE-Abonnement bereits B meinen. Ein altes 404 kann sogar aus B wegnavigieren. Die danach angebotenen Aktionen können Karten bzw. Board A verändern, während Routing/andere Requests B verwenden.
- Beleg: Anders als AppShell/BoardSwitcher/BoardListPage fehlt hier ein Aktivitäts-/Kennungscheck vollständig. `projectIdRef` und `selectedCard` werden beim Boardwechsel ebenfalls nicht bereinigt. Isolierter RTL-Test mit Deferred Promise bestätigt: zuerst erscheint Board B, nach der verspäteten A-Antwort erscheint Board A, während der Router weiterhin `/boards/2` meldet. Bestands-ID-Wechseltests verhindern keine umgekehrte Antwortreihenfolge von `load`.
- Fix: Request-Generation je ID und Reload; Erfolg und Fehler nur für aktuellste Generation anwenden. Projektreferenzen/ausgewählte Karte bei Boardwechsel zurücksetzen und keine alten Boarddaten im neuen Kontext interaktiv lassen.

### FE-07 — P2: Karten sowie Vorhaben-, Projekt- und Boardkacheln lassen sich per Tastatur nicht öffnen

- Orte: `frontend/src/components/BoardView.tsx:773–778`; `frontend/src/pages/EpicsPage.tsx:244–248`; `frontend/src/pages/ProjectsPage.tsx:170–175`; `frontend/src/pages/ProjectBoardsPage.tsx:162–165`.
- Trigger: Navigation mit Tab und Enter/Leertaste; insbesondere Vorhaben ohne Anforderung oder Projekt/Board, das noch nicht im Board-Verlauf steht.
- Ursache/Auswirkung: MUI `Paper` rendert `div`; ausschließlich `onClick`, kein Fokusziel/Link/Keyboard-Handler. Vorhaben-Menü bietet nur Ausblenden, und der Badge erhält dort kein `onOpen`. Damit ist die zentrale Öffnen-Aktion der Kachel für Tastaturnutzer unerreichbar. Projekt-/Boardkacheln haben denselben Fehler. Auch normale BoardView-Karten haben nur Paper/onClick; speziell VIEWER sehen kein Kartenmenü und haben dort keinen Tastatur-Öffner.
- Beleg: EpicsPage-Test 763ff. bestätigt ausdrücklich, dass der nächste Tab direkt auf dem Menübutton landet; die Tastaturtests decken Menü und Anforderungsverweis ab, nicht das Öffnen des Vorhabens. `jsx-a11y` erkennt MUI-Kompositionen hier nicht automatisch.
- Fix: Echten fokussierbaren Link/Button für Titel/Öffnen anbieten; sekundäre Menü-/Delete-Aktionen getrennt halten, keine ineinander verschachtelten Buttons. Tab→Enter-Tests für alle vier Ansichten, ausdrücklich auch VIEWER-Karten.

### FE-08 — P2: Login verwirft Einladungslink samt Token

- Ort: `frontend/src/routes/ProtectedRoute.tsx:18`; `frontend/src/pages/LoginPage.tsx:27–30`; geschützte Route in `frontend/src/App.tsx:90`.
- Trigger: Nicht angemeldeter Empfänger öffnet `/invitations/accept?token=…`, meldet sich anschließend an.
- Ursache/Auswirkung: ProtectedRoute ersetzt die URL durch `/login`, ohne den ursprünglichen Pfad/Query zu sichern. Login navigiert anschließend immer zu `/`. AcceptInvitationPage wird nicht aufgerufen, die Einladung nicht angenommen; der Empfänger muss den ursprünglichen Mail-Link erneut öffnen. Auch normale Board-Deep-Links gehen verloren.
- Beleg: Kein `state.from`/Rücksprung in beiden Komponenten; die Accept-Seite ist vollständig hinter ProtectedRoute. Separate Seiten-Tests decken diesen zusammengesetzten Ablauf nicht ab.
- Fix: Internen ursprünglichen Pfad einschließlich Query im Login-Flow erhalten und nach Login zurückkehren; für Registrierungs-/Verifikationsstrecke ebenfalls Einladungsbezug erhalten. Nur lokale erlaubte Rücksprungziele verwenden.

### FE-09 — P2: Karten aus Listenansicht haben leere Zuständigen- und Label-Auswahl

- Ort: `frontend/src/pages/BoardListPage.tsx:580–587`.
- Trigger: Bearbeitungsberechtigter öffnet eine Karte aus der Board-Liste, deren Board Labels und Projekt Mitglieder besitzt.
- Ursache/Auswirkung: Der Modal-Aufruf setzt `canEdit`, übergibt aber weder `members` noch `boardLabels`. Die Modal-Defaults sind `[]`, beide Autocompletes bleiben trotzdem aktiv; bestehende Zuweisungen/Labels erscheinen als leer und es kann niemand bzw. kein Label ausgewählt werden. `labels` wird in derselben Seite bereits für den Filter geladen, jedoch hier nicht weitergereicht.
- Beleg: `CardDetailModal.tsx:944–945` Defaults; `AssigneeSection`/`LabelSection` berechnen ihre sichtbaren Werte als `members.filter(...)`/`boardLabels.filter(...)`. BoardListPage hat keine membersApi-Abfrage. Bestands-Tests der Listen-Seite prüfen Label-Filter, nicht die echte Edit-Auswahl.
- Fix: Projektmitglieder laden, vorhandene Labels übergeben und Bearbeitungsoptionen bis zu vollständigem Kontext sperren bzw. bei Ladefehler lesend lassen (bereits vorhandenes Muster CardNumberSearch).

## Bekannte Punkte und Abgrenzungen

- #783 bestätigt: EpicsPage lädt ohne Loading-/Fehlerzustand, `then`-Ketten haben keinen Fehlerpfad; initial bzw. bei Fehler erscheint „Noch keine Vorhaben.“. Nicht als neuer Fund gezählt.
- #790 bestätigt als bestehende Querschnittslücke: viele Mutationen haben keine oder generische Meldungen, z. B. BoardView Move rollt ohne Hinweis zurück, BoardPage Rename lässt Promise-Rejection durch. Kein zusätzlicher pauschaler neuer Fehlerbehandlungsfund.
- #787: Präfixe [Fachlich]/[Plan] werden für Zusammensetzung erkannt, Statuswechsel bleibt an Boardspalten gebunden; als bekannte fachliche Weiterentwicklung behandelt, kein zusätzlicher Bug behauptet.
- Pool-Delete/Archive-Probleme sind beim Backend-Reviewer. Die Pool-Oberfläche bietet derzeit keinen direkten Delete-Pfad; kein duplizierter Frontend-Befund.
- Projektinterner Transfer ist serverseitig für CARD_MOVE freigegeben, daher IdeaPlanningBoard-Transfer für MEMBER kein Rechte-Bypass. Cross-Projekt bleibt serverseitig Owner/Admin-geprüft.
- Nachtlauf-null/undefined-Kontrakt wurde geprüft: `ausSicht` normalisiert die nullable Felder korrekt, der alte #734-Verdacht trifft nicht mehr zu.
- CardNumberSearch lädt gesonderten Bearbeitungskontext und verwirft veraltete Kontextantworten; fehlende Boardvorräte sperren Labels/Epics. Kein dortiger pauschaler Datenverlustverdacht.

## Prüfumfang und Grenzen

Gelesen: AGENTS.md, vorhandene CLAUDE-Workflow-/React-/Security-/Design-Guides (Codex-Dateien fehlen), komplette API-Typen/Wrapper, sämtliche Pages und Auth-/Routingpfade, große Interaktionskomponenten (BoardView, CardDetailModal, IdeaPlanningBoard, CardNumberSearch, BoardSwitcher, AppShell, Transfer, Trash, Labels, Import, Tabellen, Herkunftsbaum), zentrale State-/SSE-/Storage-/Filter-/Sortier-/Markdown-/Nachtlauf-Helfer sowie Frontend-Package-/Vite-/TS-/ESLint-/Stryker-Konfiguration. Bestands-Tests risikoorientiert auf relevante Szenarien und Lücken geprüft, nicht sämtliche Testzeilen einzeln nachgerechnet. Kleine reine Darstellungs-/Themehelfer wurden über Aufrufer und Guards quergelesen. Backend gezielt für Mutation/Transfer/Nachtlauf-Verträge bestätigt.

Keine Browser-End-to-End-Sitzung und keine echte Mehrbenutzer-/Netzwerklatenzsimulation. Volle Build/Lint/Tests führt der Hauptagent aus. Fünf isolierte RTL-Reproduktionen liefen gegen die unveränderten Originalquellen (Vitest 2.1.9, jsdom): **5 bestanden**, wobei die Assertions ausdrücklich das fehlerhafte Verhalten belegen. Artefakte: `/tmp/kanban-frontend-review-check/reproductions.test.tsx`, `board-race.test.tsx`, `night-cache.test.tsx` und `vitest.config.mjs`. Aufruf: `node /Users/manfredwolff/ki-projects/kanban-kit/frontend/node_modules/vitest/vitest.mjs run --config /tmp/kanban-frontend-review-check/vitest.config.mjs` aus `/tmp/kanban-frontend-review-check`.

Die übrigen Befunde sind durch konkrete Codepfade/Vertragsabgleiche bestätigt, nicht als ausgeführte E2E-Reproduktionen dargestellt. Keine Dependency-CVE-Recherche vorgenommen.
